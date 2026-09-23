"use strict";
const { randomInt } = require("node:crypto");

// Referral rewards. Attribution lives on users.referred_by (one referrer per
// account, set at most once). Both reward stamps live on the REFERRED user's
// row, so a payout can only ever be triggered once per event per person.
const CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // no 0/O/1/I — unambiguous when read aloud or typed
const ANALYSIS_BONUS_DAYS = 1;
const SUBSCRIPTION_BONUS_DAYS = 7;
const MANUAL_APPLY_WINDOW_DAYS = 7; // how long after signup a code can still be entered manually

function randomCode(length = 7) {
  let code = "";
  for (let i = 0; i < length; i++) code += CODE_ALPHABET[randomInt(CODE_ALPHABET.length)];
  return code;
}

async function ensureReferralColumns(pool) {
  await pool.query(`
    ALTER TABLE users ADD COLUMN IF NOT EXISTS referral_code TEXT;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS referred_by UUID REFERENCES users(id) ON DELETE SET NULL;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS referral_analysis_rewarded_at TIMESTAMPTZ;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS referral_subscription_rewarded_at TIMESTAMPTZ;
  `);
  await pool.query(`CREATE UNIQUE INDEX IF NOT EXISTS users_referral_code_key ON users(referral_code) WHERE referral_code IS NOT NULL`);
  // premium_grants originally only allowed lifetime/months/years; referral
  // bonuses are day-granular, so widen the CHECK to include 'days'.
  await pool.query(`ALTER TABLE premium_grants DROP CONSTRAINT IF EXISTS premium_grants_duration_type_check`);
  await pool.query(`ALTER TABLE premium_grants ADD CONSTRAINT premium_grants_duration_type_check CHECK (duration_type IN ('lifetime','months','years','days'))`);
  // Codes for existing accounts are created lazily on their first referral screen visit.
}

/** Generates and stores a unique code for one user, retrying on collision. */
async function assignReferralCode(pool, userId) {
  for (let attempt = 0; attempt < 8; attempt++) {
    const code = randomCode();
    try {
      const { rows } = await pool.query(
        `UPDATE users SET referral_code = $1 WHERE id = $2 AND referral_code IS NULL RETURNING referral_code`,
        [code, userId]
      );
      if (rows.length) return rows[0].referral_code;
      const { rows: existing } = await pool.query(`SELECT referral_code FROM users WHERE id = $1`, [userId]);
      return existing[0]?.referral_code || null;
    } catch (err) {
      if (String(err.code) !== "23505") throw err; // unique violation -> retry with a new code
    }
  }
  throw new Error("Could not allocate a unique referral code");
}

/**
 * Attaches a new (or not-yet-attributed) account to whoever owns `code`.
 * Silent no-ops (never throws to the caller) on any invalid/self/expired/
 * already-attributed case — callers treat this as best-effort.
 */
async function applyReferralCode(pool, { userId, code, requireWithinSignupWindow }) {
  const trimmed = String(code || "").trim().toUpperCase();
  if (!trimmed) return { applied: false, reason: "empty" };
  const { rows } = await pool.query(
    `SELECT id, referred_by, created_at FROM users WHERE id = $1`,
    [userId]
  );
  if (!rows.length) return { applied: false, reason: "no_user" };
  const me = rows[0];
  if (me.referred_by) return { applied: false, reason: "already_attributed" };
  if (requireWithinSignupWindow) {
    const ageMs = Date.now() - new Date(me.created_at).getTime();
    if (ageMs > MANUAL_APPLY_WINDOW_DAYS * 24 * 60 * 60 * 1000) {
      return { applied: false, reason: "window_expired" };
    }
  }
  // Manual entry is for a truly new invitee, not for claiming an already
  // completed first analysis or an existing subscription retroactively.
  const { rows: eligibility } = await pool.query(
    `SELECT EXISTS(SELECT 1 FROM analyses WHERE user_id = $1) AS has_analysis,
            (is_premium = true OR premium_started_at IS NOT NULL OR
             EXISTS(SELECT 1 FROM subscription_payments WHERE user_id = $1 AND status = 'success')) AS has_paid
     FROM users WHERE id = $1`, [userId]
  );
  if (eligibility[0]?.has_analysis || eligibility[0]?.has_paid) return { applied: false, reason: "already_active" };
  const { rows: referrerRows } = await pool.query(`SELECT id FROM users WHERE referral_code = $1`, [trimmed]);
  if (!referrerRows.length) return { applied: false, reason: "invalid_code" };
  const referrerId = referrerRows[0].id;
  if (String(referrerId) === String(userId)) return { applied: false, reason: "self" };
  const updated = await pool.query(
    `UPDATE users SET referred_by = $1 WHERE id = $2 AND referred_by IS NULL`,
    [referrerId, userId]
  );
  if (!updated.rowCount) return { applied: false, reason: "already_attributed" };
  return { applied: true, referrerId };
}

