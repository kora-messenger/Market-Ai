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
const monetization = require("./src/monetization");
const { sendWelcomeEmail, sendSecurityAlert, sendTrialExpiredEmail, sendHealthAlertEmail, sendStatsReportEmail, sendPremiumActivatedEmail, sendPremiumGrantedEmail, sendPremiumRevokedEmail } = require("./src/mailer");
const { termsOfServiceHtml, privacyPolicyHtml, communityGuidelinesHtml } = require("./src/legalPages");
const { fetchPrice, fetchHistory } = require("./src/prices");
const { sendFcm } = require("./src/fcm");
const { runAlertCron, holidayForToday } = require("./src/marketAlerts");
const { fetchTrending, fetchLiveQuotes } = require("./src/trending");
const { fetchWatchlist, WATCHLIST } = require("./src/markets");
const { fetchCandles, INTERVALS } = require("./src/candles");
const { fetchEconomicCalendar, fetchMarketNews } = require("./src/newsCalendar");
const { searchStock, bestMatch, fetchStockStats } = require("./src/stocks");

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
            `SELECT id, email, name, is_premium FROM users WHERE google_sub = $1`,
            [googleSub]
          );
          if (rows.length) {
            await pool.query(
              `INSERT INTO subscription_payments (user_id, reference, amount, currency, status, paid_at)
               VALUES ($1, $2, $3, $4, 'success', now())
               ON CONFLICT (reference) DO NOTHING`,
              [rows[0].id, data.reference, amount, currency]
            );
            const wasPremium = Boolean(rows[0].is_premium);
            await pool.query(
              `UPDATE users
                 SET is_premium = true,
                     premium_started_at = COALESCE(premium_started_at, now()),
                     premium_platform = 'paystack'
               WHERE id = $1`,
              [rows[0].id]
            );
            console.log(`[subscription] premium activated for google_sub ${googleSub} (ref ${data.reference})`);
            // Confirmation the moment activation is real: email + in-app.
            if (!wasPremium) {
              sendPremiumActivatedEmail({ email: rows[0].email, name: rows[0].name }).catch(() => {});
              notifyUser(rows[0].id, {
                title: "Premium activated \u2014 welcome to MarketScope AI Premium",
                body: "Your activation for MarketScope AI Premium has been successful. Enjoy unlimited AI analysis and the full signal history. (via MarketScope AI)",
                type: "signal",
                data: { type: "signal", screen: "profile" }
              }).catch(() => {});
            }
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
// Primary AI provider (fallback: OpenRouter above). Model is env-swappable so
// we can move to newer OpenAI generations without a code change.
const OPENAI_API_KEY = process.env.OPENAI_API_KEY || "";
const OPENAI_MODEL = process.env.OPENAI_ANALYSIS_MODEL || "gpt-4o";
// OpenRouter keeps a rotating ":free" model tier that works on an unfunded
// account (strict upstream rate limits, lower quality than paid GPT-4o —
// a $0 floor so analysis never hard-fails while accounts are untopped).
// AI_FREE_MODEL="" disables the floor. Vision+JSON verified live.
const AI_FREE_MODELS = (process.env.AI_FREE_MODELS !== undefined
  ? process.env.AI_FREE_MODELS
  : "nex-agi/nex-n2.5-pro:free,dots-studio/dots-3-note-preview:free,google/gemma-4-26b-a4b-it:free"
).split(",").map((s) => s.trim()).filter(Boolean);

/** Calls OpenRouter with one automatic retry for transient upstream failures
 *  (429 rate-limited, or a 5xx from the model provider) — these are common
 *  hiccups on a free-tier key/model, not real outages, and used to surface
 *  as a hard "Analysis provider error" on the very first retry-able blip.
 *  Every failure (transient or final) is logged with the real status/body
 *  so it is diagnosable from Render logs instead of vanishing silently. */
async function callOpenRouter(payload, label) {
  const RETRYABLE = new Set([408, 429, 500, 502, 503, 504, 522, 524, 529]);
  let lastStatus = 0;
  let lastDetail = "";
  for (let attempt = 1; attempt <= 2; attempt++) {
    let response;
    try {
      response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${OPENROUTER_API_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
      });
    } catch (err) {
      lastStatus = 0;
      lastDetail = String(err.message || err);
      console.error(`[openrouter:${label}] network error (attempt ${attempt}):`, lastDetail);
      if (attempt === 1) { await new Promise(r => setTimeout(r, 1200)); continue; }
      return { ok: false, status: 0, detail: lastDetail };
    }
    if (response.ok) return { ok: true, response };
    lastStatus = response.status;
    lastDetail = (await response.text()).slice(0, 500);
    console.error(`[openrouter:${label}] HTTP ${lastStatus} (attempt ${attempt}):`, lastDetail);
    if (attempt === 1 && RETRYABLE.has(lastStatus)) {
      await new Promise(r => setTimeout(r, 1200));
      continue;
    }
    return { ok: false, status: lastStatus, detail: lastDetail };
  }
  return { ok: false, status: lastStatus, detail: lastDetail };
}

/** Calls OpenAI (chat completions) with the same retry semantics as
 *  callOpenRouter. Accepts the OpenRouter-style payload and translates it:
 *  - strips the OpenRouter-only `reasoning` object
 *  - swaps in the OpenAI model (OPENAI_ANALYSIS_MODEL, default gpt-4o)
 *  - newer reasoning models (gpt-5 family / o-series) require max_completion_tokens
 *    instead of max_tokens; handled automatically. */
async function callOpenAI(payload, label) {
  const { model: _orModel, reasoning: _reasoning, ...rest } = payload;
  const body = { ...rest, model: OPENAI_MODEL };
  if (/^(gpt-5|o[134])/.test(OPENAI_MODEL)) {
    delete body.max_tokens;
    if (payload.max_tokens != null) body.max_completion_tokens = payload.max_tokens;
    body.reasoning_effort = "low";
  }
  const RETRYABLE = new Set([408, 429, 500, 502, 503, 504]);
  let lastStatus = 0;
  let lastDetail = "";
  for (let attempt = 1; attempt <= 2; attempt++) {
    let response;
    try {
      response = await fetch("https://api.openai.com/v1/chat/completions", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${OPENAI_API_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify(body)
      });
    } catch (err) {
      lastStatus = 0;
      lastDetail = String(err.message || err);
      console.error(`[openai:${label}] network error (attempt ${attempt}):`, lastDetail);
      if (attempt === 1) { await new Promise(r => setTimeout(r, 1200)); continue; }
      return { ok: false, status: 0, detail: lastDetail, provider: "openai" };
    }
    if (response.ok) return { ok: true, response, provider: "openai" };
    lastStatus = response.status;
    lastDetail = (await response.text()).slice(0, 500);
    console.error(`[openai:${label}] HTTP ${lastStatus} (attempt ${attempt}):`, lastDetail);
    if (lastStatus === 429) {
      console.error(`[openai:${label}] QUOTA/RATE LIMIT — check plan and billing at https://platform.openai.com/usage`);
    }
    if (attempt === 1 && RETRYABLE.has(lastStatus)) {
      await new Promise(r => setTimeout(r, 1200));
      continue;
    }
    return { ok: false, status: lastStatus, detail: lastDetail, provider: "openai" };
  }
  return { ok: false, status: lastStatus, detail: lastDetail, provider: "openai" };
}

/** Unified AI call: OpenAI first, OpenRouter as automatic fallback.
 *  Same { ok, response, status, detail } contract as callOpenRouter. */
