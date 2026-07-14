import twilio from "twilio";
import { twilioConfig } from "./twilioConfig.js";

const AccessToken = twilio.jwt.AccessToken;
const VoiceGrant = AccessToken.VoiceGrant;

export function createTwilioAccessToken(identity: string): string {
  const token = new AccessToken(
    twilioConfig.accountSid,
    twilioConfig.apiKey,
    twilioConfig.apiSecret,
    { identity, ttl: 3600 }
  );

  const grant = new VoiceGrant({
    outgoingApplicationSid: twilioConfig.twimlAppSid,
    incomingAllow: false,
  });
  token.addGrant(grant);
  return token.toJwt();
}

export function getTwilioClient() {
  return twilio(twilioConfig.accountSid, twilioConfig.authToken);
}