/** Add bonus days transactionally. Always start AFTER the end of the
 *  referrer's current paid term and other active grants, so a subscriber
 *  actually receives the promised extra time after their purchase ends. */
async function grantBonusDays(client, userId, days, reason) {
  const { rows: paidRows } = await client.query(
    `SELECT premium_expires_at FROM users WHERE id = $1 AND is_premium = true AND premium_expires_at > now()`,
    [userId]
  );
  const { rows: admin } = await client.query(
    `SELECT duration_type, expires_at FROM premium_grants
     WHERE user_id = $1 AND revoked_at IS NULL AND granted_by_email IS DISTINCT FROM 'referral-system'
       AND (expires_at IS NULL OR expires_at > now()) LIMIT 1 FOR UPDATE`, [userId]
  );
  const { rows: active } = await client.query(
    `SELECT id, duration_type, expires_at FROM premium_grants
     WHERE user_id = $1 AND revoked_at IS NULL AND granted_by_email = 'referral-system'
       AND expires_at > now() LIMIT 1 FOR UPDATE`, [userId]
  );
  if (admin[0]?.duration_type === "lifetime") return { extended: false, alreadyLifetime: true };
  const base = Math.max(Date.now(), new Date(paidRows[0]?.premium_expires_at || 0).getTime(),
    new Date(admin[0]?.expires_at || 0).getTime(), new Date(active[0]?.expires_at || 0).getTime());
  const expiresAt = new Date(base + days * 86400000).toISOString();
  if (active.length) {
    await client.query(
      `UPDATE premium_grants SET expires_at = $2,
         duration_count = duration_count + $3,
         reason = COALESCE(reason, '') || CASE WHEN reason IS NULL OR reason = '' THEN '' ELSE '; ' END || $4
       WHERE id = $1`, [active[0].id, expiresAt, days, reason]
    );
    return { extended: true, expiresAt };
  }
  // Retire only expired referral rows; administrator grants remain independent.
  await client.query(`UPDATE premium_grants SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL AND granted_by_email = 'referral-system' AND expires_at <= now()`, [userId]);
  await client.query(
    `INSERT INTO premium_grants (user_id, duration_type, duration_count, expires_at, reason, granted_by, granted_by_email)
     VALUES ($1, 'days', $2, $3, $4, NULL, 'referral-system')`,
    [userId, days, expiresAt, reason]
  );
  return { extended: true, created: true, expiresAt };
}

/** Serializes claims by locking the referred user and referrer rows. The stamp
 *  and premium grant commit together; a retry cannot double-reward, and a
 *  failed grant cannot burn a reward. */