async function callAI(payload, label) {
  if (OPENAI_API_KEY) {
    const r = await callOpenAI(payload, label);
    if (r.ok) return r;
    console.error(
      `[ai:${label}] OpenAI attempt failed (HTTP ${r.status}) — falling back to OpenRouter:`,
      String(r.detail).slice(0, 200)
    );
  }
  if (OPENROUTER_API_KEY) {
    const r = await callOpenRouter(payload, label);
    if (r.ok) return r;
    console.error(
      `[ai:${label}] OpenRouter paid attempt failed (HTTP ${r.status}) — trying free tier:`,
      String(r.detail).slice(0, 200)
    );
    // Last resort: OpenRouter free models. Drop the paid-only knobs
    // (response_format/reasoning) — extractJson() handles raw text anyway.
    // Failover across models (they rate-limit independently upstream),
    // give reasoning models token headroom, and skip any 200 response
    // whose body has no JSON in it (reasoning models sometimes exhaust
    // the budget before writing content).
    if (AI_FREE_MODELS.length) {
      const wantsJson = !!payload.response_format;
      const { reasoning: _r, response_format: _f, ...freeBase } = payload;
      for (const freeModel of AI_FREE_MODELS) {
        const freeBody = { ...freeBase, model: freeModel };
        if (freeBody.max_tokens != null) {
          freeBody.max_tokens = freeBody.max_tokens >= 1500
            ? Math.min(6000, freeBody.max_tokens + 2000)
            : freeBody.max_tokens * 2;
        }
        const fr = await callOpenRouter(freeBody, `${label}:free(${freeModel})`);
        if (!fr.ok) {
          console.error(`[ai:${label}] free model ${freeModel} failed (HTTP ${fr.status}) — trying next`);
          continue;
        }
        if (wantsJson) {
          const preview = await fr.response.clone().text();
          if (!preview.includes("{")) {
            console.error(`[ai:${label}] free model ${freeModel} returned no JSON — trying next`);
            continue;
          }
        }
        return fr;
      }
      console.error(`[ai:${label}] all free models exhausted`);
    }
  }
  return { ok: false, status: 0, detail: "No AI provider configured (set OPENAI_API_KEY and/or OPENROUTER_API_KEY)" };
}

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
    CREATE TABLE IF NOT EXISTS premium_grants (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      duration_type TEXT NOT NULL CHECK (duration_type IN ('lifetime','months','years')),
      duration_count INT NOT NULL DEFAULT 0,
      expires_at TIMESTAMPTZ,
      reason TEXT,
      granted_by UUID REFERENCES users(id),
      granted_by_email TEXT,
      granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      revoked_at TIMESTAMPTZ,
      revoked_by UUID
    );
    CREATE UNIQUE INDEX IF NOT EXISTS premium_grants_one_active
      ON premium_grants(user_id) WHERE revoked_at IS NULL;
    CREATE TABLE IF NOT EXISTS premium_audit (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      target_user_id UUID NOT NULL,
      target_email TEXT,
      target_name TEXT,
      admin_user_id UUID,
      admin_email TEXT,
      action TEXT NOT NULL,
      grant_kind TEXT,
      grant_expires_at TIMESTAMPTZ,
      reason TEXT,
      previous_status JSONB,
      new_status JSONB,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
  `);
  await monetization.ensureMonetizationTables(pool);
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
    CREATE TABLE IF NOT EXISTS signal_takers (
      signal_id UUID REFERENCES daily_signals(id) ON DELETE CASCADE,
      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      PRIMARY KEY (signal_id, user_id)
    );
    CREATE TABLE IF NOT EXISTS signal_testimonials (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      signal_id UUID REFERENCES daily_signals(id) ON DELETE CASCADE,
      user_id UUID REFERENCES users(id) ON DELETE SET NULL,
      author_name TEXT NOT NULL,
      author_email TEXT NOT NULL DEFAULT '',
      comment TEXT NOT NULL DEFAULT '',
      status TEXT NOT NULL DEFAULT 'pending',
      reviewed_at TIMESTAMPTZ,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    CREATE TABLE IF NOT EXISTS signal_testimonial_images (
      testimonial_id UUID PRIMARY KEY REFERENCES signal_testimonials(id) ON DELETE CASCADE,
      content_type TEXT NOT NULL DEFAULT 'image/jpeg',
      data_base64 TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
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
    CREATE TABLE IF NOT EXISTS stock_monitors (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      symbol TEXT NOT NULL,
      ticker TEXT NOT NULL,
      company TEXT NOT NULL,
      currency TEXT,
      recommendation TEXT NOT NULL,
      entry_price DOUBLE PRECISION,
      stop_loss DOUBLE PRECISION,
      take_profits JSONB,
      last_price DOUBLE PRECISION,
      last_notified_price DOUBLE PRECISION,
      last_notified_at TIMESTAMPTZ,
      last_checked_at TIMESTAMPTZ,
      status TEXT NOT NULL DEFAULT 'active',
      close_reason TEXT,
      tp1_notified BOOLEAN NOT NULL DEFAULT false,
      tp2_notified BOOLEAN NOT NULL DEFAULT false,
      tp3_notified BOOLEAN NOT NULL DEFAULT false,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      UNIQUE (user_id, symbol)
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
  // Config-driven allowance: base free limit + rewarded-ad bonuses earned in
  // the same rolling 24h window. Premium users never hit this path.
  const cfg = await monetization.getMonetizationConfig(pool);
  return monetization.analysisAllowance(pool, userId, cfg);
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

// --- Central Premium entitlement -----------------------------------------
// A user has EFFECTIVE Premium when at least ONE of these holds:
//   * a paid subscription (users.is_premium, set by the Paystack webhook)
//   * an active 7-day trial (trialInfo above)
//   * an active administrator-granted Premium (premium_grants: lifetime =
//     never expires; months/years = until expires_at)
// Every protected endpoint goes through these helpers — the client is never
// the authority on Premium state.
async function getActivePremiumGrant(userId) {
  if (!pool || !userId) return null;
  const { rows } = await pool.query(
    `SELECT id, duration_type, duration_count, expires_at, reason,
            granted_by, granted_by_email, granted_at
     FROM premium_grants
     WHERE user_id = $1
       AND revoked_at IS NULL
       AND (expires_at IS NULL OR expires_at > now())
     ORDER BY granted_at DESC
     LIMIT 1`,
    [userId]
  );
  return rows[0] || null;
}

async function hasActivePremiumGrant(userId) {
  return Boolean(await getActivePremiumGrant(userId));
}

/** Label for a grant, e.g. "Lifetime Premium", "3 months Premium". */
function premiumGrantLabel(grant) {
  if (!grant) return null;
  if (grant.duration_type === "lifetime") return "Lifetime Premium";
  return `${grant.duration_count} ${grant.duration_type === "months" ? (grant.duration_count === 1 ? "month" : "months") : (grant.duration_count === 1 ? "year" : "years")} Premium`;
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
app.get("/community-guidelines", (_req, res) => {
  res.set("Content-Type", "text/html; charset=utf-8").send(communityGuidelinesHtml());
});


// --- Health ---
app.get("/health", async (_req, res) => {
  const config = {
    database: Boolean(pool),
    databaseConnected: false,
    databaseError: null,
    fcm: !!process.env.FCM_SERVICE_ACCOUNT_JSON,
    googleAuth: Boolean(GOOGLE_WEB_CLIENT_ID),
    analysis: Boolean(OPENAI_API_KEY || OPENROUTER_API_KEY),
    analysisProvider: OPENAI_API_KEY ? "openai" : (OPENROUTER_API_KEY ? "openrouter" : null)
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
    let isNewUser = false;

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
      isNewUser = Boolean(rows[0].inserted_new);
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
// --- Ops: email the owner a real community + signals stats report ---
app.post("/api/admin/stats-report", async (req, res) => {
  if (!CRON_SECRET || req.headers["x-cron-secret"] !== CRON_SECRET) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const monthStart = new Date();
    monthStart.setDate(1);
    monthStart.setHours(0, 0, 0, 0);
    const agg = await pool.query(`
      SELECT
        (SELECT COUNT(*) FROM users)::int AS members,
        (SELECT COUNT(*) FROM users WHERE last_seen_at > now() - interval '5 minutes')::int AS online,
        (SELECT COUNT(*) FROM community_posts)::int AS posts,
        (SELECT COUNT(*) FROM post_comments)::int AS post_comments,
        (SELECT COUNT(*) FROM daily_signals WHERE published_at >= $1)::int AS signals_total,
        (SELECT COUNT(*) FROM daily_signals WHERE status <> 'closed' AND published_at >= $1)::int AS signals_live,
        (SELECT COUNT(*) FROM daily_signals WHERE status = 'closed' AND published_at >= $1)::int AS signals_closed,
        (SELECT COUNT(*) FROM daily_signals WHERE status = 'closed' AND outcome = 'successful' AND published_at >= $1)::int AS wins,
        (SELECT COUNT(*) FROM daily_signals WHERE status = 'closed' AND outcome <> 'successful' AND published_at >= $1)::int AS losses,
        (SELECT COALESCE(ROUND(AVG(risk_reward)::numeric, 2), 0) FROM daily_signals WHERE published_at >= $1 AND risk_reward IS NOT NULL)::float AS avg_rr,
        (SELECT COUNT(*) FROM analyses)::int AS analyses,
        (SELECT COUNT(*) FROM push_tokens)::int AS push_devices
    `, [monthStart.toISOString()]);
    const r = agg.rows[0] || {};
    const decided = (r.wins || 0) + (r.losses || 0);
    const winRate = decided > 0 ? Math.round(((r.wins || 0) / decided) * 100) : 0;
    const result = await sendStatsReportEmail({
      members: r.members || 0,
      online: r.online || 0,
      posts: r.posts || 0,
      postComments: r.post_comments || 0,
      signalsTotal: r.signals_total || 0,
      signalsLive: r.signals_live || 0,
      signalsClosed: r.signals_closed || 0,
      wins: r.wins || 0,
      losses: r.losses || 0,
      winRate,
      avgRR: Number(r.avg_rr) > 0 ? Number(r.avg_rr) : 0,
      analyses: r.analyses || 0,
      pushDevices: r.push_devices || 0
    });
    return res.json(result);
  } catch (err) {
    console.error("stats-report failed:", err);
    return res.status(500).json({ error: "Could not build stats report" });
  }
});

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
// --- Admin Premium Management ----------------------------------------------
// Only authenticated MarketScope AI administrators (isAdminRequest — env email
// list + role system) may search, inspect, grant or revoke Premium. Grants are
// database-backed entitlements (premium_grants), fully separate from paid
// subscriptions (users.is_premium / Paystack) — revoking a grant never touches
// a paid subscription; effective access is always recalculated from ALL
// entitlement sources.

/** Shared: build a user's full premium status snapshot for the admin UI. */
async function premiumStatusSnapshot(userId) {
  const { rows } = await pool.query(
    `SELECT id, google_sub, email, name, picture, is_premium, trial_started_at, created_at
     FROM users WHERE id = $1`,
    [userId]
  );
  if (!rows.length) return null;
  const u = rows[0];
  const trial = trialInfo(u);
  const grant = await getActivePremiumGrant(u.id);
  const { rows: payRows } = await pool.query(
    `SELECT count(*)::int AS c FROM subscription_payments WHERE user_id = $1 AND status = 'success'`,
    [u.id]
  );
  const { rows: history } = await pool.query(
    `SELECT id, duration_type, duration_count, expires_at, reason, granted_by_email, granted_at, revoked_at
     FROM premium_grants WHERE user_id = $1 ORDER BY granted_at DESC LIMIT 10`,
    [u.id]
  );
  const paid = Boolean(u.is_premium);
  const sources = [];
  if (paid) sources.push("Paid Subscription");
  if (grant) sources.push(grant.duration_type === "lifetime" ? "Lifetime Admin Grant" : premiumGrantLabel(grant) + " (Admin Grant)");
  if (trial.trialActive && !paid && !grant) sources.push("Free Trial");
  return {
    id: u.id,
    googleSub: u.google_sub,
    email: u.email,
    name: u.name || u.email || "User",
    picture: u.picture || null,
    joinedAt: u.created_at,
    paidSubscription: { active: paid, payments: payRows[0].c },
    trial: { active: trial.trialActive, endsAt: trial.trialEndsAt, daysRemaining: trial.trialDaysRemaining },
    adminGrant: grant ? {
      id: grant.id,
      kind: grant.duration_type,
      label: premiumGrantLabel(grant),
      expiresAt: grant.expires_at,
      reason: grant.reason,
      grantedBy: grant.granted_by_email,
      grantedAt: grant.granted_at
    } : null,
    grantHistory: history.map((h) => ({
      kind: h.duration_type,
      label: premiumGrantLabel(h),
      expiresAt: h.expires_at,
      reason: h.reason,
      grantedBy: h.granted_by_email,
      grantedAt: h.granted_at,
      revokedAt: h.revoked_at
    })),
    premium: { active: paid || !!grant || trial.trialActive, sources },
    plan: paid ? "premium" : grant ? (grant.duration_type === "lifetime" ? "lifetime" : "premium") : trial.trialActive ? "trial" : "free"
  };
}

/** Admin: search users for premium management (email, name, or user id). */
app.get("/api/admin/premium/users", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only MarketScope AI administrators can manage Premium." });
  }
  const search = String(req.query.search || "").trim();
  try {
    let likeRows = [];
    if (search) {
      const { rows } = await pool.query(
        `SELECT id FROM users
         WHERE lower(email) LIKE '%' || lower($1) || '%'
            OR lower(coalesce(name, '')) LIKE '%' || lower($1) || '%'
            OR lower(google_sub) LIKE '%' || lower($1) || '%'
         ORDER BY created_at DESC LIMIT 25`,
        [search]
      );
      likeRows = rows.map((r) => r.id);
      // Exact UUID match beats the LIKE search when the admin pasted an id.
      if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(search)) {
        try {
          const { rows } = await pool.query(`SELECT id FROM users WHERE id = $1`, [search]);
          if (rows.length && !likeRows.includes(rows[0].id)) likeRows.unshift(rows[0].id);
        } catch (_e) { /* not a valid uuid for pg */ }
      }
    }
    const users = [];
    for (const id of likeRows.slice(0, 25)) {
      const snap = await premiumStatusSnapshot(id);
      if (snap) users.push(snap);
    }
    res.json({ users });
  } catch (err) {
    console.error("[admin-premium] search failed:", String(err.message || err));
    res.status(500).json({ error: "Could not search users" });
  }
});

/** Admin: one user's full premium status. */
app.get("/api/admin/premium/status/:userId", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only MarketScope AI administrators can manage Premium." });
  }
  try {
    const snap = await premiumStatusSnapshot(req.params.userId);
    if (!snap) return res.status(404).json({ error: "User not found" });
    res.json(snap);
  } catch (err) {
    console.error("[admin-premium] status failed:", String(err.message || err));
    res.status(500).json({ error: "Could not load premium status" });
  }
});

/** Admin: grant free Premium — lifetime, or N months / N years. */
app.post("/api/admin/premium/grant", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only MarketScope AI administrators can grant Premium." });
  }
  const { userId, durationType, durationCount, reason } = req.body || {};
  if (!userId || typeof userId !== "string") {
    return res.status(400).json({ error: "A valid userId is required." });
  }
  if (!["lifetime", "months", "years"].includes(durationType)) {
    return res.status(400).json({ error: "durationType must be 'lifetime', 'months' or 'years'." });
  }
  let count = 0;
  if (durationType !== "lifetime") {
    count = parseInt(durationCount, 10);
    if (!Number.isInteger(count) || count < 1 || count > 50) {
      return res.status(400).json({ error: `durationCount must be a whole number between 1 and 50 for ${durationType}.` });
    }
  }
  if (reason != null && (typeof reason !== "string" || reason.length > 200)) {
    return res.status(400).json({ error: "Reason must be text, 200 characters or fewer." });
  }
  try {
    const { rows: adminRows } = await pool.query(
      `SELECT id, email FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    const adminUser = adminRows[0] || null;

    const before = await premiumStatusSnapshot(userId);
    if (!before) return res.status(404).json({ error: "User not found" });

    // No duplicate active grants: one live admin entitlement per user.
    const existing = await getActivePremiumGrant(before.id);
    if (existing) {
      return res.status(409).json({
        error: `This user already has an active ${premiumGrantLabel(existing)} from an admin grant.`,
        alreadyGranted: true,
        grant: {
          kind: existing.duration_type,
          label: premiumGrantLabel(existing),
          expiresAt: existing.expires_at
        }
      });
    }

    let expiresAt = null;
    if (durationType === "months") expiresAt = new Date(Date.now() + count * 30 * 24 * 60 * 60 * 1000);
    if (durationType === "years") expiresAt = new Date(Date.now() + count * 365 * 24 * 60 * 60 * 1000);

    await pool.query(
      `INSERT INTO premium_grants (user_id, duration_type, duration_count, expires_at, reason, granted_by, granted_by_email)
       VALUES ($1, $2, $3, $4, $5, $6, $7)`,
      [before.id, durationType, count, expiresAt, reason || null, adminUser ? adminUser.id : null, adminUser ? adminUser.email : (req.session.email || "admin")]
    );

    const after = await premiumStatusSnapshot(userId);

    // Audit trail — every admin premium change is recorded.
    await pool.query(
      `INSERT INTO premium_audit (target_user_id, target_email, target_name, admin_user_id, admin_email,
                                  action, grant_kind, grant_expires_at, reason, previous_status, new_status)
       VALUES ($1,$2,$3,$4,$5,'GRANTED',$6,$7,$8,$9::jsonb,$10::jsonb)`,
      [before.id, before.email, before.name, adminUser ? adminUser.id : null,
       adminUser ? adminUser.email : (req.session.email || "admin"),
       durationType, expiresAt, reason || null,
       JSON.stringify({ premium: before.premium }), JSON.stringify({ premium: after.premium })]
    );

    // Tell the user: email + in-app notification (with the real expiry).
    const grantLabel = premiumGrantLabel({ duration_type: durationType, duration_count: count });
    const expiresText = durationType === "lifetime"
      ? "Lifetime"
      : expiresAt.toLocaleDateString("en-GB", { day: "numeric", month: "long", year: "numeric" });
    sendPremiumGrantedEmail({ email: before.email, name: before.name }, { grantLabel, expiresText, reason: reason || null }).catch(() => {});
    notifyUser(before.id, {
      title: `Congratulations \\u2014 you've been given free ${grantLabel}`,
      body: `The MarketScope AI team granted you ${grantLabel}. Expires: ${expiresText}.${reason ? ` Reason: ${reason}` : ""} (via MarketScope AI)`,
      type: "signal",
      data: { type: "signal", screen: "profile" }
    }).catch(() => {});

    console.log(`[admin-premium] ${req.session.email} granted ${grantLabel} to ${before.email}`);
    res.json({ ok: true, user: after });
  } catch (err) {
    console.error("[admin-premium] grant failed:", String(err.message || err));
    res.status(500).json({ error: "Could not grant Premium" });
  }
});

