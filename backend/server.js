/**
 * MarketScope AI API — Google auth verification, instrument catalog, AI chart analysis.
 * Runs server-side so the app holds zero AI provider keys.
 */
const express = require("express");
const crypto = require("crypto");
const { OAuth2Client } = require("google-auth-library");
const jwt = require("jsonwebtoken");
const { Pool } = require("pg");
const { ALL, byId, categories } = require("./src/instruments");
const { sendWelcomeEmail, sendSecurityAlert, sendTrialExpiredEmail, sendHealthAlertEmail } = require("./src/mailer");
const { termsOfServiceHtml, privacyPolicyHtml } = require("./src/legalPages");
const { fetchPrice, fetchHistory } = require("./src/prices");
const { sendFcm } = require("./src/fcm");
const { runAlertCron, holidayForToday } = require("./src/marketAlerts");
const { fetchTrending, fetchLiveQuotes } = require("./src/trending");
const { fetchWatchlist, WATCHLIST } = require("./src/markets");
const { fetchCandles, INTERVALS } = require("./src/candles");
const { fetchEconomicCalendar, fetchMarketNews } = require("./src/newsCalendar");

const app = express();

// --- Paystack webhook: registered before the global JSON parser because the
// signature is an HMAC-SHA512 over the RAW request body. Route-level raw
// body parser only applies if it runs first, which it does here. ---
const PAYSTACK_SECRET_KEY = process.env.PAYSTACK_SECRET_KEY || "";
const SUBSCRIBE_URL = process.env.SUBSCRIBE_URL || "https://market-ai-api-jwfb.onrender.com/subscribe";
const SUB_CURRENCY = (process.env.SUB_CURRENCY || "USD").toUpperCase();
const SUB_PRICE = Number(process.env.SUB_PRICE || "9.99"); // price per month, 2 decimals

app.post("/api/subscription/webhook", express.raw({ type: "*/*", limit: "1mb" }), async (req, res) => {
  if (!PAYSTACK_SECRET_KEY) {
    // Payments not live yet — nothing to process. Answer 200 so Paystack doesn't retry forever.
    return res.sendStatus(200);
  }
  try {
    const signature = req.headers["x-paystack-signature"] || "";
    const expected = crypto.createHmac("sha512", PAYSTACK_SECRET_KEY).update(req.body).digest("hex");
    if (signature !== expected) {
      return res.status(401).json({ error: "Invalid signature" });
    }
    const event = JSON.parse(req.body.toString("utf8"));
    if (event.event === "charge.success" && event.data && event.data.reference) {
      // Never trust the webhook payload alone — verify the transaction with Paystack.
      const vRes = await fetch(
        `https://api.paystack.co/transaction/verify/${encodeURIComponent(event.data.reference)}`,
        { headers: { Authorization: `Bearer ${PAYSTACK_SECRET_KEY}`, Accept: "application/json" } }
      );
      const vBody = await vRes.json().catch(() => ({}));
      const data = vBody && vBody.data;
      if (vRes.ok && data && data.status === "success") {
        const googleSub = data.metadata && data.metadata.google_sub;
        const amount = typeof data.amount === "number" ? data.amount : null;
        const currency = data.currency || SUB_CURRENCY;
        if (pool && googleSub) {
          const { rows } = await pool.query(
            `SELECT id FROM users WHERE google_sub = $1`,
            [googleSub]
          );
          if (rows.length) {
            await pool.query(
              `INSERT INTO subscription_payments (user_id, reference, amount, currency, status, paid_at)
               VALUES ($1, $2, $3, $4, 'success', now())
               ON CONFLICT (reference) DO NOTHING`,
              [rows[0].id, data.reference, amount, currency]
            );
            await pool.query(
              `UPDATE users SET is_premium = true WHERE id = $1`,
              [rows[0].id]
            );
            console.log(`[subscription] premium activated for google_sub ${googleSub} (ref ${data.reference})`);
          }
        }
      }
    }
    return res.sendStatus(200);
  } catch (err) {
    console.error("[subscription] webhook error:", String(err.message || err));
    return res.sendStatus(200); // Paystack retries on non-2xx; log instead of failing
  }
});

app.use(express.json({ limit: "25mb" }));

const PORT = process.env.PORT || 3000;
const OPENROUTER_API_KEY = process.env.OPENROUTER_API_KEY || "";
const GOOGLE_WEB_CLIENT_ID = process.env.GOOGLE_WEB_CLIENT_ID || "";
const JWT_SECRET = process.env.SESSION_JWT_SECRET || "";
const ANALYSIS_MODEL = process.env.ANALYSIS_MODEL || "google/gemini-3.8-flash";
// Comma-separated admin emails (e.g. "a@gmail.com,b@gmail.com"); the
// first account ever created also stays admin as a fallback.
const ADMIN_EMAILS = (process.env.ADMIN_EMAIL || "")
  .split(",")
  .map((e) => e.trim().toLowerCase())
  .filter(Boolean);
const CRON_SECRET = process.env.CRON_SECRET || "";

// --- Database (Render Postgres) ---
const pool = process.env.DATABASE_URL
  ? new Pool({
      connectionString: process.env.DATABASE_URL,
      ssl: { rejectUnauthorized: false }
    })
  : null;

async function initDb() {
  if (!pool) return;
  // One-time migration: when questionnaire_completed_at is being introduced,
  // every user who already signed up has, by construction, gone through the
  // mandatory onboarding questionnaire (the app cannot reach the main tabs
  // without completing it) — so backfill them as completed. Brand-new users
  // start with NULL and only get a completed_at once they actually save.
  const preCheck = await pool.query(
    `SELECT 1 FROM information_schema.columns
     WHERE table_name = 'users' AND column_name = 'questionnaire_completed_at'`
  );
  const introducingQuestionnaire = preCheck.rowCount === 0;
  await pool.query(`
    CREATE TABLE IF NOT EXISTS users (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      google_sub TEXT UNIQUE NOT NULL,
      email TEXT,
      name TEXT,
      picture TEXT,
      community_joined BOOLEAN NOT NULL DEFAULT false,
      community_joined_at TIMESTAMPTZ,
      trial_started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      is_premium BOOLEAN NOT NULL DEFAULT false,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    CREATE TABLE IF NOT EXISTS analyses (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID REFERENCES users(id),
      instrument_id TEXT NOT NULL,
      mode TEXT NOT NULL,
      result JSONB NOT NULL,
      outcome TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    ALTER TABLE users ADD COLUMN IF NOT EXISTS trial_started_at TIMESTAMPTZ NOT NULL DEFAULT now();
    ALTER TABLE users ADD COLUMN IF NOT EXISTS trial_expired_email_sent_at TIMESTAMPTZ;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS questionnaire JSONB;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS questionnaire_completed_at TIMESTAMPTZ;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'user';
  `);
  if (introducingQuestionnaire) {
    await pool.query(
      `UPDATE users SET questionnaire_completed_at = created_at WHERE questionnaire_completed_at IS NULL`
    );
    console.log("[db] questionnaire columns added; existing users backfilled as completed");
  }
  await pool.query(`
    CREATE TABLE IF NOT EXISTS subscription_payments (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID REFERENCES users(id),
      reference TEXT UNIQUE NOT NULL,
      amount INTEGER,
      currency TEXT,
      status TEXT NOT NULL DEFAULT 'success',
      paid_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    ALTER TABLE users ADD COLUMN IF NOT EXISTS is_premium BOOLEAN NOT NULL DEFAULT false;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS community_joined BOOLEAN NOT NULL DEFAULT false;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS community_joined_at TIMESTAMPTZ;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS deletion_requested_at TIMESTAMPTZ;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;
    CREATE TABLE IF NOT EXISTS daily_signals (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      author TEXT NOT NULL DEFAULT 'owner',
      instrument_id TEXT NOT NULL,
      instrument_display TEXT NOT NULL,
      direction TEXT NOT NULL,
      entry DOUBLE PRECISION NOT NULL,
      stop_loss DOUBLE PRECISION NOT NULL,
      take_profits JSONB NOT NULL,
      risk_reward DOUBLE PRECISION,
      thesis TEXT,
      strength TEXT NOT NULL DEFAULT 'moderate',
      status TEXT NOT NULL DEFAULT 'pending',
      outcome TEXT,
      triggered_at TIMESTAMPTZ,
      closed_at TIMESTAMPTZ,
      published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      last_price DOUBLE PRECISION,
      last_price_at TIMESTAMPTZ
    );
    ALTER TABLE daily_signals ADD COLUMN IF NOT EXISTS mode TEXT;
    ALTER TABLE daily_signals ADD COLUMN IF NOT EXISTS exit_price DOUBLE PRECISION;
    ALTER TABLE daily_signals ADD COLUMN IF NOT EXISTS resolved_by TEXT;
    CREATE TABLE IF NOT EXISTS signal_reactions (
      signal_id UUID REFERENCES daily_signals(id) ON DELETE CASCADE,
      user_id UUID NOT NULL,
      emoji TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      PRIMARY KEY (signal_id, user_id, emoji)
    );
    CREATE TABLE IF NOT EXISTS signal_saves (
      signal_id UUID REFERENCES daily_signals(id) ON DELETE CASCADE,
      user_id UUID NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      PRIMARY KEY (signal_id, user_id)
    );
    CREATE TABLE IF NOT EXISTS signal_comments (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      signal_id UUID REFERENCES daily_signals(id) ON DELETE CASCADE,
      user_id UUID REFERENCES users(id) ON DELETE SET NULL,
      author_name TEXT NOT NULL,
      author_email TEXT NOT NULL DEFAULT '',
      body TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    ALTER TABLE signal_comments ADD COLUMN IF NOT EXISTS approved BOOLEAN NOT NULL DEFAULT true;
    CREATE TABLE IF NOT EXISTS signal_comment_images (
      comment_id UUID PRIMARY KEY REFERENCES signal_comments(id) ON DELETE CASCADE,
      content_type TEXT NOT NULL DEFAULT 'image/jpeg',
      data_base64 TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    CREATE TABLE IF NOT EXISTS signal_comment_reactions (
      comment_id UUID REFERENCES signal_comments(id) ON DELETE CASCADE,
      user_id UUID NOT NULL,
      emoji TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      PRIMARY KEY (comment_id, user_id, emoji)
    );
    CREATE TABLE IF NOT EXISTS signal_updates (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      signal_id UUID REFERENCES daily_signals(id) ON DELETE CASCADE,
      parent_id UUID REFERENCES signal_updates(id) ON DELETE CASCADE,
      author_name TEXT NOT NULL,
      author_email TEXT NOT NULL DEFAULT '',
      body TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    ALTER TABLE signal_updates ADD COLUMN IF NOT EXISTS author_email TEXT NOT NULL DEFAULT '';
    CREATE TABLE IF NOT EXISTS trade_plans (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID REFERENCES users(id),
      instrument_id TEXT NOT NULL,
      instrument_display TEXT NOT NULL,
      direction TEXT NOT NULL,
      entry DOUBLE PRECISION,
      stop_loss DOUBLE PRECISION,
      take_profit DOUBLE PRECISION,
      notes TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

    CREATE TABLE IF NOT EXISTS community_posts (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID REFERENCES users(id) ON DELETE SET NULL,
      author_name TEXT NOT NULL,
      author_email TEXT NOT NULL DEFAULT '',
      body TEXT NOT NULL,
      is_team BOOLEAN NOT NULL DEFAULT false,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS post_type TEXT NOT NULL DEFAULT 'text';
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS poll_options JSONB;
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS allow_comments BOOLEAN NOT NULL DEFAULT true;
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN NOT NULL DEFAULT false;
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS pinned_at TIMESTAMPTZ;
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS outcome_tag TEXT;
    CREATE TABLE IF NOT EXISTS community_post_images (
      post_id UUID REFERENCES community_posts(id) ON DELETE CASCADE,
      position INT NOT NULL,
      content_type TEXT NOT NULL DEFAULT 'image/jpeg',
      data_base64 TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      PRIMARY KEY (post_id, position)
    );
    CREATE TABLE IF NOT EXISTS post_poll_votes (
      poll_id UUID REFERENCES community_posts(id) ON DELETE CASCADE,
      user_id UUID NOT NULL,
      option_id TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      PRIMARY KEY (poll_id, user_id)
    );
    CREATE TABLE IF NOT EXISTS post_reactions (
      post_id UUID REFERENCES community_posts(id) ON DELETE CASCADE,
      user_id UUID NOT NULL,
      emoji TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      PRIMARY KEY (post_id, user_id, emoji)
    );
    CREATE TABLE IF NOT EXISTS post_views (
      post_id UUID REFERENCES community_posts(id) ON DELETE CASCADE,
      user_id UUID NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      PRIMARY KEY (post_id, user_id)
    );
    CREATE TABLE IF NOT EXISTS post_comments (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      post_id UUID REFERENCES community_posts(id) ON DELETE CASCADE,
      user_id UUID REFERENCES users(id) ON DELETE SET NULL,
      author_name TEXT NOT NULL,
      author_email TEXT NOT NULL DEFAULT '',
      body TEXT NOT NULL,
      parent_id UUID REFERENCES post_comments(id) ON DELETE CASCADE,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    CREATE TABLE IF NOT EXISTS push_tokens (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      token TEXT NOT NULL UNIQUE,
      platform TEXT NOT NULL DEFAULT 'android',
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    CREATE TABLE IF NOT EXISTS notifications (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      type TEXT NOT NULL,
      title TEXT NOT NULL,
      body TEXT NOT NULL,
      data JSONB,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      read_at TIMESTAMPTZ
    );
    CREATE INDEX IF NOT EXISTS idx_push_tokens_user ON push_tokens(user_id);
    CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id, created_at DESC);
    CREATE TABLE IF NOT EXISTS alert_state (
      key TEXT PRIMARY KEY,
      value JSONB,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    CREATE TABLE IF NOT EXISTS push_log (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID,
      token_suffix TEXT,
      status TEXT NOT NULL,
      http_status INTEGER,
      error TEXT,
      title TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    CREATE INDEX IF NOT EXISTS idx_push_log_time ON push_log(created_at DESC);
  `);
}

const TRIAL_DAYS = 7;

// Free-tier chart-analysis allowance per rolling 24h once the 7-day trial
// has lapsed. Premium (and active-trial) users are unlimited.
const FREE_ANALYSES_PER_DAY = 3;

async function analysisUsage(userId) {
  const { rows } = await pool.query(
    `SELECT count(*)::int AS used FROM analyses
     WHERE user_id = $1 AND created_at > now() - interval '24 hours'`,
    [userId]
  );
  const used = rows[0].used;
  return { used, limit: FREE_ANALYSES_PER_DAY, remaining: Math.max(0, FREE_ANALYSES_PER_DAY - used) };
}

function trialInfo(row) {
  const startedAt = new Date(row.trial_started_at);
  const endsAt = new Date(startedAt.getTime() + TRIAL_DAYS * 24 * 60 * 60 * 1000);
  const isPremium = Boolean(row.is_premium);
  const active = isPremium || Date.now() < endsAt.getTime();
  const daysRemaining = Math.max(0, Math.ceil((endsAt.getTime() - Date.now()) / (24 * 60 * 60 * 1000)));
  return {
    trialStartedAt: startedAt.toISOString(),
    trialEndsAt: endsAt.toISOString(),
    isPremium,
    trialActive: active,
    trialDaysRemaining: daysRemaining
  };
}

// --- Session auth middleware: verifies the Bearer session JWT issued at /api/auth/google ---
function requireAuth(req, res, next) {
  if (!JWT_SECRET) {
    return res.status(503).json({ error: "Sessions are not configured yet." });
  }
  const header = req.headers.authorization || "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : null;
  if (!token) {
    return res.status(401).json({ error: "Missing session token" });
  }
  try {
    req.session = jwt.verify(token, JWT_SECRET);
    // Presence: any authenticated call means the user is actively using the
    // app — fire-and-forget, never blocks or breaks the request.
    if (pool) {
      pool
        .query(`UPDATE users SET last_seen_at = now() WHERE id = $1`, [req.session.sub])
        .catch(() => {});
    }
    next();
  } catch (err) {
    return res.status(401).json({ error: "Invalid or expired session" });
  }
}

// --- Legal pages (real content, linked from the app's welcome screen) ---
app.get("/terms", (_req, res) => {
  res.set("Content-Type", "text/html; charset=utf-8").send(termsOfServiceHtml());
});
app.get("/privacy", (_req, res) => {
  res.set("Content-Type", "text/html; charset=utf-8").send(privacyPolicyHtml());
});

// --- Health ---
app.get("/health", async (_req, res) => {
  const config = {
    database: Boolean(pool),
    databaseConnected: false,
    databaseError: null,
    fcm: !!process.env.FCM_SERVICE_ACCOUNT_JSON,
    googleAuth: Boolean(GOOGLE_WEB_CLIENT_ID),
    analysis: Boolean(OPENROUTER_API_KEY)
  };
  // Real connectivity check: env presence is NOT enough — a bad host or
  // dead route would otherwise show green while every DB query fails.
  if (pool) {
    try {
      const t0 = Date.now();
      await pool.query("SELECT 1 AS ping");
      config.databaseConnected = true;
      config.databasePingMs = Date.now() - t0;
    } catch (err) {
      config.databaseError = String(err.message || err).slice(0, 200);
      console.error("[health] DB ping FAILED:", config.databaseError);
    }
  }
  const ok = config.databaseConnected && config.fcm && config.googleAuth;
  res.status(ok ? 200 : 503).json({
    ok,
    service: "market-ai-api",
    by: "Veltravia Technologies",
    time: new Date().toISOString(),
    config
  });
});

