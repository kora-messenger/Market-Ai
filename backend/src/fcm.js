/**
 * FCM v1 push sender for MarketScope AI.
 * Uses the FCM_SERVICE_ACCOUNT_JSON env var (Firebase Admin SDK service
 * account) to mint OAuth2 access tokens and POST to FCM v1 directly —
 * no third-party push relay, notifications never leave Google's pipeline.
 */
const crypto = require("crypto");

const SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const TOKEN_URL = "https://oauth2.googleapis.com/token";

let cached = { token: null, expiresAt: 0 };
let serviceAccount = null;

function getServiceAccount() {
  if (serviceAccount) return serviceAccount;
  const raw = process.env.FCM_SERVICE_ACCOUNT_JSON;
  if (!raw) return null;
  try {
    serviceAccount = typeof raw === "string" ? JSON.parse(raw) : raw;
    return serviceAccount;
  } catch (_e) {
    console.error("FCM: FCM_SERVICE_ACCOUNT_JSON is not valid JSON");
    return null;
  }
}

/** Mint (and cache) an OAuth2 access token from the service account private key. */
async function getAccessToken() {
  const sa = getServiceAccount();
  if (!sa) return null;
  if (cached.token && Date.now() < cached.expiresAt - 60000) return cached.token;

  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claim = {
    iss: sa.client_email,
    scope: SCOPE,
    aud: TOKEN_URL,
    iat: now,
    exp: now + 3600
  };
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
  const unsigned = `${b64(header)}.${b64(claim)}`;
  const signer = crypto.createSign("RSA-SHA256");
  signer.update(unsigned);
  const signature = signer.sign(sa.private_key.replace(/\\n/g, "\n"), "base64url");
  const assertion = `${unsigned}.${signature}`;

  const res = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion
    })
  });
  if (!res.ok) {
    throw new Error(`FCM token mint failed: ${res.status} ${await res.text()}`);
  }
  const data = await res.json();
  cached = { token: data.access_token, expiresAt: Date.now() + data.expires_in * 1000 };
  return cached.token;
}

/**
 * Send one push. Returns "ok" | "invalid" | "error" so callers can prune
 * dead tokens without a bad one breaking the flow that triggered the push.
 */
async function sendFcm(token, { title, body, data }) {
  const sa = getServiceAccount();
  if (!sa || !token) return "skipped";
  try {
    const accessToken = await getAccessToken();
    const message = {
      token,
      android: { priority: "high" },
      notification: { title: String(title || "MarketScope AI"), body: String(body || "") }
    };
    if (data) message.data = Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)]));
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({ message })
    });
    if (res.ok) return "ok";
    const errText = await res.text();
    console.error(`FCM send failed (${res.status}): ${errText.slice(0, 300)}`);
    // 404 UNREGISTERED / 400 INVALID_ARGUMENT => token is dead
    if (res.status === 404 || (res.status === 400 && /not a valid FCM registration token|UNREGISTERED/i.test(errText))) {
      return "invalid";
    }
    return "error";
  } catch (err) {
    console.error("FCM send error:", String(err.message || err));
    return "error";
  }
}

module.exports = { getAccessToken, sendFcm, getServiceAccount };
