import { synthesizeViaGoogle } from "./googleServices.js";
import { synthesizeSpeech as deepgramTts } from "./deepgram.js";
import { linear16ToMulaw, mulawToLinear16 } from "./telephonyAudio.js";

const TELEPHONY_SAMPLE_RATE = 8000;

function env(key: string, fallback = ""): string {
  return (process.env[key] ?? fallback).trim();
}

export async function synthesizeTelephonySpeech(
  text: string,
  language: string
): Promise<Buffer | null> {
  if (!text.trim()) return null;

  const provider = env("TTS_PROVIDER", "google").toLowerCase();
  let pcm: Buffer | null = null;

  if (provider === "google" && env("GOOGLE_API_KEY")) {
    pcm = await synthesizeViaGoogle(text, language, TELEPHONY_SAMPLE_RATE);
  } else {
    const raw = await deepgramTts(text, language);
    if (raw) {
      // Deepgram returns 24kHz — downsample to 8kHz
      pcm = downsample24to8(raw);
    }
  }

  return pcm;
}

function downsample24to8(pcm24: Buffer): Buffer {
  const samples24 = pcm24.length / 2;
  const ratio = 3;
  const outSamples = Math.floor(samples24 / ratio);
  const out = Buffer.alloc(outSamples * 2);
  for (let i = 0; i < outSamples; i++) {
    out.writeInt16LE(pcm24.readInt16LE(i * ratio * 2), i * 2);
  }
  return out;
}

/** Synthesize disclosure message as 8kHz linear16 PCM. */
export async function synthesizeDisclosure(language = "en"): Promise<Buffer | null> {
  const message =
    env("PSTN_DISCLOSURE_MESSAGE") ||
    "This call includes real-time AI translation. Your voice will be processed by automated speech services.";
  return synthesizeTelephonySpeech(message, language);
}

export function pcmToMulawBase64(pcm: Buffer): string {
  return linear16ToMulaw(pcm).toString("base64");
}

export { mulawToLinear16, TELEPHONY_SAMPLE_RATE };
