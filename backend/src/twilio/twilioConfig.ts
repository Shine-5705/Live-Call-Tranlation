function env(key: string, fallback = ""): string {
  return (process.env[key] ?? fallback).trim();
}

export const twilioConfig = {
  accountSid: env("TWILIO_ACCOUNT_SID"),
  authToken: env("TWILIO_AUTH_TOKEN"),
  apiKey: env("TWILIO_API_KEY"),
  apiSecret: env("TWILIO_API_SECRET"),
  phoneNumber: env("TWILIO_PHONE_NUMBER"),
  twimlAppSid: env("TWILIO_TWIML_APP_SID"),
  publicBaseUrl: env("PUBLIC_BASE_URL", `http://localhost:${env("PORT", "3000")}`),
  disclosureMessage:
    env("PSTN_DISCLOSURE_MESSAGE") ||
    "This call includes real-time AI translation. Your voice will be processed by automated speech services.",
};

export function isTwilioConfigured(): boolean {
  return Boolean(
    twilioConfig.accountSid &&
      twilioConfig.authToken &&
      twilioConfig.apiKey &&
      twilioConfig.apiSecret &&
      twilioConfig.phoneNumber &&
      twilioConfig.twimlAppSid
  );
}

export function mediaStreamWsUrl(sessionId: string, leg: "client" | "pstn"): string {
  const base = twilioConfig.publicBaseUrl
    .replace(/^http:/, "ws:")
    .replace(/^https:/, "wss:");
  return `${base}/media?sessionId=${encodeURIComponent(sessionId)}&leg=${leg}`;
}
