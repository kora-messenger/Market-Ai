/**
 * MarketScope AI — transactional email (login notifications).
 *
 * Sends plain-text, professional notification emails whenever a user signs
 * in with Google. Deliberately NO HTML and NO styling: recipients get a
 * clean, ordinary text email in any mail client.
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
const ALERT_TO = process.env.LOGIN_ALERT_EMAIL || BREVO_SENDER_EMAIL; // defaults to the sending inbox
const API_URL = "https://api.brevo.com/v3/smtp/email";
const TIMEOUT_MS = 10_000;

// Guard against duplicate alerts for the same account within a short window
// (double-tap on the sign-in button, or a client retry) — real repeat logins
// outside the window always notify.
const DEDUPE_MS = 90_000;
const lastSentBySub = new Map();
function shouldSend(googleSub) {
  const now = Date.now();
  const last = lastSentBySub.get(googleSub) || 0;
  if (now - last < DEDUPE_MS) return false;
  lastSentBySub.set(googleSub, now);
  // keep the map small
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
        second: "2-digit",
        hour12: false
      }) + " WAT"
    );
  } catch (_e) {
    return iso;
  }
}

function buildLoginAlertBody(user, meta) {
  const lines = [
    "MarketScope AI — Google Sign-In Notification",
    "",
    "A user has just signed in to MarketScope AI using Google.",
    "",
    "Name:              " + (user.name || "—"),
    "Email:             " + (user.email || "—"),
    "Google Account ID: " + (user.googleSub || "—"),
    "Sign-in time:      " + formatLagosTime(meta.at),
    "Client:            " + (meta.userAgent || "Unknown device"),
    ""
  ];
  if (typeof meta.totalUsers === "number") {
    lines.push("Total registered users: " + meta.totalUsers);
    lines.push("");
  }
  lines.push("If this sign-in looks unusual, you can review the account from your admin console.");
  lines.push("");
  lines.push("—");
  lines.push("Automated notification from MarketScope AI (Veltravia Technologies).");
  lines.push("Sent " + formatLagosTime(new Date().toISOString()) + ".");
  return lines.join("\r\n");
}

/**
 * @param {{googleSub:string, email:string, name:string}} user
 * @param {{at?:string, userAgent?:string, totalUsers?:number}} [meta]
 * @returns {Promise<{ok:boolean, reason?:string, skipped?:string, messageId?:string}>}
 */
async function sendLoginAlert(user, meta = {}) {
  if (!BREVO_API_KEY || !BREVO_SENDER_EMAIL) {
    return { ok: false, reason: "Brevo is not configured (BREVO_API_KEY/BREVO_SENDER_EMAIL missing)" };
  }
  if (!user || !user.googleSub) {
    return { ok: false, reason: "user is required" };
  }
  if (!shouldSend(user.googleSub)) {
    return { ok: true, skipped: "duplicate-sign-in-within-window", messageId: null };
  }

  const at = meta.at || new Date().toISOString();
  const subject = "MarketScope AI sign-in: " + (user.name || user.email || "unknown user");

  try {
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
        to: [{ email: ALERT_TO }],
        subject,
        textContent: buildLoginAlertBody(user, { ...meta, at }) // text only — NO htmlContent key, so the email is plain text everywhere
        // Brevo requires htmlContent OR textContent; textContent alone sends a pure text email
      })
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      const reason = (body && (body.message || body.code)) || `HTTP ${res.status}`;
      console.error(`[mailer] login alert rejected by Brevo: ${String(reason)}`);
      return { ok: false, reason: String(reason) };
    }
    console.log(`[mailer] login alert sent for ${user.email} (Brevo messageId ${body.messageId})`);
    return { ok: true, messageId: body.messageId };
  } catch (err) {
    console.error(`[mailer] login alert failed: ${String(err.message || err)}`);
    return { ok: false, reason: String(err.message || err) };
  }
}

module.exports = { sendLoginAlert, formatLagosTime };
