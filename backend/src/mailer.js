/**
 * MarketScope AI — transactional email (login notifications).
 *
 * Sends plain-text, professional notification emails whenever a user signs
 * in with Google. Deliberately NO HTML and NO styling: recipients get a
 * clean, ordinary text email in any mail client.
 *
 * Uses Gmail SMTP (same account Veltravia already uses for Kora Messenger).
 * All sends are fire-and-forget safe: failures are logged, never thrown to
 * the caller, and never block or fail the sign-in itself.
 */

const SMTP_HOST = process.env.SMTP_HOST || "smtp.gmail.com";
const SMTP_PORT = Number(process.env.SMTP_PORT || 465);
const SMTP_USER = process.env.SMTP_USER || "";
const SMTP_PASS = process.env.SMTP_PASS || "";
const MAIL_FROM = process.env.MAIL_FROM || (SMTP_USER ? `MarketScope AI <${SMTP_USER}>` : "");
const ALERT_TO = process.env.LOGIN_ALERT_EMAIL || SMTP_USER; // defaults to the sending inbox

let transporter = null;

function getTransport() {
  if (!SMTP_USER || !SMTP_PASS) return null;
  if (transporter) return transporter;
  const nodemailer = require("nodemailer");
  transporter = nodemailer.createTransport({
    host: SMTP_HOST,
    port: SMTP_PORT,
    secure: SMTP_PORT === 465,
    auth: { user: SMTP_USER, pass: SMTP_PASS }
  });
  return transporter;
}

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
    return new Date(iso).toLocaleString("en-GB", {
      timeZone: "Africa/Lagos",
      weekday: "short",
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false
    }) + " WAT";
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
  const transport = getTransport();
  if (!transport) {
    return { ok: false, reason: "SMTP is not configured (SMTP_USER/SMTP_PASS missing)" };
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
    const info = await transport.sendMail({
      from: MAIL_FROM,
      to: ALERT_TO,
      subject,
      text: buildLoginAlertBody(user, { ...meta, at }), // plain text only — no HTML part
      headers: {
        "X-Entity-Ref": "marketscope-ai-login-alert",
        "Auto-Submitted": "auto-generated"
      }
    });
    console.log(`[mailer] login alert sent for ${user.email} (${info.messageId})`);
    return { ok: true, messageId: info.messageId };
  } catch (err) {
    console.error(`[mailer] login alert failed: ${String(err.message || err)}`);
    return { ok: false, reason: String(err.message || err) };
  }
}

module.exports = { sendLoginAlert, formatLagosTime };
