import type { WebSocket } from "ws";
import type { WebSocketServer } from "ws";
import type { IncomingMessage } from "http";
import {
  attachLegSocket,
  detachLegSocket,
  endSession,
  getOtherLegWs,
  getSession,
  type CallLeg,
} from "./sessionManager.js";
import {
  createLegPipeline,
  forwardMulawToStt,
  type LegPipeline,
} from "../pipeline/telephonyPipeline.js";

interface StreamState {
  sessionId: string;
  leg: CallLeg;
  pipeline: LegPipeline | null;
}

export function attachMediaStreamServer(wss: WebSocketServer) {
  wss.on("connection", (ws: WebSocket, req: IncomingMessage) => {
    const url = new URL(req.url ?? "/", "http://localhost");
    const sessionId = url.searchParams.get("sessionId");
    const leg = url.searchParams.get("leg") as CallLeg | null;

    if (!sessionId || (leg !== "client" && leg !== "pstn")) {
      ws.close(4001, "Missing sessionId or leg");
      return;
    }

    const session = getSession(sessionId);
    if (!session) {
      ws.close(4004, "Session not found");
      return;
    }

    const state: StreamState = { sessionId, leg, pipeline: null };
    let streamSid = "";

    ws.on("message", (raw) => {
      let msg: {
        event: string;
        streamSid?: string;
        start?: { streamSid?: string; callSid?: string };
        media?: { payload?: string; track?: string };
      };

      try {
        msg = JSON.parse(raw.toString());
      } catch {
        return;
      }

      switch (msg.event) {
        case "connected":
          break;

        case "start":
          streamSid = msg.start?.streamSid ?? msg.streamSid ?? "";
          if (msg.start?.callSid) {
            if (leg === "client") session.clientCallSid = msg.start.callSid;
            else session.pstnCallSid = msg.start.callSid;
          }

          attachLegSocket(sessionId, leg, ws, streamSid);

          state.pipeline = createLegPipeline(session, leg, () => {
            const other = getOtherLegWs(session, leg);
            return other ? { ws: other.ws, streamSid: other.streamSid } : null;
          });

          console.log(`[media] ${leg} stream connected session=${sessionId}`);
          break;

        case "media": {
          const payload = msg.media?.payload;
          if (!payload || !state.pipeline) break;
          const mulaw = Buffer.from(payload, "base64");
          forwardMulawToStt(state.pipeline, mulaw);
          break;
        }

        case "stop":
          console.log(`[media] ${leg} stream stopped session=${sessionId}`);
          state.pipeline?.stt.close();
          detachLegSocket(sessionId, leg);
          break;
      }
    });

    ws.on("close", () => {
      state.pipeline?.stt.close();
      detachLegSocket(sessionId, leg);
      const s = getSession(sessionId);
      if (s && !s.clientWs && !s.pstnWs) {
        endSession(sessionId);
      }
    });
  });
}
