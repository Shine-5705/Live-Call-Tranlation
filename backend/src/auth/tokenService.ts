import jwt from "jsonwebtoken";

const JWT_SECRET = process.env.JWT_SECRET ?? "dev-secret-change-me";
const TOKEN_TTL_SECONDS = 60 * 60;

export interface TokenPayload {
  roomId: string;
  peerId: string;
  displayName: string;
}

export function issueToken(payload: TokenPayload): string {
  return jwt.sign(payload, JWT_SECRET, { expiresIn: TOKEN_TTL_SECONDS });
}

export function verifyToken(token: string): TokenPayload {
  const decoded = jwt.verify(token, JWT_SECRET) as TokenPayload;
  if (!decoded.roomId || !decoded.peerId) {
    throw new Error("Invalid token payload");
  }
  return decoded;
}

export function getIceServers(): Array<{ urls: string[]; username?: string; credential?: string }> {
  const servers: Array<{ urls: string[]; username?: string; credential?: string }> = [
    { urls: ["stun:stun.l.google.com:19302"] },
  ];

  const turnUrl = process.env.TURN_URL;
  if (turnUrl) {
    servers.push({
      urls: [turnUrl],
      username: process.env.TURN_USERNAME,
      credential: process.env.TURN_CREDENTIAL,
    });
  }

  return servers;
}
