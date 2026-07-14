function env(key: string, fallback = ""): string {
  return (process.env[key] ?? fallback).trim();
}

function toBcp47(lang: string): string {
  const code = lang.toLowerCase().split("-")[0];
  const map: Record<string, string> = {
    en: "en-US",
    hi: "hi-IN",
    es: "es-ES",
    fr: "fr-FR",
    de: "de-DE",
    ja: "ja-JP",
    ko: "ko-KR",
    zh: "zh-CN",
    ar: "ar-XA",
    pt: "pt-BR",
    ru: "ru-RU",
    ta: "ta-IN",
    te: "te-IN",
    bn: "bn-IN",
    mr: "mr-IN",
  };
  return map[code] ?? `${code}-${code.toUpperCase()}`;
}

function normalizeLangCode(lang: string): string {
  return lang.toLowerCase().split("-")[0];
}

function googleTtsVoiceForLang(langCode: string): string {
  const fixed = env("GOOGLE_TTS_FIXED_VOICE") || env("GOOGLE_TTS_VOICE");
  if (fixed) return fixed;

  const persona = env("GOOGLE_TTS_VOICE_PERSONA", "Chirp3-HD-Aoede");
  const bcp47 = toBcp47(langCode);
  const langPrefix = bcp47.split("-")[0];

  const voices: Record<string, string> = {
    en: `${langPrefix}-US-Chirp3-HD-Aoede`,
    hi: `${langPrefix}-IN-Chirp3-HD-Aoede`,
    es: `${langPrefix}-ES-Chirp3-HD-Aoede`,
    fr: `${langPrefix}-FR-Chirp3-HD-Aoede`,
    de: `${langPrefix}-DE-Chirp3-HD-Aoede`,
    ja: `${langPrefix}-JP-Chirp3-HD-Aoede`,
    ko: `${langPrefix}-KR-Chirp3-HD-Aoede`,
    zh: `${langPrefix}-CN-Chirp3-HD-Aoede`,
    pt: `${langPrefix}-BR-Chirp3-HD-Aoede`,
    ru: `${langPrefix}-RU-Chirp3-HD-Aoede`,
    ta: `${langPrefix}-IN-Chirp3-HD-Aoede`,
    te: `${langPrefix}-IN-Chirp3-HD-Aoede`,
    bn: `${langPrefix}-IN-Chirp3-HD-Aoede`,
    mr: `${langPrefix}-IN-Chirp3-HD-Aoede`,
    ar: `ar-XA-Chirp3-HD-Aoede`,
  };

  return voices[normalizeLangCode(langCode)] ?? `${bcp47}-${persona}`;
}

export async function translateViaGoogle(
  text: string,
  sourceLang: string,
  targetLang: string
): Promise<string> {
  const apiKey = env("GOOGLE_API_KEY");
  if (!apiKey) {
    throw new Error("GOOGLE_API_KEY not configured");
  }

  const url = `https://translation.googleapis.com/language/translate/v2?key=${encodeURIComponent(apiKey)}`;
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      q: text,
      source: normalizeLangCode(sourceLang),
      target: normalizeLangCode(targetLang),
      format: "text",
    }),
    signal: AbortSignal.timeout(parseInt(env("GOOGLE_TRANSLATE_TIMEOUT_MS", "3000"), 10)),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Google Translate failed: ${response.status} ${body.slice(0, 200)}`);
  }

  const payload = (await response.json()) as {
    data?: { translations?: Array<{ translatedText?: string }> };
  };
  return String(payload?.data?.translations?.[0]?.translatedText ?? "").trim();
}

export async function synthesizeViaGoogle(
  text: string,
  language: string,
  sampleRate = parseInt(env("GOOGLE_TTS_SAMPLE_RATE", "24000"), 10)
): Promise<Buffer | null> {
  const apiKey = env("GOOGLE_API_KEY");
  if (!apiKey || !text.trim()) return null;

  const bcp47 = toBcp47(language);
  const voiceName = googleTtsVoiceForLang(language);
  const rate = sampleRate;

  const url = `https://texttospeech.googleapis.com/v1/text:synthesize?key=${encodeURIComponent(apiKey)}`;
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      input: { text },
      voice: { languageCode: bcp47, name: voiceName },
      audioConfig: {
        audioEncoding: "LINEAR16",
        sampleRateHertz: rate,
      },
    }),
    signal: AbortSignal.timeout(parseInt(env("GOOGLE_TTS_TIMEOUT_MS", "30000"), 10)),
  });

  if (!response.ok) {
    console.error("[google-tts] failed:", response.status, await response.text());
    return null;
  }

  const payload = (await response.json()) as { audioContent?: string };
  if (!payload.audioContent) return null;
  return Buffer.from(payload.audioContent, "base64");
}