// --- Instruments catalog ---
app.get("/api/instruments", (_req, res) => {
  res.json({ categories, count: ALL.length, instruments: ALL });
});

// --- Google auth: verify ID token, upsert user, issue session JWT ---
app.post("/api/auth/google", async (req, res) => {
  if (!GOOGLE_WEB_CLIENT_ID) {
    return res.status(503).json({
      error: "Google sign-in is not configured yet. Add GOOGLE_WEB_CLIENT_ID."
    });
  }
  const { idToken } = req.body || {};
  if (!idToken || typeof idToken !== "string") {
    return res.status(400).json({ error: "idToken is required" });
  }

  try {
    const client = new OAuth2Client();
    const ticket = await client.verifyIdToken({
      idToken,
      audience: GOOGLE_WEB_CLIENT_ID
    });
    const payload = ticket.getPayload();
    if (!payload || !payload.sub) {
      return res.status(401).json({ error: "Invalid Google ID token" });
    }

    let user = {
      googleSub: payload.sub,
      email: payload.email || "",
      name: payload.name || "Trader",
      picture: payload.picture || ""
    };

    if (pool) {
      const { rows } = await pool.query(
        `INSERT INTO users (google_sub, email, name, picture)
         VALUES ($1, $2, $3, $4)
         ON CONFLICT (google_sub)
         DO UPDATE SET email = EXCLUDED.email, name = EXCLUDED.name, picture = EXCLUDED.picture
         RETURNING id, google_sub, email, name, picture, community_joined, community_joined_at,
                   trial_started_at, is_premium, questionnaire, questionnaire_completed_at,
                   (xmax = 0) AS inserted_new`,
        [payload.sub, user.email, user.name, user.picture]
      );
      const isNewUser = Boolean(rows[0].inserted_new);
      const questionnaireCompleted = rows[0].questionnaire_completed_at != null;
      // Admin emails are promoted to a persistent role (drives the Admin
      // badge in the community and lets admins post links un-checked).
      if (ADMIN_EMAILS.includes(String(rows[0].email || "").toLowerCase())) {
        await pool.query(`UPDATE users SET role = 'admin' WHERE id = $1 AND role <> 'admin'`, [rows[0].id]);
        rows[0].role = "admin";
      }
      user = {
        id: rows[0].id,
        googleSub: rows[0].google_sub,
        email: rows[0].email,
        name: rows[0].name,
        picture: rows[0].picture,
        communityJoined: rows[0].community_joined,
        communityJoinedAt: rows[0].community_joined_at,
        role: rows[0].role || "user",
        questionnaireCompleted,
        questionnaire: questionnaireCompleted ? rows[0].questionnaire : null,
        isNewUser,
        ...trialInfo(rows[0])
      };
    }

    const sessionToken = JWT_SECRET
      ? jwt.sign({ sub: payload.sub, email: user.email }, JWT_SECRET, { expiresIn: "30d" })
      : null;

    // User-facing email — fire-and-forget, never blocks or fails sign-in.
    // New user: welcome email. Registered user signing in again: security notice.
    (async () => {
      try {
        const meta = { at: new Date().toISOString(), userAgent: req.headers["user-agent"] || "" };
        if (isNewUser) {
          await sendWelcomeEmail({ googleSub: user.googleSub, email: user.email, name: user.name });
        } else {
          await sendSecurityAlert({ googleSub: user.googleSub, email: user.email, name: user.name }, meta);
        }
      } catch (e) {
        console.warn("[auth/google] email error:", String(e.message || e));
      }
    })();

    return res.json({ user, sessionToken });
  } catch (err) {
    console.error("[auth/google] verification failed:", err && err.message, err && err.stack);
    return res.status(401).json({
      error: "Google token verification failed",
      reason: err && err.message ? String(err.message) : "unknown"
    });
  }
});

// --- Trading profile (questionnaire) -----------------------------------
// Saves the onboarding questionnaire server-side so completion survives
// sign-out / reinstall / new devices. The app uses questionnaireCompleted
// from the sign-in response to route: completed users go straight to Home;
// only genuinely-new users go through the questionnaire flow.
app.post("/api/profile/questionnaire", requireAuth, async (req, res) => {
  const answers = req.body && req.body.answers;
  if (!answers || typeof answers !== "object" || Array.isArray(answers)) {
    return res.status(400).json({ error: "answers object is required" });
  }
  if (!pool) return res.status(503).json({ error: "Database is not available." });
  try {
    const { rows } = await pool.query(
      `UPDATE users
       SET questionnaire = $1, questionnaire_completed_at = now()
       WHERE google_sub = $2
       RETURNING questionnaire, questionnaire_completed_at`,
      [JSON.stringify(answers), req.session.sub]
    );
    if (rows.length === 0) return res.status(404).json({ error: "User not found." });
    return res.json({
      questionnaire: rows[0].questionnaire,
      questionnaireCompletedAt: rows[0].questionnaire_completed_at
    });
  } catch (err) {
    console.error("[profile/questionnaire] error:", String(err.message || err));
    return res.status(500).json({ error: "Could not save your trading profile." });
  }
});

// --- Ops: preview the two user-facing emails (welcome + security sign-in) ---
// Sends samples of both email types so delivery and formatting can be verified
// end-to-end. Samples go to LOGIN_ALERT_EMAIL (the owner inbox), never to a user.
app.post("/api/admin/login-email-test", async (req, res) => {
  if (!CRON_SECRET || req.headers["x-cron-secret"] !== CRON_SECRET) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  const sample = { googleSub: "test-sample", email: process.env.LOGIN_ALERT_EMAIL || process.env.BREVO_SENDER_EMAIL, name: "Sample Test User" };
  const welcome = await sendWelcomeEmail(sample);
  // bypass the dedupe window for the sample security email
  const security = await sendSecurityAlert({ ...sample, googleSub: "test-sample-2" }, {
    at: new Date().toISOString(),
    userAgent: req.headers["user-agent"] || "backend-test"
  });
  return res.json({
    welcome,
    security,
    sentTo: sample.email
  });
});

// --- Ops: health-down alert email (called by the health monitor cron) ---
app.post("/api/admin/health-alert", async (req, res) => {
  if (!CRON_SECRET || req.headers["x-cron-secret"] !== CRON_SECRET) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  const detail = (req.body && (req.body.detail || req.body.error)) || "health check failed";
  const result = await sendHealthAlertEmail(detail);
  return res.json(result);
});

// --- Ops: verify the new-vs-returning upsert logic used to pick the email type ---
// Runs the exact auth upsert twice with a synthetic account and reports whether
// Postgres flags the first as new and the second as returning. Test row removed.
app.post("/api/admin/upsert-check", async (req, res) => {
  if (!CRON_SECRET || req.headers["x-cron-secret"] !== CRON_SECRET) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const sub = `upsert-check-${Date.now()}`;
    const q = `INSERT INTO users (google_sub, email, name, picture)
               VALUES ($1, $2, $3, $4)
               ON CONFLICT (google_sub)
               DO UPDATE SET email = EXCLUDED.email
               RETURNING (xmax = 0) AS inserted_new`;
    const first = await pool.query(q, [sub, "upsert-check@example.com", "Upsert Check", ""]);
    const second = await pool.query(q, [sub, "upsert-check@example.com", "Upsert Check", ""]);
    await pool.query(`DELETE FROM users WHERE google_sub = $1`, [sub]);
    return res.json({
      firstSignInFlaggedNew: first.rows[0].inserted_new,
      secondSignInFlaggedNew: second.rows[0].inserted_new,
      logicCorrect: first.rows[0].inserted_new === true && second.rows[0].inserted_new === false
    });
  } catch (err) {
    return res.status(500).json({ error: "upsert check failed", detail: String(err.message || err) });
  }
});

// --- Community: free onboarding community access (real DB-persisted membership) ---
app.post("/api/community/join", requireAuth, async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const { rows } = await pool.query(
      `UPDATE users
       SET community_joined = true,
           community_joined_at = COALESCE(community_joined_at, now())
       WHERE google_sub = $1
       RETURNING community_joined, community_joined_at`,
      [req.session.sub]
    );
    if (!rows.length) {
      return res.status(404).json({ error: "User not found" });
    }
    return res.json({
      joined: rows[0].community_joined,
      joinedAt: rows[0].community_joined_at
    });
  } catch (err) {
    return res.status(500).json({ error: "Could not join community", detail: String(err.message || err) });
  }
});

// --- Account: real status + deletion request (Settings screen) ---
// Deletion is a REQUEST, not an instant hard-delete: the account has rows
// across trade_plans/analyses/community/signals that a same-instant cascade
// would silently orphan or corrupt for other users (e.g. community posts).
// Requesting sets a real timestamp the user can see and cancel; permanent
// erasure is handled by support within 30 days, same model FxLens itself
// (and most consumer apps) use for account deletion.
app.get("/api/account/status", requireAuth, async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const { rows } = await pool.query(
      `SELECT deletion_requested_at FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) {
      return res.status(404).json({ error: "User not found" });
    }
    return res.json({ deletionRequestedAt: rows[0].deletion_requested_at });
  } catch (err) {
    return res.status(500).json({ error: "Could not load account status", detail: String(err.message || err) });
  }
});

app.post("/api/account/delete-request", requireAuth, async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const { rows } = await pool.query(
      `UPDATE users SET deletion_requested_at = COALESCE(deletion_requested_at, now())
       WHERE google_sub = $1
       RETURNING deletion_requested_at`,
      [req.session.sub]
    );
    if (!rows.length) {
      return res.status(404).json({ error: "User not found" });
    }
    return res.json({ deletionRequestedAt: rows[0].deletion_requested_at });
  } catch (err) {
    return res.status(500).json({ error: "Could not request account deletion", detail: String(err.message || err) });
  }
});

app.post("/api/account/delete-request/cancel", requireAuth, async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    await pool.query(
      `UPDATE users SET deletion_requested_at = NULL WHERE google_sub = $1`,
      [req.session.sub]
    );
    return res.json({ deletionRequestedAt: null });
  } catch (err) {
    return res.status(500).json({ error: "Could not cancel deletion request", detail: String(err.message || err) });
  }
});

// Public: total real member count (used on the Home screen community card —
// no fabricated numbers, this is a literal COUNT of users who have joined).
/**
 * Fast live spot prices for the Trending rows currently on screen — polled
 * every few seconds by the app so the section visibly ticks in real time
 * between the heavier 3-minute /api/trending refreshes. Real Coinbase/
 * Binance spot prices only; a symbol is simply omitted if no venue lists it.
 */
app.get("/api/trending/quotes", async (req, res) => {
  try {
    const symbols = String(req.query.symbols || "").split(",").map((s) => s.trim()).filter(Boolean);
    if (symbols.length === 0) return res.json({ quotes: {} });
    const quotes = await fetchLiveQuotes(symbols);
    res.json({ quotes });
  } catch (err) {
    res.status(502).json({ error: "Could not load live quotes right now.", detail: String(err.message || err) });
  }
});

/**
 * Public, always-live multi-asset watchlist (Futures/Forex/Crypto) — the
 * app polls this on a short interval so prices genuinely tick, exactly
 * like a real trading watchlist. Real feeds only (src/prices.js), no
 * placeholders; changePct is null until a same-day reference exists.
 */
app.get("/api/markets/watchlist", async (_req, res) => {
  try {
    const rows = await fetchWatchlist();
    res.json({ rows, fetchedAt: new Date().toISOString() });
  } catch (err) {
    res.status(502).json({ error: "Could not load live market data right now.", detail: String(err.message || err) });
  }
});

/**
 * Public, real OHLC candles for the Market View screen — the live
 * candlestick chart behind each Watchlist row. Sources per asset class
 * (Coinbase / Yahoo), 4H aggregated from real 1H bars, cached. Includes
 * the instrument's Watchlist metadata (display name + subtitle) so the
 * screen needs a single call.
 */
app.get("/api/markets/candles", async (req, res) => {
  const id = String(req.query.id || "").toLowerCase();
  const interval = String(req.query.interval || "15m").toLowerCase();
  if (!id) {
    return res.status(400).json({ error: "Missing ?id= instrument" });
  }
  const meta = WATCHLIST.find((w) => w.id === id) || null;
  try {
    const data = await fetchCandles(id, interval);
    // Watchlist instruments keep their curated name/subtitle; any other
    // market (e.g. a Trending coin) uses the honest class labels the
    // candle engine itself resolved (crypto / forex).
    res.json({
      ...data,
      display: meta ? meta.display : data.display,
      subtitle: meta ? meta.subtitle : data.subtitle,
      fetchedAt: new Date().toISOString()
    });
  } catch (err) {
    res.status(502).json({ error: "Could not load live candles right now.", detail: String(err.message || err) });
  }
});

/** Public, real spot price for a single Watchlist instrument (reuses the
 *  cached multi-feed price engine — Yahoo/Frankfurter/CoinGecko/Binance/
 *  Stooq chains). The Market View screen polls this for its big live
 *  ticking price, so it stays fast and cheap for one instrument. */
app.get("/api/markets/price", async (req, res) => {
  const id = String(req.query.id || "").toLowerCase();
  if (!id) {
    return res.status(400).json({ error: "Missing ?id= instrument" });
  }
  try {
    const price = await fetchPrice(id);
    if (price == null || !Number.isFinite(price)) {
      return res.status(502).json({ error: "Live price temporarily unavailable for this instrument." });
    }
    res.json({ id, price, fetchedAt: new Date().toISOString() });
  } catch (err) {
    res.status(502).json({ error: "Could not load live price right now.", detail: String(err.message || err) });
  }
});

/** Public, real economic calendar (ForexFactory feed) — NFP, CPI, rate decisions, etc. */
app.get("/api/calendar/economic", async (_req, res) => {
  try {
    const events = await fetchEconomicCalendar();
    res.json({ events, fetchedAt: new Date().toISOString() });
  } catch (err) {
    res.status(502).json({ error: "Could not load the economic calendar right now.", detail: String(err.message || err) });
  }
});

/** Public, real aggregated Forex/Crypto/Stocks news (Investing.com, Cointelegraph, Yahoo Finance). */
app.get("/api/calendar/news", async (req, res) => {
  try {
    const all = await fetchMarketNews();
    const category = String(req.query.category || "all").toLowerCase();
    const limit = Math.min(parseInt(req.query.limit, 10) || 40, 100);
    const filtered = category === "all" ? all : all.filter((n) => n.category === category);
    res.json({ items: filtered.slice(0, limit), fetchedAt: new Date().toISOString() });
  } catch (err) {
    res.status(502).json({ error: "Could not load market news right now.", detail: String(err.message || err) });
  }
});

/** Public, live "Trending" tokens for the Home screen — top coins by market cap. */
app.get("/api/trending", async (_req, res) => {
  try {
    const tokens = await fetchTrending(15);
    res.json({ tokens, fetchedAt: new Date().toISOString() });
  } catch (err) {
    res.status(502).json({ error: "Could not load trending tokens right now.", detail: String(err.message || err) });
  }
});

app.get("/api/community/stats", async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const { rows } = await pool.query(
      `SELECT
         COUNT(*) FILTER (WHERE community_joined = true)::int AS total,
         COUNT(*) FILTER (WHERE community_joined = true AND last_seen_at > now() - interval '5 minutes')::int AS online
       FROM users`
    );
    const posts = await pool.query(`SELECT COUNT(*)::int AS total FROM community_posts`);
    return res.json({
      totalMembers: rows[0]?.total ?? 0,
      onlineCount: rows[0]?.online ?? 0,
      totalPosts: posts.rows[0]?.total ?? 0
    });
  } catch (err) {
    return res.status(500).json({ error: "Could not load community stats", detail: String(err.message || err) });
  }
});

/** Admin: the Team Console overview dashboard — real aggregate numbers only,
 *  no estimates. Everything here is a live COUNT from the database. */
app.get("/api/admin/overview", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only the MarketScope AI team can view the overview." });
  }
  try {
    const [members, signups, analyses, analysesDaily, signals, community, push] = await Promise.all([
      pool.query(
        `SELECT
           COUNT(*)::int AS total,
           COUNT(*) FILTER (WHERE created_at > now() - interval '7 days')::int AS last7d,
           COUNT(*) FILTER (WHERE community_joined = true)::int AS community,
           COUNT(*) FILTER (WHERE last_seen_at > now() - interval '5 minutes')::int AS online,
           COUNT(*) FILTER (WHERE trial_started_at > now() - interval '7 days' AND (is_premium = false OR is_premium IS NULL))::int AS trialsActive,
           COUNT(*) FILTER (WHERE is_premium = true)::int AS premium
         FROM users`
      ),
      // signups per day, last 14 days (for the mini chart)
      pool.query(
        `SELECT to_char(d.day, 'YYYY-MM-DD') AS day,
                (SELECT COUNT(*)::int FROM users u WHERE u.created_at >= d.day AND u.created_at < d.day + interval '1 day') AS count
           FROM generate_series(date_trunc('day', now()) - interval '13 days', date_trunc('day', now()), interval '1 day') AS d(day)
           ORDER BY d.day`
      ),
      pool.query(
        `SELECT
           COUNT(*)::int AS total,
           COUNT(*) FILTER (WHERE created_at > now() - interval '7 days')::int AS last7d
         FROM analyses`
      ),
      // analyses per day, last 7 days (for the mini chart)
      pool.query(
        `SELECT to_char(d.day, 'YYYY-MM-DD') AS day,
                (SELECT COUNT(*)::int FROM analyses a WHERE a.created_at >= d.day AND a.created_at < d.day + interval '1 day') AS count
           FROM generate_series(date_trunc('day', now()) - interval '6 days', date_trunc('day', now()), interval '1 day') AS d(day)
           ORDER BY d.day`
      ),
      pool.query(
        `SELECT
           COUNT(*)::int AS total,
           COUNT(*) FILTER (WHERE status <> 'closed')::int AS open,
           COUNT(*) FILTER (WHERE status = 'closed' AND outcome = 'successful')::int AS won,
           COUNT(*) FILTER (WHERE status = 'closed' AND outcome <> 'successful')::int AS lost
         FROM daily_signals`
      ),
      pool.query(
        `SELECT
           (SELECT COUNT(*)::int FROM community_posts) AS posts,
           (SELECT COUNT(*)::int FROM post_comments) AS comments,
           (SELECT COUNT(*)::int FROM post_poll_votes) AS pollVotes,
           (SELECT COUNT(*)::int FROM post_reactions) AS reactions
        `
      ),
      pool.query(`SELECT COUNT(DISTINCT token)::int AS devices FROM push_tokens`)
    ]);

    res.json({
      members: members.rows[0],
      signupsDaily: signups.rows,
      analyses: analyses.rows[0],
      analysesDaily: analysesDaily.rows,
      signals: signals.rows[0],
      community: community.rows[0],
      pushDevices: push.rows[0].devices
    });
  } catch (err) {
    res.status(500).json({ error: "Could not load the overview", detail: String(err.message || err) });
  }
});

/** Presence heartbeat — the app pings this while it is in the foreground so
 *  "online now" stays truthful even when the user is sitting on a public
 *  screen (no authenticated calls being made). */
app.post("/api/presence/ping", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    await pool.query(`UPDATE users SET last_seen_at = now() WHERE id = $1`, [req.session.sub]);
    res.json({ ok: true });
  } catch (_err) {
    res.json({ ok: true }); // presence is best-effort — never surface an error
  }
});

/** Admin: list members with roles + presence (for the mentor manager). */
app.get("/api/admin/members", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only the MarketScope AI team can manage members." });
  }
  const q = String(req.query.q || "").trim().toLowerCase();
  try {
    const { rows } = await pool.query(
      `SELECT id, name, email, picture, role, community_joined, created_at, last_seen_at
         FROM users
        WHERE ($1 = '' OR lower(name) LIKE '%' || $1 || '%' OR lower(email) LIKE '%' || $1 || '%')
        ORDER BY (last_seen_at IS NULL), last_seen_at DESC NULLS LAST, created_at ASC
        LIMIT 200`,
      [q]
    );
    res.json({
      members: rows.map((u) => ({
        id: u.id,
        name: u.name || u.email || "Member",
        email: u.email,
        picture: u.picture || null,
        role: u.role || "member",
        communityJoined: !!u.community_joined,
        joinedAt: u.created_at,
        lastSeenAt: u.last_seen_at,
        online: u.last_seen_at != null && Date.now() - new Date(u.last_seen_at).getTime() < 5 * 60 * 1000
      }))
    });
  } catch (err) {
    res.status(500).json({ error: "Could not load members", detail: String(err.message || err) });
  }
});

/** Admin: promote/demote a member to mentor. Admins themselves are env-gated
 *  and cannot be changed from the app. */
app.post("/api/admin/members/:id/role", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only the MarketScope AI team can manage roles." });
  }
  const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (!UUID_RE.test(req.params.id)) return res.status(404).json({ error: "Member not found." });
  const role = String((req.body || {}).role || "").toLowerCase();
  if (!["member", "mentor"].includes(role)) {
    return res.status(400).json({ error: "role must be 'member' or 'mentor'." });
  }
  try {
    const { rows } = await pool.query(
      `UPDATE users SET role = $1
        WHERE id = $2 AND role <> 'admin'
        RETURNING id, name, email, picture, role, community_joined, created_at, last_seen_at`,
      [role, req.params.id]
    );
    if (!rows.length) {
      // either the member does not exist, or they are an admin (locked)
      return res.status(404).json({ error: "Member not found or role is locked." });
    }
    const u = rows[0];
    res.json({
      member: {
        id: u.id, name: u.name || u.email || "Member", email: u.email,
        picture: u.picture || null, role: u.role || "member",
        communityJoined: !!u.community_joined, joinedAt: u.created_at,
        lastSeenAt: u.last_seen_at,
        online: u.last_seen_at != null && Date.now() - new Date(u.last_seen_at).getTime() < 5 * 60 * 1000
      }
    });
  } catch (err) {
    res.status(500).json({ error: "Could not update the role", detail: String(err.message || err) });
  }
});

app.get("/api/community/status", requireAuth, async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const { rows } = await pool.query(
      `SELECT community_joined, community_joined_at FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) {
      return res.status(404).json({ error: "User not found" });
    }
    return res.json({
      joined: rows[0].community_joined,
      joinedAt: rows[0].community_joined_at
    });
  } catch (err) {
    return res.status(500).json({ error: "Could not load community status", detail: String(err.message || err) });
  }
});