/** Admin: revoke the administrator-granted entitlement. Paid subscriptions
 *  are never touched — effective access is recalculated from all sources. */
app.post("/api/admin/premium/revoke", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only MarketScope AI administrators can revoke Premium." });
  }
  const { userId } = req.body || {};
  if (!userId || typeof userId !== "string") {
    return res.status(400).json({ error: "A valid userId is required." });
  }
  try {
    const { rows: adminRows } = await pool.query(
      `SELECT id, email FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    const adminUser = adminRows[0] || null;

    const before = await premiumStatusSnapshot(userId);
    if (!before) return res.status(404).json({ error: "User not found" });

    const grant = await getActivePremiumGrant(before.id);
    if (!grant) {
      return res.status(409).json({ error: "This user has no active admin-granted Premium to revoke.", noGrant: true });
    }

    await pool.query(
      `UPDATE premium_grants SET revoked_at = now(), revoked_by = $1 WHERE id = $2`,
      [adminUser ? adminUser.id : null, grant.id]
    );

    const after = await premiumStatusSnapshot(userId);

    await pool.query(
      `INSERT INTO premium_audit (target_user_id, target_email, target_name, admin_user_id, admin_email,
                                  action, grant_kind, grant_expires_at, reason, previous_status, new_status)
       VALUES ($1,$2,$3,$4,$5,'REVOKED',$6,$7,$8,$9::jsonb,$10::jsonb)`,
      [before.id, before.email, before.name, adminUser ? adminUser.id : null,
       adminUser ? adminUser.email : (req.session.email || "admin"),
       grant.duration_type, grant.expires_at, grant.reason || null,
       JSON.stringify({ premium: before.premium }), JSON.stringify({ premium: after.premium })]
    );

    // Only a real flip changes what we tell the user; paid users stay Premium.
    const stillPremium = after.premium.active;
    sendPremiumRevokedEmail({ email: before.email, name: before.name }, { grantLabel: premiumGrantLabel(grant), stillPremium }).catch(() => {});
    notifyUser(before.id, {
      title: `Your ${premiumGrantLabel(grant)} was removed`,
      body: stillPremium
        ? "Your admin-granted Premium was removed, but your paid subscription keeps your Premium access active. (via MarketScope AI)"
        : "Your admin-granted Premium was removed. Subscribe anytime to regain Premium access. (via MarketScope AI)",
      type: "signal",
      data: { type: "signal", screen: "profile" }
    }).catch(() => {});

    console.log(`[admin-premium] ${req.session.email} revoked ${premiumGrantLabel(grant)} from ${before.email}`);
    res.json({ ok: true, user: after });
  } catch (err) {
    console.error("[admin-premium] revoke failed:", String(err.message || err));
    res.status(500).json({ error: "Could not revoke Premium" });
  }
});

/** Admin: recent premium activity (grant/revoke audit feed). */
app.get("/api/admin/premium/audit", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only MarketScope AI administrators can view premium activity." });
  }
  const limit = Math.min(parseInt(req.query.limit, 10) || 30, 100);
  try {
    const { rows } = await pool.query(
      `SELECT a.id, a.action, a.grant_kind, a.grant_expires_at, a.reason, a.created_at,
              a.target_email, a.target_name, a.admin_email
       FROM premium_audit a
       ORDER BY a.created_at DESC
       LIMIT $1`,
      [limit]
    );
    res.json({
      events: rows.map((e) => ({
        id: e.id,
        action: e.action,
        kind: e.grant_kind,
        expiresAt: e.grant_expires_at,
        reason: e.reason,
        at: e.created_at,
        target: { email: e.target_email, name: e.target_name },
        admin: e.admin_email
      }))
    });
  } catch (err) {
    console.error("[admin-premium] audit failed:", String(err.message || err));
    res.status(500).json({ error: "Could not load premium activity" });
  }
});

// --- Monetization: public config + rewarded-ad unlock -----------------------
// The whole monetization system is server-driven so limits, placements and
// ad networks can change without an app release.
app.get("/api/monetization/config", async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const cfg = await monetization.getMonetizationConfig(pool);
    const { rows } = await pool.query(`SELECT updated_at FROM monetization_config WHERE id = 1`);
    res.json({ config: cfg, updatedAt: rows[0] ? rows[0].updated_at : null });
  } catch (err) {
    res.status(500).json({ error: "Could not load monetization settings" });
  }
});

// Rewarded-ad unlock: called ONLY after the ad SDK confirmed the reward was
// earned. The server re-validates eligibility and the daily cap — the client
// can never mint bonuses by itself.
app.post("/api/monetization/rewarded-unlock", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const { rows } = await pool.query(
      `SELECT id, trial_started_at, is_premium FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) return res.status(404).json({ error: "User not found" });
    const trial = trialInfo(rows[0]);
    const grant = await getActivePremiumGrant(rows[0].id);
    const effectivePremium = Boolean(rows[0].is_premium) || !!grant || trial.trialActive;
    try {
      const result = await monetization.grantRewardedUnlock(pool, rows[0].id, {
        isPremium: effectivePremium,
        network: String((req.body && req.body.network) || "admob")
      });
      const cfg = await monetization.getMonetizationConfig(pool);
      const usage = await monetization.analysisAllowance(pool, rows[0].id, cfg);
      return res.json({ ...result, usage: { ...usage, unlimited: false } });
    } catch (err) {
      if (err.statusCode) return res.status(err.statusCode).json({ error: err.message });
      throw err;
    }
  } catch (err) {
    console.error("[monetization] rewarded-unlock failed:", String(err.message || err));
    return res.status(500).json({ error: "Could not apply the rewarded-ad bonus" });
  }
});

// Admin: read + update the monetization configuration (no app release needed).
app.get("/api/admin/monetization/config", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only MarketScope AI administrators can manage monetization." });
  }
  try {
    const { rows } = await pool.query(`SELECT config, updated_at, updated_by FROM monetization_config WHERE id = 1`);
    const cfg = monetization.getMonetizationConfig
      ? await monetization.getMonetizationConfig(pool)
      : null;
    res.json({ config: cfg, updatedAt: rows[0] ? rows[0].updated_at : null, updatedBy: rows[0] ? rows[0].updated_by : null });
  } catch (err) {
    res.status(500).json({ error: "Could not load monetization settings" });
  }
});

