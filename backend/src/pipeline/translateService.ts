import { translateViaGoogle } from "./googleServices.js";
import { translateText as geminiTranslate } from "./geminiTranslate.js";

function env(key: string, fallback = ""): string {
  return (process.env[key] ?? fallback).trim();
}

export async function translateText(
  text: string,
  sourceLang: string,
  targetLang: string
): Promise<string> {
  const provider = env("TRANSLATION_PROVIDER", "gemini").toLowerCase();

  if (provider === "google" && env("GOOGLE_API_KEY")) {
    return translateViaGoogle(text, sourceLang, targetLang);
  }

  return geminiTranslate(text, sourceLang, targetLang);
}