// --- Trial status: 7 days of full free access from account creation, then Premium required ---
// --- Subscribe: email link landing page -> deep link into the app's Subscribe screen ---
// The trial-expired email's Subscribe button opens this page; it immediately
// tries to open the installed app at marketscopeai://subscribe and shows a
// clear fallback for anyone without the app on this device.
app.get("/subscribe", (_req, res) => {
  res.set("Content-Type", "text/html; charset=utf-8");
  res.send(`<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>MarketScope AI — Subscribe</title></head>
<body style="margin:0;background:#ffffff;font-family:Arial,Helvetica,sans-serif;color:#1a1a1a;">
<div style="max-width:420px;margin:0 auto;padding:48px 20px;text-align:center;">
<img src="https://raw.githubusercontent.com/kora-messenger/Market-Ai/main/branding/email_logo.png" width="84" height="84" alt="MarketScope AI" style="border-radius:19px;">
<h1 style="font-size:20px;margin:24px 0 8px;">MarketScope AI Premium</h1>
<p id="opening" style="font-size:15px;color:#6b6b6b;">Opening the app...</p>
<div id="fallback" style="display:none;font-size:15px;color:#1a1a1a;line-height:1.6;">
<p style="margin:0 0 12px;">If the app didn't open, tap the button below or make sure MarketScope AI is installed on this phone.</p>
<a href="marketscopeai://subscribe" style="display:inline-block;padding:13px 34px;background:#1B2232;color:#ffffff;text-decoration:none;font-size:15px;font-weight:bold;border-radius:10px;">Open Subscribe Screen</a>
</div>
</div>
<script>
setTimeout(function(){ window.location.href = "marketscopeai://subscribe"; }, 400);
setTimeout(function(){
  document.getElementById("opening").style.display = "none";
  document.getElementById("fallback").style.display = "block";
}, 3000);
</script>
</body></html>`);
});

// --- Subscription: public plan info (single source of truth for the app) ---
app.get("/api/subscription/plans", (_req, res) => {
  res.json({
    currency: SUB_CURRENCY,
    paymentsReady: Boolean(PAYSTACK_SECRET_KEY),
    plans: [
      {
        id: "monthly",
        name: "MarketScope AI Premium",
        price: SUB_PRICE,
        period: "month",
        features: [
          "Unlimited AI chart analysis",
          "Daily AI & team trading signals",
          "Full community access"
        ]
      }
    ]
  });
});

// --- Subscription: start a Paystack checkout (Premium, real payment) ---
app.post("/api/subscription/checkout", requireAuth, async (req, res) => {
  if (!PAYSTACK_SECRET_KEY) {
    return res.status(503).json({
      error: "Subscriptions are being activated right now. We'll notify you in the app the moment payments go live \u2014 thank you for your patience!"
    });
  }
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const { rows } = await pool.query(
      `SELECT id, email FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length || !rows[0].email) {
      return res.status(400).json({ error: "No email address on your account \u2014 needed for secure checkout." });
    }
    const reference = `msa-${req.session.sub.slice(0, 12)}-${Date.now()}`;
    const initRes = await fetch("https://api.paystack.co/transaction/initialize", {
      method: "POST",
      signal: AbortSignal.timeout(15_000),
      headers: {
        Authorization: `Bearer ${PAYSTACK_SECRET_KEY}`,
        "Content-Type": "application/json",
        Accept: "application/json"
      },
      body: JSON.stringify({
        email: rows[0].email,
        amount: Math.round(SUB_PRICE * 100), // Paystack uses minor units
        currency: SUB_CURRENCY,
        reference,
        callback_url: `${SUBSCRIBE_URL}?payment=done`,
        metadata: { google_sub: req.session.sub }
      })
    });
    const body = await initRes.json().catch(() => ({}));
    if (!initRes.ok || !(body && body.data && body.data.authorization_url)) {
      return res.status(502).json({ error: "Could not start checkout right now. Please try again shortly." });
    }
    return res.json({
      authorizationUrl: body.data.authorization_url,
      reference: body.data.reference
    });
  } catch (err) {
    return res.status(502).json({ error: "Could not start checkout right now.", detail: String(err.message || err) });
  }
});

// --- Subscription: current premium state for the signed-in user ---
app.get("/api/subscription/status", requireAuth, async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const { rows } = await pool.query(
      `SELECT is_premium, trial_started_at, trial_expired_email_sent_at FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) {
      return res.status(404).json({ error: "User not found" });
    }
    return res.json(trialInfo(rows[0]));
  } catch (err) {
    return res.status(500).json({ error: "Could not load subscription status", detail: String(err.message || err) });
  }
});

// --- Trial expiry emails: find users whose 7-day free trial just ended and
// email them once. Called hourly by the GitHub Actions cron. ---
app.post("/api/trial/check-expiry", async (req, res) => {
  if (!CRON_SECRET || req.headers["x-cron-secret"] !== CRON_SECRET) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const { rows } = await pool.query(
      `SELECT id, google_sub, email, name
       FROM users
       WHERE is_premium = false
         AND trial_started_at + interval '7 days' <= now()
         AND trial_expired_email_sent_at IS NULL
       ORDER BY trial_started_at ASC
       LIMIT 50`
    );
    let sent = 0;
    let failed = 0;
    for (const u of rows) {
      const r = await sendTrialExpiredEmail({
        googleSub: u.google_sub,
        email: u.email,
        name: u.name
      });
      if (r.ok) {
        // mark sent even if Brevo reported a dedupe skip — the send pipeline is healthy
        await pool.query(
          `UPDATE users SET trial_expired_email_sent_at = now() WHERE id = $1`,
          [u.id]
        );
        sent++;
      } else {
        failed++;
      }
    }
    return res.json({ checked: rows.length, sent, failed });
  } catch (err) {
    return res.status(500).json({ error: "Trial expiry check failed", detail: String(err.message || err) });
  }
});

app.get("/api/trial/status", requireAuth, async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const { rows } = await pool.query(
      `SELECT id, trial_started_at, is_premium FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) {
      return res.status(404).json({ error: "User not found" });
    }
    const trial = trialInfo(rows[0]);
    if (trial.trialActive || rows[0].is_premium) {
      return res.json({ ...trial, analysisUsage: { used: null, limit: null, remaining: null, unlimited: true } });
    }
    const usage = await analysisUsage(rows[0].id);
    return res.json({ ...trial, analysisUsage: { ...usage, unlimited: false } });
  } catch (err) {
    return res.status(500).json({ error: "Could not load trial status", detail: String(err.message || err) });
  }
});

// --- AI chart analysis (server-side, OpenRouter vision) ---
const SYSTEM_PROMPT = `You are a senior market analyst. You receive two real chart screenshots of the same instrument:
- a 4H (higher timeframe) chart and a 15M (lower timeframe) chart.
The trader picked Scalp mode (favor 15M entries, quicker targets) or Swing mode (favor 4H structure, wider targets).
Analyze structure, trend, momentum, key support/resistance, and only if conditions clearly align, give a trade.
Respond with STRICT JSON only (no markdown fences), shape:
{
  "direction": "LONG" | "SHORT" | "NO_TRADE",
  "confidence": 0-100,
  "entryZone": {"low": number, "high": number},
  "stopLoss": number,
  "takeProfits": [number, number, number],
  "riskReward": number,
  "estimatedDuration": "your best estimate of how long this setup may take to play out, as a short human string like '2h - 3h 30m', based on the timeframe and momentum shown",
  "thesis": "3-5 sentence reasoning grounded in what is visible on the charts",
  "invalidation": "what would invalidate this setup",
  "keyLevels": [number]
}
Prices must be plausible for the instrument shown on the charts. Provide a realistic estimated duration based on timeframe and momentum. If the setup is not clean, choose NO_TRADE with a clear thesis.`;

function extractJson(text) {
  let t = (text || "").trim();
  if (t.startsWith("```")) {
    t = t.replace(/^```(json)?\s*/i, "").replace(/```\s*$/, "");
  }
  const first = t.indexOf("{");
  const last = t.lastIndexOf("}");
  if (first === -1 || last === -1) throw new Error("Model did not return JSON");
  return JSON.parse(t.slice(first, last + 1));
}

app.post("/api/analyze", requireAuth, async (req, res) => {
  if (!OPENROUTER_API_KEY) {
    return res.status(503).json({
      error: "Analysis engine is not configured yet. Add OPENROUTER_API_KEY."
    });
  }
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  const { instrumentId, mode, imageH4, imageM15 } = req.body || {};

  const instrument = byId[(instrumentId || "").toLowerCase()];
  if (!instrument) {
    return res.status(400).json({ error: `Unknown instrument: ${instrumentId}` });
  }
  if (!["scalp", "swing"].includes(mode)) {
    return res.status(400).json({ error: "mode must be 'scalp' or 'swing'" });
  }
  const isDataUrl = (s) => typeof s === "string" && /^data:image\/(png|jpe?g|webp);base64,/.test(s);
  if (!isDataUrl(imageH4) || !isDataUrl(imageM15)) {
    return res.status(400).json({
      error: "imageH4 and imageM15 must be base64 image data URLs (png/jpeg/webp)"
    });
  }

  let userRow;
  try {
    const { rows } = await pool.query(
      `SELECT id, trial_started_at, is_premium FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) {
      return res.status(404).json({ error: "User not found" });
    }
    userRow = rows[0];
  } catch (err) {
    return res.status(500).json({ error: "Could not verify account", detail: String(err.message || err) });
  }

  const trial = trialInfo(userRow);
  const premium = Boolean(userRow.is_premium);
  if (!trial.trialActive && !premium) {
    // Trial lapsed without a subscription: the free tier keeps the core
    // feature alive at 3 analyses per rolling 24h — the upgrade pressure
    // comes from wanting more, never from a dead app.
    const usage = await analysisUsage(userRow.id);
    if (usage.used >= usage.limit) {
      return res.status(429).json({
        error: `You've used all ${usage.limit} free chart analyses for today. Premium gives you unlimited analyses plus the full signal history.`,
        dailyLimitReached: true,
        ...usage
      });
    }
  }

  // --- Stage 1: verify the CURRENT live market for this instrument ---
  // Real price fetch from public sources; the analysis is anchored to it.
  let livePrice = null;
  try {
    livePrice = await fetchPrice(instrument.id);
  } catch (_e) {
    livePrice = null; // synthetics or a source outage — validation still runs
  }

  // --- Stage 2: validate the uploaded images are real trading charts that
  // plausibly match this instrument. A random photo must never be analyzed. ---
  const CHART_VALIDATION_PROMPT = `You are a strict input validator for a trading analysis engine.
You receive two images that MUST be genuine trading chart screenshots (candlestick, bar, or line chart with a visible price axis and time axis) of the SAME instrument, on the 4-hour and 15-minute timeframes.
Respond ONLY with JSON:
{
  "isChart": <true only if BOTH images are genuine trading charts (candlestick, bar or line) with a visible price scale. Do NOT require timeframe labels or that the two screenshots look different \u2014 judge only that each image is a real price chart>,
  "instrumentPlausible": <true only if the visible price scale plausibly belongs to the stated instrument, given its current live price. Charts may be from days or weeks ago, so judge order-of-magnitude plausibility (e.g. a EUR/USD chart shows values around 0.8-1.6, a USD/JPY chart around 130-160, an XAU/USD chart around 1800-4000, a BTC chart around tens of thousands)>,
  "reason": "<one short sentence explaining the verdict>"
}`;

  const livePriceLine = livePrice != null
    ? ` Current live market price of ${instrument.display}: ${livePrice}.`
    : "";
  let validation;
  try {
    const vResponse = await fetch("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${OPENROUTER_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: ANALYSIS_MODEL,
        max_tokens: 1500,
        reasoning: { effort: "low" },
        response_format: { type: "json_object" },
        messages: [
          { role: "system", content: CHART_VALIDATION_PROMPT },
          {
            role: "user",
            content: [
              {
                type: "text",
                text: `Stated instrument: ${instrument.display} (4H and 15M charts).${livePriceLine}`
              },
              { type: "image_url", image_url: { url: imageH4 } },
              { type: "image_url", image_url: { url: imageM15 } }
            ]
          }
        ]
      })
    });
    if (!vResponse.ok) {
      const detail = await vResponse.text();
      return res.status(502).json({
        error: "Analysis provider error (validation)",
        status: vResponse.status,
        detail: detail.slice(0, 400)
      });
    }
    const vData = await vResponse.json();
    validation = extractJson(vData.choices?.[0]?.message?.content || "");
  } catch (err) {
    return res.status(502).json({
      error: "Could not verify the uploaded charts. Please try again.",
      detail: String(err.message || err)
    });
  }

  if (validation.isChart === false) {
    return res.status(422).json({
      error: "These images don't look like valid trading charts. Please upload clear 4H and 15M chart screenshots of " + instrument.display + " from your broker.",
      invalidChart: true,
      reason: validation.reason || ""
    });
  }
  if (validation.instrumentPlausible === false) {
    return res.status(422).json({
      error: "These charts don't appear to match " + instrument.display + ". Please make sure both screenshots are the 4H and 15M charts of " + instrument.display + ".",
      instrumentMismatch: true,
      reason: validation.reason || ""
    });
  }

  // --- Stage 3: the real analysis, anchored to the verified live market ---
  try {
    const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${OPENROUTER_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: ANALYSIS_MODEL,
        max_tokens: 4000,
        reasoning: { effort: "low" },
        response_format: { type: "json_object" },
        messages: [
          { role: "system", content: SYSTEM_PROMPT },
          {
            role: "user",
            content: [
              {
                type: "text",
                text: `Instrument: ${instrument.display}. Mode: ${mode === "scalp" ? "Scalp (15M-biased)" : "Swing (4H-biased)"}.` +
                  (livePrice != null
                    ? ` Verified current market price of ${instrument.display}: ${livePrice}. Cross-check the chart against this live market — if the chart and the live market contradict each other, say so in the thesis.`
                    : "")
              },
              { type: "image_url", image_url: { url: imageH4 } },
              { type: "image_url", image_url: { url: imageM15 } }
            ]
          }
        ]
      })
    });

    if (!response.ok) {
      const detail = await response.text();
      return res.status(502).json({
        error: "Analysis provider error",
        status: response.status,
        detail: detail.slice(0, 400)
      });
    }

    const data = await response.json();
    const text = data.choices?.[0]?.message?.content || "";
    const analysis = extractJson(text);

    const result = {
      instrument: instrument.display,
      instrumentId: instrument.id,
      mode,
      model: ANALYSIS_MODEL,
      livePrice,
      marketVerified: livePrice != null,
      chartValidated: true,
      analysis,
      analyzedAt: new Date().toISOString()
    };

    const { rows } = await pool.query(
      `INSERT INTO analyses (user_id, instrument_id, mode, result) VALUES ($1, $2, $3, $4) RETURNING id`,
      [userRow.id, instrument.id, mode, JSON.stringify(result)]
    );
    if (rows.length) result.id = rows[0].id;

    return res.json({ ...result, ...trial });
  } catch (err) {
    return res.status(500).json({ error: "Analysis failed", detail: String(err.message || err) });
  }
});

