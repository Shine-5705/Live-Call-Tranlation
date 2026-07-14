import WebSocket from "ws";
import { DeepgramSttSession } from "./deepgram.js";
import { translateText } from "./translateService.js";
import { synthesizeTelephonySpeech } from "./telephonyTts.js";
import { linear16ToMulaw } from "./telephonyAudio.js";
import type { CallLeg, CallSession } from "../calls/sessionManager.js";
import { broadcastCaption } from "../calls/sessionManager.js";

export interface LegPipeline {
  stt: DeepgramSttSession;
  sourceLanguage: string;
  targetLanguage: string;
  fromLeg: CallLeg;
}

function injectMulawAudio(
  targetWs: WebSocket,
  streamSid: string,
  mulaw: Buffer
) {
  if (targetWs.readyState !== WebSocket.OPEN) return;

  const CHUNK = 160; // 20ms at 8kHz
  for (let offset = 0; offset < mulaw.length; offset += CHUNK) {
    const slice = mulaw.subarray(offset, Math.min(offset + CHUNK, mulaw.length));
    targetWs.send(
      JSON.stringify({
        event: "media",
        streamSid,
        media: { payload: slice.toString("base64") },
      })
    );
  }
}

export function createLegPipeline(
  session: CallSession,
  fromLeg: CallLeg,
  getTarget: () => { ws: WebSocket; streamSid: string } | null
): LegPipeline {
  const isClient = fromLeg === "client";
  const sourceLanguage = isClient ? session.sourceLanguage : session.remoteLanguage;
  const targetLanguage = isClient ? session.remoteLanguage : session.targetLanguage;

  const stt = new DeepgramSttSession({
    onPartial: () => {
      /* captions sent via separate channel if needed */
    },
    onFinal: async (result) => {
      const text = result.text.trim();
      if (!text) return;

      const direction = isClient ? "outgoing" : "incoming";

      try {
        const translated = await translateText(text, sourceLanguage, targetLanguage);

        broadcastCaption(session.sessionId, {
          original: text,
          translated,
          direction,
          isFinal: true,
        });

        const pcm8k = await synthesizeTelephonySpeech(translated, targetLanguage);
        if (!pcm8k) return;

        const mulaw = linear16ToMulaw(pcm8k);
        const target = getTarget();
        if (target) {
          injectMulawAudio(target.ws, target.streamSid, mulaw);
        }
      } catch (err) {
        console.error(`[telephony-pipeline] ${fromLeg} error`, err);
      }
    },
    onError: (error) => {
      console.error(`[telephony-pipeline] STT ${fromLeg}:`, error);
    },
  });

  stt.connectTelephony(sourceLanguage);

  return { stt, sourceLanguage, targetLanguage, fromLeg };
}

export function forwardMulawToStt(pipeline: LegPipeline, mulawPayload: Buffer) {
  pipeline.stt.sendAudio(mulawPayload);
}
