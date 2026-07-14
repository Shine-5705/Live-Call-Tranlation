import { WebSocket, WebSocketServer } from "ws";
import type { IncomingMessage } from "http";
import { verifyToken } from "../auth/tokenService.js";
import { DeepgramSttSession, type SttResult } from "./deepgram.js";
import { translateText } from "./translateService.js";
import { synthesizeSpeech } from "./ttsService.js";

type PipelineMode = "webrtc" | "phone";

interface PipelinePeer {
  ws: WebSocket;
  peerId: string;
  displayName: string;
  mode: PipelineMode;
  sourceLanguage: string;
  targetLanguage: string;
  remoteLanguage: string;
  listenLanguage: string;
  stt: DeepgramSttSession | null;
}

interface PipelineRoom {
  peers: Map<string, PipelinePeer>;
}

const pipelineRooms = new Map<string, PipelineRoom>();

function getPipelineRoom(roomId: string): PipelineRoom {
  let room = pipelineRooms.get(roomId);
  if (!room) {
    room = { peers: new Map() };
    pipelineRooms.set(roomId, room);
  }
  return room;
}

function getOtherPeer(room: PipelineRoom, peerId: string): PipelinePeer | undefined {
  for (const [id, peer] of room.peers) {
    if (id !== peerId) return peer;
  }
  return undefined;
}

function sendJson(ws: WebSocket, msg: object) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(msg));
  }
}

function normalizeLang(lang: string): string {
  return lang.toLowerCase().split("-")[0];
}

function guessDirection(
  result: SttResult,
  myLanguage: string,
  remoteLanguage: string
): "incoming" | "outgoing" {
  const detected = result.detectedLanguage
    ? normalizeLang(result.detectedLanguage)
    : undefined;

  if (detected === normalizeLang(remoteLanguage)) return "incoming";
  if (detected === normalizeLang(myLanguage)) return "outgoing";

  // Heuristic: if text contains mostly Latin chars, likely English (remote for hi user)
  const latin = (result.text.match(/[a-zA-Z]/g) ?? []).length;
  const devanagari = (result.text.match(/[\u0900-\u097F]/g) ?? []).length;
  if (devanagari > latin) return "outgoing";
  if (latin > devanagari) return "incoming";

  return "incoming";
}

function createWebrtcSttSession(
  peer: PipelinePeer,
  room: PipelineRoom
): DeepgramSttSession {
  const stt = new DeepgramSttSession({
    onPartial: (result) => {
      const listener = getOtherPeer(room, peer.peerId);
      if (!listener) return;
      const partial = {
        type: "caption",
        original: result.text,
        translated: "",
        isFinal: false,
        speakerId: peer.peerId,
        direction: "incoming",
      };
      sendJson(listener.ws, partial);
      sendJson(peer.ws, partial);
    },
    onFinal: async (result) => {
      const listener = getOtherPeer(room, peer.peerId);
      if (!listener) return;

      try {
        const translated = await translateText(
          result.text,
          peer.sourceLanguage,
          listener.targetLanguage
        );

        const caption = {
          type: "caption" as const,
          original: result.text,
          translated,
          isFinal: true,
          speakerId: peer.peerId,
          direction: "incoming",
        };

        sendJson(listener.ws, caption);
        sendJson(peer.ws, caption);

        const { audio, sampleRate } = await synthesizeSpeech(
          translated,
          listener.targetLanguage
        );
        if (audio) {
          sendJson(listener.ws, {
            type: "tts",
            audio: audio.toString("base64"),
            sampleRate,
          });
        }
      } catch (err) {
        console.error("[pipeline] translation error", err);
        sendJson(peer.ws, {
          type: "error",
          message: "Translation failed",
        });
      }
    },
    onError: (error) => {
      sendJson(peer.ws, { type: "error", message: error });
    },
  });

  stt.connect(peer.sourceLanguage);
  return stt;
}