// NOTE: scoped to the authenticated user's own google_sub -> user id. Previously these two
// routes had no auth and returned EVERY user's analyses — fixed while wiring per-user trials.
app.get("/api/analyses", requireAuth, async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const limit = Math.min(parseInt(req.query.limit, 10) || 30, 100);
    const { rows } = await pool.query(
      `SELECT a.id, a.instrument_id, a.mode, a.result, a.created_at
       FROM analyses a
       JOIN users u ON u.id = a.user_id
       WHERE u.google_sub = $1
       ORDER BY a.created_at DESC LIMIT $2`,
      [req.session.sub, limit]
    );
    res.json({
      analyses: rows.map((r) => ({
        id: r.id,
        instrumentId: r.instrument_id,
        mode: r.mode,
        analysis: r.result,
        analyzedAt: r.created_at
      }))
    });
  } catch (err) {
    res.status(500).json({ error: "Could not load analyses", detail: String(err.message || err) });
  }
});

app.get("/api/analyses/:id", requireAuth, async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (!UUID_RE.test(req.params.id)) {
    return res.status(404).json({ error: "Analysis not found" });
  }
  try {
    const { rows } = await pool.query(
      `SELECT a.id, a.instrument_id, a.mode, a.result, a.created_at
       FROM analyses a
       JOIN users u ON u.id = a.user_id
       WHERE a.id = $1 AND u.google_sub = $2`,
      [req.params.id, req.session.sub]
    );
    if (!rows.length) {
      return res.status(404).json({ error: "Analysis not found" });
    }
    const r = rows[0];
    res.json({
      id: r.id,
      instrumentId: r.instrument_id,
      mode: r.mode,
      analysis: r.result,
      analyzedAt: r.created_at
    });
  } catch (err) {
    res.status(500).json({ error: "Could not load analysis", detail: String(err.message || err) });
  }
});

// --- Trade Plans ---
// A real, user-authored trading plan (distinct from AI-generated saved
// signals): instrument, planned entry/SL/TP levels and free-form notes.
app.get("/api/trade-plans", requireAuth, async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const { rows } = await pool.query(
      `SELECT p.id, p.instrument_id, p.instrument_display, p.direction, p.entry,
              p.stop_loss, p.take_profit, p.notes, p.created_at
       FROM trade_plans p
       JOIN users u ON u.id = p.user_id
       WHERE u.google_sub = $1
       ORDER BY p.created_at DESC LIMIT 100`,
      [req.session.sub]
    );
    res.json({
      plans: rows.map((r) => ({
        id: r.id,
        instrumentId: r.instrument_id,
        instrument: r.instrument_display,
        direction: r.direction,
        entry: r.entry,
        stopLoss: r.stop_loss,
        takeProfit: r.take_profit,
        notes: r.notes,
        createdAt: r.created_at
      }))
    });
  } catch (err) {
    res.status(500).json({ error: "Could not load trade plans", detail: String(err.message || err) });
  }
});

app.post("/api/trade-plans", requireAuth, async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  const { instrumentId, instrument, direction, entry, stopLoss, takeProfit, notes } = req.body || {};
  if (!instrumentId || !instrument || !direction) {
    return res.status(400).json({ error: "instrumentId, instrument and direction are required" });
  }
  try {
    const { rows: userRows } = await pool.query(
      `SELECT id FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!userRows.length) {
      return res.status(404).json({ error: "User not found" });
    }
    const { rows } = await pool.query(
      `INSERT INTO trade_plans (user_id, instrument_id, instrument_display, direction, entry, stop_loss, take_profit, notes)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8) RETURNING id, created_at`,
      [
        userRows[0].id,
        String(instrumentId),
        String(instrument),
        String(direction).toUpperCase(),
        entry != null ? Number(entry) : null,
        stopLoss != null ? Number(stopLoss) : null,
        takeProfit != null ? Number(takeProfit) : null,
        notes ? String(notes).slice(0, 2000) : null
      ]
    );
    res.status(201).json({ id: rows[0].id, createdAt: rows[0].created_at });
  } catch (err) {
    res.status(500).json({ error: "Could not save trade plan", detail: String(err.message || err) });
  }
});

app.delete("/api/trade-plans/:id", requireAuth, async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (!UUID_RE.test(req.params.id)) {
    return res.status(404).json({ error: "Trade plan not found" });
  }
  try {
    const { rowCount } = await pool.query(
      `DELETE FROM trade_plans p
       USING users u
       WHERE p.id = $1 AND p.user_id = u.id AND u.google_sub = $2`,
      [req.params.id, req.session.sub]
    );
    if (!rowCount) {
      return res.status(404).json({ error: "Trade plan not found" });
    }
    res.json({ deleted: true });
  } catch (err) {
    res.status(500).json({ error: "Could not delete trade plan", detail: String(err.message || err) });
  }
});

// ============================================================
// Daily Signals — curated signals published by the MarketScope AI team
// (owner posts manually + an AI-generated daily call), with
// AUTOMATIC outcome resolution via live price checks.
// ============================================================

/** Resolves the admin/owner: ADMIN_EMAIL env override, else the first account ever created. */
async function getAdminSub() {
  const { rows } = await pool.query(
    `SELECT google_sub, email FROM users ORDER BY created_at ASC LIMIT 1`
  );
  if (!rows.length) return null;
  return String(rows[0].google_sub);
}

async function isAdminRequest(req) {
  if (!pool) return false;
  if (ADMIN_EMAILS.includes(String(req.session.email || "").toLowerCase())) return true;
  const adminSub = await getAdminSub();
  return !!adminSub && String(req.session.sub) === adminSub;
}

/** Cron endpoints are callable by the admin or with the CRON_SECRET header. */
function isCronRequest(req) {
  if (!CRON_SECRET) return false;
  const header = req.headers["x-cron-secret"] || "";
  return typeof header === "string" && header.length > 20 && header === CRON_SECRET;
}

const SIGNAL_REACTION_EMOJIS = ["\u{1F44D}", "\u{1F525}", "\u{1F62E}", "\u{1F44F}", "\u{2753}"]; // 👍 🔥 😮 👏 ❓

function signalToApi(r, extra) {
  const e = extra || {};
  return {
    id: r.id,
    author: r.author,
    instrumentId: r.instrument_id,
    instrument: r.instrument_display,
    direction: r.direction,
    mode: r.mode || null,
    entry: r.entry,
    stopLoss: r.stop_loss,
    takeProfits: r.take_profits,
    riskReward: r.risk_reward,
    thesis: r.thesis,
    strength: r.strength,
    status: r.status,
    outcome: r.outcome,
    triggeredAt: r.triggered_at,
    closedAt: r.closed_at,
    publishedAt: r.published_at,
    lastPrice: r.last_price,
    exitPrice: r.exit_price != null ? Number(r.exit_price) : null,
    resolvedBy: r.resolved_by || null,
    lastPriceAt: r.last_price_at,
    reactions: e.reactions || SIGNAL_REACTION_EMOJIS.map((emoji) => ({ emoji, count: 0, mine: false })),
    commentCount: e.commentCount || 0,
    saved: e.saved || false
  };
}

/**
 * Batch-fetches reaction rollups, comment counts and "saved by me" flags for
 * a set of signal ids in 3 queries total (mirrors the Community feed's
 * ANY($1::uuid[]) pattern) — avoids an N+1 query per card in the feed.
 */
async function signalSocialExtras(signalIds, myUserId) {
  const extras = {};
  for (const id of signalIds) {
    extras[id] = {
      reactions: SIGNAL_REACTION_EMOJIS.map((emoji) => ({ emoji, count: 0, mine: false })),
      commentCount: 0,
      saved: false
    };
  }
  if (!signalIds.length) return extras;
  const { rows: reactionRows } = await pool.query(
    `SELECT signal_id, emoji, count(*)::int AS c, bool_or(user_id = $2::uuid) AS mine
     FROM signal_reactions WHERE signal_id = ANY($1::uuid[]) GROUP BY signal_id, emoji`,
    [signalIds, myUserId]
  );
  for (const row of reactionRows) {
    const bucket = extras[row.signal_id];
    if (!bucket) continue;
    const slot = bucket.reactions.find((r) => r.emoji === row.emoji);
    if (slot) { slot.count = row.c; slot.mine = row.mine; }
  }
  const { rows: commentRows } = await pool.query(
    `SELECT signal_id, count(*)::int AS c FROM signal_comments WHERE signal_id = ANY($1::uuid[]) GROUP BY signal_id`,
    [signalIds]
  );
  for (const row of commentRows) {
    if (extras[row.signal_id]) extras[row.signal_id].commentCount = row.c;
  }
  if (myUserId) {
    const { rows: saveRows } = await pool.query(
      `SELECT signal_id FROM signal_saves WHERE signal_id = ANY($1::uuid[]) AND user_id = $2::uuid`,
      [signalIds, myUserId]
    );
    for (const row of saveRows) {
      if (extras[row.signal_id]) extras[row.signal_id].saved = true;
    }
  }
  return extras;
}

const SIGNAL_DAYS = 7 * 24 * 60 * 60 * 1000; // signals older than 7d close automatically

/**
 * PUBLIC aggregate stats (the "This month/week at a glance" card is shown to
 * everyone, exactly like the reference app). Real math over real outcomes:
 *   win rate  = successful / (successful + invalidated_sl)
 *   avg RR    = average risk_reward of all signals in range
 *   high-conv = signals marked strength 'strong' (>= 4 on numeric scales)
 */
app.get("/api/daily-signals/stats", async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const range = req.query.range === "week" ? "week" : "month";
  try {
    const { rows } = await pool.query(
      `SELECT status, outcome, risk_reward, strength, author FROM daily_signals
       WHERE published_at > now() - ($1 || ' days')::interval`,
      [range === "week" ? "7" : "30"]
    );
    const total = rows.length;
    const live = rows.filter((r) => r.status !== "closed").length;
    const wins = rows.filter((r) => r.outcome === "successful").length;
    const losses = rows.filter((r) => r.outcome === "invalidated_sl").length;
    const inProgress = rows.filter((r) => r.outcome === "triggered_active" || (r.status === "live" && !r.outcome)).length;
    const closed = rows.filter((r) => r.status === "closed").length;
    const decided = wins + losses;
    const successPct = decided ? Math.round((wins / decided) * 100) : null;
    const rrValues = rows.map((r) => Number(r.risk_reward)).filter((v) => Number.isFinite(v) && v > 0);
    const avgRR = rrValues.length
      ? Number((rrValues.reduce((a, b) => a + b, 0) / rrValues.length).toFixed(2))
      : null;
    const strongCount = rows.filter((r) => {
      const st = String(r.strength || "").toLowerCase();
      return st === "strong" || (!isNaN(parseFloat(st)) && parseFloat(st) >= 4);
    }).length;
    res.json({ range, total, live, closed, wins, losses, inProgress, successPct, avgRR, strongCount });
  } catch (err) {
    res.status(500).json({ error: "Could not load signal stats", detail: String(err.message || err) });
  }
});

/** Admin flag + entitlement for the Signals tab. */
app.get("/api/daily-signals/access", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const { rows } = await pool.query(
      `SELECT trial_started_at, is_premium FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) return res.status(404).json({ error: "User not found" });
    const trial = trialInfo(rows[0]);
    const isAdmin = await isAdminRequest(req);
    const entitled = trial.trialActive || rows[0].is_premium || isAdmin;
    res.json({ isAdmin, entitled, trialActive: trial.trialActive, trialDaysRemaining: trial.trialDaysRemaining, isPremium: rows[0].is_premium });
  } catch (err) {
    res.status(500).json({ error: "Could not check access", detail: String(err.message || err) });
  }
});