app.put("/api/admin/monetization/config", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only MarketScope AI administrators can manage monetization." });
  }
  const partial = req.body && req.body.config;
  if (!partial || typeof partial !== "object" || Array.isArray(partial)) {
    return res.status(400).json({ error: "Body must be { config: { ... } }" });
  }
  try {
    const result = await monetization.updateMonetizationConfig(pool, partial, req.session.email || "admin");
    res.json(result);
  } catch (err) {
    console.error("[admin-monetization] update failed:", String(err.message || err));
    res.status(500).json({ error: "Could not update monetization settings" });
  }
});

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
// --- Stock monitor: the AI keeps watching every BUY verdict and pushes the
// trader an update when the stock improves or weakens — keep or sell.
// Called every 30 minutes by the GitHub Actions cron. Real rules, no filler:
//   * price fell to the AI's stop level  -> SELL alert, monitor closes
//   * price reached a take-profit target -> milestone alert (once per level)
//   * all targets hit                    -> profit alert, monitor closes
//   * price moved >= 2% since the last alert -> the AI itself judges whether
//     the position is improving or weakening and advises keep vs sell.
// Cooldown: at most one AI-judgment alert per monitor every 4 hours; closed
// markets move nowhere, so they naturally stay silent. ---
app.post("/api/cron/stock-monitor", async (req, res) => {
  if (!CRON_SECRET || req.headers["x-cron-secret"] !== CRON_SECRET) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }

  const MOVEMENT_THRESHOLD_PCT = 2.0; // alert-worthy move since last alert
  const JUDGMENT_COOLDOWN_MS = 4 * 60 * 60 * 1000; // per monitor

  const { rows: monitors } = await pool.query(
    `SELECT m.id, m.user_id, m.symbol, m.ticker, m.company, m.currency,
            m.entry_price, m.stop_loss, m.take_profits, m.last_price,
            m.last_notified_price, m.last_notified_at,
            m.tp1_notified, m.tp2_notified, m.tp3_notified
     FROM stock_monitors m
     WHERE m.status = 'active'`
  );
  if (!monitors.length) {
    return res.json({ ok: true, checked: 0, alerts: 0, skipped: 0 });
  }

  // One live fetch per distinct stock, not per monitor.
  const statsBySymbol = new Map();
  let skipped = 0;
  for (const m of monitors) {
    if (!statsBySymbol.has(m.symbol)) {
      try {
        statsBySymbol.set(m.symbol, await fetchStockStats(m.symbol));
      } catch (err) {
        console.error(`[stock-monitor] fetch failed for ${m.symbol}:`, String(err.message || err));
        statsBySymbol.set(m.symbol, null);
      }
    }
  }

  let alerts = 0;
  const pct = (from, to) => ((to - from) / from) * 100;
  const fmt = (n, cur) => {
    const decimals = n != null && Math.abs(n) < 10 ? 2 : (n != null && Math.abs(n) < 1000 ? 2 : 0);
    return (cur === "NGN" ? "\u20A6" : cur === "USD" ? "$" : "") + (n == null ? "—" : Number(n).toFixed(decimals));
  };

  for (const m of monitors) {
    try {
      const stats = statsBySymbol.get(m.symbol);
      const price = stats ? stats.price : null;
      if (price == null || !isFinite(price)) {
        skipped++;
        continue;
      }

      const prevPrice = m.last_price;
      await pool.query(
        `UPDATE stock_monitors SET last_price = $1, last_checked_at = now() WHERE id = $2`,
        [price, m.id]
      );

      // Market is not moving (closed session / no ticks) -> nothing to say.
      if (prevPrice != null && Math.abs(price - prevPrice) < 1e-9) {
        skipped++;
        continue;
      }

      const tps = Array.isArray(m.take_profits) ? m.take_profits.filter((t) => typeof t === "number") : [];
      const tpFlags = [m.tp1_notified, m.tp2_notified, m.tp3_notified];
      const cooldownActive =
        m.last_notified_at != null &&
        Date.now() - new Date(m.last_notified_at).getTime() < JUDGMENT_COOLDOWN_MS;

      const markNotified = async (extraSet = "", extraArgs = []) => {
        await pool.query(
          `UPDATE stock_monitors
             SET last_notified_price = $1, last_notified_at = now()${extraSet}
           WHERE id = $2`,
          [price, m.id, ...extraArgs]
        );
      };

      // 1) Stop level hit — the AI advises selling, monitor closes.
      if (m.stop_loss != null && price <= m.stop_loss) {
        const moveFromEntry = m.entry_price != null ? pct(m.entry_price, price) : null;
        await notifyUser(m.user_id, {
          title: `${m.ticker}: AI advises SELL — stop level reached`,
          body: `${m.company} fell to ${fmt(price, m.currency)}${m.stop_loss != null ? ` (stop ${fmt(m.stop_loss, m.currency)})` : ""}${moveFromEntry != null ? `, ${moveFromEntry.toFixed(1)}% from your entry ${fmt(m.entry_price, m.currency)}` : ""}. The original plan is invalidated — AI recommends selling your shares now.`,
          type: "signal",
          data: { type: "signal", screen: "signals", symbol: m.symbol }
        });
        await pool.query(
          `UPDATE stock_monitors SET last_notified_price=$1, last_notified_at=now(), status='closed', close_reason='stop_loss' WHERE id=$2`,
          [price, m.id]
        );
        alerts++;
        continue;
      }

      // 2) Take-profit milestones (each fires once per level).
      const tpLevel = tps.findIndex((tp, i) => tp != null && price >= tp && !tpFlags[i]);
      if (tpLevel >= 0) {
        const isFinal = tpLevel === tps.length - 1;
        const moveFromEntry = m.entry_price != null ? pct(m.entry_price, price) : null;
        await notifyUser(m.user_id, {
          title: isFinal
            ? `${m.ticker}: final target hit — take your profit`
            : `${m.ticker}: target ${tpLevel + 1} hit — still improving`,
          body: isFinal
            ? `${m.company} reached ${fmt(price, m.currency)} — your final AI target${moveFromEntry != null ? ` (${moveFromEntry.toFixed(1)}% above entry)` : ""}. AI recommends taking your profit now.`
            : `${m.company} is at ${fmt(price, m.currency)}${moveFromEntry != null ? `, up ${moveFromEntry.toFixed(1)}% from entry` : ""}. AI advises KEEPING your shares — target ${tpLevel + 1} of ${tps.length} reached.`,
          type: "signal",
          data: { type: "signal", screen: "signals", symbol: m.symbol }
        });
        const flagCol = `tp${tpLevel + 1}_notified`;
        await markNotified(`, ${flagCol} = true`);
        if (isFinal) {
          await pool.query(`UPDATE stock_monitors SET status='closed', close_reason='target_hit' WHERE id = $1`, [m.id]);
        }
        alerts++;
        continue;
      }

      // 3) Meaningful move -> the AI itself judges keep vs sell.
      const base = m.last_notified_price != null ? m.last_notified_price : m.entry_price;
      if (base == null || cooldownActive) {
        skipped++;
        continue;
      }
      const movePct = pct(base, price);
      if (Math.abs(movePct) < MOVEMENT_THRESHOLD_PCT) {
        skipped++;
        continue;
      }

      const verdictResult = await callAI({
        model: ANALYSIS_MODEL,
        max_tokens: 900,
        reasoning: { effort: "low" },
        response_format: { type: "json_object" },
        messages: [
          {
            role: "system",
            content: `You are monitoring a stock position for a trader. The original AI analysis recommended BUY. Decide whether the position is IMPROVING or WEAKENING and whether the trader should KEEP the shares or SELL now. Respond with STRICT JSON only: { "verdict": "improving" | "weakening", "advice": "keep" | "sell", "summary": "<1-2 sentences citing the real numbers provided, telling the trader clearly to keep or sell>" }`
          },
          {
            role: "user",
            content:
              `Position: ${m.company} (${m.ticker}). Entry was ${fmt(m.entry_price, m.currency)}${m.stop_loss != null ? `, stop level ${fmt(m.stop_loss, m.currency)}` : ""}${tps.length ? `, targets ${tps.map((t) => fmt(t, m.currency)).join(" / ")}` : ""}. ` +
              `Live now: price ${fmt(price, m.currency)} (${movePct.toFixed(2)}% since the last update), today ${stats.changePctToday != null ? stats.changePctToday + "%" : "—"}, RSI ${stats.rsi ?? "—"}, ` +
              `1-month ${stats.perf1M ?? "—"}%, 3-month ${stats.perf3M ?? "—"}%, 52-week range ${stats.low52w ?? "—"} - ${stats.high52w ?? "—"}. ` +
              `Should the trader keep or sell?`
          }
        ]
      }, "stock-monitor-verdict");

      if (!verdictResult.ok) {
        console.error(`[stock-monitor] AI verdict failed for ${m.symbol}: HTTP ${verdictResult.status}`);
        skipped++;
        continue;
      }
      const vData = await verdictResult.response.json();
      const verdict = extractJson(vData.choices?.[0]?.message?.content || "");
      const advice = verdict.advice === "sell" ? "sell" : "keep";
      const improving = verdict.verdict === "weakening" ? false : true;
      await notifyUser(m.user_id, {
        title: `${m.ticker}: ${improving ? "improving" : "weakening"} — AI says ${advice === "keep" ? "KEEP your shares" : "SELL now"}`,
        body: (typeof verdict.summary === "string" && verdict.summary.trim() ? verdict.summary.trim() :
          `${m.company} is now ${fmt(price, m.currency)}, ${movePct >= 0 ? "+" : ""}${movePct.toFixed(1)}% since the last update. AI advises ${advice === "keep" ? "keeping your shares" : "selling"}.`) +
          ` (via MarketScope AI)`,
        type: "signal",
        data: { type: "signal", screen: "signals", symbol: m.symbol }
      });
      await markNotified();
      alerts++;
    } catch (err) {
      console.error(`[stock-monitor] monitor ${m.symbol} (user ${m.user_id}) threw:`, String(err.message || err));
      skipped++;
    }
  }

  return res.json({ ok: true, checked: monitors.length, alerts, skipped });
});

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
    const grant = await getActivePremiumGrant(rows[0].id);
    // Paid subscription is sticky forever once activated (existing behavior);
    // an admin grant covers the rest. The plan label tells the app the truth.
    const plan = Boolean(rows[0].is_premium)
      ? "premium"
      : grant ? (grant.duration_type === "lifetime" ? "lifetime" : "premium")
      : trial.trialActive ? "trial"
      : "free";
    const effectivePremium = Boolean(rows[0].is_premium) || !!grant || trial.trialActive;
    const mcfg = await monetization.getMonetizationConfig(pool);
    const adState = monetization.adEligibility(mcfg, effectivePremium);
    if (effectivePremium) {
      return res.json({
        ...trial,
        plan,
        premiumSource: rows[0].is_premium ? "subscription" : grant ? "admin_grant" : "trial",
        adminGrant: grant ? {
          kind: grant.duration_type,
          label: premiumGrantLabel(grant),
          expiresAt: grant.expires_at,
          reason: grant.reason,
          grantedAt: grant.granted_at
        } : null,
        analysisUsage: { used: null, limit: null, remaining: null, unlimited: true },
        adsEnabled: adState.adsEnabled,
        rewardedAvailable: adState.rewardedAvailable
      });
    }
    const usage = await analysisUsage(rows[0].id);
    return res.json({ ...trial, plan, ...adState, analysisUsage: { ...usage, unlimited: false } });
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
  if (!OPENAI_API_KEY && !OPENROUTER_API_KEY) {
    return res.status(503).json({
      error: "Analysis engine is not configured yet. Add OPENAI_API_KEY or OPENROUTER_API_KEY."
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
  const premium = Boolean(userRow.is_premium) || Boolean(await getActivePremiumGrant(userRow.id));
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
  const CATEGORY_LABELS = {
    forex: "forex", crypto: "crypto", metal: "metals",
    index: "indices", synthetic: "synthetics"
  };
  const categoryLabel = CATEGORY_LABELS[instrument.kind] || instrument.kind;
  const CHART_VALIDATION_PROMPT = `You are a strict input validator for a trading analysis engine.
You receive two images that MUST be genuine trading chart screenshots (candlestick, bar, or line chart with a visible price axis and time axis) of the SAME instrument, on the 4-hour and 15-minute timeframes.
The stated instrument belongs to the ${categoryLabel.toUpperCase()} market category.
Respond ONLY with JSON:
{
  "isChart": <true only if BOTH images are genuine trading charts (candlestick, bar or line) with a visible price scale. Do NOT require timeframe labels or that the two screenshots look different \u2014 judge only that each image is a real price chart>,
  "instrumentPlausible": <true only if the visible price scale plausibly belongs to the stated instrument, given its current live price. Charts may be from days or weeks ago, so judge order-of-magnitude plausibility (e.g. a EUR/USD chart shows values around 0.8-1.6, a USD/JPY chart around 130-160, an XAU/USD chart around 1800-4000, a BTC chart around tens of thousands)>,
  "categoryMatches": <true only if the charts genuinely look like ${categoryLabel} charts of the stated instrument and NOT like charts from a different market category (e.g. a forex pair chart uploaded for a crypto instrument, or a stock chart uploaded for a forex pair, must be false)>,
  "reason": "<one short sentence explaining the verdict>"
}`;

  const livePriceLine = livePrice != null
    ? ` Current live market price of ${instrument.display}: ${livePrice}.`
    : "";
  let validation;
  try {
    const vResult = await callAI({
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
    }, "validation");
    if (!vResult.ok) {
      return res.status(502).json({
        error: "Our AI analysis service had a temporary hiccup verifying your charts. Please tap Analyze again.",
        status: vResult.status,
        detail: vResult.detail.slice(0, 400)
      });
    }
    const vData = await vResult.response.json();
    validation = extractJson(vData.choices?.[0]?.message?.content || "");
  } catch (err) {
    console.error("[analyze] validation stage threw:", err && err.message, err && err.stack);
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
  if (validation.instrumentPlausible === false || validation.categoryMatches === false) {
    const wrongCategory = validation.categoryMatches === false && validation.instrumentPlausible !== false;
    return res.status(422).json({
      error: wrongCategory
        ? "These charts don't look like real " + categoryLabel + " charts of " + instrument.display + ". Please upload the correct " + categoryLabel + " chart \u2014 a screenshot from a different market won't work here."
        : "These charts don't appear to match " + instrument.display + ". Please make sure both screenshots are the 4H and 15M " + categoryLabel + " charts of " + instrument.display + ".",
      instrumentMismatch: true,
      reason: validation.reason || ""
    });
  }

  // --- Stage 3: the real analysis, anchored to the verified live market ---
  try {
    const orResult = await callAI({
      model: ANALYSIS_MODEL,
      max_tokens: 2500,
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
    }, "analysis");

    if (!orResult.ok) {
      const billingIssue = orResult.status === 402;
      if (billingIssue) {
        console.error("[analyze] OPENROUTER ACCOUNT OUT OF CREDIT — top up at https://openrouter.ai/settings/credits (or set OPENAI_API_KEY as the primary provider)");
      }
      return res.status(502).json({
        error: billingIssue
          ? "Our AI analysis service is briefly unavailable. We're on it — please try again shortly."
          : "Our AI analysis service had a temporary hiccup. Please tap Analyze again.",
        status: orResult.status,
        detail: orResult.detail.slice(0, 400)
      });
    }

    const data = await orResult.response.json();
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
    console.error("[analyze] analysis stage threw:", err && err.message, err && err.stack);
    return res.status(500).json({ error: "Analysis failed. Please tap Analyze again.", detail: String(err.message || err) });
  }
});

// --- Stock analysis (the 3rd analyze flow: stocks have no 4H/15M upload —
// the user types a company name or uploads a stock screenshot, we fetch the
// stock's REAL live performance from its exchange and let the AI judge it) ---
const STOCK_SYSTEM_PROMPT = `You are a senior equity research analyst. You receive a real, live market-performance snapshot of a publicly traded stock, fetched moments ago from its exchange. Decide whether a trader should buy this stock now or not.
Respond with STRICT JSON only (no markdown fences), shape:
{
  "direction": "LONG" | "SHORT" | "NO_TRADE",
  "recommendation": "BUY" | "SELL" | "HOLD",
  "confidence": 0-100,
  "entryZone": {"low": number, "high": number},
  "stopLoss": number,
  "takeProfits": [number, number, number],
  "riskReward": number,
  "estimatedDuration": "your best estimate of the holding period this setup needs, as a short human string like '2-6 months'",
  "thesis": "4-6 sentences grounded in the REAL performance numbers provided — cite the actual percentages, the 52-week range and trend you were given",
  "invalidation": "what would invalidate this view",
  "keyLevels": [number]
}
Hard rules: LONG pairs with recommendation BUY; SHORT with SELL; NO_TRADE with HOLD. All prices must be in the stock's own currency and near its real current price. Ground every claim in the provided data — never invent numbers.`;

app.post("/api/analyze/stock", requireAuth, async (req, res) => {
  if (!OPENAI_API_KEY && !OPENROUTER_API_KEY) {
    return res.status(503).json({ error: "Analysis engine is not configured yet." });
  }
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  const { name, image } = req.body || {};
  const isDataUrl = (s) => typeof s === "string" && /^data:image\/(png|jpe?g|webp);base64,/.test(s);
  const hasImage = isDataUrl(image);
  const stockQuery = typeof name === "string" ? name.trim() : "";
  if (!stockQuery && !hasImage) {
    return res.status(400).json({ error: "Type a stock name or attach a stock screenshot to analyze." });
  }

  let userRow;
  try {
    const { rows } = await pool.query(
      `SELECT id, trial_started_at, is_premium FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) return res.status(404).json({ error: "User not found" });
    userRow = rows[0];
  } catch (err) {
    return res.status(500).json({ error: "Could not verify account", detail: String(err.message || err) });
  }

  const trial = trialInfo(userRow);
  const premium = Boolean(userRow.is_premium) || Boolean(await getActivePremiumGrant(userRow.id));
  if (!trial.trialActive && !premium) {
    const usage = await analysisUsage(userRow.id);
    if (usage.used >= usage.limit) {
      return res.status(429).json({
        error: `You've used all ${usage.limit} free analyses for today. Premium gives you unlimited analyses.`,
        dailyLimitReached: true,
        ...usage
      });
    }
  }

  // --- If a screenshot is attached, it must be a genuine stock screenshot.
  // With no typed name, the ticker/company is extracted from the image. ---
  let resolvedQuery = stockQuery;
  if (hasImage) {
    const visionPrompt = `You are a strict input validator for a stock analysis engine. The image should be a screenshot related to a publicly traded stock (a stock chart, a trading app screen, or a quote page). Respond ONLY with JSON: { "isStockRelated": <true only if the image genuinely relates to a stock>, "companyOrTicker": "<the company name or ticker symbol visible, or empty string>", "reason": "<one short sentence>" }`;
    try {
      const vResult = await callAI({
        model: ANALYSIS_MODEL,
        max_tokens: 1500,
        reasoning: { effort: "low" },
        response_format: { type: "json_object" },
        messages: [
          { role: "system", content: visionPrompt },
          {
            role: "user",
            content: [
              { type: "text", text: "Validate this screenshot for stock analysis." },
              { type: "image_url", image_url: { url: image } }
            ]
          }
        ]
      }, "stock-vision");
      if (!vResult.ok) {
        return res.status(502).json({
          error: "Our AI analysis service had a temporary hiccup. Please tap Analyze again.",
          status: vResult.status,
          detail: vResult.detail.slice(0, 400)
        });
      }
      const vData = await vResult.response.json();
      const vision = extractJson(vData.choices?.[0]?.message?.content || "");
      if (vision.isStockRelated === false) {
        return res.status(422).json({
          error: "This screenshot doesn't look like a valid stock screenshot. Please attach the correct stock chart or type the stock name instead.",
          invalidChart: true,
          reason: vision.reason || ""
        });
      }
      if (!resolvedQuery && typeof vision.companyOrTicker === "string" && vision.companyOrTicker.trim()) {
        resolvedQuery = vision.companyOrTicker.trim();
      }
    } catch (err) {
      console.error("[analyze/stock] vision stage threw:", err && err.message, err && err.stack);
      return res.status(502).json({ error: "Could not verify the screenshot. Please try again.", detail: String(err.message || err) });
    }
  }

  if (!resolvedQuery) {
    return res.status(422).json({ error: "We couldn't identify a stock from that screenshot. Please type the stock name instead." });
  }

  // --- Resolve the query to a REAL listed stock with live performance data ---
  let stats;
  let match;
  try {
    const matches = await searchStock(resolvedQuery);
    if (!matches.length) {
      return res.status(422).json({
        error: `We couldn't find a stock called "${resolvedQuery}". Check the spelling or try its ticker symbol.`,
        stockNotFound: true
      });
    }
    match = bestMatch(matches, resolvedQuery);
    stats = await fetchStockStats(match.symbol);
  } catch (err) {
    console.error("[analyze/stock] market data fetch failed:", err && err.message, err && err.stack);
    return res.status(502).json({ error: "We couldn't reach the stock market data right now. Please try again in a moment." });
  }
  if (!stats) {
    return res.status(422).json({
      error: `We couldn't load live market data for "${match.description}". Please try the ticker symbol instead.`,
      stockNotFound: true
    });
  }

  // --- The real analysis, grounded in the live performance snapshot ---
  try {
    const userContent = [
      {
        type: "text",
        text:
          `Stock: ${stats.company} (${stats.ticker}), listed on ${match.exchange}. Currency: ${stats.currency || "local"}.` +
          ` REAL live market data fetched moments ago: current price ${stats.price}${stats.changePctToday != null ? ` (today ${stats.changePctToday > 0 ? "+" : ""}${stats.changePctToday}%)` : ""}` +
          (stats.perf1W != null ? `, 1-week ${stats.perf1W}%` : "") +
          (stats.perf1M != null ? `, 1-month ${stats.perf1M}%` : "") +
          (stats.perf3M != null ? `, 3-month ${stats.perf3M}%` : "") +
          (stats.perf6M != null ? `, 6-month ${stats.perf6M}%` : "") +
          (stats.perf1Y != null ? `, 1-year ${stats.perf1Y}%` : "") +
          (stats.perfYTD != null ? `, YTD ${stats.perfYTD}%` : "") +
          (stats.high52w != null && stats.low52w != null ? `, 52-week range ${stats.low52w} - ${stats.high52w}` : "") +
          (stats.volume != null ? `, volume today ${stats.volume}` : "") +
          (stats.avgVolume10d != null ? `, 10-day average volume ${stats.avgVolume10d}` : "") +
          (stats.dailyVolatilityPct != null ? `, daily volatility ${stats.dailyVolatilityPct}%` : "") +
          (stats.rsi != null ? `, RSI ${stats.rsi}` : "") +
          (stats.marketCap != null ? `, market cap ${stats.marketCap}` : "") +
          (stats.peRatio != null ? `, P/E ${stats.peRatio}` : "") +
          (stats.eps != null ? `, EPS ${stats.eps}` : "") +
          (stats.sector ? `, sector: ${stats.sector}` : "") +
          (stats.tvRecommendation != null ? `. Aggregated technical rating of this stock on its exchange: ${stats.tvRecommendation} (-1 strong sell to +1 strong buy)` : "") +
          `. User query: "${resolvedQuery}". Decide: should a trader BUY this stock now or not, and with what confidence percentage?`
      }
    ];
    if (hasImage) {
      userContent.push({ type: "image_url", image_url: { url: image } });
    }

    const orResult = await callAI({
      model: ANALYSIS_MODEL,
      max_tokens: 2500,
      reasoning: { effort: "low" },
      response_format: { type: "json_object" },
      messages: [
        { role: "system", content: STOCK_SYSTEM_PROMPT },
        { role: "user", content: userContent }
      ]
    }, "stock-analysis");

    if (!orResult.ok) {
      const billingIssue = orResult.status === 402;
      if (billingIssue) {
        console.error("[analyze/stock] OPENROUTER ACCOUNT OUT OF CREDIT — top up at https://openrouter.ai/settings/credits (or set OPENAI_API_KEY as the primary provider)");
      }
      return res.status(502).json({
        error: billingIssue
          ? "Our AI analysis service is briefly unavailable. We're on it — please try again shortly."
          : "Our AI analysis service had a temporary hiccup. Please tap Analyze again.",
        status: orResult.status,
        detail: orResult.detail.slice(0, 400)
      });
    }

    const data = await orResult.response.json();
    const text = data.choices?.[0]?.message?.content || "";
    const analysis = extractJson(text);

    const result = {
      instrument: `${stats.company} (${stats.ticker})`,
      instrumentId: match.symbol,
      mode: "stock",
      model: ANALYSIS_MODEL,
      livePrice: stats.price,
      marketVerified: true,
      chartValidated: true,
      analysis,
      marketData: {
        perf1M: stats.perf1M, perf3M: stats.perf3M, perf6M: stats.perf6M, perf1Y: stats.perf1Y,
        high52w: stats.high52w, low52w: stats.low52w, currency: stats.currency,
        exchange: match.exchange
      },
      analyzedAt: new Date().toISOString()
    };

    // --- Auto-enroll monitoring: when the AI says BUY, it keeps watching the
    // stock for this trader and pushes updates (keep vs sell) as it moves. ---
    if (analysis && analysis.recommendation === "BUY" &&
        typeof analysis.stopLoss === "number" && Array.isArray(analysis.takeProfits)) {
      try {
        const tps = analysis.takeProfits.filter((t) => typeof t === "number");
        await pool.query(
          `INSERT INTO stock_monitors
             (user_id, symbol, ticker, company, currency, recommendation,
              entry_price, stop_loss, take_profits, last_price, last_notified_price, status)
           VALUES ($1,$2,$3,$4,$5,'BUY',$6,$7,$8::jsonb,$9,$9,'active')
           ON CONFLICT (user_id, symbol) DO UPDATE SET
             recommendation = 'BUY',
             entry_price = EXCLUDED.entry_price,
             stop_loss = EXCLUDED.stop_loss,
             take_profits = EXCLUDED.take_profits,
             last_price = EXCLUDED.last_price,
             last_notified_price = EXCLUDED.last_notified_price,
             status = 'active',
             close_reason = NULL,
             tp1_notified = false,
             tp2_notified = false,
             tp3_notified = false`,
          [userRow.id, match.symbol, stats.ticker, stats.company, stats.currency,
           stats.price, analysis.stopLoss, JSON.stringify(tps), stats.price]
        );
        result.monitoring = true;
        console.log(`[stock-monitor] enrolled ${userRow.id} on ${match.symbol} @ ${stats.price}`);
      } catch (monErr) {
        console.error("[stock-monitor] enrollment failed (non-fatal):", String(monErr.message || monErr));
      }
    }

    const { rows } = await pool.query(
      `INSERT INTO analyses (user_id, instrument_id, mode, result) VALUES ($1, $2, $3, $4) RETURNING id`,
      [userRow.id, match.symbol, "stock", JSON.stringify(result)]
    );
    if (rows.length) result.id = rows[0].id;

    return res.json({ ...result, ...trial });
  } catch (err) {
    console.error("[analyze/stock] analysis stage threw:", err && err.message, err && err.stack);
    return res.status(500).json({ error: "Analysis failed. Please tap Analyze again.", detail: String(err.message || err) });
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
      `SELECT id, trial_started_at, is_premium FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) return res.status(404).json({ error: "User not found" });
    const trial = trialInfo(rows[0]);
    const isAdmin = await isAdminRequest(req);
    const grant = await getActivePremiumGrant(rows[0].id);
    const entitled = trial.trialActive || rows[0].is_premium || isAdmin || !!grant;
    res.json({ isAdmin, entitled, trialActive: trial.trialActive, trialDaysRemaining: trial.trialDaysRemaining, isPremium: Boolean(rows[0].is_premium) || !!grant, plan: rows[0].is_premium ? "premium" : grant ? (grant.duration_type === "lifetime" ? "lifetime" : "premium") : (trial.trialActive ? "trial" : "free") });
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
    const entitled = trial.trialActive || me.is_premium || admin || Boolean(await getActivePremiumGrant(me.id));
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
    // Furthest target = highest TP for a long, LOWEST TP for a short. Sorting
    // ascending and grabbing the last element (the old formula) picks the
    // WORST target for shorts, not the best — the same directional bug that
    // was already fixed in outcome resolution, now fixed here too.
    const stopDistance = Math.abs(entryNum - slNum);
    const finalTp = dirLc === "long" ? Math.max(...tps) : Math.min(...tps);
    const rr = stopDistance > 0 ? Number((Math.abs(finalTp - entryNum) / stopDistance).toFixed(2)) : null;
    // A manually-entered "strong" with a weak real reward:risk is misleading —
    // downgrade it rather than publish overconfident labeling, same rule the
    // AI signal path now enforces.
    let strengthFinal = ["strong", "moderate", "weak"].includes(String(strength).toLowerCase())
      ? String(strength).toLowerCase() : "moderate";
    if (strengthFinal === "strong" && (rr == null || rr < 2.0)) strengthFinal = "moderate";
    const { rows } = await pool.query(
      `INSERT INTO daily_signals
         (author, instrument_id, instrument_display, direction, entry, stop_loss, take_profits, risk_reward, thesis, strength, status, mode)
       VALUES ('owner', $1, $2, $3, $4, $5, $6, $7, $8, $9, 'live', $10)
       RETURNING *`,
      [
        instrument.id, instrument.display, String(direction).toLowerCase(),
        entryNum, slNum, JSON.stringify(tps), rr,
        thesis ? String(thesis).slice(0, 2000) : null,
        strengthFinal,
        modeLc
      ]
    );
    res.status(201).json(signalToApi(rows[0]));
    broadcastNewSignal(signalToApi(rows[0])).catch(() => {});
  } catch (err) {
    res.status(500).json({ error: "Could not publish signal", detail: String(err.message || err) });
  }
});

/** Average True Range over the last `period` candles — a volatility yardstick
 *  used to stop the AI daily-signal model from placing stops so tight that
 *  ordinary noise clips them before the thesis ever gets to play out. */
function computeAtr(candles, period = 14) {
  if (!Array.isArray(candles) || candles.length < period + 1) return null;
  const recent = candles.slice(-(period + 1));
  const trs = [];
  for (let i = 1; i < recent.length; i++) {
    const cur = recent[i], prev = recent[i - 1];
    const tr = Math.max(
      cur.h - cur.l,
      Math.abs(cur.h - prev.c),
      Math.abs(cur.l - prev.c)
    );
    if (Number.isFinite(tr)) trs.push(tr);
  }
  if (!trs.length) return null;
  return trs.reduce((a, b) => a + b, 0) / trs.length;
}

const DAILY_SIGNAL_SYSTEM_PROMPT = `You are the senior market analyst behind MarketScope AI's Daily Signals. You receive recent OHLC candles for a set of instruments from live public market data, each annotated with its recent ATR (average true range) — a measure of how much that instrument normally moves per hour. Pick the single best trade setup among them. Respond ONLY with JSON:
{
  "instrumentId": "one of the provided ids",
  "direction": "long" | "short",
  "mode": "scalp" | "swing",
  "entry": number,
  "stopLoss": number,
  "takeProfits": [number, number, number],
  "thesis": "2-4 sentences grounded in the price action shown: name the specific structure (recent swing high/low, range, trend), the momentum context, and why the stop level is protected by real structure — not generic filler.",
  "strength": "strong" | "moderate" | "weak"
}
Hard rules (a setup that breaks any of these will be rejected and you will be asked to redo it):
1. Entry must sit within 0.5% of the latest close shown for that instrument.
2. Stop loss must be on the wrong side of entry (below for long, above for short) and placed beyond a real recent swing high/low visible in the candles — not an arbitrary round number.
3. Stop distance (|entry - stopLoss|) must be AT LEAST 0.8x that instrument's shown ATR. A stop tighter than that gets clipped by normal noise, not real invalidation — this is the single most common reason a signal fails, so never undersize it to inflate risk:reward.
4. Every take profit must be on the profitable side of entry, ordered nearest first.
5. Risk:reward to the FINAL (furthest) take profit must be at least 1.5, and to the FIRST (nearest) take profit must be at least 0.8.
6. Only use "strong" when: R:R to the final target is at least 2.0, the stop sits at a genuine structural level, AND at least two independent factors in the data (e.g. trend + momentum, or range rejection + volume-implied conviction from candle bodies) agree. Default to "moderate" or "weak" otherwise — most days do not deserve "strong".
"mode": "scalp" for a tight stop targeting a quick intraday move, "swing" for a wider stop held over multiple days. If nothing on the list is genuinely attractive, still return the least-bad setup that satisfies all the hard rules above and mark it "weak" — never invent prices outside the data range shown, and never sacrifice rule 3 just to hit a bigger R:R number.`;

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
  if (!OPENAI_API_KEY && !OPENROUTER_API_KEY) return res.status(503).json({ error: "Analysis engine is not configured yet." });
  try {
    const { rows: existing } = await pool.query(
      `SELECT id FROM daily_signals WHERE author = 'ai' AND published_at >= CURRENT_DATE`
    );
    if (existing.length) {
      return res.json({ skipped: true, reason: "AI signal already published today" });
    }

    const candidates = ["eurusd", "gbpusd", "usdjpy", "xauusd", "nas100", "btcusd", "ethusd"];
    const historyBlocks = [];
    const instContext = {}; // id -> { lastClose, atr }
    for (const id of candidates) {
      const h = await fetchHistory(id, { interval: "1h", range: "5d" });
      if (!h || h.candles.length < 20) continue;
      const inst = byId[id];
      const last = h.candles[h.candles.length - 1];
      const atr = computeAtr(h.candles, 14);
      instContext[id] = { lastClose: last.c, atr };
      // compact: last 96 hourly candles (4 days) — enough to read real structure,
      // not just the last day of noise.
      const rows = h.candles.slice(-96).map((c) =>
        `${new Date(c.t * 1000).toISOString().slice(5, 16)} O:${round(c.o)} H:${round(c.h)} L:${round(c.l)} C:${round(c.c)}`
      );
      const atrLine = atr != null ? `ATR(14h): ${round(atr)}` : "ATR(14h): unavailable";
      historyBlocks.push(`### ${inst.display} (id: ${id}) — hourly candles, most recent last. Latest close ${round(last.c)}. ${atrLine}\n${rows.join("\n")}`);
    }
    if (historyBlocks.length < 2) {
      return res.status(502).json({ error: "Not enough live market data available right now — try again later." });
    }

    /** Checks a model's proposed signal against the hard rules the prompt itself
     *  states, so a rule violation (e.g. R:R below 1.5, or a stop tighter than
     *  0.8x ATR) is rejected server-side instead of silently published. This is
     *  the gate that was missing before — the model could ignore its own rules
     *  and nothing caught it. */
    function validateSignal(signal) {
      const errors = [];
      const inst = byId[String(signal.instrumentId || "").toLowerCase()];
      if (!inst) errors.push("instrumentId is not one of the provided instruments");
      const entryNum = Number(signal.entry);
      const slNum = Number(signal.stopLoss);
      const tps = (signal.takeProfits || []).map(Number).filter(Number.isFinite).sort((a, b) => a - b);
      if (!Number.isFinite(entryNum)) errors.push("entry is not a valid number");
      if (!Number.isFinite(slNum)) errors.push("stopLoss is not a valid number");
      if (!tps.length) errors.push("takeProfits is empty");
      if (errors.length) return { ok: false, errors };

      const ctx = inst ? instContext[inst.id] : null;
      const dir = String(signal.direction).toLowerCase() === "short" ? "short" : "long";
      const sidesOk = dir === "long"
        ? slNum < entryNum && tps.every((t) => t > entryNum)
        : slNum > entryNum && tps.every((t) => t < entryNum);
      if (!sidesOk) errors.push("stopLoss/takeProfits are on the wrong side of entry for the given direction");

      if (ctx && ctx.lastClose) {
        const pctFromClose = Math.abs(entryNum - ctx.lastClose) / ctx.lastClose;
        if (pctFromClose > 0.005) errors.push(`entry is ${(pctFromClose * 100).toFixed(2)}% from the latest close — must be within 0.5%`);
      }

      const stopDistance = Math.abs(entryNum - slNum);
      if (ctx && ctx.atr) {
        if (stopDistance < ctx.atr * 0.8) {
          errors.push(`stop distance (${round(stopDistance)}) is tighter than 0.8x ATR (${round(ctx.atr * 0.8)}) — will get clipped by normal noise`);
        }
      }

      const finalTp = tps.length ? (dir === "long" ? Math.max(...tps) : Math.min(...tps)) : null;
      const nearTp = tps.length ? (dir === "long" ? Math.min(...tps) : Math.max(...tps)) : null;
      const rrFinal = finalTp != null && stopDistance > 0 ? Math.abs(finalTp - entryNum) / stopDistance : null;
      const rrNear = nearTp != null && stopDistance > 0 ? Math.abs(nearTp - entryNum) / stopDistance : null;
      if (rrFinal == null || rrFinal < 1.5) errors.push(`risk:reward to the final target is ${rrFinal != null ? rrFinal.toFixed(2) : "n/a"} — must be at least 1.5`);
      if (rrNear != null && rrNear < 0.8) errors.push(`risk:reward to the first target is ${rrNear.toFixed(2)} — must be at least 0.8`);

      if (errors.length) return { ok: false, errors };

      // Recalibrate a mislabeled "strong" — the model sometimes over-claims
      // conviction even when the setup itself is only just-adequate.
      let strength = ["strong", "moderate", "weak"].includes(String(signal.strength).toLowerCase())
        ? String(signal.strength).toLowerCase() : "moderate";
      if (strength === "strong" && (rrFinal == null || rrFinal < 2.0 || stopDistance < (ctx?.atr || 0) * 1.0)) {
        strength = "moderate";
      }

      return {
        ok: true,
        inst, entryNum, slNum, tps, dir, strength,
        rr: rrFinal != null ? Number(rrFinal.toFixed(2)) : null
      };
    }

    const baseMessages = [
      { role: "system", content: DAILY_SIGNAL_SYSTEM_PROMPT },
      { role: "user", content: `Live market data:\n\n${historyBlocks.join("\n\n")}\n\nPick the best setup and return the JSON.` }
    ];

    let validated = null;
    let lastErrors = [];
    let lastRawSignal = null;
    const maxAttempts = 2;
    for (let attempt = 0; attempt < maxAttempts && !validated; attempt++) {
      const messages = attempt === 0
        ? baseMessages
        : [
            ...baseMessages,
            { role: "assistant", content: JSON.stringify(lastRawSignal) },
            { role: "user", content: `That setup breaks the hard rules: ${lastErrors.join("; ")}. Fix it — same instrument or a different one from the data — and return corrected JSON that satisfies every hard rule.` }
          ];
      const aiResult = await callAI({
        max_tokens: 2500,
        reasoning: { effort: "low" },
        response_format: { type: "json_object" },
        messages
      }, "daily-signal");
      if (!aiResult.ok) {
        if (aiResult.status === 402) {
          console.error("[daily-signal] OPENROUTER ACCOUNT OUT OF CREDIT — top up at https://openrouter.ai/settings/credits (or set OPENAI_API_KEY as primary)");
        }
        return res.status(502).json({ error: "Analysis provider error", status: aiResult.status, detail: String(aiResult.detail || "").slice(0, 300) });
      }
      const data = await aiResult.response.json();
      const signal = extractJson(data.choices?.[0]?.message?.content || "");
      lastRawSignal = signal;
      const result = validateSignal(signal);
      if (result.ok) {
        validated = result;
      } else {
        lastErrors = result.errors;
      }
    }
    if (!validated) {
      return res.status(502).json({ error: "Model could not produce a setup meeting the quality rules after retry", errors: lastErrors, raw: lastRawSignal });
    }
    const { inst, entryNum, slNum, tps, dir, strength, rr } = validated;
    const signal = lastRawSignal;
    const aiMode = ["scalp", "swing"].includes(String(signal.mode).toLowerCase()) ? String(signal.mode).toLowerCase() : null;
    const { rows } = await pool.query(
      `INSERT INTO daily_signals
         (author, instrument_id, instrument_display, direction, entry, stop_loss, take_profits, risk_reward, thesis, strength, status, mode)
       VALUES ('ai', $1, $2, $3, $4, $5, $6, $7, $8, $9, 'live', $10)
       RETURNING *`,
      [
        inst.id, inst.display, dir, entryNum, slNum, JSON.stringify(tps), rr,
        signal.thesis ? String(signal.thesis).slice(0, 2000) : null,
        strength,
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

/** ---------- "I took this signal" (taker tracking) ---------- */

/** Returns the signed-in user's taken state for a signal + public taker count. */
app.get("/api/daily-signals/:id/take", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!SIGNAL_UUID_RE.test(req.params.id)) return res.status(404).json({ error: "Signal not found" });
  try {
    const me = await currentUser(req);
    const { rows } = await pool.query(
      `SELECT
         (SELECT COUNT(*)::int FROM signal_takers WHERE signal_id = $1::uuid) AS taker_count,
         EXISTS(SELECT 1 FROM signal_takers WHERE signal_id = $1::uuid AND user_id = $2::uuid) AS taken`,
      [req.params.id, me.id]
    );
    res.json({ taken: !!rows[0]?.taken, takerCount: rows[0]?.taker_count ?? 0 });
  } catch (err) {
    res.status(500).json({ error: "Could not load take state", detail: String(err.message || err) });
  }
});

/** Toggles "I took this signal" for the signed-in user. */
app.post("/api/daily-signals/:id/take", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!SIGNAL_UUID_RE.test(req.params.id)) return res.status(404).json({ error: "Signal not found" });
  try {
    const me = await currentUser(req);
    const { rows: signalRows } = await pool.query(`SELECT id FROM daily_signals WHERE id = $1::uuid`, [req.params.id]);
    if (!signalRows.length) return res.status(404).json({ error: "Signal not found" });
    const existing = await pool.query(
      `SELECT 1 FROM signal_takers WHERE signal_id = $1::uuid AND user_id = $2::uuid`,
      [req.params.id, me.id]
    );
    let taken;
    if (existing.rows.length) {
      await pool.query(`DELETE FROM signal_takers WHERE signal_id = $1::uuid AND user_id = $2::uuid`, [req.params.id, me.id]);
      taken = false;
    } else {
      await pool.query(`INSERT INTO signal_takers (signal_id, user_id) VALUES ($1::uuid, $2::uuid)`, [req.params.id, me.id]);
      taken = true;
    }
    const { rows } = await pool.query(
      `SELECT COUNT(*)::int AS taker_count FROM signal_takers WHERE signal_id = $1::uuid`, [req.params.id]
    );
    res.json({ taken, takerCount: rows[0]?.taker_count ?? 0 });
  } catch (err) {
    res.status(500).json({ error: "Could not update take state", detail: String(err.message || err) });
  }
});

