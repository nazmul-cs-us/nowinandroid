/**
 * Per-user settings store for the Starception app.
 *
 * Brokers access to a private R2 bucket so the Android app never holds R2 credentials. Every
 * request must carry a valid Firebase ID token (Authorization: Bearer <token>); the uid is taken
 * from the verified token, so a user can only ever read/write their own object:
 *
 *   GET  /sync  -> 200 with users/{uid}/settings.db bytes, or 404 if none yet
 *   PUT  /sync  -> stores the request body as users/{uid}/settings.db
 *
 * Token verification uses Google's public JWKs (RS256) and checks aud/iss/exp, matching the
 * Firebase project in wrangler.toml (FIREBASE_PROJECT_ID).
 */

export interface Env {
  USER_DATA: R2Bucket;
  FIREBASE_PROJECT_ID: string;
}

const JWK_URL =
  "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const MAX_BODY_BYTES = 5 * 1024 * 1024; // settings DB is tiny; cap to reject abuse

interface JwkCache {
  keys: Map<string, JsonWebKey>;
  expiresAt: number;
}
let jwkCache: JwkCache | null = null;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname !== "/sync") {
      return new Response("Not found", { status: 404 });
    }

    const uid = await verifyFirebaseToken(request, env);
    if (!uid) {
      return new Response("Unauthorized", { status: 401 });
    }

    const key = `users/${uid}/settings.db`;

    switch (request.method) {
      case "GET": {
        const obj = await env.USER_DATA.get(key);
        if (!obj) return new Response("Not found", { status: 404 });
        return new Response(obj.body, {
          headers: { "Content-Type": "application/octet-stream" },
        });
      }
      case "PUT": {
        const body = await request.arrayBuffer();
        if (body.byteLength === 0) return new Response("Empty body", { status: 400 });
        if (body.byteLength > MAX_BODY_BYTES) return new Response("Payload too large", { status: 413 });
        await env.USER_DATA.put(key, body, {
          httpMetadata: { contentType: "application/octet-stream" },
        });
        return new Response("OK", { status: 200 });
      }
      default:
        return new Response("Method not allowed", { status: 405 });
    }
  },
};

/** Returns the uid (token `sub`) if the bearer token is a valid Firebase ID token, else null. */
async function verifyFirebaseToken(request: Request, env: Env): Promise<string | null> {
  const auth = request.headers.get("Authorization") ?? "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
  if (!token) return null;

  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [headerB64, payloadB64, sigB64] = parts;

  let header: { alg?: string; kid?: string };
  let payload: Record<string, unknown>;
  try {
    header = JSON.parse(decodeUtf8(base64UrlToBytes(headerB64)));
    payload = JSON.parse(decodeUtf8(base64UrlToBytes(payloadB64)));
  } catch {
    return null;
  }

  if (header.alg !== "RS256" || !header.kid) return null;

  const jwk = await getJwk(header.kid);
  if (!jwk) return null;

  const cryptoKey = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"],
  );

  const signed = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
  const signature = base64UrlToBytes(sigB64);
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", cryptoKey, signature, signed);
  if (!ok) return null;

  const now = Math.floor(Date.now() / 1000);
  const projectId = env.FIREBASE_PROJECT_ID;
  const exp = Number(payload.exp ?? 0);
  const iat = Number(payload.iat ?? 0);
  const aud = payload.aud;
  const iss = payload.iss;
  const sub = payload.sub;

  if (exp <= now) return null;
  if (iat > now + 300) return null; // small clock-skew tolerance
  if (aud !== projectId) return null;
  if (iss !== `https://securetoken.google.com/${projectId}`) return null;
  if (typeof sub !== "string" || sub.length === 0 || sub.length > 128) return null;

  return sub;
}

async function getJwk(kid: string): Promise<JsonWebKey | null> {
  const now = Date.now();
  if (!jwkCache || jwkCache.expiresAt <= now) {
    const res = await fetch(JWK_URL);
    if (!res.ok) return null;
    const data = (await res.json()) as { keys: Array<JsonWebKey & { kid: string }> };
    const keys = new Map<string, JsonWebKey>();
    for (const k of data.keys) keys.set(k.kid, k);
    const maxAge = parseMaxAge(res.headers.get("Cache-Control")) ?? 3600;
    jwkCache = { keys, expiresAt: now + maxAge * 1000 };
  }
  return jwkCache.keys.get(kid) ?? null;
}

function parseMaxAge(cacheControl: string | null): number | null {
  if (!cacheControl) return null;
  const m = /max-age=(\d+)/.exec(cacheControl);
  return m ? Number(m[1]) : null;
}

function base64UrlToBytes(b64url: string): Uint8Array {
  const b64 = b64url.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(b64url.length / 4) * 4, "=");
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes;
}

function decodeUtf8(bytes: Uint8Array): string {
  return new TextDecoder().decode(bytes);
}