/** The live feed — entitled users only (premium/trial/admin), mirroring the reference paywall. */
app.get("/api/daily-signals", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const { rows: userRows } = await pool.query(
      `SELECT id, trial_started_at, is_premium FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!userRows.length) return res.status(404).json({ error: "User not found" });
    const me = userRows[0];
    const trial = trialInfo(me);
    const admin = await isAdminRequest(req);
    const entitled = trial.trialActive || me.is_premium || admin;
    const limit = Math.min(parseInt(req.query.limit, 10) || 50, 100);
    const { rows } = await pool.query(
      `SELECT * FROM daily_signals ORDER BY published_at DESC LIMIT $1`,
      [limit]
    );
    if (!entitled) {
      // Free tier: a real taste of the feed — the latest signal only —
      // plus an honest count of how much more is behind the paywall.
      const { rows: totalRows } = await pool.query(
        `SELECT count(*)::int AS c FROM daily_signals`
      );
      const sample = rows.slice(0, 1);
      const extras = await signalSocialExtras(sample.map((r) => r.id), me.id);
      return res.json({
        signals: sample.map((r) => signalToApi(r, extras[r.id])),
        locked: true,
        premiumSignalCount: totalRows[0].c
      });
    }
    const extras = await signalSocialExtras(rows.map((r) => r.id), me.id);
    res.json({ signals: rows.map((r) => signalToApi(r, extras[r.id])), locked: false });
  } catch (err) {
    res.status(500).json({ error: "Could not load signals", detail: String(err.message || err) });
  }
});

/** Owner publishes a curated signal manually. */
app.post("/api/daily-signals", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only the MarketScope AI team can publish daily signals." });
  }
  const { instrumentId, direction, entry, stopLoss, takeProfits, thesis, strength, mode } = req.body || {};
  const instrument = byId[(instrumentId || "").toLowerCase()];
  const tps = Array.isArray(takeProfits) ? takeProfits.filter((t) => Number.isFinite(Number(t))).map(Number).sort((a, b) => a - b) : [];
  const entryNum = Number(entry);
  const slNum = Number(stopLoss);
  if (!instrument || !direction || !Number.isFinite(entryNum) || !Number.isFinite(slNum) || tps.length === 0) {
    return res.status(400).json({ error: "instrumentId, direction, entry, stopLoss and at least one takeProfit are required" });
  }
  if (!["long", "short"].includes(String(direction).toLowerCase())) {
    return res.status(400).json({ error: "direction must be 'long' or 'short'" });
  }
  const dirLc = String(direction).toLowerCase();
  const sidesOk = dirLc === "long"
    ? slNum < entryNum && tps.every((t) => t > entryNum)
    : slNum > entryNum && tps.every((t) => t < entryNum);
  if (!sidesOk) {
    return res.status(400).json({ error: "Stop loss must sit on the losing side of entry and every take profit on the winning side" });
  }
  const modeLc = ["scalp", "swing"].includes(String(mode).toLowerCase()) ? String(mode).toLowerCase() : null;
  try {
    const rr = tps.length ? Number((Math.abs(tps[tps.length - 1] - entryNum) / Math.abs(entryNum - slNum)).toFixed(2)) : null;
    const { rows } = await pool.query(
      `INSERT INTO daily_signals
         (author, instrument_id, instrument_display, direction, entry, stop_loss, take_profits, risk_reward, thesis, strength, status, mode)
       VALUES ('owner', $1, $2, $3, $4, $5, $6, $7, $8, $9, 'live', $10)
       RETURNING *`,
      [
        instrument.id, instrument.display, String(direction).toLowerCase(),
        entryNum, slNum, JSON.stringify(tps), rr,
        thesis ? String(thesis).slice(0, 2000) : null,
        ["strong", "moderate", "weak"].includes(String(strength).toLowerCase()) ? String(strength).toLowerCase() : "moderate",
        modeLc
      ]
    );
    res.status(201).json(signalToApi(rows[0]));
    broadcastNewSignal(signalToApi(rows[0])).catch(() => {});
  } catch (err) {
    res.status(500).json({ error: "Could not publish signal", detail: String(err.message || err) });
  }
});

const DAILY_SIGNAL_SYSTEM_PROMPT = `You are the senior market analyst behind MarketScope AI's Daily Signals. You receive recent OHLC candles for a set of instruments from live public market data. Pick the single best trade setup among them — one with a clearly-defined invalidation (tight, logical stop) and realistic targets. Respond ONLY with JSON:
{
  "instrumentId": "one of the provided ids",
  "direction": "long" | "short",
  "mode": "scalp" | "swing",
  "entry": number,
  "stopLoss": number,
  "takeProfits": [number, number, number],
  "thesis": "2-3 sentences grounded in the price action shown (structure, momentum, key levels). No generic filler.",
  "strength": "strong" | "moderate" | "weak"
}
Rules: entry must sit within a few percent of the latest close; stop loss must be on the wrong side of entry (below for long, above for short); every take profit must be on the profitable side, ordered nearest first; risk:reward to the final target should be at least 1.5. "mode" reflects the real nature of the setup: "scalp" for a tight stop targeting a quick move (intraday), "swing" for a wider stop held over multiple days. If nothing qualifies, set strength "weak" and pick the least-bad setup anyway — never invent prices outside the data range shown.`;

/** AI-generated daily call (cron or admin). Runs at most once per UTC day. */
app.post("/api/daily-signals/auto", async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!isCronRequest(req)) {
    const authHeader = req.headers.authorization || "";
    if (!authHeader.startsWith("Bearer ")) {
      return res.status(401).json({ error: "Missing session token" });
    }
    try {
      req.session = jwt.verify(authHeader.slice(7), JWT_SECRET);
    } catch (_e) {
      return res.status(401).json({ error: "Invalid or expired session" });
    }
    if (!(await isAdminRequest(req))) {
      return res.status(403).json({ error: "Not authorized to trigger the daily AI signal." });
    }
  }
  if (!OPENROUTER_API_KEY) return res.status(503).json({ error: "Analysis engine is not configured yet." });
  try {
    const { rows: existing } = await pool.query(
      `SELECT id FROM daily_signals WHERE author = 'ai' AND published_at >= CURRENT_DATE`
    );
    if (existing.length) {
      return res.json({ skipped: true, reason: "AI signal already published today" });
    }

    const candidates = ["eurusd", "gbpusd", "usdjpy", "xauusd", "nas100", "btcusd", "ethusd"];
    const historyBlocks = [];
    for (const id of candidates) {
      const h = await fetchHistory(id, { interval: "1h", range: "5d" });
      if (!h || h.candles.length < 20) continue;
      const inst = byId[id];
      const last = h.candles[h.candles.length - 1];
      // compact: last 48 hourly candles
      const rows = h.candles.slice(-48).map((c) =>
        `${new Date(c.t * 1000).toISOString().slice(5, 16)} O:${round(c.o)} H:${round(c.h)} L:${round(c.l)} C:${round(c.c)}`
      );
      historyBlocks.push(`### ${inst.display} (id: ${id}) — hourly candles, most recent last. Latest close ${round(last.c)}\n${rows.join("\n")}`);
    }
    if (historyBlocks.length < 2) {
      return res.status(502).json({ error: "Not enough live market data available right now — try again later." });
    }

    const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${OPENROUTER_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: ANALYSIS_MODEL,
        max_tokens: 2500,
        reasoning: { effort: "low" },
        response_format: { type: "json_object" },
        messages: [
          { role: "system", content: DAILY_SIGNAL_SYSTEM_PROMPT },
          { role: "user", content: `Live market data:\n\n${historyBlocks.join("\n\n")}\n\nPick the best setup and return the JSON.` }
        ]
      })
    });
    if (!response.ok) {
      const detail = await response.text();
      return res.status(502).json({ error: "Analysis provider error", status: response.status, detail: detail.slice(0, 300) });
    }
    const data = await response.json();
    const signal = extractJson(data.choices?.[0]?.message?.content || "");
    const inst = byId[String(signal.instrumentId || "").toLowerCase()];
    const entryNum = Number(signal.entry);
    const slNum = Number(signal.stopLoss);
    const tps = (signal.takeProfits || []).map(Number).filter(Number.isFinite).sort((a, b) => a - b);
    if (!inst || !Number.isFinite(entryNum) || !Number.isFinite(slNum) || tps.length === 0) {
      return res.status(502).json({ error: "Model returned an unusable signal", raw: signal });
    }
    const dir = String(signal.direction).toLowerCase() === "short" ? "short" : "long";
    const sidesOk = dir === "long"
      ? slNum < entryNum && tps.every((t) => t > entryNum)
      : slNum > entryNum && tps.every((t) => t < entryNum);
    if (!sidesOk) {
      return res.status(502).json({ error: "Model returned an invalid signal (SL/TP on wrong sides)", raw: signal });
    }
    const rr = tps.length ? Number((Math.abs(tps[tps.length - 1] - entryNum) / Math.abs(entryNum - slNum)).toFixed(2)) : null;
    const aiMode = ["scalp", "swing"].includes(String(signal.mode).toLowerCase()) ? String(signal.mode).toLowerCase() : null;
    const { rows } = await pool.query(
      `INSERT INTO daily_signals
         (author, instrument_id, instrument_display, direction, entry, stop_loss, take_profits, risk_reward, thesis, strength, status, mode)
       VALUES ('ai', $1, $2, $3, $4, $5, $6, $7, $8, $9, 'live', $10)
       RETURNING *`,
      [
        inst.id, inst.display, dir, entryNum, slNum, JSON.stringify(tps), rr,
        signal.thesis ? String(signal.thesis).slice(0, 2000) : null,
        ["strong", "moderate", "weak"].includes(String(signal.strength).toLowerCase()) ? String(signal.strength).toLowerCase() : "moderate",
        aiMode
      ]
    );
    res.status(201).json(signalToApi(rows[0]));
    broadcastNewSignal(signalToApi(rows[0])).catch(() => {});
  } catch (err) {
    res.status(500).json({ error: "Could not generate the daily AI signal", detail: String(err.message || err) });
  }
});

function round(v) {
  if (v == null) return "n/a";
  if (Math.abs(v) >= 1000) return v.toFixed(2);
  if (Math.abs(v) >= 10) return v.toFixed(3);
  return v.toFixed(5);
}

/**
 * Automatic outcome resolution — cron (every 15 min) or admin.
 * For each open signal with a public price feed: update last_price and
 * resolve LONG: price <= SL -> invalidated_sl; price >= final TP -> successful;
 * price >= entry -> triggered. Mirrored for SHORT. Triggered signals older
 * than 7 days close as expired_partial. Signals with no feed (synthetics)
 * stay open until the author closes them manually.
 */
/** Resolve a signal's outcome by walking candles since publication.
 *  Uses actual traded highs/lows — so a TP/SL spike that happens between the
 *  15-minute checks is still caught. Conservative rule: a candle that trades
 *  both SL and TP counts as SL (we never claim a win on ambiguous data). */
function resolveFromCandles(sig, candles, intervalSec) {
  const tps = Array.isArray(sig.take_profits) ? sig.take_profits.map(Number).filter(Number.isFinite) : [];
  const isLong = sig.direction === "long";
  // Long: furthest target = highest TP. Short: furthest = lowest TP.
  // (Math.max for BOTH directions was a bug — shorts counted a TP1 touch as a full win.)
  const finalTp = tps.length ? (isLong ? Math.max(...tps) : Math.min(...tps)) : null;
  const startMs = new Date(sig.published_at).getTime() - intervalSec * 1000;
  let triggeredAt = null;
  let lastClose = null;
  for (const c of candles) {
    const ms = c.t * 1000;
    if (ms < startMs) continue;
    if (Number.isFinite(c.c)) lastClose = c.c;
    if (triggeredAt == null && (isLong ? c.h >= sig.entry : c.l <= sig.entry)) {
      triggeredAt = new Date(ms).toISOString();
    }
    const hitSl = isLong ? c.l <= sig.stop_loss : c.h >= sig.stop_loss;
    const hitFinalTp = finalTp != null && (isLong ? c.h >= finalTp : c.l <= finalTp);
    if (hitSl) {
      return {
        outcome: "invalidated_sl", exitPrice: sig.stop_loss,
        closedAt: new Date(ms).toISOString(),
        triggeredAt: triggeredAt || new Date(ms).toISOString(), lastClose
      };
    }
    if (hitFinalTp) {
      return {
        outcome: "successful", exitPrice: finalTp,
        closedAt: new Date(ms).toISOString(),
        triggeredAt: triggeredAt || new Date(ms).toISOString(), lastClose
      };
    }
  }
  return { outcome: null, exitPrice: null, closedAt: null, triggeredAt, lastClose };
}

app.post("/api/daily-signals/price-check", async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const authHeader = req.headers.authorization || "";
  const hasAuth = authHeader.startsWith("Bearer ");
  if (!isCronRequest(req)) {
    if (!hasAuth) return res.status(401).json({ error: "Missing session token" });
    try {
      req.session = jwt.verify(authHeader.slice(7), JWT_SECRET);
    } catch (_e) {
      return res.status(401).json({ error: "Invalid or expired session" });
    }
    if (!(await isAdminRequest(req))) {
      return res.status(403).json({ error: "Not authorized to run the price check." });
    }
  }
  try {
    const { rows } = await pool.query(
      `SELECT * FROM daily_signals WHERE status != 'closed' ORDER BY published_at ASC LIMIT 100`
    );
    const results = [];
    for (const r of rows) {
      const ageMs = Date.now() - new Date(r.published_at).getTime();
      // 15m candles give precise highs/lows while the signal is young;
      // 1h candles give full coverage for older signals (up to ~6 days).
      const interval = ageMs <= 30 * 60 * 60 * 1000 ? "15m" : "1h";
      let candleResult = null;
      try {
        const data = await fetchCandles(r.instrument_id, interval);
        if (Array.isArray(data.candles) && data.candles.length) {
          candleResult = resolveFromCandles(r, data.candles, INTERVALS[interval]);
        }
      } catch (_e) { candleResult = null; }

      let price = candleResult && candleResult.lastClose != null ? candleResult.lastClose : null;
      let outcome = candleResult ? candleResult.outcome : null;
      let status = r.status;
      let exitPrice = null;
      let closedAt = null;
      let triggeredAt = candleResult ? candleResult.triggeredAt : null;

      if (price == null) {
        // Fallback: candles unavailable for this instrument — decide on spot price.
        price = await fetchPrice(r.instrument_id);
        if (price == null) {
          results.push({ id: r.id, instrument: r.instrument_display, note: "no public feed — manual close required" });
          continue;
        }
        const tps = Array.isArray(r.take_profits) ? r.take_profits.map(Number).filter(Number.isFinite) : [];
        const isLong = r.direction === "long";
        const finalTp = tps.length ? (isLong ? Math.max(...tps) : Math.min(...tps)) : null;
        if (isLong ? price <= r.stop_loss : price >= r.stop_loss) {
          outcome = "invalidated_sl";
          exitPrice = r.stop_loss;
        } else if (finalTp != null && (isLong ? price >= finalTp : price <= finalTp)) {
          outcome = "successful";
          exitPrice = finalTp;
        } else if (isLong ? price >= r.entry : price <= r.entry) {
          outcome = "triggered_active";
        }
      } else if (outcome == null) {
        exitPrice = candleResult.exitPrice;
        closedAt = candleResult.closedAt;
      }

      if (outcome === "successful" || outcome === "invalidated_sl") {
        status = "closed";
        closedAt = closedAt || new Date().toISOString();
        exitPrice = exitPrice != null ? exitPrice : (candleResult ? candleResult.exitPrice : null);
      } else if (price != null) {
        // still open — flag it in-progress once price has reached the entry
        const isLong = r.direction === "long";
        if (isLong ? price >= r.entry : price <= r.entry) outcome = outcome || "triggered_active";
      }

      const triggered = r.triggered_at ? true : Boolean(triggeredAt) || outcome === "triggered_active";
      if (status !== "closed" && ageMs > SIGNAL_DAYS) {
        outcome = triggered ? "expired_partial" : "expired";
        status = "closed";
      }
      await pool.query(
        `UPDATE daily_signals
         SET last_price = $1, last_price_at = now(), status = $2, outcome = $3,
             triggered_at = COALESCE(triggered_at, $4),
             closed_at = COALESCE(closed_at, $5),
             exit_price = COALESCE(exit_price, $6),
             resolved_by = COALESCE(resolved_by, $7)
         WHERE id = $8`,
        [
          price, status, outcome || r.outcome,
          triggeredAt || (outcome === "triggered_active" ? new Date().toISOString() : null),
          status === "closed" ? (closedAt || new Date().toISOString()) : null,
          exitPrice,
          status === "closed" ? "auto" : null,
          r.id
        ]
      );
      results.push({
        id: r.id, instrument: r.instrument_display, price, status,
        outcome: outcome || r.outcome,
        resolvedBy: status === "closed" ? "auto" : null
      });
    }
    res.json({ checked: results.length, results });
  } catch (err) {
    res.status(500).json({ error: "Price check failed", detail: String(err.message || err) });
  }
});

/** Manual close (for no-feed instruments like synthetics, or corrections). */
app.post("/api/daily-signals/:id/close", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only the MarketScope AI team can close signals." });
  }
  const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (!UUID_RE.test(req.params.id)) return res.status(404).json({ error: "Signal not found" });
  const allowed = ["successful", "invalidated_sl", "expired_partial", "expired", "breakeven"];
  const outcome = String((req.body || {}).outcome || "").toLowerCase();
  if (!allowed.includes(outcome)) {
    return res.status(400).json({ error: `outcome must be one of: ${allowed.join(", ")}` });
  }
  try {
    const { rows } = await pool.query(
      `UPDATE daily_signals SET status = 'closed', outcome = $1, closed_at = now(), resolved_by = 'manual'
       WHERE id = $2 RETURNING *`,
      [outcome, req.params.id]
    );
    if (!rows.length) return res.status(404).json({ error: "Signal not found" });
    res.json(signalToApi(rows[0]));
  } catch (err) {
    res.status(500).json({ error: "Could not close signal", detail: String(err.message || err) });
  }
});

const SIGNAL_UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Toggle one of the 5 fixed reaction emoji on a daily signal for the signed-in user. */
app.post("/api/daily-signals/:id/react", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!SIGNAL_UUID_RE.test(req.params.id)) return res.status(404).json({ error: "Signal not found" });
  const emoji = String((req.body || {}).emoji || "");
  if (!SIGNAL_REACTION_EMOJIS.includes(emoji)) {
    return res.status(400).json({ error: "That reaction is not supported." });
  }
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    const signalId = req.params.id;
    const { rows: existing } = await pool.query(
      `SELECT 1 FROM signal_reactions WHERE signal_id = $1::uuid AND user_id = $2::uuid AND emoji = $3`,
      [signalId, me.id, emoji]
    );
    if (existing.length) {
      await pool.query(
        `DELETE FROM signal_reactions WHERE signal_id = $1::uuid AND user_id = $2::uuid AND emoji = $3`,
        [signalId, me.id, emoji]
      );
      return res.json({ emoji, active: false });
    }
    await pool.query(
      `INSERT INTO signal_reactions (signal_id, user_id, emoji) VALUES ($1::uuid, $2::uuid, $3)`,
      [signalId, me.id, emoji]
    );
    return res.json({ emoji, active: true });
  } catch (err) {
    return res.status(500).json({ error: "Could not update the reaction", detail: String(err.message || err) });
  }
});

