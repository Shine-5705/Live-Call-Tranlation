import { WebSocket, WebSocketServer } from "ws";
import type { IncomingMessage } from "http";
import { verifyToken, getIceServers } from "../auth/tokenService.js";

interface Peer {
  ws: WebSocket;
  peerId: string;
  displayName: string;
}

interface Room {
  peers: Map<string, Peer>;
}

const rooms = new Map<string, Room>();

function getOrCreateRoom(roomId: string): Room {
  let room = rooms.get(roomId);
  if (!room) {
    room = { peers: new Map() };
    rooms.set(roomId, room);
  }
  return room;
}

function broadcast(room: Room, message: object, excludePeerId?: string) {
  const data = JSON.stringify(message);
  for (const [id, peer] of room.peers) {
    if (id !== excludePeerId && peer.ws.readyState === WebSocket.OPEN) {
      peer.ws.send(data);
    }
  }
}

function send(ws: WebSocket, message: object) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(message));
  }
}

export function attachSignalingServer(wss: WebSocketServer) {
  wss.on("connection", (ws: WebSocket, req: IncomingMessage) => {
    const url = new URL(req.url ?? "/", "http://localhost");
    const token = url.searchParams.get("token");
    if (!token) {
      ws.close(4001, "Missing token");
      return;
    }

    let payload;
    try {
      payload = verifyToken(token);
    } catch {
      ws.close(4003, "Invalid token");
      return;
    }

    const { roomId, peerId, displayName } = payload;
    const room = getOrCreateRoom(roomId);

    if (room.peers.has(peerId)) {
      ws.close(4009, "Peer already connected");
      return;
    }

    const peer: Peer = { ws, peerId, displayName };
    room.peers.set(peerId, peer);

    send(ws, {
      type: "room-ready",
      iceServers: getIceServers(),
      peerCount: room.peers.size,
    });

    if (room.peers.size === 1) {
      send(ws, { type: "waiting-for-peer" });
    } else if (room.peers.size === 2) {
      broadcast(room, { type: "peer-joined", peerId, displayName }, peerId);
      broadcast(room, { type: "call-connected" });
    } else {
      send(ws, { type: "error", message: "Room full (max 2 peers)" });
      room.peers.delete(peerId);
      ws.close(4002, "Room full");
      return;
    }

    ws.on("message", (raw) => {
      let msg: { type: string; payload?: Record<string, unknown> };
      try {
        msg = JSON.parse(raw.toString());
      } catch {
        return;
      }

      switch (msg.type) {
        case "join":
          break;
        case "offer":
        case "answer":
        case "ice-candidate":
          for (const [id, other] of room.peers) {
            if (id !== peerId && other.ws.readyState === WebSocket.OPEN) {
              other.ws.send(JSON.stringify({ type: msg.type, payload: msg.payload }));
            }
          }
          break;
      }
    });

    ws.on("close", () => {
      room.peers.delete(peerId);
      broadcast(room, { type: "peer-left", peerId });
      if (room.peers.size === 0) {
        rooms.delete(roomId);
      }
    });
  });
}