/** ---------- Share your win (testimonials) ---------- */

/** Approved win testimonials for one signal (plus the caller's own pending/rejected ones). */
app.get("/api/daily-signals/:id/testimonials", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!SIGNAL_UUID_RE.test(req.params.id)) return res.status(404).json({ error: "Signal not found" });
  try {
    const me = await currentUser(req);
    const admin = await isAdminRequest(req);
    const { rows } = await pool.query(
      `SELECT t.id, t.author_name, t.author_email, t.comment, t.status, t.created_at,
              (t.user_id = $2::uuid) AS is_mine,
              EXISTS(SELECT 1 FROM signal_testimonial_images i WHERE i.testimonial_id = t.id) AS has_image,
              u.avatar_url, u.role
       FROM signal_testimonials t
       LEFT JOIN users u ON u.id = t.user_id
       WHERE t.signal_id = $1::uuid AND (t.status = 'approved' OR t.user_id = $2::uuid OR $3)
       ORDER BY t.created_at DESC
       LIMIT 100`,
      [req.params.id, me.id, admin]
    );
    res.json({
      testimonials: rows.map((r) => ({
        id: r.id,
        authorName: r.author_name,
        authorEmail: r.author_email,
        comment: r.comment,
        status: r.status,
        createdAt: r.created_at,
        isMine: !!r.is_mine,
        hasImage: r.has_image,
        avatarUrl: r.avatar_url || null,
        authorRole: r.role || "member"
      }))
    });
  } catch (err) {
    res.status(500).json({ error: "Could not load testimonials", detail: String(err.message || err) });
  }
});