/** Toggle bookmarking a daily signal for the signed-in user (shown nowhere else yet — a real save, not a placeholder). */
app.post("/api/daily-signals/:id/save", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!SIGNAL_UUID_RE.test(req.params.id)) return res.status(404).json({ error: "Signal not found" });
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    const signalId = req.params.id;
    const { rows: existing } = await pool.query(
      `SELECT 1 FROM signal_saves WHERE signal_id = $1::uuid AND user_id = $2::uuid`,
      [signalId, me.id]
    );
    if (existing.length) {
      await pool.query(`DELETE FROM signal_saves WHERE signal_id = $1::uuid AND user_id = $2::uuid`, [signalId, me.id]);
      return res.json({ saved: false });
    }
    await pool.query(`INSERT INTO signal_saves (signal_id, user_id) VALUES ($1::uuid, $2::uuid)`, [signalId, me.id]);
    return res.json({ saved: true });
  } catch (err) {
    return res.status(500).json({ error: "Could not update the saved state", detail: String(err.message || err) });
  }
});

// Reaction set for signal comments — separate from the 5 signal-level
// reactions above; these sit under each individual trader comment.
const SIGNAL_COMMENT_REACTION_EMOJIS = ["\u2764\uFE0F", "\u{1F602}", "\u{1F680}", "\u{1F44D}"]; // ❤️ 😂 🚀 👍

/**
 * Comment list on a daily signal, oldest first, with real per-comment
 * reaction rollups and image attachment flags. Comments with a pending
 * (unapproved) image are hidden from everyone except their author and the
 * admin — genuine moderation, not a cosmetic label. Text-only comments are
 * never gated.
 */
app.get("/api/daily-signals/:id/comments", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!SIGNAL_UUID_RE.test(req.params.id)) return res.status(404).json({ error: "Signal not found" });
  try {
    const me = await currentUser(req);
    const admin = await isAdminRequest(req);
    const { rows } = await pool.query(
      `SELECT c.id, c.user_id, c.author_name, c.body, c.approved, c.created_at,
              EXISTS(SELECT 1 FROM signal_comment_images i WHERE i.comment_id = c.id) AS has_image,
              NULLIF((SELECT u.picture FROM users u
                      WHERE lower(u.email) = lower(c.author_email) AND COALESCE(u.picture, '') <> '' LIMIT 1), '') AS author_picture,
              COALESCE((SELECT u.role FROM users u
                      WHERE lower(u.email) = lower(c.author_email) LIMIT 1), 'user') AS author_role
       FROM signal_comments c
       WHERE c.signal_id = $1::uuid ORDER BY c.created_at ASC LIMIT 200`,
      [req.params.id]
    );
    const visible = rows.filter((r) => r.approved || admin || (me && r.user_id === me.id));
    const ids = visible.map((r) => r.id);
    let reactionsByComment = {};
    if (ids.length) {
      const { rows: reactionRows } = await pool.query(
        `SELECT comment_id, emoji, count(*)::int AS c, bool_or(user_id = $2::uuid) AS mine
         FROM signal_comment_reactions WHERE comment_id = ANY($1::uuid[]) GROUP BY comment_id, emoji`,
        [ids, me ? me.id : "00000000-0000-0000-0000-000000000000"]
      );
      for (const rr of reactionRows) {
        (reactionsByComment[rr.comment_id] = reactionsByComment[rr.comment_id] || []).push(rr);
      }
    }
    res.json({
      comments: visible.map((r) => ({
        id: r.id,
        authorName: r.author_name,
        authorPicture: r.author_picture || "",
        authorRole: r.author_role || "user",
        body: r.body,
        createdAt: r.created_at,
        hasImage: r.has_image,
        pendingReview: !r.approved,
        isMine: !!(me && r.user_id === me.id),
        reactions: SIGNAL_COMMENT_REACTION_EMOJIS.map((emoji) => {
          const found = (reactionsByComment[r.id] || []).find((x) => x.emoji === emoji);
          return { emoji, count: found ? found.c : 0, mine: found ? found.mine : false };
        })
      }))
    });
  } catch (err) {
    res.status(500).json({ error: "Could not load comments", detail: String(err.message || err) });
  }
});

/** Post a comment on a daily signal, optionally with one attached image (a trade screenshot). Images require admin review before they're visible to other traders. */
app.post("/api/daily-signals/:id/comments", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!SIGNAL_UUID_RE.test(req.params.id)) return res.status(404).json({ error: "Signal not found" });
  const body = String((req.body || {}).body || "").trim();
  if (!body) return res.status(400).json({ error: "Comment body is required" });
  if (body.length > 1000) return res.status(400).json({ error: "Comments are limited to 1000 characters." });
  const imageDataUrl = (req.body || {}).image ? String((req.body || {}).image) : null;
  if (imageDataUrl) {
    if (!/^data:image\/(png|jpe?g|webp);base64,/.test(imageDataUrl)) {
      return res.status(400).json({ error: "Images must be png/jpeg/webp data URLs." });
    }
    const b64 = imageDataUrl.split(",")[1] || "";
    if (b64.length > 4_000_000) {
      return res.status(400).json({ error: "The image must be under 3MB." });
    }
  }
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    const linkVerdict = await guardLinks(me, body);
    if (!linkVerdict.allowed) {
      return res.status(422).json({ error: linkVerdict.error, linkBlocked: true });
    }
    const { rows: signalRows } = await pool.query(`SELECT id FROM daily_signals WHERE id = $1::uuid`, [req.params.id]);
    if (!signalRows.length) return res.status(404).json({ error: "Signal not found" });
    const approved = !imageDataUrl; // text-only comments post instantly; screenshots wait for review
    const { rows } = await pool.query(
      `INSERT INTO signal_comments (signal_id, user_id, author_name, author_email, body, approved)
       VALUES ($1::uuid, $2::uuid, $3, $4, $5, $6) RETURNING id, author_name, body, created_at, approved`,
      [req.params.id, me.id, me.name, me.email, body.slice(0, 1000), approved]
    );
    const c = rows[0];
    if (imageDataUrl) {
      const m = imageDataUrl.match(/^data:(image\/(?:png|jpe?g|webp));base64,/);
      await pool.query(
        `INSERT INTO signal_comment_images (comment_id, content_type, data_base64) VALUES ($1::uuid, $2, $3)`,
        [c.id, m ? m[1] : "image/jpeg", imageDataUrl.split(",")[1] || ""]
      );
    }
    res.status(201).json({
      comment: {
        id: c.id, authorName: c.author_name, body: c.body, createdAt: c.created_at,
        hasImage: !!imageDataUrl, pendingReview: !c.approved, isMine: true,
        reactions: SIGNAL_COMMENT_REACTION_EMOJIS.map((emoji) => ({ emoji, count: 0, mine: false }))
      }
    });
  } catch (err) {
    res.status(500).json({ error: "Could not post comment", detail: String(err.message || err) });
  }
});

/** Streams a signal comment's attached image. Pending (unapproved) images are only visible to their author or the admin. */
app.get("/api/daily-signals/comments/:commentId/image", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const me = await currentUser(req);
    const admin = await isAdminRequest(req);
    const { rows } = await pool.query(
      `SELECT c.user_id, c.approved, i.content_type, i.data_base64
       FROM signal_comments c JOIN signal_comment_images i ON i.comment_id = c.id
       WHERE c.id = $1::uuid`,
      [req.params.commentId]
    );
    if (!rows.length) return res.status(404).json({ error: "Image not found" });
    const row = rows[0];
    if (!row.approved && !admin && !(me && row.user_id === me.id)) {
      return res.status(403).json({ error: "This image is awaiting review." });
    }
    const buf = Buffer.from(row.data_base64, "base64");
    res.setHeader("Content-Type", row.content_type);
    res.setHeader("Cache-Control", "private, max-age=3600");
    res.send(buf);
  } catch (err) {
    res.status(500).json({ error: "Could not load image", detail: String(err.message || err) });
  }
});

/** Toggle one of the 4 fixed reaction emoji on a signal comment for the signed-in user. */
app.post("/api/daily-signals/comments/:commentId/react", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const emoji = String((req.body || {}).emoji || "");
  if (!SIGNAL_COMMENT_REACTION_EMOJIS.includes(emoji)) {
    return res.status(400).json({ error: "That reaction is not supported." });
  }
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    const { rows: existing } = await pool.query(
      `SELECT 1 FROM signal_comment_reactions WHERE comment_id = $1::uuid AND user_id = $2::uuid AND emoji = $3`,
      [req.params.commentId, me.id, emoji]
    );
    if (existing.length) {
      await pool.query(
        `DELETE FROM signal_comment_reactions WHERE comment_id = $1::uuid AND user_id = $2::uuid AND emoji = $3`,
        [req.params.commentId, me.id, emoji]
      );
      return res.json({ mine: false });
    }
    await pool.query(
      `INSERT INTO signal_comment_reactions (comment_id, user_id, emoji) VALUES ($1::uuid, $2::uuid, $3)`,
      [req.params.commentId, me.id, emoji]
    );
    return res.json({ mine: true });
  } catch (err) {
    return res.status(500).json({ error: "Could not update the reaction", detail: String(err.message || err) });
  }
});

/**
 * Live updates on a daily signal — real-time mentor/team commentary while a
 * trade is being managed ("Apply good risk management" → "Close gold in
 * profits"), returned as top-level updates each with their reply thread
 * nested one level deep (matches the reference layout: "Mentor Desk" for
 * the opening note, "Follow-up" for replies).
 */
app.get("/api/daily-signals/:id/updates", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!SIGNAL_UUID_RE.test(req.params.id)) return res.status(404).json({ error: "Signal not found" });
  try {
    const { rows } = await pool.query(
      `SELECT s.id, s.parent_id, s.author_name, s.body, s.created_at,
              COALESCE(
                NULLIF((SELECT u.picture FROM users u
                        WHERE lower(u.email) = lower(s.author_email) AND COALESCE(u.picture, '') <> '' LIMIT 1), ''),
                NULLIF((SELECT u2.picture FROM users u2
                        WHERE COALESCE(u2.picture, '') <> '' ORDER BY u2.created_at ASC LIMIT 1), '')
              ) AS author_picture
       FROM signal_updates s
       WHERE s.signal_id = $1::uuid ORDER BY s.created_at ASC LIMIT 200`,
      [req.params.id]
    );
    const byId = {};
    rows.forEach((r) => { byId[r.id] = { id: r.id, authorName: r.author_name, authorPicture: r.author_picture || "", body: r.body, createdAt: r.created_at, replies: [] }; });
    const top = [];
    rows.forEach((r) => {
      if (r.parent_id && byId[r.parent_id]) byId[r.parent_id].replies.push(byId[r.id]);
      else top.push(byId[r.id]);
    });
    res.json({ updates: top });
  } catch (err) {
    res.status(500).json({ error: "Could not load updates", detail: String(err.message || err) });
  }
});

/** Post a live update (or a reply/follow-up) on a signal — admin/mentor desk only. */
app.post("/api/daily-signals/:id/updates", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!SIGNAL_UUID_RE.test(req.params.id)) return res.status(404).json({ error: "Signal not found" });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only the MarketScope AI mentor desk can post live updates." });
  }
  const body = String((req.body || {}).body || "").trim();
  if (!body) return res.status(400).json({ error: "Update body is required" });
  if (body.length > 500) return res.status(400).json({ error: "Live updates are limited to 500 characters." });
  const parentId = (req.body || {}).parentId ? String((req.body || {}).parentId) : null;
  const authorName = String((req.body || {}).authorName || "").trim() || "Mentor Desk";
  try {
    const { rows: signalRows } = await pool.query(`SELECT id FROM daily_signals WHERE id = $1::uuid`, [req.params.id]);
    if (!signalRows.length) return res.status(404).json({ error: "Signal not found" });
    const { rows } = await pool.query(
      `INSERT INTO signal_updates (signal_id, parent_id, author_name, author_email, body)
       VALUES ($1::uuid, $2::uuid, $3, $4, $5) RETURNING id, parent_id, author_name, body, created_at`,
      [req.params.id, parentId, authorName, String(req.session.email || ""), body.slice(0, 500)]
    );
    const u = rows[0];
    const me = await pool.query(`SELECT picture FROM users WHERE google_sub = $1 AND COALESCE(picture,'') <> ''`, [req.session.sub]);
    res.status(201).json({ update: { id: u.id, authorName: u.author_name, authorPicture: (me.rows[0] || {}).picture || "", body: u.body, createdAt: u.created_at, replies: [] } });
  } catch (err) {
    res.status(500).json({ error: "Could not post the update", detail: String(err.message || err) });
  }
});

/** Admin: list pending (unapproved) signal-comment screenshots awaiting review. */
app.get("/api/admin/signal-comments/pending", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) return res.status(403).json({ error: "Admin only." });
  try {
    const { rows } = await pool.query(
      `SELECT c.id, c.signal_id, c.author_name, c.body, c.created_at, s.instrument_display
       FROM signal_comments c JOIN daily_signals s ON s.id = c.signal_id
       WHERE c.approved = false ORDER BY c.created_at ASC LIMIT 50`
    );
    res.json({
      pending: rows.map((r) => ({
        id: r.id, signalId: r.signal_id, instrument: r.instrument_display,
        authorName: r.author_name, body: r.body, createdAt: r.created_at
      }))
    });
  } catch (err) {
    res.status(500).json({ error: "Could not load pending comments", detail: String(err.message || err) });
  }
});

/** Admin: approve a pending signal-comment screenshot, making it visible to everyone. */
app.post("/api/admin/signal-comments/:id/approve", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) return res.status(403).json({ error: "Admin only." });
  try {
    await pool.query(`UPDATE signal_comments SET approved = true WHERE id = $1::uuid`, [req.params.id]);
    res.json({ approved: true });
  } catch (err) {
    res.status(500).json({ error: "Could not approve comment", detail: String(err.message || err) });
  }
});

/** Admin: reject and delete a pending signal-comment screenshot. */
app.post("/api/admin/signal-comments/:id/reject", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) return res.status(403).json({ error: "Admin only." });
  try {
    await pool.query(`DELETE FROM signal_comments WHERE id = $1::uuid`, [req.params.id]);
    res.json({ rejected: true });
  } catch (err) {
    res.status(500).json({ error: "Could not reject comment", detail: String(err.message || err) });
  }
});


// ---------------------------------------------------------------------------
// Community feed — posts, reactions, threaded comments (all requireAuth).
// ---------------------------------------------------------------------------

const REACTION_EMOJIS = ["\u{1F44D}", "\u{2764}\u{FE0F}", "\u{1F525}", "\u{1F680}", "\u{1F4B0}", "\u{1F4C8}", "\u{1F4C9}", "\u{1F4AF}", "\u{1F44F}", "\u{1F602}", "\u{1F62E}", "\u{1F64F}"];

async function currentUser(req) {
  const { rows } = await pool.query(
    `SELECT id, name, email, picture, role FROM users WHERE google_sub = $1`,
    [req.session.sub]
  );
  return rows[0] || null;
}

// --- AI link monitor -------------------------------------------------------
// Community content (posts + comments + signal comments) is scanned for
// links. Non-admin links are judged by the SAME AI engine that powers the
// chart analysis — no hardcoded blocklists, it reads the link AND the text
// around it and decides: legit trading link vs scam/spam/DM-bait.
// Fail-closed: if the check cannot run, the content is rejected with a
// clear retry message — a missed scam costs traders money, a delayed
// legit post costs nothing.

const LINK_EXTRACT_RE = /(?:https?:\/\/|www\.|t\.me\/|wa\.me\/|bit\.ly\/|tinyurl\.com\/)[^\s<>"']+[^\s<>"'.,!?;:)]/gi;
const linkVerdictCache = new Map(); // domain -> { allowed, reason, at }
const LINK_CACHE_TTL_MS = 7 * 24 * 3600 * 1000;

function extractLinks(text) {
  return [...String(text || "").matchAll(LINK_EXTRACT_RE)].map((m) => m[0]);
}

function linkDomain(link) {
  try {
    const withProto = /^https?:\/\//i.test(link) ? link : "https://" + link.replace(/^www\./i, "");
    const u = new URL(withProto);
    return u.hostname.toLowerCase().replace(/^www\./, "");
  } catch (_e) {
    return link.toLowerCase();
  }
}

/**
 * Judges the links inside a community post/comment body.
 * Admins bypass entirely. Returns { allowed: true } or
 * { allowed: false, error: friendly user-facing message }.
 */
async function guardLinks(author, body) {
  const role = String((author && author.role) || "user");
  if (role === "admin" || ADMIN_EMAILS.includes(String((author && author.email) || "").toLowerCase())) {
    return { allowed: true };
  }
  const links = extractLinks(body);
  if (!links.length) return { allowed: true };

  const now = Date.now();
  const toCheck = [];
  const verdicts = [];
  for (const link of links) {
    const domain = linkDomain(link);
    const cached = linkVerdictCache.get(domain);
    if (cached && now - cached.at < LINK_CACHE_TTL_MS) {
      verdicts.push({ link, domain, ...cached, cached: true });
    } else {
      toCheck.push({ link, domain });
    }
  }

  if (toCheck.length) {
    if (!OPENROUTER_API_KEY) {
      return { allowed: false, error: "Links can't be posted right now — please try again in a moment or remove the link." };
    }
    let checked = [];
    try {
      const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${OPENROUTER_API_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          model: ANALYSIS_MODEL,
          max_tokens: 400,
          reasoning: { effort: "low" },
          response_format: { type: "json_object" },
          messages: [
            {
              role: "system",
              content:
                "You are the link safety monitor for a trading community app. Judge each link from a trading context. " +
                "ALLOW: reputable broker/exchange sites, financial news, charting tools, educational trading content, official docs, government/central-bank sites. " +
                "BLOCK: WhatsApp/Telegram/Discord invite links and any DM-bait, signal-selling or VIP groups, socials used to funnel users, referral/affiliate spam, " +
                "giveaways or get-rich schemes, unverified investment/cryptocurrency platforms, URL shorteners pointing anywhere unknown, phishing or brand lookalikes, " +
                "gambling, adult content, malware. Judge the domain AND the surrounding message. " +
                'Respond ONLY as JSON: {"verdicts":[{"domain":"...","allowed":true|false,"reason":"short"}]} with one entry per link.'
            },
            {
              role: "user",
              content: "Links to judge:\n" + toCheck.map((c) => "- " + c.link).join("\n") +
                "\n\nFull message for context:\n" + String(body).slice(0, 1500)
            }
          ]
        })
      });
      if (!response.ok) {
        return { allowed: false, error: "Links can't be posted right now — please try again in a moment or remove the link." };
      }
      const data = await response.json();
      const parsed = JSON.parse(data.choices?.[0]?.message?.content || "{}");
      checked = Array.isArray(parsed.verdicts) ? parsed.verdicts : [];
    } catch (_e) {
      return { allowed: false, error: "Links can't be posted right now — please try again in a moment or remove the link." };
    }
    for (const c of toCheck) {
      const v = checked.find((x) => String(x.domain || "").toLowerCase() === c.domain) || {};
      const allowed = v.allowed === true;
      const entry = { allowed, reason: String(v.reason || ""), at: now };
      linkVerdictCache.set(c.domain, entry);
      verdicts.push({ link: c.link, domain: c.domain, ...entry });
    }
  }

  const blocked = verdicts.filter((v) => !v.allowed);
  if (blocked.length) {
    return {
      allowed: false,
      error:
        "Links aren't allowed in the community — they keep traders safe from scams. " +
        (blocked[0].reason ? `(${blocked[0].domain}: ${blocked[0].reason})` : "")
    };
  }
  return { allowed: true };
}

