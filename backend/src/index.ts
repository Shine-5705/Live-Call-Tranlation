import "dotenv/config";
import cors from "cors";
import express from "express";
import rateLimit from "express-rate-limit";
import { createServer } from "http";
import { WebSocketServer } from "ws";
import { issueToken } from "./auth/tokenService.js";
import { attachSignalingServer } from "./signaling/signalingServer.js";
import { attachPipelineServer } from "./pipeline/pipelineServer.js";
import { callRouter } from "./calls/callRoutes.js";
import { twimlRouter } from "./calls/twimlRoutes.js";
import { attachMediaStreamServer } from "./calls/mediaStreamHandler.js";
import { attachCallEventsServer } from "./calls/callEventsHandler.js";
import { isTwilioConfigured } from "./twilio/twilioConfig.js";

const PORT = parseInt(process.env.PORT ?? "3000", 10);
const app = express();

app.use(cors());
app.use(express.json({ limit: "1mb" }));
app.use(express.urlencoded({ extended: true }));

const authLimiter = rateLimit({
  windowMs: 60_000,
  max: 30,
  message: { error: "Too many auth requests" },
});

app.get("/health", (_req, res) => {
  res.json({
    status: "ok",
    timestamp: new Date().toISOString(),
    twilio: isTwilioConfigured(),
  });
});

app.post("/auth/token", authLimiter, (req, res) => {
  const { roomId, peerId, displayName, identity } = req.body ?? {};
  const id = roomId ?? peerId ?? identity ?? `user-${Date.now()}`;
  const token = issueToken({
    roomId: String(id),
    peerId: String(peerId ?? id),
    displayName: String(displayName ?? identity ?? "User"),
  });
  res.json({ token, expiresIn: 3600 });
});

app.use("/v1/calls", callRouter);
app.use("/twiml", twimlRouter);

const server = createServer(app);

const signalingWss = new WebSocketServer({ noServer: true });
const pipelineWss = new WebSocketServer({ noServer: true });
const mediaWss = new WebSocketServer({ noServer: true });
const callEventsWss = new WebSocketServer({ noServer: true });

attachSignalingServer(signalingWss);
attachPipelineServer(pipelineWss);
attachMediaStreamServer(mediaWss);
attachCallEventsServer(callEventsWss);

server.on("upgrade", (req, socket, head) => {
  const url = req.url ?? "";
  if (url.startsWith("/signaling")) {
    signalingWss.handleUpgrade(req, socket, head, (ws) => {
      signalingWss.emit("connection", ws, req);
    });
  } else if (url.startsWith("/pipeline")) {
    pipelineWss.handleUpgrade(req, socket, head, (ws) => {
      pipelineWss.emit("connection", ws, req);
    });
  } else if (url.startsWith("/media")) {
    mediaWss.handleUpgrade(req, socket, head, (ws) => {
      mediaWss.emit("connection", ws, req);
    });
  } else if (url.startsWith("/call-events")) {
    callEventsWss.handleUpgrade(req, socket, head, (ws) => {
      callEventsWss.emit("connection", ws, req);
    });
  } else {
    socket.destroy();
  }
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`Live Translation backend listening on http://0.0.0.0:${PORT}`);
  console.log(`  PSTN calls:  POST /v1/calls/start`);
  console.log(`  TwiML:       POST /twiml/client, /twiml/pstn`);
  console.log(`  Media WS:    ws://0.0.0.0:${PORT}/media`);
  console.log(`  Twilio:      ${isTwilioConfigured() ? "configured" : "NOT configured"}`);
  console.log(`  PUBLIC_URL:  ${process.env.PUBLIC_BASE_URL ?? "(not set — required for Twilio webhooks)"}`);
  console.log(`  Translation: ${process.env.TRANSLATION_PROVIDER ?? "gemini"}`);
  console.log(`  TTS:         ${process.env.TTS_PROVIDER ?? "google"}`);
});