/** Submit a win testimonial for a signal — text + optional proof screenshot. Goes to review. */
app.post("/api/daily-signals/:id/testimonials", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!SIGNAL_UUID_RE.test(req.params.id)) return res.status(404).json({ error: "Signal not found" });
  const body = String((req.body || {}).comment || "").trim();
  const imageDataUrl = (req.body || {}).image ? String((req.body || {}).image) : null;
  if (!body && !imageDataUrl) {
    return res.status(400).json({ error: "Add a comment or attach your proof screenshot to share your win." });
  }
  if (body.length > 800) return res.status(400).json({ error: "Comments are limited to 800 characters." });
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
    if (body) {
      const linkVerdict = await guardLinks(me, body);
      if (!linkVerdict.allowed) {
        return res.status(422).json({ error: linkVerdict.error, linkBlocked: true });
      }
    }
    const { rows: signalRows } = await pool.query(
      `SELECT id, status, outcome FROM daily_signals WHERE id = $1::uuid`,
      [req.params.id]
    );
    if (!signalRows.length) return res.status(404).json({ error: "Signal not found" });
    const s = signalRows[0];
    if (s.status !== "closed" || s.outcome !== "successful") {
      return res.status(422).json({ error: "You can only share wins on signals that closed at a profit." });
    }
    const { rows } = await pool.query(
      `INSERT INTO signal_testimonials (signal_id, user_id, author_name, author_email, comment, status)
       VALUES ($1::uuid, $2::uuid, $3, $4, $5, 'pending')
       RETURNING id, author_name, comment, status, created_at`,
      [req.params.id, me.id, me.name, me.email, body.slice(0, 800)]
    );
    const t = rows[0];
    if (imageDataUrl) {
      const m = imageDataUrl.match(/^data:(image\/(?:png|jpe?g|webp));base64,/);
      await pool.query(
        `INSERT INTO signal_testimonial_images (testimonial_id, content_type, data_base64) VALUES ($1::uuid, $2, $3)`,
        [t.id, m ? m[1] : "image/jpeg", imageDataUrl.split(",")[1] || ""]
      );
    }
    res.status(201).json({
      testimonial: {
        id: t.id, authorName: t.author_name, comment: t.comment, status: t.status,
        createdAt: t.created_at, isMine: true, hasImage: !!imageDataUrl, pendingReview: true
      }
    });
  } catch (err) {
    res.status(500).json({ error: "Could not share your win", detail: String(err.message || err) });
  }
});