function postToApi(row, reactions, commentCount, poll, myVote, isTopContributor, viewCount) {
  return {
    id: row.id,
    authorName: row.author_name,
    authorEmail: row.author_email,
    authorPicture: row.author_picture || "",
    authorRole: row.author_role || "user",
    isTeam: row.is_team,
    isTopContributor: isTopContributor || false,
    body: row.body,
    createdAt: row.created_at,
    commentCount,
    allowComments: row.allow_comments !== false,
    postType: row.post_type || "text",
    isPinned: row.is_pinned === true,
    imageCount: row.image_count || 0,
    outcomeTag: row.outcome_tag || null,
    viewCount: viewCount || 0,
    poll: poll ? {
      options: poll.options,
      totalVotes: poll.totalVotes,
      counts: poll.counts,
      myVote: myVote || null
    } : null,
    reactions: reactions.map((r) => ({ emoji: r.emoji, count: r.count, mine: r.mine }))
  };
}

// Weekly competition scoring — computed from real engagement, resets weekly.
// post=5, comment=2, reaction given=1, reaction received=1, poll vote=1.
function isoWeekBounds(d = new Date()) {
  const date = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));
  const dayNum = (date.getUTCDay() + 6) % 7; // Monday = 0
  const weekStart = new Date(date.getTime() - dayNum * 86400000);
  const weekEnd = new Date(weekStart.getTime() + 7 * 86400000);
  return { weekStart, weekEnd };
}

async function weeklyScores(from, to) {
  const { rows } = await pool.query(
    `SELECT author_email AS email, author_name AS name,
            (SELECT COUNT(*)::int FROM community_posts p WHERE p.author_email = e.email AND p.created_at >= $1 AND p.created_at < $2) * 5 +
            (SELECT COUNT(*)::int FROM post_comments c WHERE c.author_email = e.email AND c.created_at >= $1 AND c.created_at < $2) * 2 +
            (SELECT COUNT(*)::int FROM post_reactions r JOIN community_posts p ON p.id = r.post_id WHERE r.created_at >= $1 AND r.created_at < $2 AND p.author_email = e.email) +
            (SELECT COUNT(*)::int FROM post_reactions r WHERE r.created_at >= $1 AND r.created_at < $2 AND r.user_id IN (SELECT id FROM users WHERE email = e.email)) +
            (SELECT COUNT(*)::int FROM post_poll_votes v WHERE v.created_at >= $1 AND v.created_at < $2 AND v.user_id IN (SELECT id FROM users WHERE email = e.email)) AS score,
            (SELECT COUNT(*)::int FROM community_posts p WHERE p.author_email = e.email AND p.created_at >= $1 AND p.created_at < $2) AS posts,
            (SELECT COUNT(*)::int FROM post_comments c WHERE c.author_email = e.email AND c.created_at >= $1 AND c.created_at < $2) AS comments,
            (SELECT COUNT(*)::int FROM post_reactions r JOIN community_posts p ON p.id = r.post_id WHERE r.created_at >= $1 AND r.created_at < $2 AND p.author_email = e.email) AS reactionsReceived,
            (SELECT COUNT(*)::int FROM post_reactions r WHERE r.created_at >= $1 AND r.created_at < $2 AND r.user_id IN (SELECT id FROM users WHERE email = e.email)) AS reactionsGiven,
            (SELECT COUNT(*)::int FROM post_poll_votes v WHERE v.created_at >= $1 AND v.created_at < $2 AND v.user_id IN (SELECT id FROM users WHERE email = e.email)) AS pollVotes
     FROM (SELECT DISTINCT author_email AS email, MAX(author_name) AS author_name
           FROM (
             SELECT author_email, author_name FROM community_posts
             UNION ALL SELECT author_email, author_name FROM post_comments
           ) a WHERE author_email <> ''
           GROUP BY author_email) e
     ORDER BY score DESC, name ASC LIMIT 50`,
    [from, to]
  );
  return rows;
}

// Feed: paginated posts newest-first with reaction rollups + comment counts.
app.get("/api/community/feed", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const limit = Math.min(parseInt(req.query.limit) || 20, 50);
    const offset = Math.max(parseInt(req.query.offset) || 0, 0);
    const me = await currentUser(req);
    const meId = me ? me.id : null;

    const { rows: posts } = await pool.query(
      `SELECT p.*, (SELECT COUNT(*)::int FROM community_post_images i WHERE i.post_id = p.id) AS image_count,
              (SELECT COUNT(*)::int FROM post_views v WHERE v.post_id = p.id) AS view_count,
              NULLIF((SELECT u.picture FROM users u
                      WHERE lower(u.email) = lower(p.author_email) AND COALESCE(u.picture, '') <> '' LIMIT 1), '') AS author_picture,
              COALESCE((SELECT u.role FROM users u
                      WHERE lower(u.email) = lower(p.author_email) LIMIT 1), 'user') AS author_role
       FROM community_posts p
       ORDER BY p.is_pinned DESC, p.created_at DESC
       LIMIT $1 OFFSET $2`,
      [limit, offset]
    );
    const { rows: total } = await pool.query(`SELECT COUNT(*)::int AS c FROM community_posts`);

    if (!posts.length) {
      return res.json({ posts: [], total: total[0].c, hasMore: false });
    }

    const ids = posts.map((p) => p.id);
    const { rows: reactions } = await pool.query(
      `SELECT post_id, emoji, COUNT(*)::int AS count,
              COALESCE(BOOL_OR(user_id = $2), false) AS mine
       FROM post_reactions WHERE post_id = ANY($1::uuid[])
       GROUP BY post_id, emoji ORDER BY count DESC`,
      [ids, meId]
    );
    const { rows: comments } = await pool.query(
      `SELECT post_id, COUNT(*)::int AS c
       FROM post_comments WHERE post_id = ANY($1::uuid[])
       GROUP BY post_id`,
      [ids]
    );
    const { rows: votes } = await pool.query(
      `SELECT poll_id, option_id, COUNT(*)::int AS c,
              COALESCE(BOOL_OR(user_id = $2), false) AS mine
       FROM post_poll_votes WHERE poll_id = ANY($1::uuid[])
       GROUP BY poll_id, option_id`,
      [ids, meId]
    );
    const myVoteOption = {};
    for (const v of votes) if (v.mine) myVoteOption[v.poll_id] = v.option_id;

    // Last week's top-5 badge holders (weekly competition).
    const now = new Date();
    const { weekStart: thisWeekStart } = isoWeekBounds(now);
    const lastWeek = isoWeekBounds(new Date(thisWeekStart.getTime() - 3 * 86400000));
    const badgeRows = (await weeklyScores(lastWeek.weekStart, lastWeek.weekEnd))
      .filter((r) => r.score > 0).slice(0, 5);
    const badgeEmails = new Set(badgeRows.map((r) => (r.email || "").toLowerCase()));

    const byPost = {};
    for (const r of reactions) (byPost[r.post_id] = byPost[r.post_id] || []).push(r);
    const cByPost = {};
    for (const c of comments) cByPost[c.post_id] = c.c;
    const votesByPoll = {};
    for (const v of votes) (votesByPoll[v.poll_id] = votesByPoll[v.poll_id] || []).push(v);

    return res.json({
      posts: posts.map((p) => {
        let poll = null;
        if (p.post_type === "poll" && Array.isArray(p.poll_options)) {
          const options = p.poll_options;
          const pv = votesByPoll[p.id] || [];
          const counts = {};
          let totalVotes = 0;
          for (const v of pv) {
            counts[v.option_id] = v.c;
            totalVotes += v.c;
          }
          poll = { options, counts, totalVotes };
        }
        const isBadge = badgeEmails.has((p.author_email || "").toLowerCase());
        return postToApi(p, byPost[p.id] || [], cByPost[p.id] || 0, poll, myVoteOption[p.id] || null, isBadge, p.view_count || 0);
      }),
      total: total[0].c,
      hasMore: offset + posts.length < total[0].c
    });
  } catch (err) {
    return res.status(500).json({ error: "Could not load the feed", detail: String(err.message || err) });
  }
});

// Create a text post or a poll. Posts by the admin email are flagged as team posts.
app.post("/api/community/posts", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const body = String(req.body.body || "").trim();
  const postType = req.body.postType === "poll" ? "poll" : "text";
  if (!body) return res.status(400).json({ error: postType === "poll" ? "Write the poll question first." : "Write something first." });
  if (body.length > 2000) return res.status(400).json({ error: "Posts are limited to 2000 characters." });

  const imageList = Array.isArray(req.body.images) ? req.body.images.slice(0, 4) : [];
  if (postType === "text" && imageList.length > 0 && !body) {
    return res.status(400).json({ error: "Add a few words about your image." });
  }
  for (const dataUrl of imageList) {
    if (!/^data:image\/(png|jpe?g|webp);base64,/.test(String(dataUrl))) {
      return res.status(400).json({ error: "Images must be png/jpeg/webp data URLs." });
    }
    const b64 = String(dataUrl).split(",")[1] || "";
    if (b64.length > 4_000_000) { // ~3MB decoded
      return res.status(400).json({ error: "Each image must be under 3MB." });
    }
  }

  let pollOptions = null;
  if (postType === "poll") {
    const raw = Array.isArray(req.body.pollOptions) ? req.body.pollOptions : [];
    pollOptions = raw
      .map((o) => ({ id: String((o && o.id) || Math.random().toString(36).slice(2, 10)), label: String((o && o.label) || "").trim() }))
      .filter((o) => o.label.length > 0)
      .slice(0, 6);
    if (pollOptions.length < 2) {
      return res.status(400).json({ error: "A poll needs at least two options." });
    }
    if (pollOptions.length !== raw.length) {
      return res.status(400).json({ error: "Every poll option needs a label." });
    }
    if (imageList.length > 0) {
      return res.status(400).json({ error: "Polls cannot include images." });
    }
  }
  const allowComments = req.body.allowComments !== false;
  // Self-reported trade outcome — only meaningful on an image post (a chart/proof
  // screenshot). Never inferred or fabricated by the backend; the author tags it.
  let outcomeTag = String(req.body.outcomeTag || "").toLowerCase();
  if (!["win", "loss"].includes(outcomeTag)) outcomeTag = null;
  if (outcomeTag && imageList.length === 0) {
    return res.status(400).json({ error: "An outcome tag can only be added to a post with an image." });
  }

  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    const linkVerdict = await guardLinks(me, body);
    if (!linkVerdict.allowed) {
      return res.status(422).json({ error: linkVerdict.error, linkBlocked: true });
    }
    const isTeam = ADMIN_EMAILS.includes(String((me.email || "")).toLowerCase());
    const { rows } = await pool.query(
      `INSERT INTO community_posts (user_id, author_name, author_email, body, is_team, post_type, poll_options, allow_comments, outcome_tag)
       VALUES ($1, $2, $3, $4, $5, $6, $7::jsonb, $8, $9) RETURNING *`,
      [me.id, me.name || "Trader", me.email || "", body, isTeam, postType, JSON.stringify(pollOptions), allowComments, outcomeTag]
    );
    const row = rows[0];
    for (let i = 0; i < imageList.length; i++) {
      const dataUrl = String(imageList[i]);
      const m = dataUrl.match(/^data:(image\/(?:png|jpe?g|webp));base64,/);
      await pool.query(
        `INSERT INTO community_post_images (post_id, position, content_type, data_base64) VALUES ($1::uuid, $2, $3, $4)`,
        [row.id, i, m ? m[1] : "image/jpeg", dataUrl.split(",")[1] || ""]
      );
    }
    const poll = postType === "poll"
      ? { options: row.poll_options, counts: {}, totalVotes: 0 }
      : null;
    const post = postToApi({ ...row, image_count: imageList.length }, [], 0, poll, null, false, 0);
    return res.json({ post });
  } catch (err) {
    return res.status(500).json({ error: "Could not publish the post", detail: String(err.message || err) });
  }
});

// Cast or switch a poll vote (one vote per user per poll).
app.post("/api/community/posts/:id/vote", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const optionId = String(req.body.optionId || "");
  if (!optionId) return res.status(400).json({ error: "Pick an option first." });
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    const postId = req.params.id;
    const { rows: posts } = await pool.query(
      `SELECT id, post_type, poll_options FROM community_posts WHERE id = $1::uuid`,
      [postId]
    );
    const post = posts[0];
    if (!post || post.post_type !== "poll" || !Array.isArray(post.poll_options)) {
      return res.status(404).json({ error: "Poll not found" });
    }
    if (!post.poll_options.some((o) => o.id === optionId)) {
      return res.status(400).json({ error: "That option is not part of this poll." });
    }
    await pool.query(
      `INSERT INTO post_poll_votes (poll_id, user_id, option_id)
       VALUES ($1::uuid, $2::uuid, $3)
       ON CONFLICT (poll_id, user_id) DO UPDATE SET option_id = $3, created_at = now()`,
      [postId, me.id, optionId]
    );
    const { rows: votes } = await pool.query(
      `SELECT option_id, COUNT(*)::int AS c FROM post_poll_votes WHERE poll_id = $1::uuid GROUP BY option_id`,
      [postId]
    );
    const counts = {};
    let totalVotes = 0;
    for (const v of votes) { counts[v.option_id] = v.c; totalVotes += v.c; }
    return res.json({ optionId, counts, totalVotes, myVote: optionId });
  } catch (err) {
    return res.status(500).json({ error: "Could not record the vote", detail: String(err.message || err) });
  }
});

// Serve a post image (auth required — community is members-only).
app.get("/api/community/posts/:postId/images/:position", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const pos = parseInt(req.params.position) || 0;
    const { rows } = await pool.query(
      `SELECT content_type, data_base64 FROM community_post_images
       WHERE post_id = $1::uuid AND position = $2 LIMIT 1`,
      [req.params.postId, pos]
    );
    if (!rows.length) return res.status(404).json({ error: "Image not found" });
    const buf = Buffer.from(rows[0].data_base64, "base64");
    res.setHeader("Content-Type", rows[0].content_type);
    res.setHeader("Cache-Control", "private, max-age=86400");
    return res.send(buf);
  } catch (err) {
    return res.status(500).json({ error: "Could not load image" });
  }
});

// Pin/unpin a post (team announcements — admin only).
app.post("/api/community/posts/:id/pin", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const me = await currentUser(req);
    const isAdmin = (await isAdminRequest(req)) || ADMIN_EMAILS.includes(String((me.email || "")).toLowerCase());
    if (!isAdmin) return res.status(403).json({ error: "Only the MarketScope AI team can pin posts." });
    const { rows } = await pool.query(
      `UPDATE community_posts
       SET is_pinned = NOT is_pinned,
           pinned_at = CASE WHEN NOT is_pinned THEN now() ELSE NULL END
       WHERE id = $1::uuid RETURNING id, is_pinned, pinned_at`,
      [req.params.id]
    );
    if (!rows.length) return res.status(404).json({ error: "Post not found" });
    return res.json({ id: rows[0].id, isPinned: rows[0].is_pinned, pinnedAt: rows[0].pinned_at });
  } catch (err) {
    return res.status(500).json({ error: "Could not pin the post", detail: String(err.message || err) });
  }
});

