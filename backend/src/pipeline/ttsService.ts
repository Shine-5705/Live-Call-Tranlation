import { synthesizeSpeech as deepgramTts } from "./deepgram.js";
import { synthesizeViaGoogle } from "./googleServices.js";

function env(key: string, fallback = ""): string {
  return (process.env[key] ?? fallback).trim();
}

export async function synthesizeSpeech(
  text: string,
  language: string
): Promise<{ audio: Buffer | null; sampleRate: number }> {
  const provider = env("TTS_PROVIDER", "google").toLowerCase();

  if (provider === "google" && env("GOOGLE_API_KEY")) {
    const sampleRate = parseInt(env("GOOGLE_TTS_SAMPLE_RATE", "24000"), 10);
    const audio = await synthesizeViaGoogle(text, language);
    return { audio, sampleRate };
  }

  const audio = await deepgramTts(text, language);
  return { audio, sampleRate: 24000 };
}
