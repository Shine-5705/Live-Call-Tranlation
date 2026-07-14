import type { WebSocketServer } from "ws";
import type { IncomingMessage } from "http";
import type { WebSocket } from "ws";
import { subscribeToEvents, unsubscribeFromEvents } from "./sessionManager.js";

export function attachCallEventsServer(wss: WebSocketServer) {
  wss.on("connection", (ws: WebSocket, req: IncomingMessage) => {
    const url = new URL(req.url ?? "/", "http://localhost");
    const sessionId = url.searchParams.get("sessionId");

    if (!sessionId || !subscribeToEvents(sessionId, ws)) {
      ws.close(4004, "Session not found");
      return;
    }

    ws.on("close", () => {
      unsubscribeFromEvents(sessionId, ws);
    });
  });
}
