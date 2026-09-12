/**
 * MarketScope AI — user-facing transactional emails.
 *
 * Two professional emails:
 *   1. Welcome email — sent once, when a NEW user signs in for the first time.
 *   2. Security sign-in email — sent whenever a REGISTERED user signs in again.
 *
 * Every email carries the MarketScope AI app logo at the top. Formatting stays
 * deliberately restrained: black text on a white background, no colored
 * backgrounds or buttons — the logo is the only branding. Each email has both
 * a plain-text part (renders in any client) and a minimal HTML part (shows the
 * logo); clients that prefer plain text still get the exact same message.
 *
 * Emails are addressed only to the account owner's own email address and
 * contain no other users' information, no account IDs, and no platform
 * statistics.
 *
 * Sends via Brevo's HTTPS API (port 443) — Render blocks outbound SMTP
 * ports (25/465/587). Same Brevo account Veltravia already uses for Kora.
 * All sends are fire-and-forget safe: failures are logged, never thrown to
 * the caller, and never block or fail the sign-in itself.
 */

const BREVO_API_KEY = process.env.BREVO_API_KEY || "";
const BREVO_SENDER_EMAIL = process.env.BREVO_SENDER_EMAIL || "";
const SENDER_NAME = process.env.MAIL_FROM_NAME || "MarketScope AI";
const SUPPORT_EMAIL = process.env.SUPPORT_EMAIL || "support@marketscopeai.com";
const LOGO_URL =
  process.env.APP_LOGO_URL ||
  "https://raw.githubusercontent.com/kora-messenger/Market-Ai/main/branding/email_logo.png";
const API_URL = "https://api.brevo.com/v3/smtp/email";
const SUBSCRIBE_URL =
  process.env.SUBSCRIBE_URL || "https://market-ai-api-jwfb.onrender.com/subscribe";
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

// ---------------------------------------------------------------------------
// Rendering: one content model -> plain-text part + logo-headed HTML part
// ---------------------------------------------------------------------------

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

/**
 * content = { subject, paragraphs: [string], fields?: [{label, value}], signoff: [string] }
 * - paragraphs: normal sentences
 * - fields:     short key/value lines (sign-in details)
 * - signoff:    closing lines
 */
function renderText(content) {
  const lines = [];
  for (const p of content.paragraphs) {
    lines.push(p, "");
  }
  if (content.fields) {
    for (const f of content.fields) {
      lines.push(`${f.label}: ${f.value}`);
    }
    lines.push("");
  }
  for (const p of content.paragraphsAfterFields || []) {
    lines.push(p, "");
  }
  if (content.cta) {
    lines.push(`${content.cta.label}: ${content.cta.url}`, "");
  }
  lines.push(...content.signoff);
  return lines.join("\r\n");
}

function renderHtml(content) {
  const esc = (s) => escapeHtml(s);
  const wrap = (inner) => `<!DOCTYPE html>
<html><body style="margin:0;padding:0;background:#ffffff;">
<div style="max-width:460px;margin:0 auto;padding:28px 12px 36px;font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:1.6;color:#1a1a1a;">
<img src="${LOGO_URL}" width="84" height="84" alt="MarketScope AI" style="display:block;margin:0 auto 24px;width:84px;height:84px;border-radius:19px;">
${inner}
<p style="margin:36px 0 0;padding-top:18px;border-top:1px solid #e8e8e8;font-size:13px;line-height:1.5;color:#6b6b6b;">
${content.signoff.map(esc).join("<br>")}
</p>
</div>
</body></html>`;

  const parts = [];
  for (const p of content.paragraphs) {
    parts.push(`<p style="margin:0 0 16px;">${esc(p)}</p>`);
  }
  if (content.fields) {
    const rows = content.fields
      .map(
        (f) =>
          `<tr><td style="padding:2px 24px 2px 0;font-size:14px;color:#6b6b6b;white-space:nowrap;vertical-align:top;">${esc(f.label)}</td><td style="padding:2px 0;font-size:14px;color:#1a1a1a;">${esc(f.value)}</td></tr>`
      )
      .join("");
    parts.push(`<table style="border-collapse:collapse;margin:0 0 16px;">${rows}</table>`);
  }
  for (const p of content.paragraphsAfterFields || []) {
    parts.push(`<p style="margin:0 0 16px;">${esc(p)}</p>`);
  }
  if (content.cta) {
    parts.push(
      `<div style="margin:24px 0 8px;text-align:center;">` +
      `<a href="${content.cta.url}" style="display:inline-block;padding:13px 34px;background:#1B2232;color:#ffffff;text-decoration:none;font-size:15px;font-weight:bold;border-radius:10px;">${esc(content.cta.label)}</a>` +
      `</div>`
    );
  }
  return wrap(parts.join("\n"));
}

