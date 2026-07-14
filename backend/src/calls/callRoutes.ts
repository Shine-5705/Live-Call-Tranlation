import { Router } from "express";
import rateLimit from "express-rate-limit";
import { createSession, endSession, getSession } from "./sessionManager.js";
import { createTwilioAccessToken, getTwilioClient } from "../twilio/twilioClient.js";
import { isTwilioConfigured, twilioConfig } from "../twilio/twilioConfig.js";
import { verifyToken } from "../auth/tokenService.js";

const callLimiter = rateLimit({
  windowMs: 60_000,
  max: 10,
  message: { error: "Too many call requests" },
});

export const callRouter = Router();

callRouter.post("/start", callLimiter, async (req, res) => {
  if (!isTwilioConfigured()) {
    res.status(503).json({
      error: "Twilio not configured",
      hint: "Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_API_KEY, TWILIO_API_SECRET, TWILIO_PHONE_NUMBER, TWILIO_TWIML_APP_SID, PUBLIC_BASE_URL",
    });
    return;
  }

  const authHeader = req.headers.authorization;
  const bearer = authHeader?.startsWith("Bearer ") ? authHeader.slice(7) : null;
  if (!bearer) {
    res.status(401).json({ error: "Authorization required" });
    return;
  }

  try {
    verifyToken(bearer);
  } catch {
    res.status(403).json({ error: "Invalid token" });
    return;
  }

  const {
    toNumber,
    sourceLanguage = "hi",
    targetLanguage = "hi",
    remoteLanguage = "en",
    identity = "app-user",
  } = req.body ?? {};

  if (!toNumber || typeof toNumber !== "string") {
    res.status(400).json({ error: "toNumber is required (E.164 format, e.g. +919876543210)" });
    return;
  }

  const normalized = toNumber.trim();
  if (!/^\+[1-9]\d{6,14}$/.test(normalized)) {
    res.status(400).json({ error: "toNumber must be E.164 format (e.g. +919876543210)" });
    return;
  }

  const session = createSession({
    toNumber: normalized,
    identity: String(identity),
    sourceLanguage: String(sourceLanguage),
    targetLanguage: String(targetLanguage),
    remoteLanguage: String(remoteLanguage),
  });

  try {
    const client = getTwilioClient();
    const pstnCall = await client.calls.create({
      to: normalized,
      from: twilioConfig.phoneNumber,
      url: `${twilioConfig.publicBaseUrl}/twiml/pstn?sessionId=${session.sessionId}`,
      method: "POST",
      statusCallback: `${twilioConfig.publicBaseUrl}/v1/calls/status?sessionId=${session.sessionId}`,
      statusCallbackMethod: "POST",
      statusCallbackEvent: ["completed", "failed", "no-answer", "busy"],
    });
    session.pstnCallSid = pstnCall.sid;
  } catch (err) {
    endSession(session.sessionId);
    console.error("[calls] PSTN dial failed", err);
    res.status(502).json({
      error: "Failed to place PSTN call",
      detail: err instanceof Error ? err.message : "unknown",
    });
    return;
  }

  const accessToken = createTwilioAccessToken(session.identity);

  res.json({
    sessionId: session.sessionId,
    accessToken,
    pstnCallSid: session.pstnCallSid,
    expiresIn: 3600,
    params: { sessionId: session.sessionId },
  });
});

callRouter.post("/status", (req, res) => {
  const sessionId = req.query.sessionId ?? req.body.sessionId;
  const callStatus = req.body.CallStatus;
  if (sessionId && (callStatus === "completed" || callStatus === "failed" || callStatus === "busy" || callStatus === "no-answer")) {
    endSession(String(sessionId));
  }
  res.sendStatus(200);
});

callRouter.post("/end", (req, res) => {
  const { sessionId } = req.body ?? {};
  if (!sessionId) {
    res.status(400).json({ error: "sessionId required" });
    return;
  }

  const session = getSession(String(sessionId));
  if (session) {
    const client = getTwilioClient();
    if (session.pstnCallSid) {
      client.calls(session.pstnCallSid).update({ status: "completed" }).catch(() => {});
    }
    if (session.clientCallSid) {
      client.calls(session.clientCallSid).update({ status: "completed" }).catch(() => {});
    }
    endSession(session.sessionId);
  }

  res.json({ ok: true });
});

callRouter.get("/:sessionId", (req, res) => {
  const session = getSession(req.params.sessionId);
  if (!session) {
    res.status(404).json({ error: "Session not found" });
    return;
  }
  res.json({
    sessionId: session.sessionId,
    status: session.status,
    toNumber: session.toNumber,
    sourceLanguage: session.sourceLanguage,
    targetLanguage: session.targetLanguage,
    remoteLanguage: session.remoteLanguage,
  });
});
