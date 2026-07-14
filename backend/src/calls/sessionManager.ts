import { v4 as uuidv4 } from "uuid";
import type { WebSocket } from "ws";

export type CallLeg = "client" | "pstn";

export interface CaptionEvent {
  original: string;
  translated: string;
  direction: "incoming" | "outgoing";
  isFinal: boolean;
}

export interface CallSession {
  sessionId: string;
  toNumber: string;
  identity: string;
  sourceLanguage: string;
  targetLanguage: string;
  remoteLanguage: string;
  clientCallSid?: string;
  pstnCallSid?: string;
  clientStreamSid?: string;
  pstnStreamSid?: string;
  clientWs?: WebSocket;
  pstnWs?: WebSocket;
  eventSubscribers: Set<WebSocket>;
  status: "starting" | "active" | "ended";
  createdAt: number;
}

const sessions = new Map<string, CallSession>();

export function createSession(params: {
  toNumber: string;
  identity: string;
  sourceLanguage: string;
  targetLanguage: string;
  remoteLanguage: string;
}): CallSession {
  const session: CallSession = {
    sessionId: uuidv4(),
    toNumber: params.toNumber,
    identity: params.identity,
    sourceLanguage: params.sourceLanguage,
    targetLanguage: params.targetLanguage,
    remoteLanguage: params.remoteLanguage,
    eventSubscribers: new Set(),
    status: "starting",
    createdAt: Date.now(),
  };
  sessions.set(session.sessionId, session);
  return session;
}

export function getSession(sessionId: string): CallSession | undefined {
  return sessions.get(sessionId);
}

export function endSession(sessionId: string) {
  const session = sessions.get(sessionId);
  if (!session) return;
  session.status = "ended";
  session.clientWs?.close();
  session.pstnWs?.close();
  sessions.delete(sessionId);
}

export function attachLegSocket(
  sessionId: string,
  leg: CallLeg,
  ws: WebSocket,
  streamSid: string
) {
  const session = sessions.get(sessionId);
  if (!session) return;

  if (leg === "client") {
    session.clientWs = ws;
    session.clientStreamSid = streamSid;
  } else {
    session.pstnWs = ws;
    session.pstnStreamSid = streamSid;
  }

  if (session.clientWs && session.pstnWs) {
    session.status = "active";
  }
}

export function detachLegSocket(sessionId: string, leg: CallLeg) {
  const session = sessions.get(sessionId);
  if (!session) return;

  if (leg === "client") {
    session.clientWs = undefined;
    session.clientStreamSid = undefined;
  } else {
    session.pstnWs = undefined;
    session.pstnStreamSid = undefined;
  }
}

export function subscribeToEvents(sessionId: string, ws: WebSocket) {
  const session = sessions.get(sessionId);
  if (!session) return false;
  session.eventSubscribers.add(ws);
  return true;
}

export function unsubscribeFromEvents(sessionId: string, ws: WebSocket) {
  sessions.get(sessionId)?.eventSubscribers.delete(ws);
}

export function broadcastCaption(sessionId: string, caption: CaptionEvent) {
  const session = sessions.get(sessionId);
  if (!session) return;
  const data = JSON.stringify({ type: "caption", ...caption });
  for (const sub of session.eventSubscribers) {
    if (sub.readyState === 1) sub.send(data);
  }
}

export function getOtherLegWs(
  session: CallSession,
  fromLeg: CallLeg
): { ws: WebSocket; streamSid: string } | null {
  if (fromLeg === "client" && session.pstnWs && session.pstnStreamSid) {
    return { ws: session.pstnWs, streamSid: session.pstnStreamSid };
  }
  if (fromLeg === "pstn" && session.clientWs && session.clientStreamSid) {
    return { ws: session.clientWs, streamSid: session.clientStreamSid };
  }
  return null;
}