/** Streams a testimonial's proof image. Pending images are only visible to their author or the admin. */
app.get("/api/daily-signals/testimonials/:testimonialId/image", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const me = await currentUser(req);
    const admin = await isAdminRequest(req);
    const { rows } = await pool.query(
      `SELECT t.user_id, t.status, i.content_type, i.data_base64
       FROM signal_testimonials t JOIN signal_testimonial_images i ON i.testimonial_id = t.id
       WHERE t.id = $1::uuid`,
      [req.params.testimonialId]
    );
    if (!rows.length) return res.status(404).json({ error: "Image not found" });
    const row = rows[0];
    if (row.status !== "approved" && !admin && !(me && row.user_id === me.id)) {
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

/** Featured (latest approved) win testimonials across all signals — for the Signals screen strip. */
app.get("/api/daily-signals/testimonials/featured", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const { rows } = await pool.query(
      `SELECT t.id, t.comment, t.created_at, t.author_name, t.author_email,
              t.signal_id, s.instrument_id, s.instrument_display, s.direction, s.take_profits, s.exit_price,
              EXISTS(SELECT 1 FROM signal_testimonial_images i WHERE i.testimonial_id = t.id) AS has_image,
              u.avatar_url
       FROM signal_testimonials t
       JOIN daily_signals s ON s.id = t.signal_id
       LEFT JOIN users u ON u.id = t.user_id
       WHERE t.status = 'approved'
       ORDER BY t.created_at DESC
       LIMIT 10`
    );
    res.json({
      featured: rows.map((r) => ({
        id: r.id,
        signalId: r.signal_id,
        comment: r.comment,
        createdAt: r.created_at,
        authorName: r.author_name,
        avatarUrl: r.avatar_url || null,
        hasImage: r.has_image,
        instrument: r.instrument_display,
        instrumentId: r.instrument_id,
        direction: r.direction,
        exitPrice: r.exit_price
      }))
    });
  } catch (err) {
    res.status(500).json({ error: "Could not load featured wins", detail: String(err.message || err) });
  }
});