// Real curated pinned-posts list, most-recently-pinned first — backs the
// "Pinned posts" carousel in the Community screen. Title is derived honestly
// from the post's own first line (never fabricated).
app.get("/api/community/pinned", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const { rows } = await pool.query(
      `SELECT id, body, author_name, created_at, pinned_at
       FROM community_posts
       WHERE is_pinned = true
       ORDER BY pinned_at DESC NULLS LAST, created_at DESC
       LIMIT 20`
    );
    return res.json({
      pinned: rows.map((r) => {
        const firstLine = String(r.body || "").split("\n")[0].trim();
        const title = firstLine.length > 70 ? `${firstLine.slice(0, 67)}...` : firstLine;
        return {
          id: r.id,
          title: title || "Pinned post",
          authorName: r.author_name,
          createdAt: r.created_at,
          pinnedAt: r.pinned_at
        };
      })
    });
  } catch (err) {
    return res.status(500).json({ error: "Could not load pinned posts", detail: String(err.message || err) });
  }
});

// Registers a real view (deduped per user per post) — backs the eye-count on
// each post. Fire-and-forget from the client; idempotent via the PK.
app.post("/api/community/posts/:id/view", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    const postId = req.params.id;
    await pool.query(
      `INSERT INTO post_views (post_id, user_id) VALUES ($1::uuid, $2::uuid) ON CONFLICT DO NOTHING`,
      [postId, me.id]
    );
    const { rows } = await pool.query(
      `SELECT COUNT(*)::int AS c FROM post_views WHERE post_id = $1::uuid`,
      [postId]
    );
    return res.json({ viewCount: rows[0].c });
  } catch (err) {
    return res.status(500).json({ error: "Could not register the view", detail: String(err.message || err) });
  }
});

// Weekly competition leaderboard: current standings + last week's top 5.
app.get("/api/community/leaderboard", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const me = await currentUser(req);
    const { weekStart, weekEnd } = isoWeekBounds(new Date());
    const rows = (await weeklyScores(weekStart, weekEnd)).filter((r) => r.score > 0);

    const lastWeekBounds = isoWeekBounds(new Date(weekStart.getTime() - 3 * 86400000));
    const lastWeekRows = (await weeklyScores(lastWeekBounds.weekStart, lastWeekBounds.weekEnd))
      .filter((r) => r.score > 0).slice(0, 5);

    const myEmail = (me ? me.email : "") .toLowerCase();
    let myRank = null;
    let myScore = 0;
    for (let i = 0; i < rows.length; i++) {
      if ((rows[i].email || "").toLowerCase() === myEmail) {
        myRank = i + 1;
        myScore = rows[i].score;
        break;
      }
    }

    // Featured proof of the week: the most-reacted image posts published this
    // week, most-reacted first. Real engagement-ranked data, never fabricated.
    const { rows: proofRows } = await pool.query(
      `SELECT p.id, p.author_name, p.body, p.outcome_tag,
              (SELECT COUNT(*)::int FROM community_post_images i WHERE i.post_id = p.id) AS image_count,
              (SELECT COUNT(*)::int FROM post_reactions r WHERE r.post_id = p.id AND r.created_at >= $1 AND r.created_at < $2) AS week_reactions
       FROM community_posts p
       WHERE p.created_at >= $1 AND p.created_at < $2
         AND EXISTS (SELECT 1 FROM community_post_images i WHERE i.post_id = p.id)
       ORDER BY week_reactions DESC, p.created_at DESC LIMIT 5`,
      [weekStart, weekEnd]
    );
    const topProofs = proofRows.map((r) => ({
      postId: r.id, authorName: r.author_name, body: r.body,
      outcomeTag: r.outcome_tag || null,
      imageCount: r.image_count, weekReactions: r.week_reactions
    }));
    const proof = topProofs.length
      ? { postId: topProofs[0].postId, authorName: topProofs[0].authorName, body: topProofs[0].body,
          imageCount: topProofs[0].imageCount, weekReactions: topProofs[0].weekReactions }
      : null;

    return res.json({
      weekStart,
      nextReset: weekEnd,
      proof,
      topProofs,
      standings: rows.slice(0, 10).map((r, i) => ({
        rank: i + 1,
        name: r.name,
        email: r.email,
        score: r.score,
        posts: r.posts,
        comments: r.comments,
        reactionsReceived: r.reactionsReceived,
        reactionsGiven: r.reactionsGiven,
        pollVotes: r.pollVotes
      })),
      myRank,
      myScore,
      lastWeekWinners: lastWeekRows.map((r, i) => ({
        rank: i + 1,
        name: r.name,
        email: r.email,
        score: r.score,
        badge: true
      }))
    });
  } catch (err) {
    return res.status(500).json({ error: "Could not load the standings", detail: String(err.message || err) });
  }
});

// Toggle one emoji reaction on a post for the signed-in user.
app.post("/api/community/posts/:id/react", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const emoji = String(req.body.emoji || "");
  if (!REACTION_EMOJIS.includes(emoji)) {
    return res.status(400).json({ error: "That reaction is not supported." });
  }
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    const postId = req.params.id;
    const { rows: existing } = await pool.query(
      `SELECT 1 FROM post_reactions WHERE post_id = $1::uuid AND user_id = $2::uuid AND emoji = $3`,
      [postId, me.id, emoji]
    );
    if (existing.length) {
      await pool.query(
        `DELETE FROM post_reactions WHERE post_id = $1::uuid AND user_id = $2::uuid AND emoji = $3`,
        [postId, me.id, emoji]
      );
      return res.json({ emoji, active: false });
    }
    await pool.query(
      `INSERT INTO post_reactions (post_id, user_id, emoji) VALUES ($1::uuid, $2::uuid, $3)`,
      [postId, me.id, emoji]
    );
    return res.json({ emoji, active: true });
  } catch (err) {
    return res.status(500).json({ error: "Could not update the reaction", detail: String(err.message || err) });
  }
});

// Flat comment list (client nests by parent_id).
app.get("/api/community/posts/:id/comments", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const { rows } = await pool.query(
      `SELECT c.id, c.author_name, c.author_email, c.body, c.parent_id, c.created_at,
              NULLIF((SELECT u.picture FROM users u
                      WHERE lower(u.email) = lower(c.author_email) AND COALESCE(u.picture, '') <> '' LIMIT 1), '') AS author_picture,
              COALESCE((SELECT u.role FROM users u
                      WHERE lower(u.email) = lower(c.author_email) LIMIT 1), 'user') AS author_role
       FROM post_comments c WHERE c.post_id = $1::uuid
       ORDER BY c.created_at ASC LIMIT 300`,
      [req.params.id]
    );
    return res.json({ comments: rows.map((r) => ({ ...r, author_picture: r.author_picture || "", author_role: r.author_role || "user" })) });
  } catch (err) {
    return res.status(500).json({ error: "Could not load comments", detail: String(err.message || err) });
  }
});

// Add a comment (optionally a reply via parent_id).

/* ---------- Push notifications (FCM v1) ---------- */

/**
 * Insert an in-app notification row for a user (and optionally everyone).
 * Returns nothing; failures never break the caller.
 */
async function addNotification({ userId, type, title, body, data }) {
  if (!pool) return;
  try {
    await pool.query(
      `INSERT INTO notifications (user_id, type, title, body, data) VALUES ($1, $2, $3, $4, $5)`,
      [userId, type, title, body, data ? JSON.stringify(data) : null]
    );
  } catch (err) {
    console.error("addNotification failed:", String(err.message || err));
  }
}

/**
 * Fan out a push + in-app notification to every registered device of one user.
 * Prunes tokens FCM reports as dead.
 */
/** Persist the outcome of every FCM send attempt for diagnostics. */
async function logPush(userId, token, status, title) {
  if (!pool) return;
  await pool.query(
    `INSERT INTO push_log (user_id, token_suffix, status, title)
     VALUES ($1, $2, $3, $4)`,
    [userId || null, String(token).slice(-8), String(status), String(title || "").slice(0, 120)]
  );
}

async function notifyUser(userId, { title, body, type, data }) {
  if (!pool || !userId) return;
  await addNotification({ userId, type, title, body, data });
  try {
    const { rows: tokens } = await pool.query(
      `SELECT token FROM push_tokens WHERE user_id = $1`,
      [userId]
    );
    for (const t of tokens) {
      const result = await sendFcm(t.token, { title, body, data });
      await logPush(userId, t.token, result, title).catch(() => {});
      if (result === "invalid") {
        await pool.query(`DELETE FROM push_tokens WHERE token = $1`, [t.token]);
      }
    }
  } catch (err) {
    console.error("notifyUser failed:", String(err.message || err));
  }
}

/** Broadcast a new-signal notification to ALL users with push tokens. */
async function broadcastNewSignal(signal) {
  if (!pool) return;
  const title = `New ${signal.author === "ai" ? "AI" : "Team"} Signal: ${signal.instrument}`;
  const body = `${signal.direction === "long" ? "Long" : "Short"} setup is live on Daily Signals now.`;
  try {
    const { rows: users } = await pool.query(
      `SELECT id FROM users`
    );
    for (const u of users) {
      // fire-and-forget per user; notifyUser never throws
      await notifyUser(u.id, { title, body, type: "signal", data: { route: "signals", signalId: String(signal.id) } });
    }
  } catch (err) {
    console.error("broadcastNewSignal failed:", String(err.message || err));
  }
}

app.post("/api/push/register", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const token = String(req.body.token || "").trim();
  if (!token || token.length > 512) return res.status(400).json({ error: "A valid FCM token is required." });
  const platform = ["android", "ios", "web"].includes(req.body.platform) ? req.body.platform : "android";
  try {
    await pool.query(
      `INSERT INTO push_tokens (user_id, token, platform)
       VALUES ($1, $2, $3)
       ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, last_seen_at = now()`,
      [req.session.userId, token, platform]
    );
    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ error: "Could not register the push token", detail: String(err.message || err) });
  }
});

app.delete("/api/push/register", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const token = String(req.body.token || "").trim();
  try {
    await pool.query(`DELETE FROM push_tokens WHERE user_id = $1 AND token = $2`, [req.session.userId, token]);
    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ error: "Could not remove the push token", detail: String(err.message || err) });
  }
});

/**
 * Machine/admin gate shared by the push diagnostics + test endpoints.
 * Callable with the CRON_SECRET header (used by the owner's automation) or
 * with a signed-in admin session, exactly like /api/daily-signals/auto.
 */
async function requireCronOrAdmin(req, res) {
  if (isCronRequest(req)) return true;
  const authHeader = req.headers.authorization || "";
  if (!authHeader.startsWith("Bearer ")) {
    res.status(401).json({ error: "Missing session token" });
    return false;
  }
  try {
    const session = jwt.verify(authHeader.slice(7), JWT_SECRET);
    req.session = session;
    let isAdmin = ADMIN_EMAILS.includes(String(session.email || "").toLowerCase());
    if (!isAdmin) {
      const { rows } = await pool.query(`SELECT id FROM users ORDER BY created_at ASC LIMIT 1`);
      isAdmin = rows.length > 0 && rows[0].id === session.userId;
    }
    if (!isAdmin) {
      res.status(403).json({ error: "Admin access required" });
      return false;
    }
    return true;
  } catch (_e) {
    res.status(401).json({ error: "Invalid or expired session" });
    return false;
  }
}

/**
 * Send a test push to every registered device (no signal published).
 * Returns the per-token outcome so delivery can be verified end to end.
 */
app.post("/api/push/test", async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await requireCronOrAdmin(req, res))) return;
  const results = [];
  try {
    const { rows: tokens } = await pool.query(`SELECT id, user_id, token FROM push_tokens`);
    if (tokens.length === 0) {
      return res.json({ sent: 0, note: "No registered push tokens — open the app once so the device registers.", results });
    }
    for (const t of tokens) {
      const result = await sendFcm(t.token, {
        title: "MarketScope AI — test notification",
        body: "If you can read this, push delivery works on this device.",
        data: { type: "general", route: "notifications" }
      });
      await logPush(t.user_id, t.token, result, "MarketScope AI — test notification");
      if (result === "invalid") {
        await pool.query(`DELETE FROM push_tokens WHERE token = $1`, [t.token]);
      }
      results.push({ tokenId: t.id, suffix: String(t.token).slice(-8), result });
    }
    res.json({ sent: results.filter(r => r.result === "ok").length, total: results.length, results });
  } catch (err) {
    res.status(500).json({ error: "Test push failed", detail: String(err.message || err) });
  }
});

/**
 * Push diagnostics: registered tokens, recent send attempts, recent
 * notifications. Lets the owner (or automation) verify exactly where the
 * chain stands — token registered? FCM accepted? notification created?
 */
app.get("/api/push/diagnostics", async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await requireCronOrAdmin(req, res))) return;
  try {
    const { rows: tokens } = await pool.query(`
      SELECT u.email, pt.platform, pt.last_seen_at, right(pt.token, 8) AS token_suffix
      FROM push_tokens pt JOIN users u ON u.id = pt.user_id
      ORDER BY pt.last_seen_at DESC`);
    const { rows: log } = await pool.query(`
      SELECT status, token_suffix, title, created_at FROM push_log ORDER BY created_at DESC LIMIT 25`);
    const { rows: notifs } = await pool.query(`
      SELECT n.type, n.title, n.body, n.created_at, u.email
      FROM notifications n JOIN users u ON u.id = n.user_id
      ORDER BY n.created_at DESC LIMIT 10`);
    res.json({ tokens, recentSends: log, recentNotifications: notifs });
  } catch (err) {
    res.status(500).json({ error: "Diagnostics failed", detail: String(err.message || err) });
  }
});

/* ---------- Automatic market alerts (cron) ---------- */

/**
 * Every 15 minutes from GitHub Actions. Detects big 24h price moves
 * (coins / forex / gold / US indices), market session opens & closes,
 * the forex weekend break, and US market/bank holiday closures —
 * and fans them out to every user as a "general"-channel push.
 */
app.post("/api/market-alerts/cron", async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await requireCronOrAdmin(req, res))) return;
  try {
    const { rows: users } = await pool.query(`SELECT id FROM users`);
    const recipients = users.map((u) => u.id);
    const notify = (title, body) => {
      for (const userId of recipients) {
        notifyUser(userId, { title, body, type: "alert", data: { type: "general", route: "notifications" } });
      }
    };
    const result = await runAlertCron(pool, notify);
    res.json({ ok: true, recipients: recipients.length, ...result });
  } catch (err) {
    res.status(500).json({ error: "Market alert cron failed", detail: String(err.message || err) });
  }
});

/** Admin/diagnostic view: which US market holiday is today, if any. */
app.get("/api/market-alerts/holiday-today", async (_req, res) => {
  res.json({ holiday: holidayForToday(new Date()) });
});

app.get("/api/notifications", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const { rows } = await pool.query(
      `SELECT id, type, title, body, data, created_at, read_at
       FROM notifications WHERE user_id = $1
       ORDER BY created_at DESC LIMIT 50`,
      [req.session.userId]
    );
    const unread = rows.filter((r) => !r.read_at).length;
    res.json({ notifications: rows, unread });
  } catch (err) {
    res.status(500).json({ error: "Could not load notifications", detail: String(err.message || err) });
  }
});

app.post("/api/notifications/read-all", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    await pool.query(
      `UPDATE notifications SET read_at = now() WHERE user_id = $1 AND read_at IS NULL`,
      [req.session.userId]
    );
    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ error: "Could not mark notifications read", detail: String(err.message || err) });
  }
});

app.post("/api/community/posts/:id/comments", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const body = String(req.body.body || "").trim();
  if (!body) return res.status(400).json({ error: "Write a comment first." });
  if (body.length > 1000) return res.status(400).json({ error: "Comments are limited to 1000 characters." });
  const parentId = req.body.parentId || null;
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    const linkVerdict = await guardLinks(me, body);
    if (!linkVerdict.allowed) {
      return res.status(422).json({ error: linkVerdict.error, linkBlocked: true });
    }
    const { rows } = await pool.query(
      `INSERT INTO post_comments (post_id, user_id, author_name, author_email, body, parent_id)
       VALUES ($1::uuid, $2, $3, $4, $5, $6)
       RETURNING id, author_name, author_email, body, parent_id, created_at`,
      [req.params.id, me.id, me.name || "Trader", me.email || "", body, parentId]
    );
    rows[0].author_role = me.role || "user";
    const { rows: post } = await pool.query(
      `SELECT user_id, author_name FROM community_posts WHERE id = $1::uuid`,
      [req.params.id]
    );
    if (post.length && post[0].user_id !== me.id) {
      const firstName = (me.name || "A trader").split(" ")[0];
      notifyUser(post[0].user_id, {
        title: `${firstName} commented on your post`,
        body: body.length > 80 ? `${body.slice(0, 77)}...` : body,
        type: "community",
        data: { route: "community", postId: String(req.params.id) }
      }).catch(() => {});
    }
    return res.json({ comment: rows[0] });
  } catch (err) {
    return res.status(500).json({ error: "Could not post the comment", detail: String(err.message || err) });
  }
});

initDb()
  .then(() => {
    app.listen(PORT, () => {
      console.log(`market-ai-api listening on port ${PORT}`);
    });
  })
  .catch((err) => {
    console.error("Database init failed — starting without DB features:", err.message);
    app.listen(PORT, () => {
      console.log(`market-ai-api listening on port ${PORT} (no DB)`);
    });
  });