async function reward(pool, userId, kind) {
  const client = await pool.connect();
  const stamp = kind === "analysis" ? "referral_analysis_rewarded_at" : "referral_subscription_rewarded_at";
  try {
    await client.query("BEGIN");
    const { rows } = await client.query(`SELECT id, referred_by, ${stamp} FROM users WHERE id = $1 FOR UPDATE`, [userId]);
    const me = rows[0];
    if (!me?.referred_by || me[stamp]) { await client.query("COMMIT"); return false; }
    if (kind === "analysis") {
      const { rows: counts } = await client.query(`SELECT COUNT(*)::int AS c FROM analyses WHERE user_id = $1`, [userId]);
      if (counts[0].c < 1) { await client.query("COMMIT"); return false; }
    }
    const { rows: referrer } = await client.query(`SELECT id FROM users WHERE id = $1 FOR UPDATE`, [me.referred_by]);
    if (!referrer.length) { await client.query("COMMIT"); return false; }
    await grantBonusDays(client, me.referred_by,
      kind === "analysis" ? ANALYSIS_BONUS_DAYS : SUBSCRIPTION_BONUS_DAYS,
      kind === "analysis" ? "Referral: first completed analysis" : "Referral: first paid subscription");
    await client.query(`UPDATE users SET ${stamp} = now() WHERE id = $1`, [userId]);
    await client.query("COMMIT");
    return true;
  } catch (err) {
    await client.query("ROLLBACK").catch(() => {});
    console.error(`[referrals] ${kind} reward failed:`, String(err.message || err));
    return false;
  } finally { client.release(); }
}

async function rewardFirstAnalysis(pool, userId) { return reward(pool, userId, "analysis"); }
async function rewardFirstSubscription(pool, userId) { return reward(pool, userId, "subscription"); }

const DOWNLOAD_URL = "https://github.com/kora-messenger/Market-Ai/releases/download/v1.5.1/MarketScopeAI-v1.5.1.apk";

function esc(s) {
  return String(s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

/** Public web landing for a shared invite link — used when the recipient
 *  doesn't have the app installed yet (no Play Store listing to carry an
 *  install referrer, so the code is applied by hand after they sign in). */
function renderInviteLandingHtml(code) {
  const safeCode = esc(String(code || "").toUpperCase());
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>You're invited to MarketScope AI</title>
<style>
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=Sora:wght@600;700;800&display=swap');
  :root { --ink:#0a0e1a; --text-2:#414b60; --muted:#79839a; --border:#e7eaf1; --violet:#7c3aed; --cyan:#06b6d4; --grad:linear-gradient(135deg,#7c3aed 0%,#06b6d4 100%); }
  * { margin:0; padding:0; box-sizing:border-box; }
  body { background:#fff; color:var(--ink); font-family:'Inter',-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif; line-height:1.65; -webkit-font-smoothing:antialiased; }
  .wrap { max-width:560px; margin:0 auto; padding:64px 24px 80px; text-align:center; }
  h1 { font-family:'Sora','Inter',sans-serif; font-size:clamp(26px,5vw,34px); font-weight:800; letter-spacing:-.02em; margin-bottom:14px; }
  p { color:var(--text-2); font-size:16px; margin-bottom:24px; }
  .code { display:inline-block; font-family:'Sora',sans-serif; font-weight:800; font-size:28px; letter-spacing:.12em; background:var(--grad); color:#fff; padding:14px 28px; border-radius:14px; margin-bottom:28px; }
  .btn { display:inline-block; background:var(--grad); color:#fff; font-weight:700; padding:14px 30px; border-radius:12px; text-decoration:none; margin-bottom:36px; }
  ol { text-align:left; color:var(--text-2); font-size:15px; padding-left:20px; margin:0 auto 8px; max-width:420px; }
  li { margin-bottom:10px; }
  strong { color:var(--ink); }
</style>
</head>
<body>
  <div class="wrap">
    <h1>You're invited to MarketScope AI</h1>
    <p>A friend wants you to try AI-powered trade analysis, signals and a trading community.</p>
    <div class="code">${safeCode}</div>
    <a class="btn" href="${esc(DOWNLOAD_URL)}">Download the app</a>
    <ol>
      <li>Install the APK and open MarketScope AI.</li>
      <li>Sign in with Google.</li>
      <li>Go to <strong>Profile → Invite friends → Enter code</strong> and type <strong>${safeCode}</strong>.</li>
    </ol>
  </div>
</body>
</html>`;
}

module.exports = {
  ANALYSIS_BONUS_DAYS,
  SUBSCRIPTION_BONUS_DAYS,
  ensureReferralColumns,
  assignReferralCode,
  applyReferralCode,
  grantBonusDays,
  rewardFirstAnalysis,
  rewardFirstSubscription,
  renderInviteLandingHtml
};
