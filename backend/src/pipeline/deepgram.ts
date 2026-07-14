import WebSocket from "ws";

export interface SttResult {
  text: string;
  detectedLanguage?: string;
  isFinal: boolean;
}

export interface SttCallbacks {
  onPartial: (result: SttResult) => void;
  onFinal: (result: SttResult) => void;
  onError: (error: string) => void;
}

export interface SttConnectOptions {
  language?: string;
  detectLanguage?: boolean;
  multilingual?: boolean;
}

export class DeepgramSttSession {
  private ws: WebSocket | null = null;
  private closed = false;
  private encoding: "linear16" | "mulaw" = "linear16";
  private sampleRate = 16000;

  constructor(private callbacks: SttCallbacks) {}

  /** 8kHz mu-law for PSTN / Twilio Media Streams. */
  connectTelephony(language: string) {
    this.encoding = "mulaw";
    this.sampleRate = 8000;
    this.connectInternal(language, {});
  }

  connect(language: string, options: SttConnectOptions = {}) {
    this.encoding = "linear16";
    this.sampleRate = 16000;
    this.connectInternal(language, options);
  }

  private connectInternal(language: string, options: SttConnectOptions) {
    const apiKey = process.env.DEEPGRAM_API_KEY;
    if (!apiKey) {
      this.callbacks.onError("DEEPGRAM_API_KEY not configured");
      return;
    }

    const model = process.env.DEEPGRAM_STT_MODEL ?? "nova-2";
    const params = new URLSearchParams({
      model,
      encoding: this.encoding,
      sample_rate: String(this.sampleRate),
      channels: "1",
      interim_results: "true",
      punctuate: "true",
      endpointing: "300",
    });

    if (options.multilingual) {
      params.set("language", "multi");
    } else if (options.detectLanguage) {
      params.set("detect_language", "true");
      params.set("language", language);
    } else {
      params.set("language", language);
    }

    this.ws = new WebSocket(`wss://api.deepgram.com/v1/listen?${params}`, {
      headers: { Authorization: `Token ${apiKey}` },
    });

    this.ws.on("message", (data) => {
      try {
        const msg = JSON.parse(data.toString());
        const alt = msg.channel?.alternatives?.[0];
        const transcript = alt?.transcript?.trim();
        if (!transcript) return;

        const detectedLanguage =
          alt?.languages?.[0] ??
          msg.channel?.detected_language ??
          alt?.detected_language;

        const result: SttResult = {
          text: transcript,
          detectedLanguage: detectedLanguage
            ? String(detectedLanguage).toLowerCase().split("-")[0]
            : undefined,
          isFinal: Boolean(msg.is_final),
        };

        if (result.isFinal) {
          this.callbacks.onFinal(result);
        } else {
          this.callbacks.onPartial(result);
        }
      } catch (err) {
        console.error("[deepgram-stt] parse error", err);
      }
    });

    this.ws.on("error", (err) => {
      this.callbacks.onError(err.message);
    });

    this.ws.on("close", () => {
      this.closed = true;
    });
  }

  sendAudio(pcm: Buffer) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(pcm);
    }
  }

  close() {
    if (this.ws && !this.closed) {
      this.ws.close();
    }
    this.ws = null;
  }
}

export async function synthesizeSpeech(text: string, language: string): Promise<Buffer | null> {
  const apiKey = process.env.DEEPGRAM_API_KEY;
  if (!apiKey || !text.trim()) return null;

  const voice = pickAuraVoice(language);

  const response = await fetch(
    `https://api.deepgram.com/v1/speak?model=${voice}&encoding=linear16&sample_rate=24000`,
    {
      method: "POST",
      headers: {
        Authorization: `Token ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ text }),
    }
  );

  if (!response.ok) {
    console.error("[deepgram-tts] failed:", response.status, await response.text());
    return null;
  }

  const arrayBuffer = await response.arrayBuffer();
  return Buffer.from(arrayBuffer);
}

function pickAuraVoice(language: string): string {
  const code = language.toLowerCase().split("-")[0];
  const map: Record<string, string> = {
    en: "aura-asteria-en",
    hi: "aura-asteria-en",
    es: "aura-luna-es",
    fr: "aura-asteria-fr",
    de: "aura-asteria-de",
    ja: "aura-asteria-ja",
    ko: "aura-asteria-ko",
    zh: "aura-asteria-zh",
    ar: "aura-asteria-ar",
    pt: "aura-asteria-pt",
    ru: "aura-asteria-ru",
    ta: "aura-asteria-en",
    te: "aura-asteria-en",
    bn: "aura-asteria-en",
    mr: "aura-asteria-en",
  };
  return map[code] ?? "aura-asteria-en";
}
