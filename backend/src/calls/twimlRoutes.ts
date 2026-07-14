import { Router } from "express";
import twilio from "twilio";
import { getSession } from "./sessionManager.js";
import { mediaStreamWsUrl, twilioConfig } from "../twilio/twilioConfig.js";

const VoiceResponse = twilio.twiml.VoiceResponse;
export const twimlRouter = Router();

/** TwiML for the app user's VoIP leg (Leg A). */
twimlRouter.post("/client", (req, res) => {
  const sessionId =
    req.body.sessionId ?? req.query.sessionId ?? req.body.SessionId;
  const session = sessionId ? getSession(String(sessionId)) : undefined;

  if (!session) {
    res.type("text/xml").send("<Response><Say>Session not found.</Say><Hangup/></Response>");
    return;
  }

  if (req.body.CallSid) {
    session.clientCallSid = req.body.CallSid;
  }

  const twiml = new VoiceResponse();
  const connect = twiml.connect();
  connect.stream({
    url: mediaStreamWsUrl(session.sessionId, "client"),
  });

  res.type("text/xml").send(twiml.toString());
});

/** TwiML for the PSTN leg (Leg B) — disclosure then media stream only. */
twimlRouter.post("/pstn", (req, res) => {
  const sessionId = req.query.sessionId ?? req.body.sessionId;
  const session = sessionId ? getSession(String(sessionId)) : undefined;

  if (!session) {
    res.type("text/xml").send("<Response><Hangup/></Response>");
    return;
  }

  if (req.body.CallSid) {
    session.pstnCallSid = req.body.CallSid;
  }

  const twiml = new VoiceResponse();
  twiml.say({ voice: "Polly.Joanna" }, twilioConfig.disclosureMessage);

  const connect = twiml.connect();
  connect.stream({
    url: mediaStreamWsUrl(session.sessionId, "pstn"),
  });

  res.type("text/xml").send(twiml.toString());
});