function createPhoneSttSession(peer: PipelinePeer): DeepgramSttSession {
  const stt = new DeepgramSttSession({
    onPartial: (result) => {
      const direction = guessDirection(
        result,
        peer.sourceLanguage,
        peer.remoteLanguage
      );
      sendJson(peer.ws, {
        type: "caption",
        original: result.text,
        translated: "",
        isFinal: false,
        direction,
      });
    },
    onFinal: async (result) => {
      try {
        const direction = guessDirection(
          result,
          peer.sourceLanguage,
          peer.remoteLanguage
        );

        if (direction === "incoming") {
          const translated = await translateText(
            result.text,
            peer.remoteLanguage,
            peer.listenLanguage
          );

          sendJson(peer.ws, {
            type: "caption",
            original: result.text,
            translated,
            isFinal: true,
            direction: "incoming",
          });

          const { audio, sampleRate } = await synthesizeSpeech(
            translated,
            peer.listenLanguage
          );
          if (audio) {
            sendJson(peer.ws, {
              type: "tts",
              audio: audio.toString("base64"),
              sampleRate,
            });
          }
        } else {
          const translated = await translateText(
            result.text,
            peer.sourceLanguage,
            peer.remoteLanguage
          );

          sendJson(peer.ws, {
            type: "caption",
            original: result.text,
            translated,
            isFinal: true,
            direction: "outgoing",
            label: "Say to them",
          });
        }
      } catch (err) {
        console.error("[pipeline] phone translation error", err);
        sendJson(peer.ws, {
          type: "error",
          message: "Translation failed",
        });
      }
    },
    onError: (error) => {
      sendJson(peer.ws, { type: "error", message: error });
    },
  });

  stt.connect(peer.remoteLanguage, { multilingual: true });
  return stt;
}

export function attachPipelineServer(wss: WebSocketServer) {
  wss.on("connection", (ws: WebSocket, req: IncomingMessage) => {
    const url = new URL(req.url ?? "/", "http://localhost");
    const token = url.searchParams.get("token");
    const roomId = url.searchParams.get("roomId");
    const peerId = url.searchParams.get("peerId");

    if (!token || !roomId || !peerId) {
      ws.close(4001, "Missing params");
      return;
    }

    let payload;
    try {
      payload = verifyToken(token);
    } catch {
      ws.close(4003, "Invalid token");
      return;
    }

    if (payload.roomId !== roomId || payload.peerId !== peerId) {
      ws.close(4003, "Token does not match room/peer");
      return;
    }

    const room = getPipelineRoom(roomId);
    const peer: PipelinePeer = {
      ws,
      peerId,
      displayName: payload.displayName,
      mode: roomId.startsWith("phone-") ? "phone" : "webrtc",
      sourceLanguage: "hi",
      targetLanguage: "en",
      remoteLanguage: "en",
      listenLanguage: "hi",
      stt: null,
    };
    room.peers.set(peerId, peer);

    sendJson(ws, {
      type: "pipeline-ready",
      peerId,
      roomId,
      mode: peer.mode,
    });

    ws.on("message", (raw, isBinary) => {
      if (isBinary) {
        const pcm = Buffer.from(raw as Buffer);
        peer.stt?.sendAudio(pcm);
        return;
      }

      let msg: {
        type: string;
        mode?: PipelineMode;
        sourceLanguage?: string;
        targetLanguage?: string;
        remoteLanguage?: string;
        listenLanguage?: string;
      };
      try {
        msg = JSON.parse(raw.toString());
      } catch {
        return;
      }

      if (msg.type === "config") {
        peer.mode = msg.mode ?? peer.mode;
        peer.sourceLanguage = msg.sourceLanguage ?? "hi";
        peer.targetLanguage = msg.targetLanguage ?? "en";
        peer.remoteLanguage = msg.remoteLanguage ?? peer.targetLanguage;
        peer.listenLanguage = msg.listenLanguage ?? peer.sourceLanguage;

        peer.stt?.close();
        peer.stt =
          peer.mode === "phone"
            ? createPhoneSttSession(peer)
            : createWebrtcSttSession(peer, room);

        sendJson(ws, {
          type: "translation-status",
          running: true,
          mode: peer.mode,
          sourceLanguage: peer.sourceLanguage,
          targetLanguage: peer.targetLanguage,
          remoteLanguage: peer.remoteLanguage,
          listenLanguage: peer.listenLanguage,
        });
      }
    });

    ws.on("close", () => {
      peer.stt?.close();
      room.peers.delete(peerId);
      if (room.peers.size === 0) {
        pipelineRooms.delete(roomId);
      }
    });
  });
}