/**
 * Wall of Wins — the full, paginated wall of approved member win proofs,
 * plus real wall stats (total shared wins, wins this week, traders sharing).
 */
app.get("/api/wins/wall", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const limit = Math.min(Math.max(parseInt(req.query.limit, 10) || 12, 1), 24);
  const offset = Math.max(parseInt(req.query.offset, 10) || 0, 0);
  try {
    const { rows } = await pool.query(
      `SELECT t.id, t.comment, t.created_at, t.author_name, t.signal_id,
              s.instrument_display, s.instrument_id, s.direction, s.exit_price,
              EXISTS(SELECT 1 FROM signal_testimonial_images i WHERE i.testimonial_id = t.id) AS has_image,
              u.avatar_url
       FROM signal_testimonials t
       JOIN daily_signals s ON s.id = t.signal_id
       LEFT JOIN users u ON u.id = t.user_id
       WHERE t.status = 'approved'
       ORDER BY t.created_at DESC
       LIMIT $1 OFFSET $2`,
      [limit, offset]
    );
    const { rows: statRows } = await pool.query(
      `SELECT COUNT(*)::int AS total,
              COUNT(*) FILTER (WHERE t.created_at >= now() - interval '7 days')::int AS this_week,
              COUNT(DISTINCT t.user_id)::int AS traders
       FROM signal_testimonials t WHERE t.status = 'approved'`
    );
    const st = statRows[0] || { total: 0, this_week: 0, traders: 0 };
    res.json({
      wins: rows.map((r) => ({
        id: r.id,
        signalId: r.signal_id,
        comment: r.comment,
        createdAt: r.created_at,
        authorName: r.author_name,
        avatarUrl: r.avatar_url || null,
        hasImage: r.has_image,
        instrument: r.instrument_display,
        instrumentId: r.instrument_id,
        direction: r.direction,
        exitPrice: r.exit_price
      })),
      stats: { total: st.total, thisWeek: st.this_week, traders: st.traders },
      hasMore: rows.length === limit
    });
  } catch (err) {
    res.status(500).json({ error: "Could not load the wall", detail: String(err.message || err) });
  }
});

/** ---------- Admin: win review queue ---------- */

/** Admin: list testimonials (default: pending) for the Team Console review queue. */
app.get("/api/admin/testimonials", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) return res.status(401).json({ error: "Admins only." });
  try {
    const status = ["pending", "approved", "rejected"].includes(String(req.query.status)) ? String(req.query.status) : "pending";
    const { rows } = await pool.query(
      `SELECT t.id, t.comment, t.status, t.created_at, t.author_name, t.author_email,
              s.instrument_display, s.outcome,
              EXISTS(SELECT 1 FROM signal_testimonial_images i WHERE i.testimonial_id = t.id) AS has_image
       FROM signal_testimonials t
       LEFT JOIN daily_signals s ON s.id = t.signal_id
       ORDER BY t.created_at DESC
       LIMIT 200`
    );
    res.json({
      testimonials: rows.map((r) => ({
        id: r.id, comment: r.comment, status: r.status, createdAt: r.created_at,
        authorName: r.author_name, authorEmail: r.author_email,
        instrument: r.instrument_display, outcome: r.outcome, hasImage: r.has_image
      }))
    });
  } catch (err) {
    res.status(500).json({ error: "Could not load review queue", detail: String(err.message || err) });
  }
});

/** Admin: approve or reject a testimonial. body: {decision: "approve"|"reject"} */
app.post("/api/admin/testimonials/:id/review", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) return res.status(401).json({ error: "Admins only." });
  const decision = String((req.body || {}).decision || "");
  if (!["approve", "reject"].includes(decision)) {
    return res.status(400).json({ error: "decision must be approve or reject" });
  }
  try {
    const { rows } = await pool.query(
      `UPDATE signal_testimonials
       SET status = $1, reviewed_at = now()
       WHERE id = $2::uuid AND status = 'pending'
       RETURNING id, status`,
      [decision === "approve" ? "approved" : "rejected", req.params.id]
    );
    if (!rows.length) return res.status(404).json({ error: "Pending testimonial not found" });
    res.json({ id: rows[0].id, status: rows[0].status });
  } catch (err) {
    res.status(500).json({ error: "Could not review testimonial", detail: String(err.message || err) });
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
    if (!OPENROUTER_API_KEY && !OPENAI_API_KEY) {
      return { allowed: false, error: "Links can't be posted right now — please try again in a moment or remove the link." };
    }
    let checked = [];
    try {
      const response = await callAI({
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
      }, "link-guard");
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
