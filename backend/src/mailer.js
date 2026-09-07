/**
 * MarketScope AI — user-facing transactional emails.
 *
 * Two plain-text, professional emails (no HTML, no colors — they render as
 * ordinary text in every mail client):
 *   1. Welcome email — sent once, when a NEW user signs in for the first time.
 *   2. Security sign-in email — sent whenever a REGISTERED user signs in again.
 *
 * Emails are addressed only to the account owner's own email address and
 * contain no other users' information, no account IDs, and no platform
 * statistics (nothing like a total-user count).
 *
 * Sends via Brevo's HTTPS API (port 443) — Render blocks outbound SMTP
 * ports (25/465/587), so raw SMTP cannot leave the server. Same Brevo
 * account Veltravia already uses for Kora Messenger.
 *
 * All sends are fire-and-forget safe: failures are logged, never thrown to
 * the caller, and never block or fail the sign-in itself.
 */

const BREVO_API_KEY = process.env.BREVO_API_KEY || "";
const BREVO_SENDER_EMAIL = process.env.BREVO_SENDER_EMAIL || "";
const SENDER_NAME = process.env.MAIL_FROM_NAME || "MarketScope AI";
const SUPPORT_EMAIL = process.env.SUPPORT_EMAIL || "support@veltraviatech.com";
const API_URL = "https://api.brevo.com/v3/smtp/email";
const TIMEOUT_MS = 10_000;

// Guard against duplicate security alerts for the same account within a
// short window (double-tap on the sign-in button, or a client retry) —
// real repeat sign-ins outside the window always notify.
const DEDUPE_MS = 90_000;
const lastSentBySub = new Map();
function shouldSend(googleSub) {
  const now = Date.now();
  const last = lastSentBySub.get(googleSub) || 0;
  if (now - last < DEDUPE_MS) return false;
  lastSentBySub.set(googleSub, now);
  if (lastSentBySub.size > 500) {
    for (const [k, t] of lastSentBySub) {
      if (now - t > DEDUPE_MS) lastSentBySub.delete(k);
    }
  }
  return true;
}

function formatLagosTime(iso) {
  try {
    return (
      new Date(iso).toLocaleString("en-GB", {
        timeZone: "Africa/Lagos",
        weekday: "short",
        day: "2-digit",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false
      }) + " WAT"
    );
  } catch (_e) {
    return iso;
  }
}

function firstName(fullName) {
  return String(fullName || "").trim().split(/\s+/)[0] || "there";
}

/** Short, human-readable device description derived from a user-agent string. */
function describeDevice(userAgent) {
  const ua = String(userAgent || "");
  if (!ua) return "Unknown device";
  const os = /Android/i.test(ua)
    ? "Android"
    : /iPhone|iPad|iOS/i.test(ua)
      ? "iOS"
      : /Windows/i.test(ua)
        ? "Windows"
        : /Mac OS|Macintosh/i.test(ua)
          ? "Mac"
          : /Linux/i.test(ua)
            ? "Linux"
            : "Unknown";
  const browser = /Edg\//i.test(ua)
    ? "Edge"
    : /OPR|Opera/i.test(ua)
      ? "Opera"
      : /SamsungBrowser/i.test(ua)
        ? "Samsung Internet"
        : /Firefox\//i.test(ua)
          ? "Firefox"
          : /Chrome\//i.test(ua)
            ? "Chrome"
            : /Safari\//i.test(ua)
              ? "Safari"
              : "a web browser";
  return `${os} device (${browser})`;
}

async function sendViaBrevo({ to, subject, textContent }) {
  const res = await fetch(API_URL, {
    method: "POST",
    signal: AbortSignal.timeout(TIMEOUT_MS),
    headers: {
      "api-key": BREVO_API_KEY,
      "Content-Type": "application/json",
      Accept: "application/json"
    },
    body: JSON.stringify({
      sender: { name: SENDER_NAME, email: BREVO_SENDER_EMAIL },
      to: [{ email: to }],
      subject,
      textContent // text only — no htmlContent key, so the email is plain text everywhere
    })
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    const reason = (body && (body.message || body.code)) || `HTTP ${res.status}`;
    throw new Error(String(reason));
  }
  return body.messageId;
}

function configured() {
  return Boolean(BREVO_API_KEY && BREVO_SENDER_EMAIL);
}

/**
 * Welcome email — new user's first sign-in. Once per user, ever.
 * @param {{googleSub:string, email:string, name:string}} user
 */
async function sendWelcomeEmail(user) {
  try {
    if (!configured()) return { ok: false, reason: "Brevo is not configured" };
    if (!user || !user.email) return { ok: false, reason: "no email address on account" };

    const body = [
      `Hello ${firstName(user.name)},`,
      "",
      "Welcome to MarketScope AI — we're glad to have you on board.",
      "",
      "Your account has been created successfully. You now have access to AI-powered",
      "chart analysis, daily trading signals, and our community of traders.",
      "",
      "To get the most out of MarketScope AI, complete your trading profile in the",
      "app and run your first chart analysis whenever you're ready.",
      "",
      `If you ever need help, just reply to this email or contact us at ${SUPPORT_EMAIL}.`,
      "",
      "Welcome aboard,",
      "The MarketScope AI Team",
      "Veltravia Technologies"
    ].join("\r\n");

    const messageId = await sendViaBrevo({
      to: user.email,
      subject: "Welcome to MarketScope AI",
      textContent: body
    });
    console.log(`[mailer] welcome email sent to ${user.email} (${messageId})`);
    return { ok: true, messageId };
  } catch (err) {
    console.error(`[mailer] welcome email failed: ${String(err.message || err)}`);
    return { ok: false, reason: String(err.message || err) };
  }
}

/**
 * Security sign-in email — a registered user signed in again.
 * Contains only the user's own sign-in details; no platform statistics.
 * @param {{googleSub:string, email:string, name:string}} user
 * @param {{at?:string, userAgent?:string}} [meta]
 */
async function sendSecurityAlert(user, meta = {}) {
  try {
    if (!configured()) return { ok: false, reason: "Brevo is not configured" };
    if (!user || !user.email) return { ok: false, reason: "no email address on account" };
    if (!shouldSend(user.googleSub || user.email)) {
      return { ok: true, skipped: "duplicate-sign-in-within-window", messageId: null };
    }

    const at = meta.at || new Date().toISOString();
    const body = [
      `Hello ${firstName(user.name)},`,
      "",
      "A new sign-in to your MarketScope AI account was just detected.",
      "",
      `Date and time: ${formatLagosTime(at)}`,
      `Device: ${describeDevice(meta.userAgent)}`,
      "",
      "If this was you, no action is needed.",
      "",
      "If you do not recognize this sign-in, please secure your Google account and",
      `contact us immediately at ${SUPPORT_EMAIL}.`,
      "",
      "The MarketScope AI Team",
      "Veltravia Technologies"
    ].join("\r\n");

    const messageId = await sendViaBrevo({
      to: user.email,
      subject: "New sign-in to your MarketScope AI account",
      textContent: body
    });
    console.log(`[mailer] security sign-in email sent to ${user.email} (${messageId})`);
    return { ok: true, messageId };
  } catch (err) {
    console.error(`[mailer] security sign-in email failed: ${String(err.message || err)}`);
    return { ok: false, reason: String(err.message || err) };
  }
}

module.exports = { sendWelcomeEmail, sendSecurityAlert, formatLagosTime, describeDevice };