async function sendViaBrevo({ to, subject, content }) {
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
      textContent: renderText(content),
      htmlContent: renderHtml(content)
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

/** Welcome email — new user's first sign-in. Once per user, ever. */
async function sendWelcomeEmail(user) {
  try {
    if (!configured()) return { ok: false, reason: "Brevo is not configured" };
    if (!user || !user.email) return { ok: false, reason: "no email address on account" };

    const content = {
      subject: "Welcome to MarketScope AI",
      paragraphs: [
        `Hello ${firstName(user.name)},`,
        "Welcome to MarketScope AI — we're glad to have you on board.",
        "Your account has been created successfully. You now have access to AI-powered chart analysis, daily trading signals, and our community of traders.",
        "Your 7-day free premium trial starts now — full access to every MarketScope AI feature for the next 7 days, with no payment details required.",
        "To get the most out of MarketScope AI, complete your trading profile in the app and run your first chart analysis whenever you're ready.",
        `If you ever need help, just reply to this email or contact us at ${SUPPORT_EMAIL}.`
      ],
      signoff: ["Welcome aboard,", "The MarketScope AI Team", "Veltravia Technologies"]
    };

    const messageId = await sendViaBrevo({ to: user.email, subject: content.subject, content });
    console.log(`[mailer] welcome email sent to ${user.email} (${messageId})`);
    return { ok: true, messageId };
  } catch (err) {
    console.error(`[mailer] welcome email failed: ${String(err.message || err)}`);
    return { ok: false, reason: String(err.message || err) };
  }
}

/** Security sign-in email — a registered user signed in again. */
async function sendSecurityAlert(user, meta = {}) {
  try {
    if (!configured()) return { ok: false, reason: "Brevo is not configured" };
    if (!user || !user.email) return { ok: false, reason: "no email address on account" };
    if (!shouldSend(user.googleSub || user.email)) {
      return { ok: true, skipped: "duplicate-sign-in-within-window", messageId: null };
    }

    const at = meta.at || new Date().toISOString();
    const content = {
      subject: "New sign-in to your MarketScope AI account",
      paragraphs: [
        `Hello ${firstName(user.name)},`,
        "A new sign-in to your MarketScope AI account was just detected."
      ],
      fields: [
        { label: "Date and time", value: formatLagosTime(at) },
        { label: "Device", value: describeDevice(meta.userAgent) }
      ],
      paragraphsAfterFields: [
        "If this was you, no action is needed.",
        `If you do not recognize this sign-in, please secure your Google account and contact us immediately at ${SUPPORT_EMAIL}.`
      ],
      signoff: ["The MarketScope AI Team", "Veltravia Technologies"]
    };

    const messageId = await sendViaBrevo({ to: user.email, subject: content.subject, content });
    console.log(`[mailer] security sign-in email sent to ${user.email} (${messageId})`);
    return { ok: true, messageId };
  } catch (err) {
    console.error(`[mailer] security sign-in email failed: ${String(err.message || err)}`);
    return { ok: false, reason: String(err.message || err) };
  }
}

/** Trial-expired email — sent once, when the 7-day free premium trial ends. */
async function sendTrialExpiredEmail(user) {
  try {
    if (!configured()) return { ok: false, reason: "Brevo is not configured" };
    if (!user || !user.email) return { ok: false, reason: "no email address on account" };

    const content = {
      subject: "Your MarketScope AI free trial has ended",
      paragraphs: [
        `Hello ${firstName(user.name)},`,
        "Your 7-day free premium trial has come to an end.",
        "We hope you enjoyed AI-powered chart analysis, daily trading signals, and the trader community. To keep enjoying trading with full access, subscribe to MarketScope AI Premium."
      ],
      cta: {
        label: "Subscribe to Premium",
        url: SUBSCRIBE_URL
      },
      paragraphsAfterFields: [
        "You can also subscribe anytime from the app \u2014 the Subscribe screen in your MarketScope AI profile."
      ],
      signoff: ["The MarketScope AI Team", "Veltravia Technologies"]
    };

    const messageId = await sendViaBrevo({ to: user.email, subject: content.subject, content });
    console.log(`[mailer] trial-expired email sent to ${user.email} (${messageId})`);
    return { ok: true, messageId };
  } catch (err) {
    console.error(`[mailer] trial-expired email failed: ${String(err.message || err)}`);
    return { ok: false, reason: String(err.message || err) };
  }
}

/** Premium: paid subscription activated (Paystack webhook). */
async function sendPremiumActivatedEmail(user) {
  try {
    if (!configured()) return { ok: false, reason: "Brevo is not configured" };
    if (!user || !user.email) return { ok: false, reason: "no email address on account" };
    const content = {
      subject: "Your MarketScope AI Premium activation was successful",
      paragraphs: [
        `Hello ${firstName(user.name)},`,
        "Your activation for MarketScope AI Premium has been successful.",
        "You now have unlimited AI chart and market analysis, the full Daily Signals history, and every Premium feature — welcome aboard."
      ],
      signoff: ["The MarketScope AI Team", "Veltravia Technologies"]
    };
    const messageId = await sendViaBrevo({ to: user.email, subject: content.subject, content });
    console.log(`[mailer] premium-activated email sent to ${user.email} (${messageId})`);
    return { ok: true, messageId };
  } catch (err) {
    console.error(`[mailer] premium-activated email failed: ${String(err.message || err)}`);
    return { ok: false, reason: String(err.message || err) };
  }
}

/** Premium: an administrator granted the user free Premium (lifetime / N months / N years). */
async function sendPremiumGrantedEmail(user, { grantLabel, expiresText, reason }) {
  try {
    if (!configured()) return { ok: false, reason: "Brevo is not configured" };
    if (!user || !user.email) return { ok: false, reason: "no email address on account" };
    const content = {
      subject: `Congratulations \u2014 you've been granted ${grantLabel} on MarketScope AI`,
      paragraphs: [
        `Hello ${firstName(user.name)},`,
        `Congratulations! You've been given free ${grantLabel} on MarketScope AI by the MarketScope AI team.`,
        "You now have the same Premium access as a paid subscriber: unlimited AI chart and market analysis, the full Daily Signals history, and every Premium feature."
      ],
      fields: [
        { label: "Premium", value: grantLabel },
        { label: "Expires", value: expiresText }
      ].concat(reason ? [{ label: "Reason", value: reason }] : []),
      signoff: ["The MarketScope AI Team", "Veltravia Technologies"]
    };
    const messageId = await sendViaBrevo({ to: user.email, subject: content.subject, content });
    console.log(`[mailer] premium-granted email sent to ${user.email} (${messageId})`);
    return { ok: true, messageId };
  } catch (err) {
    console.error(`[mailer] premium-granted email failed: ${String(err.message || err)}`);
    return { ok: false, reason: String(err.message || err) };
  }
}

/** Premium: an administrator revoked the granted Premium. */
async function sendPremiumRevokedEmail(user, { grantLabel, stillPremium }) {
  try {
    if (!configured()) return { ok: false, reason: "Brevo is not configured" };
    if (!user || !user.email) return { ok: false, reason: "no email address on account" };
    const content = {
      subject: "Your admin-granted MarketScope AI Premium has been removed",
      paragraphs: [
        `Hello ${firstName(user.name)},`,
        `Your administrator-granted ${grantLabel} on MarketScope AI has been removed.`,
        stillPremium
          ? "Your paid Premium subscription is unaffected \u2014 you still have full Premium access."
          : "If you'd like Premium access again, you can subscribe anytime from the Subscribe screen in your MarketScope AI profile."
      ],
      signoff: ["The MarketScope AI Team", "Veltravia Technologies"]
    };
    const messageId = await sendViaBrevo({ to: user.email, subject: content.subject, content });
    console.log(`[mailer] premium-revoked email sent to ${user.email} (${messageId})`);
    return { ok: true, messageId };
  } catch (err) {
    console.error(`[mailer] premium-revoked email failed: ${String(err.message || err)}`);
    return { ok: false, reason: String(err.message || err) };
  }
}

/** Ops: health-check failure alert to the owner inbox. Throttled so a
 *  long outage sends at most one email per hour instead of one per cron tick. */
let lastHealthAlertAt = 0;
async function sendHealthAlertEmail(detail) {
  try {
    if (!configured()) return { ok: false, reason: "Brevo is not configured" };
    const now = Date.now();
    if (now - lastHealthAlertAt < 60 * 60 * 1000) {
      return { ok: true, skipped: "throttled-within-1h", messageId: null };
    }
    const content = {
      subject: "ALERT: MarketScope AI backend health check FAILED",
      paragraphs: [
        "The MarketScope AI backend health endpoint reported a problem.",
        "The health check now performs a REAL database ping, so this alert means a critical dependency (database, auth, or push) is actually unreachable — not just unconfigured.",
        `Failure detail: ${String(detail || "unknown").slice(0, 300)}`,
        `Detected at: ${formatLagosTime(new Date().toISOString())} (WAT)`,
        "Check the service on Render: https://dashboard.render.com/web/srv-dae7mh8u01pc73de6190"
      ],
      signoff: ["Automated health monitor,", "MarketScope AI Ops"]
    };
    const to = process.env.LOGIN_ALERT_EMAIL || process.env.BREVO_SENDER_EMAIL;
    const messageId = await sendViaBrevo({ to, subject: content.subject, content });
    lastHealthAlertAt = now;
    console.log(`[mailer] health alert email sent to ${to} (${messageId})`);
    return { ok: true, messageId };
  } catch (err) {
    console.error(`[mailer] health alert failed: ${String(err.message || err)}`);
    return { ok: false, reason: String(err.message || err) };
  }
}

/** Community & signals stats report — real numbers gathered by the backend. */
async function sendStatsReportEmail(stats) {
  try {
    if (!configured()) return { ok: false, reason: "Brevo is not configured" };
    const content = {
      subject: "MarketScope AI — Community & Signals Report",
      paragraphs: [
        "Here is the latest snapshot of the MarketScope AI community and signal desk, gathered live from the production database.",
        "COMMUNITY",
        "DAILY SIGNALS (this month)",
        "New today — Share Your Win (v1.3.13): traders can mark \"I took this signal\" on won calls, share a comment plus a trader-proof screenshot (mentor-desk reviewed), and approved proofs appear as Win proofs under the signal and in the new Recent wins strip. The Team Console gained a Win reviews queue."
      ],
      fields: [
        { label: "Members", value: String(stats.members) },
        { label: "Online now", value: String(stats.online) },
        { label: "Community posts", value: String(stats.posts) },
        { label: "Post comments", value: String(stats.postComments) },
        { label: "Published signals (month)", value: String(stats.signalsTotal) },
        { label: "Live / closed", value: `${stats.signalsLive} / ${stats.signalsClosed}` },
        { label: "Wins / losses", value: `${stats.wins} / ${stats.losses}` },
        { label: "Win rate", value: `${stats.winRate}%` },
        { label: "Average R:R", value: `1:${stats.avgRR}` },
        { label: "Chart analyses", value: String(stats.analyses) },
        { label: "Push devices", value: String(stats.pushDevices) }
      ],
      signoff: ["Solas,", "MarketScope AI"]
    };
    const to = process.env.LOGIN_ALERT_EMAIL || process.env.BREVO_SENDER_EMAIL;
    const messageId = await sendViaBrevo({ to, subject: content.subject, content });
    console.log(`[mailer] stats report email sent to ${to} (${messageId})`);
    return { ok: true, messageId };
  } catch (err) {
    console.error(`[mailer] stats report failed: ${String(err.message || err)}`);
    return { ok: false, reason: String(err.message || err) };
  }
}

module.exports = { sendWelcomeEmail, sendSecurityAlert, sendTrialExpiredEmail, sendHealthAlertEmail, sendStatsReportEmail, sendPremiumActivatedEmail, sendPremiumGrantedEmail, sendPremiumRevokedEmail, formatLagosTime, describeDevice };
