/**
 * MarketScope AI API — Google auth verification, instrument catalog, AI chart analysis.
 * Runs server-side so the app holds zero AI provider keys.
 */
const express = require("express");
const crypto = require("crypto");
const { OAuth2Client, GoogleAuth } = require("google-auth-library");
const jwt = require("jsonwebtoken");
const { Pool } = require("pg");
const { ALL, byId, categories } = require("./src/instruments");
const monetization = require("./src/monetization");
const appVersion = require("./src/appVersion");
const { sendWelcomeEmail, sendSecurityAlert, sendTrialExpiredEmail, sendHealthAlertEmail, sendStatsReportEmail, sendPremiumActivatedEmail, sendPremiumPaymentFailedEmail, sendPremiumGrantedEmail, sendPremiumRevokedEmail } = require("./src/mailer");
const { termsOfServiceHtml, privacyPolicyHtml, communityGuidelinesHtml } = require("./src/legalPages");
const { fetchPrice, fetchHistory } = require("./src/prices");
const { sendFcm } = require("./src/fcm");
const { runAlertCron, holidayForToday } = require("./src/marketAlerts");
const { fetchTrending, fetchLiveQuotes } = require("./src/trending");
const { fetchWatchlist, WATCHLIST } = require("./src/markets");
const { fetchCandles, INTERVALS } = require("./src/candles");
const { fetchEconomicCalendar, fetchMarketNews } = require("./src/newsCalendar");
const { searchStock, bestMatch, fetchStockStats, searchNgxIpo } = require("./src/stocks");
const r2 = require("./src/r2");

const app = express();

// --- Paystack webhook: registered before the global JSON parser because the
// signature is an HMAC-SHA512 over the RAW request body. Route-level raw
// body parser only applies if it runs first, which it does here. ---
const PAYSTACK_SECRET_KEY = process.env.PAYSTACK_SECRET_KEY || "";
const SUBSCRIBE_URL = process.env.SUBSCRIBE_URL || "https://market-ai-api-jwfb.onrender.com/subscribe";
const SUB_CURRENCY = (process.env.SUB_CURRENCY || "USD").toUpperCase();
const SUB_PRICE = Number(process.env.SUB_PRICE || "9.99"); // price per month, 2 decimals

// --- Google Play Billing (the Play-Store-native way to subscribe) ---
// Requires a Google Play service account key + the app's package name, and
// the matching subscription product must exist in Play Console.
const GOOGLE_PLAY_PACKAGE_NAME = process.env.GOOGLE_PLAY_PACKAGE_NAME || "";
const GOOGLE_PLAY_SERVICE_ACCOUNT_JSON = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON || "";
const GOOGLE_PLAY_SUBSCRIPTION_IDS = String(process.env.GOOGLE_PLAY_SUBSCRIPTION_IDS || "premium-monthly")
  .split(",").map(s => s.trim()).filter(Boolean);
const googlePlayBillingReady = Boolean(GOOGLE_PLAY_PACKAGE_NAME) && Boolean(GOOGLE_PLAY_SERVICE_ACCOUNT_JSON);

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
            // Confirmation the moment activation is real: email + in-app +
            // push. The push deep-links straight into the Notifications
            // screen, scrolled to this exact message (via notificationId,
            // attached automatically by notifyUser).
            if (!wasPremium) {
              sendPremiumActivatedEmail({ email: rows[0].email, name: rows[0].name }).catch(() => {});
              notifyUser(rows[0].id, {
                title: "Premium activated \u2014 welcome to MarketScope AI Premium",
                body: "Your subscription payment was successful. You now have unlimited AI analysis, the full Daily Signals history and zero ads.",
                type: "billing",
                data: { type: "billing", route: "notifications" }
              }).catch(() => {});
            }
          }
        }
      }
    } else if (event.event === "charge.failed" && event.data && event.data.reference) {
      // A failed charge attempt — no re-verification needed (nothing to
      // activate), but still confirm the signature-matched payload before
      // acting on it, which the HMAC check above already did.
      const data = event.data;
      const googleSub = data.metadata && data.metadata.google_sub;
      const amount = typeof data.amount === "number" ? data.amount : null;
      const currency = data.currency || SUB_CURRENCY;
      const failReason = data.gateway_response || "The payment was declined.";
      if (pool && googleSub) {
        const { rows } = await pool.query(
          `SELECT id, email, name FROM users WHERE google_sub = $1`,
          [googleSub]
        );
        if (rows.length) {
          await pool.query(
            `INSERT INTO subscription_payments (user_id, reference, amount, currency, status, paid_at)
             VALUES ($1, $2, $3, $4, 'failed', now())
             ON CONFLICT (reference) DO NOTHING`,
            [rows[0].id, data.reference, amount, currency]
          );
          console.log(`[subscription] payment failed for google_sub ${googleSub} (ref ${data.reference}): ${failReason}`);
          sendPremiumPaymentFailedEmail({ email: rows[0].email, name: rows[0].name }, { reason: failReason }).catch(() => {});
          notifyUser(rows[0].id, {
            title: "Payment failed \u2014 MarketScope AI Premium",
            body: `Your subscription payment did not go through (${failReason}). No charge was made. You can try again from the Subscribe screen.`,
            type: "billing",
            data: { type: "billing", route: "notifications" }
          }).catch(() => {});
        }
      }
    }
    return res.sendStatus(200);
  } catch (err) {
    console.error("[subscription] webhook error:", String(err.message || err));
    return res.sendStatus(200); // Paystack retries on non-2xx; log instead of failing
  }
});

// CORS for the public marketing site (marketscope-site on GitHub Pages):
// the site fetches read-only public endpoints (watchlist ticker, community
// stats, signal stats) straight from the browser. Only that exact origin is
// allowed, and only simple GETs ever work cross-origin here — no credentials,
// no preflighted mutations. Authenticated app traffic (OkHttp) is unaffected.
app.use((req, res, next) => {
  const origin = req.headers.origin || "";
  if (origin === "https://kora-messenger.github.io") {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
  }
  next();
});

app.use(express.json({ limit: "25mb" }));

const PORT = process.env.PORT || 3000;
const OPENROUTER_API_KEY = process.env.OPENROUTER_API_KEY || "";
// Primary AI provider (fallback: OpenRouter above). Model is env-swappable so
// we can move to newer OpenAI generations without a code change.
const OPENAI_API_KEY = process.env.OPENAI_API_KEY || "";
// Paid subscribers analyze on GPT-5 (the strongest reasoning model);
// trial/free users run Gemini via OpenRouter. OPENAI_ANALYSIS_MODEL env
// still overrides this without a code change.
const OPENAI_MODEL = process.env.OPENAI_ANALYSIS_MODEL || "gpt-5";
// OpenRouter keeps a rotating ":free" model tier that works on an unfunded
// account (strict upstream rate limits, lower quality than paid GPT-4o —
// a $0 floor so analysis never hard-fails while accounts are untopped).
// AI_FREE_MODEL="" disables the floor. Vision+JSON verified live.
const AI_FREE_MODELS = (process.env.AI_FREE_MODELS !== undefined
  ? process.env.AI_FREE_MODELS
  : "nex-agi/nex-n2.5-pro:free,dots-studio/dots-3-note-preview:free,google/gemma-4-26b-a4b-it:free"
).split(",").map((s) => s.trim()).filter(Boolean);

// --- Fast-fail circuit breakers ---
// When a provider is DEFINITIVELY dead (OpenAI quota exhausted, OpenRouter
// paid credits gone), the first failed call stamps a "dead until" time.
// Every call after that skips the provider instantly — no HTTP round trip,
// no retry, no sleep — until the window expires (so topping up the account
// recovers automatically within minutes). Without this, an analysis makes
// 2-3 AI calls and EACH one re-attempted the dead provider from scratch.
const BREAKER = {
  openaiDeadUntil: 0,        // insufficient_quota 429s never clear mid-session
  openrouterPaidDeadUntil: 0 // 402 = not enough credit for this request size
};
const OPENAI_DEAD_MS = 15 * 60 * 1000;  // re-probe OpenAI every 15 min
const OPENROUTER_PAID_DEAD_MS = 5 * 60 * 1000; // re-probe paid tier every 5 min
const AI_CALL_TIMEOUT_MS = 90 * 1000;  // hard cap — a hung provider socket
                                       // used to stall an analysis forever

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
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(AI_CALL_TIMEOUT_MS)
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
    // 402 = the account cannot afford THIS request. Definitive for the paid
    // tier (retrying the same priced call cannot succeed) — stamp the breaker
    // so the next call goes straight to free models with zero wasted time.
    if (lastStatus === 402 && !(payload.model || "").endsWith(":free")) {
      BREAKER.openrouterPaidDeadUntil = Date.now() + OPENROUTER_PAID_DEAD_MS;
      console.error(`[openrouter:${label}] 402 — paid tier breaker OPEN for ${OPENROUTER_PAID_DEAD_MS / 60000} min`);
      return { ok: false, status: lastStatus, detail: lastDetail };
    }
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
        body: JSON.stringify(body),
        signal: AbortSignal.timeout(AI_CALL_TIMEOUT_MS)
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
    const outOfCredits = lastStatus === 429 && /insufficient_quota|credit_balance_exhausted/.test(lastDetail);
    if (outOfCredits) {
      BREAKER.openaiDeadUntil = Date.now() + OPENAI_DEAD_MS;
      console.error(`[openai:${label}] quota exhausted — OpenAI breaker OPEN for ${OPENAI_DEAD_MS / 60000} min`);
    }
    if (lastStatus === 429) {
      console.error(`[openai:${label}] QUOTA/RATE LIMIT — check plan and billing at https://platform.openai.com/usage`);
    }
    // A hard quota-exhaustion 429 can never succeed on retry within this
    // request — waiting 1.2s and asking again just delays every analysis
    // for no benefit. Skip straight to OpenRouter fallback in that case;
    // still retry genuine transient rate limits / 5xx once as before.
    if (attempt === 1 && !outOfCredits && RETRYABLE.has(lastStatus)) {
      await new Promise(r => setTimeout(r, 1200));
      continue;
    }
    return { ok: false, status: lastStatus, detail: lastDetail, provider: "openai" };
  }
  return { ok: false, status: lastStatus, detail: lastDetail, provider: "openai" };
}

/** Unified AI call: OpenAI first, OpenRouter as automatic fallback.
 *  Same { ok, response, status, detail } contract as callOpenRouter. */
async function callAI(payload, label, opts = {}) {
  // Premium routing: OpenAI (GPT-5) is reserved for paid-subscriber ANALYSIS
  // passes ({ premium: true }). Everything else — trial/free users, chart
  // validation, cron signals — runs straight on OpenRouter (Gemini), which
  // keeps the OpenAI credit burn to paid users only.
  const useOpenAI = opts.premium === true;
  const openaiDead = Date.now() < BREAKER.openaiDeadUntil;
  const paidRouterDead = Date.now() < BREAKER.openrouterPaidDeadUntil;
  if (OPENAI_API_KEY && useOpenAI && !openaiDead) {
    const r = await callOpenAI(payload, label);
    if (r.ok) return r;
    console.error(
      `[ai:${label}] OpenAI attempt failed (HTTP ${r.status}) — falling back to OpenRouter:`,
      String(r.detail).slice(0, 200)
    );
  } else if (useOpenAI && openaiDead) {
    console.log(`[ai:${label}] OpenAI skipped — quota breaker open`);
  }
  if (OPENROUTER_API_KEY) {
    if (paidRouterDead) {
      console.log(`[ai:${label}] OpenRouter paid tier skipped — credit breaker open, trying free models`);
    } else {
    const r = await callOpenRouter(payload, label);
    if (r.ok) return r;
    console.error(
      `[ai:${label}] OpenRouter paid attempt failed (HTTP ${r.status}) — trying free tier:`,
      String(r.detail).slice(0, 200)
    );
    }
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
          const first = preview.indexOf("{");
          const last = preview.lastIndexOf("}");
          let parseOk = first !== -1 && last > first;
          if (parseOk) {
            try { JSON.parse(preview.slice(first, last + 1)); }
            catch (_e) { parseOk = false; }
          }
          if (!parseOk) {
            console.error(`[ai:${label}] free model ${freeModel} returned no parseable JSON — trying next`);
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
// Explicit owner email(s). When configured these are the only accounts with
// backend role-management authority. Oldest-account fallback is used only
// when this setting is empty, for first-run recovery.
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
    -- Bottom tab badge bookkeeping: the last time the user had the Signals
    -- / Community tabs open, so the app can badge "N new since your last
    -- visit". NULL = never visited; initialized to now() on first read so
    -- badges only count content published after the user joined the app.
    ALTER TABLE users ADD COLUMN IF NOT EXISTS signals_seen_at TIMESTAMPTZ;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS community_seen_at TIMESTAMPTZ;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS community_last_seen_at TIMESTAMPTZ;
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
  // Unique lowercase handles. Runs after the column migration above; a
  // failure (e.g. unexpected duplicates) must never break startup.
  try {
    await pool.query(`CREATE UNIQUE INDEX IF NOT EXISTS users_username_lower_unique ON users (lower(username))`);
  } catch (e) {
    console.warn("[db] username unique index skipped:", String(e.message || e));
  }
  await monetization.ensureMonetizationTables(pool);
  await appVersion.ensureAppVersionTable(pool);
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
    ALTER TABLE users ADD COLUMN IF NOT EXISTS premium_started_at TIMESTAMPTZ;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS premium_platform TEXT;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS premium_expires_at TIMESTAMPTZ;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS premium_product_id TEXT;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS premium_purchase_token TEXT;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS community_joined BOOLEAN NOT NULL DEFAULT false;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS community_joined_at TIMESTAMPTZ;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS deletion_requested_at TIMESTAMPTZ;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_key TEXT;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS username TEXT;
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
    ALTER TABLE daily_signals ADD COLUMN IF NOT EXISTS tps_hit JSONB;
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
    ALTER TABLE signal_comments ADD COLUMN IF NOT EXISTS author_email TEXT NOT NULL DEFAULT '';
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
    ALTER TABLE signal_testimonial_images ADD COLUMN IF NOT EXISTS r2_key TEXT;
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

    CREATE TABLE IF NOT EXISTS ai_trade_plans (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      inputs JSONB NOT NULL,
      content JSONB NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    CREATE INDEX IF NOT EXISTS idx_ai_trade_plans_user ON ai_trade_plans(user_id, created_at DESC);

    CREATE TABLE IF NOT EXISTS community_posts (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID REFERENCES users(id) ON DELETE SET NULL,
      author_name TEXT NOT NULL,
      author_email TEXT NOT NULL DEFAULT '',
      body TEXT NOT NULL,
      is_team BOOLEAN NOT NULL DEFAULT false,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS author_email TEXT NOT NULL DEFAULT '';
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS is_team BOOLEAN NOT NULL DEFAULT false;
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS post_type TEXT NOT NULL DEFAULT 'text';
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS poll_options JSONB;
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS allow_comments BOOLEAN NOT NULL DEFAULT true;
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN NOT NULL DEFAULT false;
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS pinned_at TIMESTAMPTZ;
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS outcome_tag TEXT;
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS link_preview JSONB;
    -- Admin/mentor reposts of a mentor-reviewed win. A repost is a normal
    -- community post (shows in the feed like any other) but is linked back
    -- to the signal_testimonials row it came from, and carries who reposted
    -- it separately from who the post displays as author (the original
    -- trader). One testimonial can only ever be reposted once.
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS is_repost BOOLEAN NOT NULL DEFAULT false;
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS repost_of_testimonial_id UUID REFERENCES signal_testimonials(id) ON DELETE SET NULL;
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS reposted_by_name TEXT;
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS reposted_by_email TEXT;
    -- A trader's OWN casual note that a post relates to a take-profit or
    -- stop-loss moment. Purely a personal label — unlike outcome_tag it is
    -- never mentor-reviewed, never counts toward the weekly proof strip, and
    -- can never make a post eligible for the repost feature.
    ALTER TABLE community_posts ADD COLUMN IF NOT EXISTS self_tag TEXT;
    CREATE UNIQUE INDEX IF NOT EXISTS community_posts_repost_unique
      ON community_posts(repost_of_testimonial_id) WHERE repost_of_testimonial_id IS NOT NULL;
    -- Remove only the exact synthetic proof made during the 2026-09-22
    -- endpoint smoke test (its repost was already deleted). Keep this in
    -- the first deployment only; no real member record matches both guards.
    DELETE FROM signal_testimonials
      WHERE id = '271a5193-3b2d-4f3b-b1f3-f6adb8748329'::uuid
        AND comment = 'TEST: verifying the new repost pipeline end-to-end (will be cleaned up).';
    -- Server-side scraped OpenGraph cards (title/description/og:image),
    -- cached per URL — the same "pasted link becomes a preview card"
    -- behavior the reference app shows on its community posts.
    CREATE TABLE IF NOT EXISTS link_previews (
      url TEXT PRIMARY KEY,
      title TEXT,
      description TEXT,
      image TEXT,
      domain TEXT,
      fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      failed_at TIMESTAMPTZ
    );
    CREATE TABLE IF NOT EXISTS community_post_images (
      post_id UUID REFERENCES community_posts(id) ON DELETE CASCADE,
      position INT NOT NULL,
      content_type TEXT NOT NULL DEFAULT 'image/jpeg',
      data_base64 TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      PRIMARY KEY (post_id, position)
    );
    ALTER TABLE community_post_images ADD COLUMN IF NOT EXISTS r2_key TEXT;
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
    ALTER TABLE post_comments ADD COLUMN IF NOT EXISTS author_email TEXT NOT NULL DEFAULT '';
    ALTER TABLE post_comments ADD COLUMN IF NOT EXISTS author_name TEXT NOT NULL DEFAULT '';
    ALTER TABLE post_comments ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE SET NULL;
    ALTER TABLE post_comments ADD COLUMN IF NOT EXISTS parent_id UUID REFERENCES post_comments(id) ON DELETE CASCADE;
    ALTER TABLE post_comments ADD COLUMN IF NOT EXISTS post_id UUID REFERENCES community_posts(id) ON DELETE CASCADE;
    ALTER TABLE post_comments ADD COLUMN IF NOT EXISTS body TEXT NOT NULL DEFAULT '';
    ALTER TABLE post_comments ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
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
    CREATE TABLE IF NOT EXISTS price_alerts (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      symbol TEXT NOT NULL,
      display TEXT NOT NULL,
      direction TEXT NOT NULL,
      target_price DOUBLE PRECISION NOT NULL,
      status TEXT NOT NULL DEFAULT 'active',
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      triggered_at TIMESTAMPTZ,
      triggered_price DOUBLE PRECISION
    );
    CREATE INDEX IF NOT EXISTS idx_price_alerts_user ON price_alerts(user_id, created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_price_alerts_active ON price_alerts(symbol) WHERE status = 'active';
    CREATE TABLE IF NOT EXISTS bug_reports (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID REFERENCES users(id) ON DELETE SET NULL,
      user_email TEXT NOT NULL DEFAULT '',
      description TEXT NOT NULL,
      attachments JSONB NOT NULL DEFAULT '[]',
      app_version TEXT,
      device_model TEXT,
      android_version TEXT,
      status TEXT NOT NULL DEFAULT 'open',
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    CREATE INDEX IF NOT EXISTS idx_bug_reports_created ON bug_reports(created_at DESC);
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

/**
 * A paid subscription only counts while it hasn't lapsed. Paystack
 * subscriptions (and legacy accounts) have no expiry — sticky forever, as
 * before. Google Play subscriptions carry the Play-provided renewal date in
 * premium_expires_at and lapse when it passes (Play bills the renewal, and
 * the app re-verifies the purchase on resume).
 */
function paidPremiumActive(row) {
  if (!row || !row.is_premium) return false;
  const exp = row.premium_expires_at;
  return !exp || new Date(exp).getTime() > Date.now();
}

function trialInfo(row) {
  const startedAt = new Date(row.trial_started_at);
  const endsAt = new Date(startedAt.getTime() + TRIAL_DAYS * 24 * 60 * 60 * 1000);
  const isPremium = paidPremiumActive(row);
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
        .query(`UPDATE users SET last_seen_at = now() WHERE google_sub = $1`, [req.session.sub])
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
    analysisProvider: OPENAI_API_KEY ? "openai" : (OPENROUTER_API_KEY ? "openrouter" : null),
    // Image storage: r2 = Cloudflare R2 (primary, when all keys are set);
    // otherwise member images fall back to base64-in-Postgres.
    imageStorage: r2.isR2Configured() ? "r2" : "database"
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
         RETURNING id, google_sub, email, name, picture, username, community_joined, community_joined_at,
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
        username: rows[0].username || null,
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
// --- Profile: avatar + username (FxLens-style editable profile) --------
const USERNAME_RE = /^[a-z0-9._]{3,20}$/;
const AVATAR_UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Upload a custom avatar (data URL) -> R2 -> users.avatar_key. */
app.post("/api/profile/avatar", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const imageDataUrl = (req.body || {}).image ? String(req.body.image) : null;
  if (!imageDataUrl) return res.status(400).json({ error: "image (data URL) is required." });
  const m = imageDataUrl.match(/^data:(image\/(?:png|jpe?g|webp));base64,/);
  if (!m) return res.status(400).json({ error: "Images must be png/jpeg/webp data URLs." });
  const b64 = imageDataUrl.split(",")[1] || "";
  if (b64.length > 2_000_000) return res.status(400).json({ error: "The image must be under 1.5MB." });
  if (!r2.isR2Configured()) {
    return res.status(503).json({ error: "Image storage is not configured yet." });
  }
  try {
    const key = await r2.uploadImage(Buffer.from(b64, "base64"), m[1], "avatars");
    const { rows } = await pool.query(
      `UPDATE users SET avatar_key = $1 WHERE google_sub = $2 RETURNING id`,
      [key, req.session.sub]
    );
    if (!rows.length) return res.status(404).json({ error: "User not found." });
    return res.json({ avatar: `avatar:${rows[0].id}` });
  } catch (err) {
    return res.status(500).json({ error: "Could not save the avatar.", detail: String(err.message || err) });
  }
});

/** Serve any member's avatar: custom (R2, signed redirect) or Google picture. */
app.get("/api/profile/avatar/:userId", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!AVATAR_UUID_RE.test(req.params.userId)) return res.status(404).json({ error: "Avatar not found" });
  try {
    const { rows } = await pool.query(
      `SELECT avatar_key, picture FROM users WHERE id = $1`,
      [req.params.userId]
    );
    if (!rows.length) return res.status(404).json({ error: "Avatar not found" });
    const row = rows[0];
    if (row.avatar_key) {
      return res.redirect(302, await r2.signedImageUrl(row.avatar_key));
    }
    if (row.picture) {
      return res.redirect(302, row.picture);
    }
    return res.status(404).json({ error: "Avatar not found" });
  } catch (err) {
    return res.status(500).json({ error: "Could not load the avatar.", detail: String(err.message || err) });
  }
});

/** Set or change the public @username handle. */
app.post("/api/profile/username", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const raw = String((req.body || {}).username || "").trim().toLowerCase();
  if (!USERNAME_RE.test(raw)) {
    return res.status(400).json({
      error: "Handles are 3-20 characters: lowercase letters, numbers, dots or underscores."
    });
  }
  try {
    const { rows } = await pool.query(
      `SELECT id FROM users WHERE lower(username) = $1 AND google_sub <> $2 LIMIT 1`,
      [raw, req.session.sub]
    );
    if (rows.length) {
      return res.status(409).json({ error: "That handle is already taken." });
    }
    const { rows: updated } = await pool.query(
      `UPDATE users SET username = $1 WHERE google_sub = $2 RETURNING username`,
      [raw, req.session.sub]
    );
    if (!updated.length) return res.status(404).json({ error: "User not found." });
    return res.json({ username: updated[0].username });
  } catch (err) {
    return res.status(500).json({ error: "Could not save the handle.", detail: String(err.message || err) });
  }
});

// --- Learning Hub: pattern library (original educational content) --------
const learningContent = require("./src/learningContent");
app.get("/api/learning/patterns", requireAuth, (req, res) => {
  res.json({
    categories: learningContent.categoriesPayload(),
    patterns: learningContent.fullPayload()
  });
});

app.get("/api/account/status", requireAuth, async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const { rows } = await pool.query(
      `SELECT u.deletion_requested_at, u.username, u.avatar_key, u.id,
              (SELECT COUNT(*)::int FROM analyses a WHERE a.user_id = u.id) AS analyses_count,
              (SELECT COUNT(*)::int FROM trade_plans t WHERE t.user_id = u.id) AS saved_count
       FROM users u WHERE u.google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) {
      return res.status(404).json({ error: "User not found" });
    }
    const r = rows[0];
    return res.json({
      deletionRequestedAt: r.deletion_requested_at,
      username: r.username || null,
      avatar: r.avatar_key ? `avatar:${r.id}` : null,
      analysesCount: r.analyses_count,
      savedTradesCount: r.saved_count
    });
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

/**
 * News Outlook — AI directional implication for one specific economic
 * calendar event. Requires auth (costs an AI call). Runs on the OpenRouter
 * tier (same as trial/free chart analysis) — this is a lightweight utility,
 * not a paid premium pass, so it never touches the OpenAI budget.
 */
app.post("/api/calendar/directional-implication", requireAuth, async (req, res) => {
  const { title, country, currency, impact, forecast, previous, actual, timestamp } = req.body || {};
  if (!title) {
    return res.status(400).json({ error: "title is required" });
  }
  const hasActual = actual != null && String(actual).trim() !== "";
  const eventIsFuture = timestamp ? new Date(timestamp).getTime() > Date.now() : false;
  const factLines = [
    `Event: ${title}`,
    country ? `Country: ${country}` : null,
    currency ? `Currency: ${currency}` : null,
    impact ? `Impact level: ${impact}` : null,
    forecast != null ? `Forecast: ${forecast}` : null,
    previous != null ? `Previous: ${previous}` : null,
    hasActual ? `Actual (already released): ${actual}` : "Actual: not released yet",
    eventIsFuture ? "This event has not happened yet." : "This event has already occurred or is in progress."
  ].filter(Boolean).join("\n");

  try {
    const result = await callAI({
      model: ANALYSIS_MODEL,
      max_tokens: 260,
      reasoning: { effort: "low" },
      response_format: { type: "json_object" },
      messages: [
        {
          role: "system",
          content:
            "You are a concise market-education assistant inside a trading app. Given one economic-calendar " +
            "event, write a short, neutral 'directional implication' explaining how the reading could move its " +
            "currency. If the actual reading has already been released, compare it to the forecast/previous and " +
            "state the likely near-term bias for that currency (stronger-than-forecast = typically supportive; " +
            "weaker-than-forecast = typically a headwind), always noting this is a general tendency, not a " +
            "guarantee. If the event hasn't happened yet, explain what a beat vs a miss vs an in-line print would " +
            "each likely mean for the currency. Keep it to 2-3 sentences, plain language, no financial advice " +
            'disclaimers beyond a short closing caveat. Reply with strict JSON: {"implication": "..."}.'
        },
        { role: "user", content: factLines }
      ]
    }, "calendar-directional-implication");

    if (!result.ok) {
      return res.status(502).json({ error: "AI is temporarily unavailable — try again shortly." });
    }
    const data = await result.response.json();
    const text = data.choices?.[0]?.message?.content || "";
    let implication = null;
    try {
      const parsed = extractJson(text);
      implication = typeof parsed.implication === "string" ? parsed.implication.trim() : null;
    } catch (_e) { /* fall through */ }
    if (!implication) {
      return res.status(502).json({ error: "Could not read the AI's response — try again." });
    }
    res.json({ implication });
  } catch (err) {
    res.status(502).json({ error: "Could not generate a directional implication right now.", detail: String(err.message || err) });
  }
});

// Community composer rights: posting is reserved for the MarketScope AI team
// (admins, moderators) and mentors. Everyone else can read, react and comment.
const COMPOSER_ROLES = new Set(["admin", "moderator", "mentor"]);
function canComposeCommunity(role) {
  return COMPOSER_ROLES.has(String(role || "").toLowerCase());
}

// Reposting a mentor-reviewed win into Community is intentionally narrower
// than general posting rights: only admin and mentor (never moderator,
// never a plain member no matter how they got Premium).
const REPOST_ROLES = new Set(["admin", "mentor"]);
function canRepostCommunity(role) {
  return REPOST_ROLES.has(String(role || "").toLowerCase());
}

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

/**
 * PUBLIC wins feed for the marketing site's Results section (CORS above
 * allows only the GitHub Pages origin, GET only). Read-only, approved
 * testimonials only, capped at 8, no auth — the site shows initials, not
 * full profiles, and never any member email or identifiers.
 */
app.get("/api/community/wins/public", async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const { rows } = await pool.query(
      `SELECT t.id, t.comment, t.created_at, t.author_name,
              s.instrument_display, s.direction, s.outcome
       FROM signal_testimonials t
       JOIN daily_signals s ON s.id = t.signal_id
       WHERE t.status = 'approved' AND s.outcome = 'successful'
         AND t.comment IS NOT NULL AND length(trim(t.comment)) > 0
       ORDER BY t.created_at DESC
       LIMIT 8`
    );
    res.json({
      wins: rows.map((r) => ({
        id: r.id,
        comment: r.comment,
        createdAt: r.created_at,
        authorName: r.author_name,
        instrument: r.instrument_display,
        direction: r.direction,
        outcome: r.outcome
      }))
    });
  } catch (err) {
    res.status(500).json({ error: "Could not load wins", detail: String(err.message || err) });
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
// Only the explicitly configured owner account (isAdminRequest) may search,
// inspect, grant or revoke Premium. Premium entitlement never changes a
// community role and never grants posting or role-management permissions. Grants are
// database-backed entitlements (premium_grants), fully separate from paid
// subscriptions (users.is_premium / Paystack) — revoking a grant never touches
// a paid subscription; effective access is always recalculated from ALL
// entitlement sources.

/** Shared: build a user's full premium status snapshot for the admin UI. */
async function premiumStatusSnapshot(userId) {
  const { rows } = await pool.query(
    `SELECT id, google_sub, email, name, picture, role, is_premium, premium_expires_at, premium_platform, trial_started_at, created_at
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
  const paid = paidPremiumActive(u);
  const sources = [];
  if (paid) sources.push(u.premium_platform === "google_play" ? "Google Play Subscription" : "Paid Subscription");
  if (grant) sources.push(grant.duration_type === "lifetime" ? "Lifetime Admin Grant" : premiumGrantLabel(grant) + " (Admin Grant)");
  if (trial.trialActive && !paid && !grant) sources.push("Free Trial");
  return {
    id: u.id,
    googleSub: u.google_sub,
    email: u.email,
    name: u.name || u.email || "User",
    picture: u.picture || null,
    joinedAt: u.created_at,
    communityRole: u.role || "member",
    canComposeCommunity: canComposeCommunity(u.role),
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
      type: "premium_gift",
      data: { type: "premium_gift", route: "notifications" }
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
      type: "premium_gift",
      data: { type: "premium_gift", route: "notifications" }
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
      `SELECT id, trial_started_at, is_premium, premium_expires_at FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) return res.status(404).json({ error: "User not found" });
    const trial = trialInfo(rows[0]);
    const grant = await getActivePremiumGrant(rows[0].id);
    const effectivePremium = paidPremiumActive(rows[0]) || !!grant || trial.trialActive;
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

// Force-update gate — public, called by the app BEFORE sign-in so an
// out-of-date install is blocked before it can even reach the welcome
// screen. Server-driven: the admin raises minVersionCode any time, no
// app release needed to start enforcing it.
app.get("/api/app-version/check", async (req, res) => {
  if (!pool) return res.json({ updateRequired: false });
  try {
    const cfg = await appVersion.getAppVersionConfig(pool);
    const versionCode = parseInt(req.query.versionCode, 10) || 0;
    const updateRequired = !!cfg.enforced && versionCode > 0 && versionCode < cfg.minVersionCode;
    // Platform-aware store link: the client reports "android" or "ios" so
    // the button says Open Play Store / Open App Store with the right URL.
    const platform = String(req.query.platform || "android").toLowerCase() === "ios" ? "ios" : "android";
    const isIos = platform === "ios" && !!cfg.appStoreUrl;
    res.json({
      updateRequired,
      minVersionName: cfg.minVersionName,
      latestVersionName: cfg.latestVersionName,
      updateMessage: cfg.updateMessage,
      playStoreUrl: cfg.playStoreUrl,
      appStoreUrl: cfg.appStoreUrl || null,
      storeLabel: isIos ? "Open App Store" : "Open Play Store",
      storeUrl: isIos ? cfg.appStoreUrl : cfg.playStoreUrl
    });
  } catch (err) {
    console.error("[app-version] check failed:", String(err.message || err));
    // Fail OPEN — a backend hiccup must never lock users out of the app.
    res.json({ updateRequired: false });
  }
});

// Admin: read + update the force-update configuration (no app release needed).
app.get("/api/admin/app-version/config", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only MarketScope AI administrators can manage the update gate." });
  }
  try {
    const { rows } = await pool.query(`SELECT updated_at, updated_by FROM app_version_config WHERE id = 1`);
    const cfg = await appVersion.getAppVersionConfig(pool);
    res.json({ config: cfg, updatedAt: rows[0] ? rows[0].updated_at : null, updatedBy: rows[0] ? rows[0].updated_by : null });
  } catch (err) {
    res.status(500).json({ error: "Could not load update-gate settings" });
  }
});

app.put("/api/admin/app-version/config", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only MarketScope AI administrators can manage the update gate." });
  }
  const partial = req.body && req.body.config;
  if (!partial || typeof partial !== "object" || Array.isArray(partial)) {
    return res.status(400).json({ error: "Body must be { config: { ... } }" });
  }
  try {
    const result = await appVersion.updateAppVersionConfig(pool, partial, req.session.email || "admin");
    res.json(result);
  } catch (err) {
    console.error("[admin-app-version] update failed:", String(err.message || err));
    res.status(500).json({ error: "Could not update the update-gate settings" });
  }
});

/** True only for the real backend admin/owner (env ADMIN_EMAIL match, or the
 *  very first account ever created) — this is the account whose role can
 *  never be relabeled away by mistake. Any OTHER account, even one currently
 *  labeled "admin" for display, is a normal member as far as this lock goes. */
async function isRealAdminAccount(user) {
  if (!user) return false;
  if (ADMIN_EMAILS.includes(String(user.email || "").toLowerCase())) return true;
  const adminSub = await getAdminSub();
  return !!adminSub && String(user.google_sub || "") === adminSub;
}

app.get("/api/admin/members", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only the MarketScope AI team can manage members." });
  }
  const q = String(req.query.q || "").trim().toLowerCase();
  try {
    const { rows } = await pool.query(
      `SELECT id, name, email, picture, role, community_joined, created_at, last_seen_at, google_sub
         FROM users
        WHERE ($1 = '' OR lower(name) LIKE '%' || $1 || '%' OR lower(email) LIKE '%' || $1 || '%')
        ORDER BY (last_seen_at IS NULL), last_seen_at DESC NULLS LAST, created_at ASC
        LIMIT 200`,
      [q]
    );
    const adminSub = await getAdminSub();
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
        online: u.last_seen_at != null && Date.now() - new Date(u.last_seen_at).getTime() < 5 * 60 * 1000,
        // The platform owner — never relabelable from the app.
        locked: ADMIN_EMAILS.includes(String(u.email || "").toLowerCase()) ||
          (!!adminSub && String(u.google_sub || "") === adminSub)
      }))
    });
  } catch (err) {
    res.status(500).json({ error: "Could not load members", detail: String(err.message || err) });
  }
});

/** Admin: set a member's community role — member, mentor, moderator or
 *  admin (a display/permissions label; the real backend admin/owner is
 *  env-gated separately and is never touched by this endpoint — see
 *  isRealAdminAccount). Mentor gets the violet "Mentor" badge everywhere;
 *  admin + moderator additionally get the "MarketScope AI Team" tag on
 *  their posts, marking them as official team messages. */
app.post("/api/admin/members/:id/role", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only the MarketScope AI team can manage roles." });
  }
  const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (!UUID_RE.test(req.params.id)) return res.status(404).json({ error: "Member not found." });
  const role = String((req.body || {}).role || "").toLowerCase();
  if (!["member", "mentor", "moderator", "admin"].includes(role)) {
    return res.status(400).json({ error: "role must be 'member', 'mentor', 'moderator' or 'admin'." });
  }
  try {
    const { rows: targetRows } = await pool.query(
      `SELECT id, email, google_sub FROM users WHERE id = $1`,
      [req.params.id]
    );
    if (!targetRows.length) return res.status(404).json({ error: "Member not found." });
    if (await isRealAdminAccount(targetRows[0])) {
      return res.status(403).json({ error: "This is the platform owner's account and can't be relabeled." });
    }
    const { rows } = await pool.query(
      `UPDATE users SET role = $1 WHERE id = $2
        RETURNING id, name, email, picture, role, community_joined, created_at, last_seen_at`,
      [role, req.params.id]
    );
    const u = rows[0];
    res.json({
      member: {
        id: u.id, name: u.name || u.email || "Member", email: u.email,
        picture: u.picture || null, role: u.role || "member",
        communityJoined: !!u.community_joined, joinedAt: u.created_at,
        lastSeenAt: u.last_seen_at,
        online: u.last_seen_at != null && Date.now() - new Date(u.last_seen_at).getTime() < 5 * 60 * 1000,
        locked: false
      }
    });
  } catch (err) {
    res.status(500).json({ error: "Could not update the role", detail: String(err.message || err) });
  }
});

/** Admin/cron: permanently delete every non-admin account and all the data
 *  attached to it (analyses, payments, trade plans, premium grants, reactions,
 *  votes, views, saves, monitors, push tokens, notifications). The owner/admin
 *  account (ADMIN_EMAIL, or the first account ever created) is never deleted.
 *  Requires an explicit confirm string in the body so it can never fire by
 *  accident. */
app.post("/api/admin/users/purge", async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!((await isAdminRequest(req)) || isCronRequest(req))) {
    return res.status(403).json({ error: "Only the MarketScope AI team can purge accounts." });
  }
  if (String((req.body || {}).confirm || "") !== "purge-all-accounts") {
    return res.status(400).json({ error: "Missing confirm field. Send {\"confirm\": \"purge-all-accounts\"}." });
  }
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const { rows: targets } = await client.query(
      `SELECT id, email FROM users
        WHERE NOT (lower(email) = ANY($1))
          AND google_sub <> COALESCE((
                SELECT google_sub FROM users ORDER BY created_at ASC LIMIT 1
              ), '')`,
      [ADMIN_EMAILS.length ? ADMIN_EMAILS.map((e) => String(e).toLowerCase()) : [""]]
    );
    const ids = targets.map((t) => t.id);
    if (!ids.length) {
      await client.query("COMMIT");
      return res.json({ deleted: 0, accounts: [], kept: ADMIN_EMAILS });
    }
    for (const table of [
      "analyses",
      "subscription_payments",
      "trade_plans",
      "premium_grants",
      "signal_reactions",
      "signal_saves",
      "signal_comment_reactions",
      "post_poll_votes",
      "post_reactions",
      "post_views"
    ]) {
      await client.query(`DELETE FROM ${table} WHERE user_id = ANY($1)`, [ids]);
    }
    // signal_takers / stock_monitors / push_tokens / notifications cascade on
    // user deletion; comments/posts/testimonials fall back to anonymous.
    const { rowCount } = await client.query(`DELETE FROM users WHERE id = ANY($1)`, [ids]);
    await client.query("COMMIT");
    console.log(`[purge] deleted ${rowCount} account(s): ${targets.map((t) => t.email).join(", ")}`);
    res.json({ deleted: rowCount, accounts: targets.map((t) => t.email), kept: ADMIN_EMAILS });
  } catch (err) {
    await client.query("ROLLBACK").catch(() => {});
    res.status(500).json({ error: "Purge failed — nothing was deleted", detail: String(err.message || err) });
  } finally {
    client.release();
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
// Two real plans — Free (what every account has by default) and Premium
// (what a subscription unlocks). Numbers are pulled live from the same
// monetization config that actually enforces them, so this list can never
// drift out of sync with what the app really does.
app.get("/api/subscription/plans", async (_req, res) => {
  const cfg = pool ? await monetization.getMonetizationConfig(pool) : monetization.DEFAULT_CONFIG;
  const freeLimit = cfg.freeDailyAnalysisLimit;
  const rewardBonus = cfg.rewarded?.bonusPerReward || 1;
  res.json({
    currency: SUB_CURRENCY,
    paymentsReady: Boolean(PAYSTACK_SECRET_KEY),
    // How this account can actually pay, so the app can offer exactly what
    // works right now — Google Play Billing (Play-Store-native) and/or
    // Paystack (card/bank/USSD in the browser).
    paymentMethods: {
      paystack: Boolean(PAYSTACK_SECRET_KEY),
      googlePlay: {
        enabled: googlePlayBillingReady,
        productIds: GOOGLE_PLAY_SUBSCRIPTION_IDS
      }
    },
    plans: [
      {
        id: "free",
        name: "Free",
        price: 0,
        period: null,
        features: [
          `${freeLimit} AI chart analyses per day`,
          `Watch a video for +${rewardBonus} extra analysis (up to ${cfg.rewarded?.maxUnlocksPerDay || 3}/day)`,
          "7-day full-access trial for new accounts",
          "Daily AI & team trading signals",
          "Full community access",
          "Forex, crypto & stock analysis"
        ]
      },
      {
        id: "premium",
        name: "Premium",
        price: SUB_PRICE,
        period: "month",
        features: [
          "Unlimited AI chart & market analysis",
          "Analysis runs on GPT-5, our strongest model",
          "Zero ads, anywhere in the app",
          "Full Daily Signals history",
          "Full community access",
          "Everything in Free"
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

// --- Google Play Billing: verify a Play Store subscription purchase and
// activate Premium. The app sends the purchase token straight from the
// Billing Library; the server verifies it against Google's Play Developer
// API with the service account — the client is never the authority, exactly
// like the Paystack webhook path.
app.post("/api/subscription/google-play/verify", requireAuth, async (req, res) => {
  if (!googlePlayBillingReady) {
    return res.status(503).json({
      error: "Google Play subscriptions are being activated right now. You can subscribe with Paystack in the meantime \u2014 thank you for your patience!"
    });
  }
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  const purchaseToken = String((req.body && req.body.purchaseToken) || "").trim();
  const productId = String((req.body && req.body.productId) || "").trim();
  if (!purchaseToken || !productId) {
    return res.status(400).json({ error: "Missing purchase details from Google Play." });
  }
  if (!GOOGLE_PLAY_SUBSCRIPTION_IDS.includes(productId)) {
    return res.status(400).json({ error: "Unknown subscription product." });
  }
  try {
    const { rows: usersRows } = await pool.query(
      `SELECT id, email, name, is_premium FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!usersRows.length) return res.status(404).json({ error: "User not found" });
    const user = usersRows[0];

    // Mint a service-account access token for the Play Developer API.
    const auth = new GoogleAuth({
      credentials: JSON.parse(GOOGLE_PLAY_SERVICE_ACCOUNT_JSON),
      scopes: ["https://www.googleapis.com/auth/androidpublisher"]
    });
    const client = await auth.getClient();
    const { token: accessToken } = await client.getAccessToken();

    // subscriptionsv2 is Google's current purchase-state API.
    const gpRes = await fetch(
      `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(GOOGLE_PLAY_PACKAGE_NAME)}/purchases/subscriptionsv2/${encodeURIComponent(purchaseToken)}`,
      { headers: { Authorization: `Bearer ${accessToken}`, Accept: "application/json" }, signal: AbortSignal.timeout(15_000) }
    );
    const gpBody = await gpRes.json().catch(() => ({}));
    if (!gpRes.ok) {
      const reason = gpBody && gpBody.error && gpBody.error.message ? gpBody.error.message : "Google rejected this purchase verification.";
      return res.status(502).json({ error: "Could not verify this purchase with Google right now. Please try again shortly.", detail: reason });
    }

    const state = gpBody.subscriptionState;
    // Active or in grace = keep premium until the current period's expiry.
    const activeNow = state === "SUBSCRIPTION_STATE_ACTIVE" || state === "SUBSCRIPTION_STATE_IN_GRACE";
    // Latest period's expiry — lineItems are ordered, last line item's
    // expiryTime is the end of the currently paid period.
    let premiumUntil = null;
    const items = Array.isArray(gpBody.lineItems) ? gpBody.lineItems : [];
    for (const item of items) {
      if (item.expiryTime) premiumUntil = new Date(item.expiryTime);
    }

    if (!activeNow || !premiumUntil) {
      // Purchase exists but isn't active (expired, canceled, pending).
      // If this user's premium came from this Google subscription, lapse it
      // honestly; never touch Paystack/admin-granted premium.
      if (user.is_premium) {
        const { rows: cur } = await pool.query(
          `SELECT premium_platform, premium_purchase_token FROM users WHERE id = $1`,
          [user.id]
        );
        if (cur.length && cur[0].premium_platform === "google_play" && cur[0].premium_purchase_token === purchaseToken) {
          await pool.query(
            `UPDATE users SET is_premium = false, premium_expires_at = NULL WHERE id = $1`,
            [user.id]
          );
          notifyUser(user.id, {
            title: "Your Google Play subscription ended",
            body: "Your MarketScope AI Premium subscription is no longer active on Google Play. You can re-subscribe anytime from the Subscribe screen.",
            type: "billing",
            data: { type: "billing", route: "notifications" }
          }).catch(() => {});
        }
      }
      return res.json({ active: false, premiumUntil: null });
    }

    // Verified active subscription — record the payment and activate.
    await pool.query(
      `INSERT INTO subscription_payments (user_id, reference, amount, currency, status, paid_at)
       VALUES ($1, $2, $3, $4, 'success', now())
       ON CONFLICT (reference) DO NOTHING`,
      [user.id, purchaseToken, Math.round(SUB_PRICE * 100), SUB_CURRENCY]
    );
    const wasPremium = Boolean(user.is_premium);
    await pool.query(
      `UPDATE users
         SET is_premium = true,
             premium_platform = 'google_play',
             premium_product_id = $2,
             premium_purchase_token = $3,
             premium_expires_at = $4,
             premium_started_at = COALESCE(premium_started_at, now())
       WHERE id = $1`,
      [user.id, productId, purchaseToken, premiumUntil.toISOString()]
    );
    console.log(`[subscription] Google Play premium activated for google_sub ${req.session.sub} (product ${productId}, until ${premiumUntil.toISOString()})`);
    if (!wasPremium) {
      sendPremiumActivatedEmail({ email: user.email, name: user.name }).catch(() => {});
      notifyUser(user.id, {
        title: "Premium activated \u2014 welcome to MarketScope AI Premium",
        body: "Your Google Play subscription was successful. You now have unlimited AI analysis, the full Daily Signals history and zero ads.",
        type: "billing",
        data: { type: "billing", route: "notifications" }
      }).catch(() => {});
    }
    return res.json({ active: true, premiumUntil: premiumUntil.toISOString() });
  } catch (err) {
    return res.status(502).json({ error: "Could not verify this purchase right now. Please try again shortly.", detail: String(err.message || err) });
  }
});

// --- Subscription: current premium state for the signed-in user ---
app.get("/api/subscription/status", requireAuth, async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const { rows } = await pool.query(
      `SELECT is_premium, premium_expires_at, trial_started_at, trial_expired_email_sent_at FROM users WHERE google_sub = $1`,
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
          data: { type: "signal", route: "signals", symbol: m.symbol }
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
          data: { type: "signal", route: "signals", symbol: m.symbol }
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
        data: { type: "signal", route: "signals", symbol: m.symbol }
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
      `SELECT id, trial_started_at, is_premium, premium_expires_at, premium_platform, premium_purchase_token FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) {
      return res.status(404).json({ error: "User not found" });
    }
    // Google Play subscriptions lapse when their renewal date passes — the
    // moment any request sees an expired one, drop it so the user gets the
    // honest free tier until they renew. (Paystack rows have no expiry and
    // stay sticky as before.)
    if (
      rows[0].is_premium &&
      rows[0].premium_platform === "google_play" &&
      rows[0].premium_expires_at &&
      new Date(rows[0].premium_expires_at).getTime() <= Date.now()
    ) {
      await pool.query(
        `UPDATE users SET is_premium = false, premium_expires_at = NULL WHERE id = $1`,
        [rows[0].id]
      ).catch(() => {});
      rows[0].is_premium = false;
      rows[0].premium_expires_at = null;
    }
    const trial = trialInfo(rows[0]);
    const grant = await getActivePremiumGrant(rows[0].id);
    // Paid subscription is sticky forever once activated (existing behavior);
    // an admin grant covers the rest. The plan label tells the app the truth.
    const plan = paidPremiumActive(rows[0])
      ? "premium"
      : grant ? (grant.duration_type === "lifetime" ? "lifetime" : "premium")
      : trial.trialActive ? "trial"
      : "free";
    const effectivePremium = paidPremiumActive(rows[0]) || !!grant || trial.trialActive;
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
// Builds the "this is who you are analyzing for" text injected into every
// analysis request from the trader's own onboarding questionnaire — the
// system literally gives the user back what they filled in: their capital,
// risk %, target return, style, timeframes and entry criteria shape the
// AI's framing, duration estimates and risk language.
function profilePromptText(q) {
  if (!q || typeof q !== "object") return "";
  const bits = [];
  if (q.experience) bits.push(`experience level: ${q.experience}`);
  if (q.goal) bits.push(`primary goal: ${q.goal}`);
  if (q.capitalUsd) bits.push(`trading capital: $${q.capitalUsd}`);
  if (q.riskPerTrade) bits.push(`risk per trade: ${q.riskPerTrade} of capital`);
  if (q.targetReturn) bits.push(`target monthly return: ${q.targetReturn}`);
  if (Array.isArray(q.assets) && q.assets.length) bits.push(`assets they trade: ${q.assets.join(", ")}`);
  if (q.style) bits.push(`trading style: ${q.style}`);
  if (Array.isArray(q.timeframes) && q.timeframes.length) bits.push(`preferred timeframes: ${q.timeframes.join(", ")}`);
  if (q.entryCriteria) bits.push(`their own entry criteria: ${q.entryCriteria}`);
  if (!bits.length) return "";
  return ` Trader profile from their onboarding questionnaire — this is the person you are advising: ${bits.join("; ")}.`;
}

/**
 * The FULL binding context for every AI analysis this user runs:
 * their questionnaire profile + their most recent AI trade plan, turned into
 * hard rules the model must obey (never soft "tailor to it" advice).
 *
 * Also resolves the analysis mode their declared style maps to — a declared
 * Day Trader / Scalper must only ever receive intraday (scalp-biased)
 * setups, a Swing / Position trader only swing-biased setups. The request's
 * own mode is overridden by this when a style is on file.
 */
async function traderProfileContext(userRow) {
  const q = userRow && userRow.questionnaire && typeof userRow.questionnaire === "object" ? userRow.questionnaire : null;
  const desc = profilePromptText(q);

  // --- Enforced mode from declared style ---
  const style = String((q && q.style) || "").trim().toLowerCase();
  let enforcedMode = null;
  let styleRule = "";
  if (style.includes("day trad") || style === "scalping" || style.includes("scalp")) {
    enforcedMode = "scalp";
    styleRule = `STYLE RULE (binding): this trader declared ${q.style}. Produce ONLY intraday setups: entries on the 15M, targets and stops sized for completion within the trading day, and estimatedDuration of hours, not days/weeks. A multi-day swing setup is a violation of their profile even if technically good — in that case respond NO_TRADE and say the clean setup on the chart doesn't fit their day-trading profile.`;
  } else if (style.includes("swing") || style.includes("position")) {
    enforcedMode = "swing";
    styleRule = `STYLE RULE (binding): this trader declared ${q.style}. Produce ONLY swing/position setups: entries anchored to 4H structure, targets and stops sized for a multi-day-to-multi-week hold. A quick intraday scalp is a violation of their profile even if technically good — in that case respond NO_TRADE and say the intraday move doesn't fit their ${q.style} profile.`;
  }

  // --- Their most recent AI trade plan: their own written rules ---
  let planBlock = "";
  try {
    const { rows } = await pool.query(
      `SELECT name, inputs, content FROM ai_trade_plans WHERE user_id = $1 ORDER BY created_at DESC LIMIT 1`,
      [userRow.id]
    );
    if (rows.length) {
      const p = rows[0];
      const inp = p.inputs && typeof p.inputs === "object" ? p.inputs : {};
      const c = p.content && typeof p.content === "object" ? p.content : {};
      const lines = [];
      lines.push(`their active trade plan "${String(p.name || "Trade plan").slice(0, 60)}"`);
      if (inp.style) lines.push(`plan style: ${inp.style}`);
      if (Array.isArray(inp.timeframes) && inp.timeframes.length) lines.push(`plan timeframes: ${inp.timeframes.join(", ")}`);
      if (inp.riskPerTrade) lines.push(`plan max risk per trade: ${inp.riskPerTrade}`);
      if (inp.rrRatio) lines.push(`plan target risk-to-reward: ${inp.rrRatio}`);
      if (inp.entryCriteria) lines.push(`plan entry criteria: ${String(inp.entryCriteria).slice(0, 300)}`);
      if (inp.avoidConditions) lines.push(`conditions they avoid: ${String(inp.avoidConditions).slice(0, 300)}`);
      if (c.execution) lines.push(`plan execution rules: ${String(c.execution).slice(0, 450)}`);
      if (c.riskRules) lines.push(`plan risk rules: ${String(c.riskRules).slice(0, 450)}`);
      if (Array.isArray(c.checklist) && c.checklist.length) {
        lines.push(`plan pre-trade checklist: ${c.checklist.slice(0, 6).map((i) => String(i).slice(0, 90)).join("; ")}`);
      }
      planBlock = ` Their ACTIVE TRADE PLAN (their own written rules — treat as binding): ${lines.join("; ")}. If the setup you see would violate one of these plan rules (risk per trade, entry criteria, avoided conditions, checklist), respond NO_TRADE and name the exact rule it breaks.`;
    }
  } catch (_e) {
    planBlock = ""; // plan lookup must never break the analysis
  }

  const text = [desc, styleRule, planBlock].filter(Boolean).join(" ").trim();
  return { text, enforcedMode };
}

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
Prices must be plausible for the instrument shown on the charts. Provide a realistic estimated duration based on timeframe and momentum. If the setup is not clean, choose NO_TRADE with a clear thesis.
If a trader profile is provided with the request it is BINDING, not a suggestion: give setups ONLY in their declared style and preferred timeframes, respect their stated risk per trade and capital when framing risk, obey their own entry criteria, and pitch the explanation to their experience level. If the charts offer no setup that fits their profile, respond NO_TRADE and say plainly why it doesn't fit THEIR way of trading — never hand them a different style's setup. If their active trade plan is included, the setup must comply with its risk rules, entry criteria and avoided conditions or it is NO_TRADE with the broken rule named.`;

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
      `SELECT id, trial_started_at, is_premium, premium_expires_at, questionnaire FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) {
      return res.status(404).json({ error: "User not found" });
    }
    userRow = rows[0];
  } catch (err) {
    return res.status(500).json({ error: "Could not verify account", detail: String(err.message || err) });
  }

  const profileCtx = await traderProfileContext(userRow);
  // The user's declared style wins: a Day Trader never gets swing setups
  // (and vice versa), even if the toggle on the form said otherwise.
  const enforcedMode = profileCtx.enforcedMode || mode;
  const modeOverridden = profileCtx.enforcedMode != null && profileCtx.enforcedMode !== mode;
  const traderProfile = profileCtx.text;
  const trial = trialInfo(userRow);
  const premium = paidPremiumActive(userRow) || Boolean(await getActivePremiumGrant(userRow.id));
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
      // 1800 covers the thesis comfortably and fits OpenRouter's remaining
      // credit ceiling (was 2500 — 402'd every time, forcing the slow
      // free-model fallback chain on every single chart analysis).
      max_tokens: 1800,
      reasoning: { effort: "low" },
      response_format: { type: "json_object" },
      messages: [
        { role: "system", content: SYSTEM_PROMPT },
        {
          role: "user",
          content: [
            {
              type: "text",
              text: `Instrument: ${instrument.display}. Mode: ${enforcedMode === "scalp" ? "Scalp (15M-biased)" : "Swing (4H-biased)"}${modeOverridden ? ` (enforced from the trader's declared style — they selected ${mode} but their profile overrides it)` : ""}.` +
                (livePrice != null
                  ? ` Verified current market price of ${instrument.display}: ${livePrice}. Cross-check the chart against this live market — if the chart and the live market contradict each other, say so in the thesis.`
                  : "") +
                (traderProfile || "")
            },
            { type: "image_url", image_url: { url: imageH4 } },
            { type: "image_url", image_url: { url: imageM15 } }
          ]
        }
      ]
    }, "analysis", { premium });

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
      mode: enforcedMode,
      modeOverridden,
      model: premium ? OPENAI_MODEL : ANALYSIS_MODEL,
      livePrice,
      marketVerified: livePrice != null,
      chartValidated: true,
      analysis,
      analyzedAt: new Date().toISOString()
    };

    const { rows } = await pool.query(
      `INSERT INTO analyses (user_id, instrument_id, mode, result) VALUES ($1, $2, $3, $4) RETURNING id`,
      [userRow.id, instrument.id, enforcedMode, JSON.stringify(result)]
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
Hard rules: LONG pairs with recommendation BUY; SHORT with SELL; NO_TRADE with HOLD. All prices must be in the stock's own currency and near its real current price. Ground every claim in the provided data — never invent numbers.
Critical: if the 3-month, 6-month, 1-year or YTD performance is strongly positive (double digits) but you are NOT recommending BUY, the FIRST sentence of your thesis MUST explicitly reconcile that apparent tension — e.g. explain the rally already looks priced in, that short-term momentum has stalled versus the longer-term trend, that you'd want a pullback before entering, or a valuation concern — so a trader skimming the performance numbers immediately understands why you are not chasing an already-strong stock rather than seeing a contradiction.
If a trader profile is provided with the request it is BINDING, not a suggestion: shape the holding-period estimate (estimatedDuration) toward their declared style and preferred timeframes, respect their stated risk per trade and capital when framing position risk, obey their own entry criteria, and pitch the explanation to their experience level. A declared day trader should not be handed a 6-month buy-and-hold — if the profile conflicts with the setup, choose NO_TRADE/HOLD and say the mismatch plainly. If their active trade plan is included, the recommendation must comply with its risk rules and avoided conditions or it is NO_TRADE with the broken rule named.`;

/** Conservative, fully data-backed stock result used only when every AI
 * provider is unavailable or too slow. It never invents fundamentals: the
 * score is derived from the real performance snapshot fetched above. */
function stockMarketFallback(stats) {
  const price = Number(stats.price);
  const values = [stats.perf1W, stats.perf1M, stats.perf3M, stats.perf6M, stats.perf1Y, stats.perfYTD]
    .filter((v) => Number.isFinite(Number(v))).map(Number);
  let score = values.reduce((sum, v) => sum + (v > 2 ? 1 : v < -2 ? -1 : 0), 0);
  if (Number.isFinite(Number(stats.tvRecommendation))) score += Number(stats.tvRecommendation) * 2;
  if (Number.isFinite(Number(stats.rsi))) {
    const rsi = Number(stats.rsi);
    if (rsi > 72) score -= 1;
    else if (rsi < 28) score += 1;
  }
  const recommendation = score >= 3 ? "BUY" : score <= -3 ? "SELL" : "HOLD";
  const direction = recommendation === "BUY" ? "LONG" : recommendation === "SELL" ? "SHORT" : "NO_TRADE";
  const dailyVol = Number.isFinite(Number(stats.dailyVolatilityPct)) ? Number(stats.dailyVolatilityPct) : 2;
  const riskPct = Math.max(0.03, Math.min(0.10, dailyVol * 0.03));
  const round = (n) => Number(n.toFixed(price >= 100 ? 2 : 4));
  const entryZone = { low: round(price * 0.995), high: round(price * 1.005) };
  const stopLoss = round(direction === "SHORT" ? price * (1 + riskPct) : price * (1 - riskPct));
  const takeProfits = direction === "SHORT"
    ? [0.96, 0.92, 0.88].map((m) => round(price * m))
    : [1.04, 1.08, 1.12].map((m) => round(price * m));
  const fmt = (v) => Number.isFinite(Number(v)) ? `${Number(v).toFixed(1)}%` : "unavailable";
  const trend = score >= 3 ? "positive" : score <= -3 ? "negative" : "mixed";
  const confidence = Math.max(52, Math.min(78, Math.round(55 + Math.abs(score) * 4)));
  return {
    direction,
    recommendation,
    confidence,
    entryZone,
    stopLoss,
    takeProfits,
    riskReward: 2,
    estimatedDuration: "Several weeks to 3 months",
    thesis: `The live market snapshot is ${trend}: one-week performance is ${fmt(stats.perf1W)}, one-month is ${fmt(stats.perf1M)}, three-month is ${fmt(stats.perf3M)}, and year-to-date is ${fmt(stats.perfYTD)}. The stock is trading at ${price} within a 52-week range of ${stats.low52w ?? "unavailable"} to ${stats.high52w ?? "unavailable"}. ${recommendation === "BUY" ? "Momentum is sufficiently broad to support a measured long setup." : recommendation === "SELL" ? "Weakness across the measured periods argues against holding a new long position." : "The timeframes are not aligned strongly enough for a high-conviction entry, so waiting is safer."} This conservative result is calculated from the current exchange data while the primary AI provider is temporarily unavailable.`,
    invalidation: recommendation === "BUY"
      ? `A sustained move below ${stopLoss} invalidates the bullish setup.`
      : recommendation === "SELL"
        ? `A sustained move above ${stopLoss} invalidates the bearish setup.`
        : "Reassess when short- and medium-term momentum align clearly.",
    keyLevels: [round(price), stopLoss, ...takeProfits]
  };
}

function ipoMarketFallback(offer) {
  const price = Number(offer.offerPrice);
  const hasPrice = Number.isFinite(price) && price > 0;
  const round = (n) => Number(n.toFixed(price >= 100 ? 2 : 4));
  return {
    direction: "NO_TRADE",
    recommendation: "HOLD",
    confidence: 62,
    entryZone: hasPrice ? { low: price, high: price } : { low: 0, high: 0 },
    stopLoss: hasPrice ? round(price * 0.90) : 0,
    takeProfits: hasPrice ? [1.10, 1.20, 1.30].map((m) => round(price * m)) : [],
    riskReward: 0,
    estimatedDuration: "IPO subscription and post-listing review",
    thesis: `${offer.company} is currently an open NGX public offer, not yet a normally traded stock with an established market-price history. The official offer price is ${hasPrice ? `NGN ${price}` : "not stated"} per share${offer.sharesOffered ? ` for ${offer.sharesOffered}` : ""}${offer.offerSize ? `, with an announced offer size of ${offer.offerSize}` : ""}. ${offer.revenue ? `The official NGX announcement cites ${offer.revenue} in revenue` : "The announcement provides no standardized historical market return data"}${offer.profitAfterTax ? ` and ${offer.profitAfterTax} profit after tax` : ""}. Because there is no post-listing price action, liquidity record, or 52-week range yet, a technical BUY/SELL call would be misleading. Review the prospectus and reassess after the ticker begins trading and reliable market data becomes available.`,
    invalidation: "Reassess this view when NGX publishes the trading ticker and live post-listing price history becomes available.",
    keyLevels: hasPrice ? [price] : []
  };
}

const IPO_SYSTEM_PROMPT = `You are evaluating a newly opened Nigerian Exchange IPO using facts retrieved from an official NGX publication. This is NOT yet an ordinarily traded stock and has no genuine post-listing price history. Never invent a ticker, chart trend, technical indicator, 52-week range or market return. Evaluate the offer terms and disclosed operating figures conservatively. Use BUY only if the supplied official facts justify subscribing at the offer price; otherwise use HOLD/NO_TRADE and explain what prospectus or post-listing evidence is still needed.
Respond with STRICT JSON only (no markdown fences), shape:
{
  "direction": "LONG" | "NO_TRADE",
  "recommendation": "BUY" | "HOLD",
  "confidence": 0-100,
  "entryZone": {"low": number, "high": number},
  "stopLoss": number,
  "takeProfits": [number, number, number],
  "riskReward": number,
  "estimatedDuration": "short human-readable timeframe",
  "thesis": "4-6 factual sentences that clearly state this is an IPO-stage assessment",
  "invalidation": "what would invalidate this view",
  "keyLevels": [number]
}`;

// Resolve the one optional screenshot before batch quota preflight. This
// prevents an image of the same issuer as a typed entry from consuming another
// daily analysis, and lets the final stock analyses run name-only against live
// market data instead of sending the image through the model twice.
app.post("/api/analyze/stock/identify", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!OPENAI_API_KEY && !OPENROUTER_API_KEY) {
    return res.status(503).json({ error: "Analysis engine is not configured yet." });
  }
  const image = req.body?.image;
  const existingNames = Array.isArray(req.body?.existingNames)
    ? req.body.existingNames.map((x) => String(x || "").trim()).filter(Boolean).slice(0, 3)
    : [];
  const isDataUrl = (v) => typeof v === "string" && /^data:image\/(png|jpe?g|webp);base64,/.test(v);
  if (!isDataUrl(image)) return res.status(400).json({ error: "Attach one valid stock screenshot." });

  let premium = false;
  try {
    const { rows } = await pool.query(
      `SELECT id, is_premium, premium_expires_at FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) return res.status(404).json({ error: "User not found" });
    premium = paidPremiumActive(rows[0]) || Boolean(await getActivePremiumGrant(rows[0].id));
  } catch (err) {
    return res.status(500).json({ error: "Could not verify account", detail: String(err.message || err) });
  }

  const prompt = `Validate this screenshot for a stock-analysis batch. It must genuinely relate to one publicly traded company (stock chart, quote page, broker page, or official public offer). Identify the company or ticker. The user already typed these entries: ${JSON.stringify(existingNames)}. Mark duplicate true when the screenshot represents the same issuer as any typed entry, even if one uses a ticker and the other uses a company name. Respond ONLY with JSON: {"isStockRelated": boolean, "companyOrTicker": string, "duplicate": boolean, "reason": string}.`;
  try {
    const ai = await callAI({
      model: ANALYSIS_MODEL,
      max_tokens: 250,
      reasoning: { effort: "low" },
      response_format: { type: "json_object" },
      messages: [{
        role: "user",
        content: [
          { type: "text", text: prompt },
          { type: "image_url", image_url: { url: image, detail: "low" } }
        ]
      }]
    }, "stock-batch-image-identify", { premium });
    if (!ai.ok) {
      return res.status(502).json({ error: "We couldn't read that stock screenshot right now. Please type its company name instead." });
    }
    const body = await ai.response.json();
    const parsed = extractJson(body.choices?.[0]?.message?.content || "");
    const query = String(parsed.companyOrTicker || "").trim();
    if (!parsed.isStockRelated || !query) {
      return res.status(422).json({ error: parsed.reason || "That image does not show an identifiable stock." });
    }
    if (parsed.duplicate) {
      return res.status(409).json({
        error: "The screenshot shows a stock you already entered. Attach a different stock or remove the duplicate image.",
        duplicateStock: true
      });
    }
    return res.json({ query, reason: parsed.reason || "Stock identified" });
  } catch (err) {
    return res.status(502).json({ error: "We couldn't read that stock screenshot. Please type its company name instead.", detail: String(err.message || err) });
  }
});

// Batch-stock quota preflight. Each requested stock produces its own saved
// analysis row, so each one consumes one free daily analysis. Reject the whole
// batch before any model work when the account cannot afford every result.
app.post("/api/analyze/stock/preflight", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const requestedCount = Number(req.body?.count);
  if (!Number.isInteger(requestedCount) || requestedCount < 1 || requestedCount > 4) {
    return res.status(400).json({ error: "Stock analysis count must be between 1 and 4." });
  }
  try {
    const { rows } = await pool.query(
      `SELECT id, trial_started_at, is_premium, premium_expires_at FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) return res.status(404).json({ error: "User not found" });
    const user = rows[0];
    const premium = paidPremiumActive(user) || Boolean(await getActivePremiumGrant(user.id));
    const trial = trialInfo(user);
    if (premium || trial.trialActive) {
      return res.json({ allowed: true, requestedCount, unlimited: true, remaining: null });
    }
    const usage = await analysisUsage(user.id);
    if (usage.remaining < requestedCount) {
      return res.status(429).json({
        error: `Insufficient daily trades. This request needs ${requestedCount} analyses, but you have ${usage.remaining} remaining today. Subscribe for unlimited stock analysis.`,
        insufficientDailyTrades: true,
        dailyLimitReached: true,
        requestedCount,
        ...usage
      });
    }
    return res.json({ allowed: true, requestedCount, unlimited: false, ...usage });
  } catch (err) {
    return res.status(500).json({ error: "Could not verify daily analysis allowance", detail: String(err.message || err) });
  }
});

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
      `SELECT id, trial_started_at, is_premium, premium_expires_at, questionnaire FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) return res.status(404).json({ error: "User not found" });
    userRow = rows[0];
  } catch (err) {
    return res.status(500).json({ error: "Could not verify account", detail: String(err.message || err) });
  }

  const profileCtx = await traderProfileContext(userRow);
  const traderProfile = profileCtx.text;
  const trial = trialInfo(userRow);
  const premium = paidPremiumActive(userRow) || Boolean(await getActivePremiumGrant(userRow.id));
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

  // --- Resolve either a traded stock or a newly opened official NGX IPO. ---
  let stats;
  let match;
  let ipoOffer = null;
  try {
    const matches = await searchStock(resolvedQuery);
    match = bestMatch(matches, resolvedQuery);
    if (match) stats = await fetchStockStats(match.symbol);
  } catch (err) {
    // TradingView can lag a brand-new NGX issue or be briefly unavailable.
    // Continue to the official NGX offer feed before failing the request.
    console.error("[analyze/stock] traded-stock lookup failed; checking NGX IPO feed:", String(err.message || err));
  }
  if (!match || !stats) {
    try {
      ipoOffer = await searchNgxIpo(resolvedQuery);
    } catch (err) {
      console.error("[analyze/stock] NGX IPO lookup failed:", String(err.message || err));
    }
  }
  if (ipoOffer) {
    const ipoKey = String(ipoOffer.slug || resolvedQuery).replace(/[^a-z0-9]+/gi, "-").replace(/^-|-$/g, "").toUpperCase();
    match = {
      symbol: `NGXIPO:${ipoKey}`,
      ticker: null,
      description: ipoOffer.company,
      exchange: ipoOffer.exchange || "NGX",
      currency: ipoOffer.currency || "NGN"
    };
    stats = {
      company: ipoOffer.company,
      ticker: "IPO",
      currency: ipoOffer.currency || "NGN",
      price: ipoOffer.offerPrice,
      changePctToday: null,
      perf1W: null, perf1M: null, perf3M: null, perf6M: null, perf1Y: null, perfYTD: null,
      high52w: null, low52w: null, volume: null, avgVolume10d: null,
      dailyVolatilityPct: null, marketCap: null, rsi: null, tvRecommendation: null,
      peRatio: null, eps: null, sector: null
    };
  }
  if (!match || !stats) {
    return res.status(422).json({
      error: `We couldn't verify an exact listed stock or active NGX public offer for "${resolvedQuery}". Check the official company name or ticker symbol.`,
      stockNotFound: true
    });
  }

  // --- The real analysis, grounded in the live performance snapshot ---
  try {
    const userContent = [
      {
        type: "text",
        text: ipoOffer
          ? `Official NGX IPO: ${ipoOffer.company}. Offer price: ${ipoOffer.offerPrice != null ? `${ipoOffer.currency} ${ipoOffer.offerPrice} per share` : "not stated"}. Shares offered: ${ipoOffer.sharesOffered || "not stated"}. Minimum subscription: ${ipoOffer.minimumSubscription || "not stated"}. Offer size: ${ipoOffer.offerSize || "not stated"}. Subscription opened: ${ipoOffer.openedAt || "not stated"}; closes: ${ipoOffer.closesAt || "not stated"}. Disclosed revenue: ${ipoOffer.revenue || "not stated"}; profit after tax: ${ipoOffer.profitAfterTax || "not stated"}; implied market capitalization: ${ipoOffer.impliedMarketCap || "not stated"}. Official source: ${ipoOffer.sourceUrl}. There is no live post-listing trading history yet. Evaluate the public offer without inventing technical market data.` + (traderProfile || "")
          : `Stock: ${stats.company} (${stats.ticker}), listed on ${match.exchange}. Currency: ${stats.currency || "local"}.` +
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
          `. User query: "${resolvedQuery}". Decide: should a trader BUY this stock now or not, and with what confidence percentage?` +
          (traderProfile || "")
      }
    ];
    // The screenshot was already validated above and, when needed, its
    // company/ticker was extracted into resolvedQuery. Do NOT send the same
    // large image through the final analysis call again: the recommendation
    // is grounded in the real exchange snapshot we just fetched, and a second
    // vision pass only adds latency/cost (and can time out on fallback models).

    // A free fallback model can occasionally stream forever after returning
    // HTTP 200. Cap this stage so the user still gets a real, data-backed
    // result instead of waiting 90 seconds and seeing a generic failure.
    const aiAttempt = callAI({
      model: ANALYSIS_MODEL,
      max_tokens: 1800,
      reasoning: { effort: "low" },
      response_format: { type: "json_object" },
      messages: [
        { role: "system", content: ipoOffer ? IPO_SYSTEM_PROMPT : STOCK_SYSTEM_PROMPT },
        { role: "user", content: userContent }
      ]
    }, "stock-analysis", { premium }).catch((err) => ({
      ok: false, status: 0, detail: String(err.message || err)
    }));
    const orResult = await Promise.race([
      aiAttempt,
      new Promise((resolve) => setTimeout(
        () => resolve({ ok: false, status: 0, detail: "AI analysis timed out after 25 seconds" }),
        25000
      ))
    ]);

    let analysis;
    let usedMarketFallback = false;
    if (orResult.ok) {
      try {
        const data = await orResult.response.json();
        const text = data.choices?.[0]?.message?.content || "";
        analysis = extractJson(text);
      } catch (err) {
        console.error("[analyze/stock] AI response parsing failed — using live-market fallback:", String(err.message || err));
      }
    } else {
      console.error(`[analyze/stock] AI unavailable (${orResult.status || 0}) — using live-market fallback:`, String(orResult.detail || "").slice(0, 300));
    }
    if (!analysis) {
      analysis = ipoOffer ? ipoMarketFallback(ipoOffer) : stockMarketFallback(stats);
      usedMarketFallback = true;
    }

    const result = {
      instrument: ipoOffer ? `${stats.company} (NGX IPO)` : `${stats.company} (${stats.ticker})`,
      instrumentId: match.symbol,
      mode: "stock",
      model: usedMarketFallback ? (ipoOffer ? "official-offer-fallback" : "live-market-fallback") : (premium ? OPENAI_MODEL : ANALYSIS_MODEL),
      livePrice: stats.price,
      marketVerified: true,
      chartValidated: true,
      analysis,
      marketData: {
        price: stats.price, changePctToday: stats.changePctToday,
        perf1W: stats.perf1W, perf1M: stats.perf1M, perf3M: stats.perf3M,
        perf6M: stats.perf6M, perf1Y: stats.perf1Y, perfYTD: stats.perfYTD,
        high52w: stats.high52w, low52w: stats.low52w, currency: stats.currency,
        exchange: match.exchange, volume: stats.volume, avgVolume10d: stats.avgVolume10d,
        dailyVolatilityPct: stats.dailyVolatilityPct, marketCap: stats.marketCap,
        rsi: stats.rsi, peRatio: stats.peRatio, eps: stats.eps, sector: stats.sector,
        tvRecommendation: stats.tvRecommendation,
        isIpo: Boolean(ipoOffer),
        ipo: ipoOffer ? {
          offerPrice: ipoOffer.offerPrice,
          sharesOffered: ipoOffer.sharesOffered,
          minimumSubscription: ipoOffer.minimumSubscription,
          openedAt: ipoOffer.openedAt,
          closesAt: ipoOffer.closesAt,
          offerSize: ipoOffer.offerSize,
          revenue: ipoOffer.revenue,
          profitAfterTax: ipoOffer.profitAfterTax,
          impliedMarketCap: ipoOffer.impliedMarketCap,
          sourceTitle: ipoOffer.sourceTitle,
          sourceUrl: ipoOffer.sourceUrl
        } : null
      },
      analyzedAt: new Date().toISOString()
    };

    // --- Auto-enroll monitoring: when the AI says BUY, it keeps watching the
    // stock for this trader and pushes updates (keep vs sell) as it moves. ---
    if (!ipoOffer && analysis && analysis.recommendation === "BUY" &&
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
// AI Trade Plan — a personalized trading playbook generated from a
// 5-step questionnaire (profile, strategy, risk rules, psychology,
// review) plus the trader's REAL data: their onboarding questionnaire
// profile and their actual usage stats. Capped at MAX_AI_TRADE_PLANS
// saved plans per user so plans stay meaningful rather than piling up.
// ============================================================
const MAX_AI_TRADE_PLANS = 3;

function buildTradePlanMessages(inputs, traderProfileText, statsText) {
  const {
    name, experience, goal, capital, assets, style, timeframes,
    entryCriteria, riskPerTrade, rrRatio, avoidConditions,
    emotions, losingPlan, idealRoutine, notes
  } = inputs;

  const system = `You are a veteran trading coach writing a personalized trading playbook for one specific trader. You write in plain, direct language — no hype, no guaranteed-return language, no financial promises. You are blunt when their stated habits are risky (e.g. if their risk per trade is high, or their emotional challenges suggest revenge trading, say so plainly and give a concrete fix). Ground every section in the specific details you were given — never write generic filler that could apply to anyone.

Respond with ONLY a JSON object (no markdown fences) with these exact keys, each a string except checklist which is an array of short strings:
{
  "snapshot": "2-3 sentences summarizing who this trader is and what this plan is built to fix or reinforce, referencing their actual stated experience, goal and capital.",
  "execution": "A concrete execution framework: how they should identify and enter trades given their style, timeframes and entry criteria. 3-5 sentences or short paragraphs.",
  "riskRules": "Specific, numeric risk rules built from their stated risk-per-trade and risk-to-reward ratio, plus what to do about their stated avoid-conditions. Call out if their numbers look risky.",
  "mindset": "Address their specific stated emotional challenges and losing-streak plan directly — give one concrete technique for each challenge they named, not generic advice.",
  "checklist": ["4 to 6 short, specific pre-market checklist items this exact trader should run through before taking a trade, derived from everything above"],
  "bottomLine": "1-2 sentences: the single most important thing this trader should hold onto, tied to their stated goal."
}`;

  const user = `Build this trader's personalized trade plan named "${name}".

Profile: experience level ${experience || "not specified"}; main goal: ${goal || "not specified"}; trading capital: $${capital || "not specified"}.
Strategy: trades ${Array.isArray(assets) && assets.length ? assets.join(", ") : "unspecified assets"}; style: ${style || "not specified"}; timeframes: ${Array.isArray(timeframes) && timeframes.length ? timeframes.join(", ") : "not specified"}. Their own entry criteria, in their words: "${entryCriteria || "not specified"}".
Risk: max risk per trade ${riskPerTrade ? riskPerTrade + "%" : "not specified"}; target risk-to-reward ratio ${rrRatio || "not specified"}; conditions they say they should avoid trading in: "${avoidConditions || "not specified"}".
Psychology: emotional challenges they admit to: "${emotions || "not specified"}"; their current plan for handling a losing streak: "${losingPlan || "not specified"}"; their ideal daily routine: "${idealRoutine || "not specified"}".
${notes ? `Additional notes from the trader: "${notes}".` : ""}
${traderProfileText || ""}
${statsText || ""}

Write the plan now as the JSON object described.`;

  return [{ role: "system", content: system }, { role: "user", content: user }];
}

app.get("/api/ai-trade-plans", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const { rows } = await pool.query(
      `SELECT p.id, p.name, p.inputs, p.created_at
       FROM ai_trade_plans p
       JOIN users u ON u.id = p.user_id
       WHERE u.google_sub = $1
       ORDER BY p.created_at DESC LIMIT ${MAX_AI_TRADE_PLANS}`,
      [req.session.sub]
    );
    res.json({
      plans: rows.map((r) => ({
        id: r.id,
        name: r.name,
        experience: r.inputs?.experience || null,
        goal: r.inputs?.goal || null,
        createdAt: r.created_at
      })),
      maxPlans: MAX_AI_TRADE_PLANS
    });
  } catch (err) {
    res.status(500).json({ error: "Could not load trade plans", detail: String(err.message || err) });
  }
});

app.get("/api/ai-trade-plans/:id", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (!UUID_RE.test(req.params.id)) {
    return res.status(404).json({ error: "Trade plan not found" });
  }
  try {
    const { rows } = await pool.query(
      `SELECT p.id, p.name, p.inputs, p.content, p.created_at
       FROM ai_trade_plans p
       JOIN users u ON u.id = p.user_id
       WHERE p.id = $1 AND u.google_sub = $2`,
      [req.params.id, req.session.sub]
    );
    if (!rows.length) return res.status(404).json({ error: "Trade plan not found" });
    const r = rows[0];
    res.json({ id: r.id, name: r.name, inputs: r.inputs, content: r.content, createdAt: r.created_at });
  } catch (err) {
    res.status(500).json({ error: "Could not load trade plan", detail: String(err.message || err) });
  }
});

app.post("/api/ai-trade-plans/generate", requireAuth, async (req, res) => {
  if (!OPENAI_API_KEY && !OPENROUTER_API_KEY) {
    return res.status(503).json({
      error: "Plan generation is not configured yet. Add OPENAI_API_KEY or OPENROUTER_API_KEY."
    });
  }
  if (!pool) return res.status(503).json({ error: "Database is not configured." });

  const body = req.body || {};
  const name = String(body.name || "").trim().slice(0, 60);
  const experience = String(body.experience || "").trim().slice(0, 40);
  const goal = String(body.goal || "").trim().slice(0, 60);
  const capital = body.capital != null ? Number(body.capital) : null;
  const assets = Array.isArray(body.assets) ? body.assets.map(String).slice(0, 8) : [];
  const style = String(body.style || "").trim().slice(0, 40);
  const timeframes = Array.isArray(body.timeframes) ? body.timeframes.map(String).slice(0, 3) : [];
  const entryCriteria = String(body.entryCriteria || "").trim().slice(0, 800);
  const riskPerTrade = body.riskPerTrade != null ? Number(body.riskPerTrade) : null;
  const rrRatio = String(body.rrRatio || "").trim().slice(0, 20);
  const avoidConditions = String(body.avoidConditions || "").trim().slice(0, 800);
  const emotions = String(body.emotions || "").trim().slice(0, 400);
  const losingPlan = String(body.losingPlan || "").trim().slice(0, 800);
  const idealRoutine = String(body.idealRoutine || "").trim().slice(0, 800);
  const notes = String(body.notes || "").trim().slice(0, 800);

  const missing = [];
  if (!name) missing.push("Plan name");
  if (!experience) missing.push("Experience Level");
  if (!goal) missing.push("Main Trading Goal");
  if (!capital || capital <= 0) missing.push("Trading Capital");
  if (!assets.length) missing.push("Assets Traded");
  if (!style) missing.push("Trading Style");
  if (!timeframes.length) missing.push("Preferred Timeframe(s)");
  if (!entryCriteria) missing.push("Entry Criteria");
  if (!riskPerTrade || riskPerTrade <= 0) missing.push("Max Risk Per Trade");
  if (!rrRatio) missing.push("Risk-to-Reward Ratio");
  if (missing.length) {
    return res.status(400).json({ error: `Please complete the "${missing[0]}" field.`, missing });
  }

  let userRow;
  try {
    const { rows } = await pool.query(
      `SELECT id, is_premium, premium_expires_at, questionnaire,
              (SELECT COUNT(*)::int FROM ai_trade_plans WHERE user_id = u.id) AS plan_count,
              (SELECT COUNT(*)::int FROM analyses WHERE user_id = u.id) AS analyses_count
       FROM users u WHERE u.google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) return res.status(404).json({ error: "User not found" });
    userRow = rows[0];
  } catch (err) {
    return res.status(500).json({ error: "Could not load account", detail: String(err.message || err) });
  }

  if (userRow.plan_count >= MAX_AI_TRADE_PLANS) {
    return res.status(409).json({
      error: `You already have ${MAX_AI_TRADE_PLANS} trade plans saved. Delete one before creating a new plan.`,
      limitReached: true,
      maxPlans: MAX_AI_TRADE_PLANS
    });
  }

  const isPremium = paidPremiumActive(userRow);
  const traderProfileText = profilePromptText(userRow.questionnaire);
  const statsText = userRow.analyses_count > 0
    ? ` Real usage on record: they have run ${userRow.analyses_count} AI chart ${userRow.analyses_count === 1 ? "analysis" : "analyses"} in MarketScope AI so far — if their stated habits above seem to contradict a disciplined amount of analysis, or if very few analyses suggest they're still building the habit, mention it briefly.`
    : ` Real usage on record: they have not yet run an AI chart analysis in MarketScope AI — gently note that reviewing a real chart analysis would sharpen this plan.`;

  const messages = buildTradePlanMessages(
    { name, experience, goal, capital, assets, style, timeframes, entryCriteria, riskPerTrade, rrRatio, avoidConditions, emotions, losingPlan, idealRoutine, notes },
    traderProfileText,
    statsText
  );

  const payload = {
    model: ANALYSIS_MODEL,
    messages,
    max_tokens: 1600,
    response_format: { type: "json_object" },
    reasoning: { effort: "low" }
  };

  let content;
  try {
    const result = await callAI(payload, "trade-plan", { premium: isPremium });
    if (!result.ok) {
      return res.status(502).json({ error: "The plan generator is unavailable right now. Try again shortly." });
    }
    const data = await result.response.json();
    const text = data.choices?.[0]?.message?.content || "";
    const parsed = extractJson(text);
    content = {
      snapshot: String(parsed.snapshot || "").slice(0, 1200),
      execution: String(parsed.execution || "").slice(0, 1600),
      riskRules: String(parsed.riskRules || "").slice(0, 1600),
      mindset: String(parsed.mindset || "").slice(0, 1600),
      checklist: Array.isArray(parsed.checklist) ? parsed.checklist.map(String).slice(0, 8) : [],
      bottomLine: String(parsed.bottomLine || "").slice(0, 500)
    };
  } catch (err) {
    console.error("[ai-trade-plans/generate] AI/parse error:", String(err.message || err));
    return res.status(502).json({ error: "Something went wrong while creating your plan. Try again." });
  }

  const inputs = { name, experience, goal, capital, assets, style, timeframes, entryCriteria, riskPerTrade, rrRatio, avoidConditions, emotions, losingPlan, idealRoutine, notes };

  try {
    const { rows } = await pool.query(
      `INSERT INTO ai_trade_plans (user_id, name, inputs, content)
       VALUES ($1, $2, $3, $4) RETURNING id, created_at`,
      [userRow.id, name, JSON.stringify(inputs), JSON.stringify(content)]
    );
    res.status(201).json({ id: rows[0].id, name, inputs, content, createdAt: rows[0].created_at });
  } catch (err) {
    res.status(500).json({ error: "Could not save your trade plan", detail: String(err.message || err) });
  }
});

app.delete("/api/ai-trade-plans/:id", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (!UUID_RE.test(req.params.id)) {
    return res.status(404).json({ error: "Trade plan not found" });
  }
  try {
    const { rowCount } = await pool.query(
      `DELETE FROM ai_trade_plans p
       USING users u
       WHERE p.id = $1 AND p.user_id = u.id AND u.google_sub = $2`,
      [req.params.id, req.session.sub]
    );
    if (!rowCount) return res.status(404).json({ error: "Trade plan not found" });
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

/**
 * Resolves the platform owner. When ADMIN_EMAIL is configured it is the sole
 * source of owner authority; the oldest-account fallback exists only for a
 * brand-new deployment where no owner email has been configured yet.
 */
async function getAdminSub() {
  if (ADMIN_EMAILS.length) {
    const { rows } = await pool.query(
      `SELECT google_sub, email FROM users
       WHERE lower(email) = ANY($1::text[])
       ORDER BY array_position($1::text[], lower(email)) NULLS LAST
       LIMIT 1`,
      [ADMIN_EMAILS]
    );
    return rows.length ? String(rows[0].google_sub) : null;
  }
  const { rows } = await pool.query(
    `SELECT google_sub, email FROM users ORDER BY created_at ASC LIMIT 1`
  );
  return rows.length ? String(rows[0].google_sub) : null;
}

async function isAdminRequest(req) {
  if (!pool) return false;
  const email = String((req.session && req.session.email) || "").toLowerCase();
  // Once an explicit owner email exists, no role label, Premium grant,
  // subscription, or oldest-account fallback can confer backend admin rights.
  if (ADMIN_EMAILS.length) return ADMIN_EMAILS.includes(email);
  const adminSub = await getAdminSub();
  return !!adminSub && String((req.session && req.session.sub) || "") === adminSub;
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
    tpsHit: Array.isArray(r.tps_hit) ? r.tps_hit.map(Number).filter(Number.isFinite) : [],
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
      `SELECT id, role, trial_started_at, is_premium, premium_expires_at FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) return res.status(404).json({ error: "User not found" });
    const trial = trialInfo(rows[0]);
    const isAdmin = await isAdminRequest(req);
    const communityRole = rows[0].role || "member";
    const canCompose = isAdmin || canComposeCommunity(communityRole);
    // Reposting a win into Community is a narrower right than composing —
    // Premium/lifetime grants never factor in, only the real owner or an
    // explicitly assigned admin/mentor community role.
    const canRepost = isAdmin || canRepostCommunity(communityRole);
    const grant = await getActivePremiumGrant(rows[0].id);
    const paid = paidPremiumActive(rows[0]);
    const entitled = trial.trialActive || paid || isAdmin || !!grant;
    res.json({ isAdmin, entitled, trialActive: trial.trialActive, trialDaysRemaining: trial.trialDaysRemaining, isPremium: paid || !!grant, plan: paid ? "premium" : grant ? (grant.duration_type === "lifetime" ? "lifetime" : "premium") : (trial.trialActive ? "trial" : "free"), communityRole, canCompose, canRepost });
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

    // Alternating focus days: even UTC dates cover FX/metals/indices, odd
    // UTC dates cover crypto — forex one day, crypto the next, forever.
    // Calendar-based (not last-signal-based) so a failed day never breaks
    // the rhythm; both the cron (06:00 UTC) and manual admin triggers
    // resolve the same category for the same date.
    const FOREX_DAY_CANDIDATES = ["eurusd", "gbpusd", "usdjpy", "xauusd", "nas100"];
    const CRYPTO_DAY_CANDIDATES = ["btcusd", "ethusd", "solusd", "bnbusd", "xrpusd", "dogeusd"];
    const signalCategory = new Date().getUTCDate() % 2 === 0 ? "forex" : "crypto";
    const candidates = signalCategory === "crypto" ? CRYPTO_DAY_CANDIDATES : FOREX_DAY_CANDIDATES;
    console.log(`[daily-signal] category today: ${signalCategory} — candidates: ${candidates.join(", ")}`);
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
        model: ANALYSIS_MODEL,
        // 1600 is plenty for the signal JSON (~500 tokens used) and stays
        // under the OpenRouter "can only afford ~2000" credit floor.
        max_tokens: 1600,
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
      // Parse defensively INSIDE the attempt loop: a model that answers
      // without valid JSON used to throw out of the whole run (one bad
      // response killed the day's signal). Now it counts as a failed
      // attempt and the loop retries with the error fed back.
      let signal = null;
      try {
        const data = await aiResult.response.json();
        signal = extractJson(data.choices?.[0]?.message?.content || "");
      } catch (parseErr) {
        lastErrors = [`model did not return valid JSON (${String(parseErr.message || parseErr).slice(0, 120)})`];
        continue;
      }
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
    res.status(201).json({ ...signalToApi(rows[0]), signalCategory });
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
  // Every TP level actually traded through since publication (drives the
  // in-app tick list and the one-time "TP n hit" push notifications).
  const tpsHit = [];
  const tpHit = (tp) => {
    if (!tpsHit.some((t) => Math.abs(t - tp) < 1e-9)) tpsHit.push(tp);
  };
  for (const c of candles) {
    const ms = c.t * 1000;
    if (ms < startMs) continue;
    if (Number.isFinite(c.c)) lastClose = c.c;
    if (triggeredAt == null && (isLong ? c.h >= sig.entry : c.l <= sig.entry)) {
      triggeredAt = new Date(ms).toISOString();
    }
    for (const tp of tps) {
      if (isLong ? c.h >= tp : c.l <= tp) tpHit(tp);
    }
    const hitSl = isLong ? c.l <= sig.stop_loss : c.h >= sig.stop_loss;
    const hitFinalTp = finalTp != null && (isLong ? c.h >= finalTp : c.l <= finalTp);
    if (hitSl) {
      return {
        outcome: "invalidated_sl", exitPrice: sig.stop_loss,
        closedAt: new Date(ms).toISOString(),
        triggeredAt: triggeredAt || new Date(ms).toISOString(), lastClose, tpsHit
      };
    }
    if (hitFinalTp) {
      tpHit(finalTp);
      return {
        outcome: "successful", exitPrice: finalTp,
        closedAt: new Date(ms).toISOString(),
        triggeredAt: triggeredAt || new Date(ms).toISOString(), lastClose, tpsHit
      };
    }
  }
  return { outcome: null, exitPrice: null, closedAt: null, triggeredAt, lastClose, tpsHit };
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

      // --- TP-hit tracking: which targets has price actually traded through?
      const tpsAll = Array.isArray(r.take_profits) ? r.take_profits.map(Number).filter(Number.isFinite) : [];
      const prevHit = Array.isArray(r.tps_hit) ? r.tps_hit.map(Number).filter(Number.isFinite) : [];
      let tpsHit = [];
      if (candleResult && Array.isArray(candleResult.tpsHit)) tpsHit = candleResult.tpsHit.slice();
      if (price == null) {
        // spot-price fallback below will re-decide outcome; seed hit TPs from spot
      } else if (!tpsHit.length) {
        // candles returned but no TP recorded — keep prev hits (feed hiccup, not a miss)
        tpsHit = prevHit.slice();
      }
      const isLongTp = r.direction === "long";

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
        // Spot fallback: judge TP touches from the last known price too.
        for (const tp of tpsAll) {
          if (isLongTp ? price >= tp : price <= tp) {
            if (!tpsHit.some((t) => Math.abs(t - tp) < 1e-9)) tpsHit.push(tp);
          }
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
      // One-time push + in-app notification per newly hit target.
      const newHits = tpsAll.filter((tp) => {
        const already = prevHit.some((t) => Math.abs(t - tp) < 1e-9);
        const hitNow = tpsHit.some((t) => Math.abs(t - tp) < 1e-9);
        return hitNow && !already;
      });
      for (const tp of newHits) {
        const idx = tpsAll.indexOf(tp);
        const tpLabel = idx === 0 ? "Initial TP" : `TP ${idx + 1}`;
        await broadcastTpHit(r, tpLabel, tp, tpsHit, tpsAll).catch((e) =>
          console.error("broadcastTpHit failed:", String(e.message || e))
        );
      }
      if (newHits.length) tpsHit = Array.from(new Set([...prevHit, ...newHits]));
      await pool.query(
        `UPDATE daily_signals
         SET last_price = $1, last_price_at = now(), status = $2, outcome = $3,
             triggered_at = COALESCE(triggered_at, $4),
             closed_at = COALESCE(closed_at, $5),
             exit_price = COALESCE(exit_price, $6),
             resolved_by = COALESCE(resolved_by, $7),
             tps_hit = $9
         WHERE id = $8`,
        [
          price, status, outcome || r.outcome,
          triggeredAt || (outcome === "triggered_active" ? new Date().toISOString() : null),
          status === "closed" ? (closedAt || new Date().toISOString()) : null,
          exitPrice,
          status === "closed" ? "auto" : null,
          r.id,
          JSON.stringify(tpsHit)
        ]
      );
      results.push({
        id: r.id, instrument: r.instrument_display, price, status,
        outcome: outcome || r.outcome,
        resolvedBy: status === "closed" ? "auto" : null,
        newTpsHit: newHits
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

/** The signed-in user's bookmarked daily signals (the Signals tab save button)
 *  — the Saved screen fetches this so a saved signal actually shows up there. */
app.get("/api/daily-signals/saved", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found." });
    const { rows } = await pool.query(
      `SELECT d.*, sv.created_at AS saved_at
       FROM signal_saves sv
       JOIN daily_signals d ON d.id = sv.signal_id
       WHERE sv.user_id = $1::uuid
       ORDER BY sv.created_at DESC
       LIMIT 100`,
      [me.id]
    );
    const ids = rows.map((r) => r.id);
    const extras = await signalSocialExtras(ids, me.id);
    res.json({ signals: rows.map((r) => signalToApi(r, { ...(extras[r.id] || {}), saved: true })) });
  } catch (err) {
    return res.status(500).json({ error: "Could not load saved signals", detail: String(err.message || err) });
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
              NULLIF((SELECT CASE WHEN u.avatar_key IS NOT NULL THEN 'avatar:' || u.id ELSE NULL END FROM users u
                      WHERE lower(u.email) = lower(c.author_email) LIMIT 1), '') AS author_picture,
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
        authorPicture: (await pool.query(
          `SELECT CASE WHEN avatar_key IS NOT NULL THEN 'avatar:' || id ELSE picture END AS p FROM users WHERE id = $1`,
          [me.id]
        )).rows[0]?.p || "",
        authorRole: me.role || "user",
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
              EXISTS(SELECT 1 FROM community_posts cp WHERE cp.repost_of_testimonial_id = t.id) AS is_reposted,
              (CASE WHEN u.avatar_key IS NOT NULL THEN 'avatar:' || u.id ELSE NULL END) AS avatar_url, u.role
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
        isReposted: !!r.is_reposted,
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
    if (s.status !== "closed" || !["successful", "invalidated_sl"].includes(s.outcome)) {
      return res.status(422).json({ error: "You can only share a TP or SL result from a settled signal." });
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
      const contentType = m ? m[1] : "image/jpeg";
      // R2 first (bytes in object storage, DB keeps only the key);
      // base64-in-Postgres remains the fallback when R2 is not configured.
      let r2Key = null;
      if (r2.isR2Configured()) {
        try {
          r2Key = await r2.uploadImage(Buffer.from(imageDataUrl.split(",")[1] || "", "base64"), contentType, "wins");
        } catch (err) {
          console.error("[r2] win-proof upload failed, storing in DB:", String(err.message || err));
        }
      }
      await pool.query(
        `INSERT INTO signal_testimonial_images (testimonial_id, content_type, data_base64, r2_key) VALUES ($1::uuid, $2, $3, $4)`,
        [t.id, contentType, r2Key ? "" : (imageDataUrl.split(",")[1] || ""), r2Key]
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
      `SELECT t.user_id, t.status, i.content_type, i.data_base64, i.r2_key
       FROM signal_testimonials t JOIN signal_testimonial_images i ON i.testimonial_id = t.id
       WHERE t.id = $1::uuid`,
      [req.params.testimonialId]
    );
    if (!rows.length) return res.status(404).json({ error: "Image not found" });
    const row = rows[0];
    if (row.status !== "approved" && !admin && !(me && row.user_id === me.id)) {
      return res.status(403).json({ error: "This image is awaiting review." });
    }
    // R2-stored image: sign a short-lived URL and redirect. Access checks
    // above already ran, so only this caller gets a working URL.
    if (row.r2_key && r2.isR2Configured()) {
      try {
        return res.redirect(302, await r2.signedImageUrl(row.r2_key));
      } catch (err) {
        console.error("[r2] win-proof sign failed:", String(err.message || err));
      }
    }
    const buf = Buffer.from(row.data_base64 || "", "base64");
    if (!buf.length) return res.status(500).json({ error: "Image data unavailable." });
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
              EXISTS(SELECT 1 FROM community_posts cp WHERE cp.repost_of_testimonial_id = t.id) AS is_reposted,
              COALESCE(u.is_premium OR EXISTS (
                SELECT 1 FROM premium_grants g
                WHERE g.user_id = u.id AND g.revoked_at IS NULL
                  AND (g.expires_at IS NULL OR g.expires_at > now())
              ), false) AS author_is_premium,
              (CASE WHEN u.avatar_key IS NOT NULL THEN 'avatar:' || u.id ELSE NULL END) AS avatar_url
       FROM signal_testimonials t
       JOIN daily_signals s ON s.id = t.signal_id
       LEFT JOIN users u ON u.id = t.user_id
       WHERE t.status = 'approved' AND s.outcome = 'successful'
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
        authorIsPremium: r.author_is_premium || false,
        avatarUrl: r.avatar_url || null,
        hasImage: r.has_image,
        isReposted: !!r.is_reposted,
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
              EXISTS(SELECT 1 FROM community_posts cp WHERE cp.repost_of_testimonial_id = t.id) AS is_reposted,
              COALESCE(u.is_premium OR EXISTS (
                SELECT 1 FROM premium_grants g
                WHERE g.user_id = u.id AND g.revoked_at IS NULL
                  AND (g.expires_at IS NULL OR g.expires_at > now())
              ), false) AS author_is_premium,
              (CASE WHEN u.avatar_key IS NOT NULL THEN 'avatar:' || u.id ELSE NULL END) AS avatar_url
       FROM signal_testimonials t
       JOIN daily_signals s ON s.id = t.signal_id
       LEFT JOIN users u ON u.id = t.user_id
       WHERE t.status = 'approved' AND s.outcome = 'successful'
       ORDER BY t.created_at DESC
       LIMIT $1 OFFSET $2`,
      [limit, offset]
    );
    const { rows: statRows } = await pool.query(
      `SELECT COUNT(*)::int AS total,
              COUNT(*) FILTER (WHERE t.created_at >= now() - interval '7 days')::int AS this_week,
              COUNT(DISTINCT t.user_id)::int AS traders
       FROM signal_testimonials t JOIN daily_signals s ON s.id = t.signal_id WHERE t.status = 'approved' AND s.outcome = 'successful'`
    );
    const st = statRows[0] || { total: 0, this_week: 0, traders: 0 };
    res.json({
      wins: rows.map((r) => ({
        id: r.id,
        signalId: r.signal_id,
        comment: r.comment,
        createdAt: r.created_at,
        authorName: r.author_name,
        authorIsPremium: r.author_is_premium || false,
        avatarUrl: r.avatar_url || null,
        hasImage: r.has_image,
        isReposted: !!r.is_reposted,
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
                NULLIF((SELECT CASE WHEN u.avatar_key IS NOT NULL THEN 'avatar:' || u.id ELSE NULL END FROM users u
                        WHERE lower(u.email) = lower(s.author_email) LIMIT 1), ''),
                NULLIF((SELECT CASE WHEN u2.avatar_key IS NOT NULL THEN 'avatar:' || u2.id ELSE NULL END FROM users u2
                        ORDER BY u2.created_at ASC LIMIT 1), '')
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
    // Never the raw Google photo — same rule as everywhere else: an uploaded
    // MarketScope AI avatar or nothing (client shows the default icon).
    const me = await pool.query(`SELECT id, avatar_key FROM users WHERE google_sub = $1`, [req.session.sub]);
    const authorPicture = (me.rows[0] && me.rows[0].avatar_key) ? `avatar:${me.rows[0].id}` : "";
    res.status(201).json({ update: { id: u.id, authorName: u.author_name, authorPicture, body: u.body, createdAt: u.created_at, replies: [] } });
  } catch (err) {
    res.status(500).json({ error: "Could not post the update", detail: String(err.message || err) });
  }
});

/** A single daily signal by id — powers the full-screen signal detail view
 *  (tapping "View Details" or the card itself opens this, instead of the old
 *  inline expand). Same shape + social extras as the feed list. */
app.get("/api/daily-signals/:id", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!SIGNAL_UUID_RE.test(req.params.id)) return res.status(404).json({ error: "Signal not found" });
  try {
    const me = await currentUser(req);
    const { rows } = await pool.query(`SELECT * FROM daily_signals WHERE id = $1::uuid`, [req.params.id]);
    if (!rows.length) return res.status(404).json({ error: "Signal not found" });
    const extras = await signalSocialExtras([rows[0].id], me ? me.id : null);
    res.json({ signal: signalToApi(rows[0], extras[rows[0].id]) });
  } catch (err) {
    return res.status(500).json({ error: "Could not load signal", detail: String(err.message || err) });
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

// ===========================================================================
// Link previews — the "paste a link, get a preview card" behavior for
// community posts. The SERVER scrapes OpenGraph tags (never the app, so
// users' devices never probe arbitrary hosts), caches per URL, and hard
// blocks private/loopback addresses (SSRF guard).
// ===========================================================================

const LINK_PREVIEW_MAX_BYTES = 512 * 1024; // half an MB of HTML is plenty
const LINK_PREVIEW_TTL_MS = 7 * 86400000; // success cache: 7 days
const LINK_PREVIEW_FAIL_TTL_MS = 86400000; // failure cache: 1 day

const URL_IN_TEXT_RE = /https?:\/\/[A-Za-z0-9\-._~:/?#@!$&'()*+,;=%]+/g;

function firstUrlInText(text) {
  const m = String(text || "").match(URL_IN_TEXT_RE);
  return m ? m[0] : null;
}

function isPrivateIPv4(ip) {
  const p = ip.split(".").map(Number);
  if (p.length !== 4 || p.some((n) => Number.isNaN(n))) return true; // be safe
  if (p[0] === 127 || p[0] === 10 || p[0] === 0) return true;
  if (p[0] === 172 && p[1] >= 16 && p[1] <= 31) return true;
  if (p[0] === 192 && p[1] === 168) return true;
  if (p[0] === 169 && p[1] === 254) return true;
  if (p[0] >= 224) return true; // multicast + reserved
  return false;
}

function isPrivateIPv6(ip) {
  const low = ip.toLowerCase();
  if (low === "::1" || low === "::" ) return true;
  if (low.startsWith("fe80") || low.startsWith("fc") || low.startsWith("fd")) return true;
  // IPv4-mapped / NAT64 style embedded addresses
  const v4mapped = low.match(/::ffff:(\d+\.\d+\.\d+\.\d+)$/);
  if (v4mapped) return isPrivateIPv4(v4mapped[1]);
  if (/^64:ff9b::/.test(low)) return true;
  return false;
}

async function assertPublicHost(hostname) {
  if (!hostname) throw new Error("No hostname");
  if (hostname === "localhost" || hostname.endsWith(".local") || hostname.endsWith(".internal")) {
    throw new Error("Private host");
  }
  const dns = require("node:dns").promises;
  const results = await dns.lookup(hostname, { all: true, verbatim: true });
  if (!results.length) throw new Error("Host did not resolve");
  for (const r of results) {
    if (r.family === 6 ? isPrivateIPv6(r.address) : isPrivateIPv4(r.address)) {
      throw new Error("Private host");
    }
  }
}

function decodeHtmlEntities(s) {
  return String(s || "")
    .replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"').replace(/&#0?39;|&apos;/g, "'")
    .replace(/&#x27;/gi, "'").replace(/&#x2F;/gi, "/")
    .replace(/\s+/g, " ")
    .trim();
}

function extractMeta(html) {
  const pick = (prop) => {
    // order: property=, name=, with/without quotes — all common shapes
    const res = [
      new RegExp(`<meta[^>]+(?:property|name)=["\']${prop}["\'][^>]+content=["\']([^"\']+)["\']`, "i"),
      new RegExp(`<meta[^>]+content=["\']([^"\']+)["\'][^>]+(?:property|name)=["\']${prop}["\']`, "i")
    ];
    for (const re of res) {
      const m = html.match(re);
      if (m && m[1]) return decodeHtmlEntities(m[1]);
    }
    return null;
  };
  let title = pick("og:title") || pick("twitter:title");
  if (!title) {
    const m = html.match(/<title[^>]*>([^<]*)<\/title>/i);
    if (m) title = decodeHtmlEntities(m[1]);
  }
  let description = pick("og:description") || pick("description") || pick("twitter:description");
  let image = pick("og:image") || pick("og:image:url") || pick("twitter:image");
  return { title, description, image };
}

async function scrapeLinkPreview(rawUrl) {
  let u;
  try { u = new URL(rawUrl); } catch { throw new Error("Not a valid URL"); }
  if (u.protocol !== "https:" && u.protocol !== "http:") throw new Error("Only http(s) links");
  if (u.username || u.password) throw new Error("Credentials in URL are not allowed");
  await assertPublicHost(u.hostname);

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 5000);
  let html = "";
  try {
    const resp = await fetch(u.toString(), {
      signal: controller.signal,
      redirect: "follow",
      headers: { "user-agent": "MarketScopeAI-LinkPreview/1.0", accept: "text/html,*/*" }
    });
    const ctype = String(resp.headers.get("content-type") || "");
    if (!ctype.includes("text/html")) throw new Error("Not an HTML page");
    const reader = resp.body && typeof resp.body.getReader === "function" ? resp.body.getReader() : null;
    if (!reader) throw new Error("No body");
    const dec = new TextDecoder();
    let total = 0;
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      html += dec.decode(value, { stream: true });
      if (total > LINK_PREVIEW_MAX_BYTES) {
        await reader.cancel().catch(() => {});
        break; // enough for the <head> in practice; og tags sit up top
      }
    }
  } finally {
    clearTimeout(timer);
  }
  if (!html) throw new Error("Empty response");

  const meta = extractMeta(html);
  if (!meta.title && !meta.image && !meta.description) throw new Error("No preview data found");

  // Resolve the og:image against the page URL (sites love relative paths).
  if (meta.image) {
    try { meta.image = new URL(meta.image, u.toString()).toString(); } catch { meta.image = null; }
  }
  return {
    url: u.toString(),
    title: (meta.title || "").slice(0, 140) || null,
    description: (meta.description || "").slice(0, 280) || null,
    image: meta.image,
    domain: u.hostname.replace(/^www\./, "")
  };
}

/** Cached lookup + scrape. Never throws — failures are cached and returned as null. */
async function linkPreviewFor(rawUrl) {
  if (!pool) return null;
  const url = String(rawUrl || "").slice(0, 2000);
  try {
    const { rows } = await pool.query(
      `SELECT title, description, image, domain, fetched_at, failed_at FROM link_previews WHERE url = $1`,
      [url]
    );
    if (rows.length) {
      const r = rows[0];
      if (r.failed_at && Date.now() - new Date(r.failed_at).getTime() < LINK_PREVIEW_FAIL_TTL_MS) return null;
      if (r.fetched_at && Date.now() - new Date(r.fetched_at).getTime() < LINK_PREVIEW_TTL_MS) {
        if (r.title || r.image || r.description) {
          return { url, title: r.title, description: r.description, image: r.image, domain: r.domain };
        }
        return null;
      }
    }
  } catch { /* cache read failure is non-fatal */ }

  try {
    const preview = await scrapeLinkPreview(url);
    await pool.query(
      `INSERT INTO link_previews (url, title, description, image, domain, fetched_at)
       VALUES ($1, $2, $3, $4, $5, now())
       ON CONFLICT (url) DO UPDATE SET title = $2, description = $3, image = $4, domain = $5, fetched_at = now(), failed_at = NULL`,
      [preview.url, preview.title, preview.description, preview.image, preview.domain]
    );
    return preview;
  } catch (err) {
    await pool.query(
      `INSERT INTO link_previews (url, failed_at) VALUES ($1, now())
       ON CONFLICT (url) DO UPDATE SET failed_at = now()`,
      [url]
    ).catch(() => {});
    return null;
  }
}

// GET /api/link-preview?url=... — app fetches a card for any pasted link
// (composer live preview, older posts published before previews existed).
app.get("/api/link-preview", requireAuth, async (req, res) => {
  try {
    const url = firstUrlInText(String(req.query.url || ""));
    if (!url) return res.status(400).json({ error: "Provide a http(s) link." });
    const preview = await linkPreviewFor(url);
    if (!preview) return res.json({ preview: null });
    return res.json({ preview });
  } catch (err) {
    return res.status(500).json({ error: "Could not build a preview for that link." });
  }
});

function postToApi(row, reactions, commentCount, poll, myVote, isTopContributor, viewCount, lastSeenAt) {
  let linkPreview = null;
  if (row.link_preview && typeof row.link_preview === "object") {
    const lp = row.link_preview;
    if (lp.title || lp.image || lp.description) {
      linkPreview = {
        url: lp.url || null,
        title: lp.title || null,
        description: lp.description || null,
        image: lp.image || null,
        domain: lp.domain || null
      };
    }
  }
  // "New since your last visit" — the feed marks unread messages, and
  // the act of reading the feed then clears the bar (below).
  let isNew = false;
  if (lastSeenAt && row.created_at && new Date(row.created_at) > new Date(lastSeenAt)) {
    isNew = true;
  }
  return {
    linkPreview,
    isNew,
    id: row.id,
    authorName: row.author_name,
    authorUsername: row.author_username || null,
    authorEmail: row.author_email,
    authorPicture: row.author_picture || "",
    authorRole: row.author_role || "user",
    authorIsPremium: row.author_is_premium || false,
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
    isRepost: row.is_repost === true,
    repostedByName: row.reposted_by_name || null,
    selfTag: row.self_tag || null,
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
    `SELECT e.email AS email, e.author_name AS name,
            (SELECT COUNT(*)::int FROM community_posts p WHERE p.author_email = e.email AND p.created_at >= $1 AND p.created_at < $2) * 5 +
            (SELECT COUNT(*)::int FROM post_comments c WHERE c.author_email = e.email AND c.created_at >= $1 AND c.created_at < $2) * 2 +
            (SELECT COUNT(*)::int FROM post_reactions r JOIN community_posts p ON p.id = r.post_id WHERE r.created_at >= $1 AND r.created_at < $2 AND p.author_email = e.email) +
            (SELECT COUNT(*)::int FROM post_reactions r WHERE r.created_at >= $1 AND r.created_at < $2 AND r.user_id IN (SELECT id FROM users WHERE email = e.email)) +
            (SELECT COUNT(*)::int FROM post_poll_votes v WHERE v.created_at >= $1 AND v.created_at < $2 AND v.user_id IN (SELECT id FROM users WHERE email = e.email)) AS score,
            (SELECT COUNT(*)::int FROM community_posts p WHERE p.author_email = e.email AND p.created_at >= $1 AND p.created_at < $2) AS posts,
            (SELECT COUNT(*)::int FROM post_comments c WHERE c.author_email = e.email AND c.created_at >= $1 AND c.created_at < $2) AS comments,
            (SELECT COUNT(*)::int FROM post_reactions r JOIN community_posts p ON p.id = r.post_id WHERE r.created_at >= $1 AND r.created_at < $2 AND p.author_email = e.email) AS reactionsReceived,
            (SELECT COUNT(*)::int FROM post_reactions r WHERE r.created_at >= $1 AND r.created_at < $2 AND r.user_id IN (SELECT id FROM users WHERE email = e.email)) AS reactionsGiven,
            (SELECT COUNT(*)::int FROM post_poll_votes v WHERE v.created_at >= $1 AND v.created_at < $2 AND v.user_id IN (SELECT id FROM users WHERE email = e.email)) AS pollVotes,
            NULLIF((SELECT u.username FROM users u
                    WHERE lower(u.email) = lower(e.email) LIMIT 1), '') AS username,
            COALESCE((
              SELECT (u.is_premium OR EXISTS (
                SELECT 1 FROM premium_grants g
                WHERE g.user_id = u.id AND g.revoked_at IS NULL
                  AND (g.expires_at IS NULL OR g.expires_at > now())
              )) FROM users u WHERE lower(u.email) = lower(e.email) LIMIT 1
            ), false) AS is_premium
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
    // The viewer's last-visit bar: posts newer than this carry isNew, and
    // the visit itself moves the bar forward (page 0 only, so paging an
    // old session doesn't wipe markers for content you haven't reached).
    let lastSeenAt = null;
    if (me) {
      const { rows: seen } = await pool.query(
        `SELECT community_last_seen_at FROM users WHERE id = $1 LIMIT 1`,
        [me.id]
      );
      lastSeenAt = seen.length ? seen[0].community_last_seen_at : null;
    }

    const { rows: posts } = await pool.query(
      `SELECT p.*, (SELECT COUNT(*)::int FROM community_post_images i WHERE i.post_id = p.id) AS image_count,
              (SELECT COUNT(*)::int FROM post_views v WHERE v.post_id = p.id) AS view_count,
              NULLIF((SELECT CASE WHEN u.avatar_key IS NOT NULL THEN 'avatar:' || u.id ELSE NULL END FROM users u
                      WHERE lower(u.email) = lower(p.author_email) LIMIT 1), '') AS author_picture,
              NULLIF((SELECT u.username FROM users u
                      WHERE lower(u.email) = lower(p.author_email) LIMIT 1), '') AS author_username,
              COALESCE((SELECT u.role FROM users u
                      WHERE lower(u.email) = lower(p.author_email) LIMIT 1), 'user') AS author_role,
              COALESCE((
                SELECT (u.is_premium OR EXISTS (
                  SELECT 1 FROM premium_grants g
                  WHERE g.user_id = u.id AND g.revoked_at IS NULL
                    AND (g.expires_at IS NULL OR g.expires_at > now())
                )) FROM users u
                WHERE lower(u.email) = lower(p.author_email) LIMIT 1
              ), false) AS author_is_premium
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

    const payload = {
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
        return postToApi(p, byPost[p.id] || [], cByPost[p.id] || 0, poll, myVoteOption[p.id] || null, isBadge, p.view_count || 0, lastSeenAt);
      }),
      total: total[0].c,
      hasMore: offset + posts.length < total[0].c
    };
    // Reading the feed IS the visit: advance the bar so the next visit only
    // marks what landed after this one. Page 0 only — paging a session back
    // in time must not wipe the markers. Fired without blocking the reply.
    if (me && offset === 0) {
      pool.query(`UPDATE users SET community_last_seen_at = now() WHERE id = $1`, [me.id])
        .catch(() => {});
    }
    return res.json(payload);
  } catch (err) {
    return res.status(500).json({ error: "Could not load the feed", detail: String(err.message || err) });
  }
});

// Create a text post or a poll. Posts by the admin email are flagged as team posts.
app.post("/api/community/posts", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  // Posting is reserved for the MarketScope AI team and mentors. Everyone
  // else can read, react and comment — enforced here, not just in the app.
  const { rows: authorRows } = await pool.query(
    `SELECT role FROM users WHERE google_sub = $1`,
    [req.session.sub]
  );
  const authorRole = authorRows.length ? (authorRows[0].role || "member") : "member";
  const admin = await isAdminRequest(req);
  if (!admin && !canComposeCommunity(authorRole)) {
    return res.status(403).json({ error: "Posting is currently reserved for the MarketScope AI team and mentors. You can still react and comment on posts." });
  }
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
  // A community post is for community discussion only — it can never
  // self-declare a REVIEWED trade outcome. The green "win" tag (outcomeTag)
  // is now only ever set by the server itself, on an admin/mentor repost of
  // a mentor-reviewed win (see POST /api/community/posts/repost). Client
  // input for outcomeTag is ignored. A trader can still casually label
  // their own post as a TP/SL moment via selfTag — that's personal
  // commentary, never mentor-reviewed and never repostable.
  const outcomeTag = null;
  let selfTag = String(req.body.selfTag || "").toLowerCase();
  if (!["tp", "sl"].includes(selfTag) || postType !== "text") selfTag = null;

  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    const linkVerdict = await guardLinks(me, body);
    if (!linkVerdict.allowed) {
      return res.status(422).json({ error: linkVerdict.error, linkBlocked: true });
    }
    // Official team message: real backend admin OR anyone promoted to
    // admin/moderator via the members & roles manager. Mentors get their own
    // violet "Mentor" badge instead (RoleBadge), not the Team tag.
    const isTeam = ADMIN_EMAILS.includes(String((me.email || "")).toLowerCase()) ||
      ["admin", "moderator"].includes(String(me.role || "").toLowerCase());
    const { rows } = await pool.query(
      `INSERT INTO community_posts (user_id, author_name, author_email, body, is_team, post_type, poll_options, allow_comments, outcome_tag, self_tag)
       VALUES ($1, $2, $3, $4, $5, $6, $7::jsonb, $8, $9, $10) RETURNING *`,
      [me.id, me.name || "Trader", me.email || "", body, isTeam, postType, JSON.stringify(pollOptions), allowComments, outcomeTag, selfTag]
    );
    const row = rows[0];
    // The feed query joins these live from `users` (username/role/premium/
    // avatar), but INSERT...RETURNING * on community_posts alone never has
    // them — without this, the response right after posting showed "@null",
    // no Mentor/Admin badge and the default icon until the next feed
    // refetch filled them in. Fetch once here so the immediate response is
    // already correct.
    const { rows: authorProfileRows } = await pool.query(
      `SELECT u.username, u.role,
              (u.is_premium OR EXISTS (
                SELECT 1 FROM premium_grants g
                WHERE g.user_id = u.id AND g.revoked_at IS NULL
                  AND (g.expires_at IS NULL OR g.expires_at > now())
              )) AS is_premium,
              CASE WHEN u.avatar_key IS NOT NULL THEN 'avatar:' || u.id ELSE NULL END AS avatar_url
       FROM users u WHERE u.id = $1`,
      [me.id]
    );
    const authorProfile = authorProfileRows[0] || {};
    row.author_username = authorProfile.username || null;
    row.author_role = authorProfile.role || "user";
    row.author_is_premium = authorProfile.is_premium || false;
    row.author_picture = authorProfile.avatar_url || "";
    // This week's top-5 badge (weekly competition) — same lookup the feed
    // uses, so a badge holder's own fresh post shows the trophy right away.
    const nowForBadge = new Date();
    const { weekStart: thisWeekStartForBadge } = isoWeekBounds(nowForBadge);
    const lastWeekForBadge = isoWeekBounds(new Date(thisWeekStartForBadge.getTime() - 3 * 86400000));
    const badgeRowsForNewPost = (await weeklyScores(lastWeekForBadge.weekStart, lastWeekForBadge.weekEnd))
      .filter((r) => r.score > 0).slice(0, 5);
    const isTopContributorForNewPost = badgeRowsForNewPost.some(
      (r) => (r.email || "").toLowerCase() === (me.email || "").toLowerCase()
    );
    for (let i = 0; i < imageList.length; i++) {
      const dataUrl = String(imageList[i]);
      const m = dataUrl.match(/^data:(image\/(?:png|jpe?g|webp));base64,/);
      const contentType = m ? m[1] : "image/jpeg";
      // R2 first (bytes in object storage, DB keeps only the key);
      // base64-in-Postgres remains the fallback when R2 is not configured.
      let r2Key = null;
      if (r2.isR2Configured()) {
        try {
          r2Key = await r2.uploadImage(Buffer.from(dataUrl.split(",")[1] || "", "base64"), contentType, "posts");
        } catch (err) {
          console.error("[r2] post-image upload failed, storing in DB:", String(err.message || err));
        }
      }
      await pool.query(
        `INSERT INTO community_post_images (post_id, position, content_type, data_base64, r2_key) VALUES ($1::uuid, $2, $3, $4, $5)`,
        [row.id, i, contentType, r2Key ? "" : (dataUrl.split(",")[1] || ""), r2Key]
      );
    }
    const poll = postType === "poll"
      ? { options: row.poll_options, counts: {}, totalVotes: 0 }
      : null;
    // Pasted link -> preview card. Scrape server-side (bounded, cached,
    // non-fatal): the author sees the exact card their readers will see.
    let linkPreview = null;
    const pastedUrl = firstUrlInText(body);
    if (pastedUrl) {
      linkPreview = await Promise.race([
        linkPreviewFor(pastedUrl),
        new Promise((resolve) => setTimeout(() => resolve(null), 4500))
      ]);
      if (linkPreview) {
        await pool.query(
          `UPDATE community_posts SET link_preview = $1::jsonb WHERE id = $2`,
          [JSON.stringify(linkPreview), row.id]
        );
        row.link_preview = linkPreview;
      }
    }
    const post = postToApi({ ...row, image_count: imageList.length }, [], 0, poll, null, isTopContributorForNewPost, 0);
    return res.json({ post });
  } catch (err) {
    return res.status(500).json({ error: "Could not publish the post", detail: String(err.message || err) });
  }
});

/**
 * Admin/mentor repost: takes a mentor-reviewed, approved win testimonial and
 * publishes it into the Community feed as a normal post — displayed under
 * the ORIGINAL trader's name (not the reposter's), tagged with a green win
 * marker, and linked back to the source testimonial so it can only ever be
 * reposted once. Never touches Premium/role — see canRepostCommunity.
 */
app.post("/api/community/posts/repost", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const testimonialId = String((req.body || {}).testimonialId || "");
  const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (!UUID_RE.test(testimonialId)) return res.status(400).json({ error: "A valid testimonialId is required." });

  let createdPostId = null;
  try {
    const { rows: meRows } = await pool.query(
      `SELECT id, name, email, role FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!meRows.length) return res.status(404).json({ error: "User not found" });
    const me = meRows[0];
    const admin = await isAdminRequest(req);
    if (!admin && !canRepostCommunity(me.role)) {
      return res.status(403).json({ error: "Reposting a win is reserved for the MarketScope AI team and mentors." });
    }

    const { rows: tRows } = await pool.query(
      `SELECT t.id, t.user_id, t.author_name, t.author_email, t.comment, t.status,
              s.instrument_display, s.direction, s.exit_price, s.outcome
       FROM signal_testimonials t
       JOIN daily_signals s ON s.id = t.signal_id
       WHERE t.id = $1::uuid`,
      [testimonialId]
    );
    if (!tRows.length) return res.status(404).json({ error: "Win not found." });
    const t = tRows[0];
    if (t.status !== "approved" || t.outcome !== "successful") {
      return res.status(422).json({ error: "Only a reviewed, approved TP win can be reposted." });
    }

    const { rows: existing } = await pool.query(
      `SELECT id FROM community_posts WHERE repost_of_testimonial_id = $1::uuid`,
      [testimonialId]
    );
    if (existing.length) {
      return res.status(409).json({ error: "This win has already been reposted to Community.", alreadyReposted: true });
    }

    const dirLabel = String(t.direction || "").toLowerCase() === "long" ? "LONG" : "SHORT";
    const body = (t.comment && t.comment.trim())
      ? t.comment.trim()
      : `${t.instrument_display || "Signal"} ${dirLabel} — profit target hit at ${t.exit_price ?? "target"}.`;

    const { rows: postRows } = await pool.query(
      `INSERT INTO community_posts
         (user_id, author_name, author_email, body, is_team, post_type, allow_comments, outcome_tag,
          is_repost, repost_of_testimonial_id, reposted_by_name, reposted_by_email)
       VALUES ($1, $2, $3, $4, false, 'text', true, 'win', true, $5::uuid, $6, $7)
       RETURNING *`,
      [t.user_id, t.author_name || "Trader", t.author_email || "", body, testimonialId, me.name || "Team", me.email || ""]
    );
    const row = postRows[0];
    createdPostId = row.id;

    const { rows: imgRows } = await pool.query(
      `SELECT content_type, data_base64, r2_key FROM signal_testimonial_images WHERE testimonial_id = $1::uuid LIMIT 1`,
      [testimonialId]
    );
    let imageCount = 0;
    if (imgRows.length) {
      const img = imgRows[0];
      // Never share an R2 key with the original testimonial: deletion of
      // either record must not break the other record's proof image.
      const repostR2Key = img.r2_key
        ? await r2.copyImage(img.r2_key, img.content_type || "image/jpeg", "posts")
        : null;
      await pool.query(
        `INSERT INTO community_post_images (post_id, position, content_type, data_base64, r2_key) VALUES ($1::uuid, 0, $2, $3, $4)`,
        [row.id, img.content_type || "image/jpeg", img.data_base64 || "", repostR2Key]
      );
      imageCount = 1;
    }

    if (t.user_id && t.user_id !== me.id) {
      notifyUser(t.user_id, {
        title: "Your win was reposted to Community — MarketScope AI",
        body: "The MarketScope AI team reposted your win to the Community feed for everyone to see.",
        type: "community_repost",
        data: { type: "community_repost", route: "community" }
      }).catch(() => {});
    }

    const post = postToApi({ ...row, image_count: imageCount }, [], 0, null, null, false, 0);
    return res.json({ post });
  } catch (err) {
    // On proof-copy failure, never leave a broken "repost" in the feed.
    if (createdPostId) {
      await pool.query(`DELETE FROM community_posts WHERE id = $1::uuid`, [createdPostId]).catch(() => {});
    }
    if (String(err && err.code) === "23505") {
      return res.status(409).json({ error: "This win has already been reposted to Community.", alreadyReposted: true });
    }
    return res.status(500).json({ error: "Could not repost this win", detail: String(err.message || err) });
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

// Remove the caller's own poll vote — tapping the option you already picked
// again clears it (unvote), matching a normal poll's expectations.
app.delete("/api/community/posts/:id/vote", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    const postId = req.params.id;
    const { rows: posts } = await pool.query(
      `SELECT id, post_type FROM community_posts WHERE id = $1::uuid`,
      [postId]
    );
    if (!posts.length || posts[0].post_type !== "poll") {
      return res.status(404).json({ error: "Poll not found" });
    }
    await pool.query(
      `DELETE FROM post_poll_votes WHERE poll_id = $1::uuid AND user_id = $2::uuid`,
      [postId, me.id]
    );
    const { rows: votes } = await pool.query(
      `SELECT option_id, COUNT(*)::int AS c FROM post_poll_votes WHERE poll_id = $1::uuid GROUP BY option_id`,
      [postId]
    );
    const counts = {};
    let totalVotes = 0;
    for (const v of votes) { counts[v.option_id] = v.c; totalVotes += v.c; }
    return res.json({ counts, totalVotes, myVote: null });
  } catch (err) {
    return res.status(500).json({ error: "Could not remove the vote", detail: String(err.message || err) });
  }
});

// Serve a post image (auth required — community is members-only).
app.get("/api/community/posts/:postId/images/:position", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const pos = parseInt(req.params.position) || 0;
    const { rows } = await pool.query(
      `SELECT content_type, data_base64, r2_key FROM community_post_images
       WHERE post_id = $1::uuid AND position = $2 LIMIT 1`,
      [req.params.postId, pos]
    );
    if (!rows.length) return res.status(404).json({ error: "Image not found" });
    // R2-stored image: sign a short-lived URL and redirect (auth already passed).
    if (rows[0].r2_key && r2.isR2Configured()) {
      try {
        return res.redirect(302, await r2.signedImageUrl(rows[0].r2_key));
      } catch (err) {
        console.error("[r2] post-image sign failed:", String(err.message || err));
      }
    }
    const buf = Buffer.from(rows[0].data_base64 || "", "base64");
    if (!buf.length) return res.status(500).json({ error: "Image data unavailable." });
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

// Delete a community post — the author or the MarketScope AI team. Every
// dependent row (comments, reactions, views, poll votes, images) is removed
// with it via ON DELETE CASCADE; R2 image objects are cleaned up best-effort
// so orphaned bytes never linger in the bucket.
app.delete("/api/community/posts/:id", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const { rows: found } = await pool.query(
      `SELECT id, author_email, is_repost, reposted_by_email FROM community_posts WHERE id = $1::uuid`,
      [req.params.id]
    );
    if (!found.length) return res.status(404).json({ error: "Post not found" });
    const me = await currentUser(req);
    const isAdmin = (await isAdminRequest(req)) || ADMIN_EMAILS.includes(String((me.email || "")).toLowerCase());
    const deletableEmail = found[0].is_repost ? found[0].reposted_by_email : found[0].author_email;
    const isAuthor = String(deletableEmail || "").toLowerCase() === String((me.email || "")).toLowerCase();
    if (!isAuthor && !isAdmin) {
      return res.status(403).json({ error: "Only the author or the MarketScope AI team can delete a post." });
    }
    try {
      const { rows: images } = await pool.query(
        `SELECT r2_key FROM community_post_images WHERE post_id = $1::uuid AND r2_key IS NOT NULL`,
        [req.params.id]
      );
      for (const img of images) await r2.deleteObject(img.r2_key);
    } catch (err) {
      console.warn("post delete: R2 image cleanup skipped:", String(err.message || err));
    }
    await pool.query(`DELETE FROM community_posts WHERE id = $1::uuid`, [req.params.id]);
    return res.json({ ok: true, id: req.params.id });
  } catch (err) {
    return res.status(500).json({ error: "Could not delete the post", detail: String(err.message || err) });
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
              NULLIF((SELECT u.username FROM users u WHERE lower(u.email) = lower(p.author_email) LIMIT 1), '') AS author_username,
              (SELECT COUNT(*)::int FROM community_post_images i WHERE i.post_id = p.id) AS image_count,
              (SELECT COUNT(*)::int FROM post_reactions r WHERE r.post_id = p.id AND r.created_at >= $1 AND r.created_at < $2) AS week_reactions,
              COALESCE((
                SELECT (u.is_premium OR EXISTS (
                  SELECT 1 FROM premium_grants g
                  WHERE g.user_id = u.id AND g.revoked_at IS NULL
                    AND (g.expires_at IS NULL OR g.expires_at > now())
                )) FROM users u WHERE lower(u.email) = lower(p.author_email) LIMIT 1
              ), false) AS author_is_premium
       FROM community_posts p
       WHERE p.created_at >= $1 AND p.created_at < $2
         AND EXISTS (SELECT 1 FROM community_post_images i WHERE i.post_id = p.id)
         AND p.outcome_tag = 'win'
       ORDER BY week_reactions DESC, p.created_at DESC LIMIT 5`,
      [weekStart, weekEnd]
    );
    const topProofs = proofRows.map((r) => ({
      postId: r.id, authorName: r.author_name, authorUsername: r.author_username || null, body: r.body,
      outcomeTag: r.outcome_tag || null,
      authorIsPremium: r.author_is_premium || false,
      imageCount: r.image_count, weekReactions: r.week_reactions
    }));
    const proof = topProofs.length
      ? { postId: topProofs[0].postId, authorName: topProofs[0].authorName, authorUsername: topProofs[0].authorUsername, body: topProofs[0].body,
          authorIsPremium: topProofs[0].authorIsPremium,
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
        username: r.username || null,
        email: r.email,
        score: r.score,
        posts: r.posts,
        comments: r.comments,
        reactionsReceived: r.reactionsReceived,
        reactionsGiven: r.reactionsGiven,
        pollVotes: r.pollVotes,
        isPremium: r.is_premium || false
      })),
      myRank,
      myScore,
      lastWeekWinners: lastWeekRows.map((r, i) => ({
        rank: i + 1,
        name: r.name,
        username: r.username || null,
        email: r.email,
        score: r.score,
        isPremium: r.is_premium || false,
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
              NULLIF((SELECT CASE WHEN u.avatar_key IS NOT NULL THEN 'avatar:' || u.id ELSE NULL END FROM users u
                      WHERE lower(u.email) = lower(c.author_email) LIMIT 1), '') AS author_picture,
              NULLIF((SELECT u.username FROM users u
                      WHERE lower(u.email) = lower(c.author_email) LIMIT 1), '') AS author_username,
              COALESCE((SELECT u.role FROM users u
                      WHERE lower(u.email) = lower(c.author_email) LIMIT 1), 'user') AS author_role,
              COALESCE((
                SELECT (u.is_premium OR EXISTS (
                  SELECT 1 FROM premium_grants g
                  WHERE g.user_id = u.id AND g.revoked_at IS NULL
                    AND (g.expires_at IS NULL OR g.expires_at > now())
                )) FROM users u
                WHERE lower(u.email) = lower(c.author_email) LIMIT 1
              ), false) AS author_is_premium
       FROM post_comments c WHERE c.post_id = $1::uuid
       ORDER BY c.created_at ASC LIMIT 300`,
      [req.params.id]
    );
    return res.json({ comments: rows.map((r) => ({ ...r, author_picture: r.author_picture || "", author_username: r.author_username || null, author_role: r.author_role || "user", authorIsPremium: r.author_is_premium || false })) });
  } catch (err) {
    return res.status(500).json({ error: "Could not load comments", detail: String(err.message || err) });
  }
});

// Add a comment (optionally a reply via parent_id).

/* ---------- Direct messages (mentor DMs) ---------- */

/**
 * Private 1:1 threads between a member and a roled author
 * (admin / moderator / mentor). The Message pill on a roled community
 * post opens (or reuses) a thread here; both sides see the thread in
 * their Messages inbox. Tables are created lazily and idempotently on
 * first use, so no deploy coordination is needed.
 */
let dmTablesReady = false;
async function ensureDmTables() {
  if (!pool || dmTablesReady) return;
  await pool.query(`
    CREATE TABLE IF NOT EXISTS dm_threads (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_email TEXT NOT NULL,
      mentor_email TEXT NOT NULL,
      last_message TEXT,
      last_message_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      last_sender TEXT,
      user_unread INTEGER NOT NULL DEFAULT 0,
      mentor_unread INTEGER NOT NULL DEFAULT 0,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    CREATE INDEX IF NOT EXISTS dm_threads_user_idx ON dm_threads (lower(user_email));
    CREATE INDEX IF NOT EXISTS dm_threads_mentor_idx ON dm_threads (lower(mentor_email));
    CREATE TABLE IF NOT EXISTS dm_messages (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      thread_id UUID NOT NULL REFERENCES dm_threads(id) ON DELETE CASCADE,
      sender_email TEXT NOT NULL,
      body TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    CREATE INDEX IF NOT EXISTS dm_messages_thread_idx ON dm_messages (thread_id, created_at);
  `);
  dmTablesReady = true;
}

/** Open (or reuse) a private thread with a roled author. */
app.post("/api/dm/threads", requireAuth, async (req, res) => {
  try {
    await ensureDmTables();
    const me = await currentUser(req);
    if (!me) return res.status(401).json({ error: "Please sign in again." });
    const mentorEmail = String(req.body?.mentorEmail || "").trim().toLowerCase();
    if (!mentorEmail) return res.status(400).json({ error: "Missing recipient." });
    if (mentorEmail === String(me.email || "").toLowerCase()) {
      return res.status(400).json({ error: "You can't message yourself." });
    }
    const { rows: targetRows } = await pool.query(
      `SELECT id, name, email, picture, role FROM users WHERE lower(email) = $1 LIMIT 1`,
      [mentorEmail]
    );
    const target = targetRows[0];
    if (!target) return res.status(404).json({ error: "That member isn't on MarketScope AI yet." });
    const targetRole = String(target.role || "").toLowerCase();
    if (!["admin", "moderator", "mentor"].includes(targetRole)) {
      return res.status(403).json({ error: "Only team members and mentors can be messaged." });
    }
    const myEmail = String(me.email || "").toLowerCase();
    const { rows: existing } = await pool.query(
      `SELECT id FROM dm_threads WHERE lower(user_email) = $1 AND lower(mentor_email) = $2 LIMIT 1`,
      [myEmail, mentorEmail]
    );
    let threadId = existing[0]?.id;
    if (!threadId) {
      const { rows: created } = await pool.query(
        `INSERT INTO dm_threads (user_email, mentor_email) VALUES ($1, $2) RETURNING id`,
        [me.email, target.email]
      );
      threadId = created[0].id;
    }
    return res.json({ thread: { id: threadId } });
  } catch (err) {
    return res.status(500).json({ error: "Could not open the chat", detail: String(err.message || err) });
  }
});

/** My inbox — threads on either side, newest activity first. */
app.get("/api/dm/threads", requireAuth, async (req, res) => {
  try {
    await ensureDmTables();
    const me = await currentUser(req);
    if (!me) return res.status(401).json({ error: "Please sign in again." });
    const email = String(me.email || "").toLowerCase();
    const { rows } = await pool.query(
      `SELECT t.id, t.last_message, t.last_message_at, t.last_sender, t.created_at,
              CASE WHEN lower(t.user_email) = $1 THEN t.mentor_email ELSE t.user_email END AS counterpart_email,
              -- MY OWN unread count, not the counterpart's: when I'm on the
              -- user side, the mentor's message-to-me bumps user_unread, so
              -- THAT is what I should see here — not mentor_unread, which
              -- only counts messages I sent that the mentor hasn't read yet
              -- (that swap was the bug: my own sent messages were showing up
              -- as unread on MY OWN inbox instead of theirs).
              CASE WHEN lower(t.user_email) = $1 THEN t.user_unread ELSE t.mentor_unread END AS unread,
              cu.name AS counterpart_name, cu.role AS counterpart_role,
              CASE WHEN cu.avatar_key IS NOT NULL AND cu.avatar_key <> '' THEN 'avatar:' || cu.id ELSE NULL END AS counterpart_avatar
       FROM dm_threads t
       LEFT JOIN users cu
         ON lower(cu.email) = lower(CASE WHEN lower(t.user_email) = $1 THEN t.mentor_email ELSE t.user_email END)
       WHERE lower(t.user_email) = $1 OR lower(t.mentor_email) = $1
       ORDER BY t.last_message_at DESC LIMIT 100`,
      [email]
    );
    const unreadTotal = rows.reduce((n, r) => n + (r.unread || 0), 0);
    return res.json({ threads: rows, unreadTotal });
  } catch (err) {
    return res.status(500).json({ error: "Could not load your messages", detail: String(err.message || err) });
  }
});

/** One thread + its messages (participants only). */
app.get("/api/dm/threads/:id/messages", requireAuth, async (req, res) => {
  try {
    await ensureDmTables();
    const me = await currentUser(req);
    if (!me) return res.status(401).json({ error: "Please sign in again." });
    const { rows: threadRows } = await pool.query(
      `SELECT * FROM dm_threads WHERE id = $1::uuid LIMIT 1`,
      [req.params.id]
    );
    const thread = threadRows[0];
    if (!thread) return res.status(404).json({ error: "Chat not found." });
    const myEmail = String(me.email || "").toLowerCase();
    const isMember = String(thread.user_email).toLowerCase() === myEmail || String(thread.mentor_email).toLowerCase() === myEmail;
    if (!isMember) return res.status(403).json({ error: "This chat is private." });
    const counterpartEmail = String(thread.user_email).toLowerCase() === myEmail ? thread.mentor_email : thread.user_email;
    const { rows: cuRows } = await pool.query(
      `SELECT id, name, email, role,
              CASE WHEN avatar_key IS NOT NULL AND avatar_key <> '' THEN 'avatar:' || id ELSE picture END AS avatar
       FROM users WHERE lower(email) = lower($1) LIMIT 1`,
      [counterpartEmail]
    );
    const { rows: messages } = await pool.query(
      `SELECT id, sender_email, body, created_at FROM dm_messages
       WHERE thread_id = $1::uuid ORDER BY created_at ASC LIMIT 500`,
      [req.params.id]
    );
    return res.json({
      thread: {
        id: thread.id,
        counterpart: cuRows[0] || { email: counterpartEmail, name: counterpartEmail, role: "user" }
      },
      messages
    });
  } catch (err) {
    return res.status(500).json({ error: "Could not load the chat", detail: String(err.message || err) });
  }
});

/** Send a message in a thread. Notifies the other side (in-app + push). */
app.post("/api/dm/threads/:id/messages", requireAuth, async (req, res) => {
  try {
    await ensureDmTables();
    const me = await currentUser(req);
    if (!me) return res.status(401).json({ error: "Please sign in again." });
    const body = String(req.body?.body || "").trim().slice(0, 4000);
    if (!body) return res.status(400).json({ error: "Message is empty." });
    const { rows: threadRows } = await pool.query(
      `SELECT * FROM dm_threads WHERE id = $1::uuid LIMIT 1`,
      [req.params.id]
    );
    const thread = threadRows[0];
    if (!thread) return res.status(404).json({ error: "Chat not found." });
    const myEmail = String(me.email || "").toLowerCase();
    if (String(thread.user_email).toLowerCase() !== myEmail && String(thread.mentor_email).toLowerCase() !== myEmail) {
      return res.status(403).json({ error: "This chat is private." });
    }
    const isMentorSide = String(thread.mentor_email).toLowerCase() === myEmail;
    const { rows: inserted } = await pool.query(
      `INSERT INTO dm_messages (thread_id, sender_email, body) VALUES ($1::uuid, $2, $3) RETURNING id, sender_email, body, created_at`,
      [req.params.id, me.email, body]
    );
    await pool.query(
      `UPDATE dm_threads
         SET last_message = $2, last_message_at = now(), last_sender = $3,
             ${isMentorSide ? "user_unread" : "mentor_unread"} = ${isMentorSide ? "user_unread" : "mentor_unread"} + 1
       WHERE id = $1::uuid`,
      [req.params.id, body, me.email]
    );
    // Notify the recipient — in-app row + push, never blocking the reply.
    const recipientEmail = (isMentorSide ? thread.user_email : thread.mentor_email).toLowerCase();
    try {
      const { rows: recipients } = await pool.query(
        `SELECT id, name FROM users WHERE lower(email) = $1 LIMIT 1`,
        [recipientEmail]
      );
      const recipient = recipients[0];
      if (recipient) {
        const title = `${me.name || "A MarketScope AI member"} sent you a message`;
        notifyUser(recipient.id, {
          title,
          body: body.slice(0, 120),
          type: "dm",
          data: { route: "dm", threadId: String(thread.id) }
        });
      }
    } catch (_nErr) { /* notification failure never blocks the send */ }
    return res.json({ message: inserted[0] });
  } catch (err) {
    return res.status(500).json({ error: "Could not send the message", detail: String(err.message || err) });
  }
});

/** Mark a thread as read for the caller (clears only MY unread counter). */
app.post("/api/dm/threads/:id/read", requireAuth, async (req, res) => {
  try {
    await ensureDmTables();
    const me = await currentUser(req);
    if (!me) return res.status(401).json({ error: "Please sign in again." });
    const myEmail = String(me.email || "").toLowerCase();
    await pool.query(`UPDATE dm_threads SET user_unread = 0 WHERE id = $1::uuid AND lower(user_email) = $2`, [req.params.id, myEmail]);
    await pool.query(`UPDATE dm_threads SET mentor_unread = 0 WHERE id = $1::uuid AND lower(mentor_email) = $2`, [req.params.id, myEmail]);
    return res.json({ ok: true });
  } catch (err) {
    return res.status(500).json({ error: "Could not mark as read", detail: String(err.message || err) });
  }
});

/* ---------- Bottom-tab new-content badges ---------- */

/**
 * How many new items each bottom tab has waiting for the signed-in user:
 *   signals   — daily signals published since their last visit to the tab
 *   community — posts published since their last visit (own posts excluded)
 * First-time callers get their seen-at stamps initialized to now(), so the
 * badges only ever count content published AFTER the user starts using the
 * app — no artificial backlog on day one.
 */
app.get("/api/tab-activity", requireAuth, async (req, res) => {
  try {
    const me = await currentUser(req);
    if (!me) return res.status(401).json({ error: "Please sign in again." });
    const { rows: meRows } = await pool.query(
      `SELECT id, signals_seen_at, community_seen_at FROM users WHERE id = $1 LIMIT 1`,
      [me.id]
    );
    const meRow = meRows[0];
    if (!meRow) return res.status(404).json({ error: "Account not found." });
    if (!meRow.signals_seen_at || !meRow.community_seen_at) {
      await pool.query(
        `UPDATE users
           SET signals_seen_at   = COALESCE(signals_seen_at, now()),
               community_seen_at = COALESCE(community_seen_at, now())
         WHERE id = $1`,
        [me.id]
      );
      return res.json({ signals: 0, community: 0 });
    }
    const { rows: sigRows } = await pool.query(
      `SELECT count(*)::int AS n FROM daily_signals WHERE published_at > $1`,
      [meRow.signals_seen_at]
    );
    const { rows: postRows } = await pool.query(
      `SELECT count(*)::int AS n FROM community_posts
        WHERE created_at > $1
          AND (user_id IS NULL OR user_id <> $2)`,
      [meRow.community_seen_at, me.id]
    );
    return res.json({ signals: sigRows[0].n, community: postRows[0].n });
  } catch (err) {
    return res.status(500).json({ error: "Could not load tab activity", detail: String(err.message || err) });
  }
});

/** Mark a tab as visited — clears its badge by stamping "seen now". */
app.post("/api/tab-activity/seen", requireAuth, async (req, res) => {
  try {
    const me = await currentUser(req);
    if (!me) return res.status(401).json({ error: "Please sign in again." });
    const tab = String(req.body?.tab || "").toLowerCase();
    if (tab === "signals") {
      await pool.query(`UPDATE users SET signals_seen_at = now() WHERE id = $1`, [me.id]);
    } else if (tab === "community") {
      await pool.query(`UPDATE users SET community_seen_at = now() WHERE id = $1`, [me.id]);
    } else {
      return res.status(400).json({ error: "Unknown tab." });
    }
    return res.json({ ok: true });
  } catch (err) {
    return res.status(500).json({ error: "Could not mark tab as seen", detail: String(err.message || err) });
  }
});

/* ---------- Bug reports (in-app "Report a bug" flow) ---------- */

/**
 * User-submitted bug reports from the app's Settings screen. Attachments are
 * uploaded to R2 under bug-reports/ (bytes never in the DB long-term; the
 * DB keeps only keys). If R2 is unavailable the base64 is kept inline in the
 * attachments JSON so a report is never silently lost.
 */
app.post("/api/bug-reports", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const description = String(req.body.description || "").trim();
  if (description.length < 3) return res.status(400).json({ error: "Describe the problem in a few words first." });
  if (description.length > 2000) return res.status(400).json({ error: "Descriptions are limited to 2000 characters." });

  const imageList = Array.isArray(req.body.images) ? req.body.images.slice(0, 4) : [];
  for (const dataUrl of imageList) {
    if (!/^data:image\/(png|jpe?g|webp);base64,/.test(String(dataUrl))) {
      return res.status(400).json({ error: "Screenshots must be png/jpeg/webp." });
    }
    const b64 = String(dataUrl).split(",")[1] || "";
    if (b64.length > 4_000_000) return res.status(400).json({ error: "Each screenshot must be under 3MB." });
  }
  let video = req.body.video || null;
  if (video != null) {
    if (!/^data:video\/(mp4|webm|3gpp|3gp|quicktime);base64,/.test(String(video))) {
      return res.status(400).json({ error: "Recordings must be mp4/webm/3gp." });
    }
    const b64 = String(video).split(",")[1] || "";
    if (b64.length > 20_000_000) return res.status(400).json({ error: "Keep the recording under 15MB." });
  }

  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found." });

    const attachments = [];
    for (const dataUrl of imageList) {
      attachments.push(await storeBugAttachment(String(dataUrl), "image"));
    }
    if (video) attachments.push(await storeBugAttachment(String(video), "video"));

    const appVersion = String(req.body.appVersion || "").slice(0, 60);
    const deviceModel = String(req.body.deviceModel || "").slice(0, 120);
    const androidVersion = String(req.body.androidVersion || "").slice(0, 40);

    const { rows } = await pool.query(
      `INSERT INTO bug_reports (user_id, user_email, description, attachments, app_version, device_model, android_version)
       VALUES ($1, $2, $3, $4::jsonb, $5, $6, $7) RETURNING id, created_at`,
      [me.id, me.email || "", description, JSON.stringify(attachments), appVersion, deviceModel, androidVersion]
    );

    // Tell the owner a report landed — in-app notification + push (never blocks the response).
    notifyAdminBugReport(me, description).catch(() => {});

    return res.json({ ok: true, id: rows[0].id });
  } catch (err) {
    console.error("bug-report failed:", String(err.message || err));
    return res.status(500).json({ error: "Could not send the report. Try again in a moment." });
  }
});

/** Upload one attachment to R2 (bug-reports/), falling back to inline base64. */
async function storeBugAttachment(dataUrl, kind) {
  const m = dataUrl.match(/^data:([^;]+);base64,(.*)$/s) || [];
  const contentType = m[1] || (kind === "video" ? "video/mp4" : "image/jpeg");
  const b64 = (m[2] || "").replace(/\s+/g, "");
  const buffer = Buffer.from(b64, "base64");
  const entry = { kind, contentType, sizeBytes: buffer.length };
  try {
    entry.r2Key = await r2.uploadImage(buffer, contentType, "bug-reports");
  } catch (err) {
    // R2 down/unconfigured — keep the bytes inline so nothing is lost.
    console.warn("bug-report R2 upload fell back to inline:", String(err.message || err));
    entry.dataBase64 = b64;
  }
  return entry;
}

/** In-app + push notification for the owner account (oldest user = owner). */
async function notifyAdminBugReport(reporter, description) {
  if (!pool) return;
  // The real owner is whoever matches ADMIN_EMAIL(S). The very first account
  // created is NOT reliably the owner (an old test account can be older),
  // so it is only a fallback.
  let rows = [];
  if (ADMIN_EMAILS.length) {
    rows = (await pool.query(
      `SELECT id FROM users WHERE lower(email) = ANY($1::text[])`,
      [ADMIN_EMAILS]
    )).rows;
  }
  if (!rows.length) {
    rows = (await pool.query(`SELECT id FROM users ORDER BY created_at ASC LIMIT 1`)).rows;
  }
  if (!rows.length) return;
  const snippet = description.length > 90 ? description.slice(0, 90) + "\u2026" : description;
  for (const t of rows) {
    await notifyUser(t.id, {
      title: "New bug report",
      body: `${reporter.email || "A user"} reported: ${snippet}`,
      type: "general",
      data: { type: "bug_report", route: "notifications" }
    });
  }
}

/** Admin inbox: the latest bug reports with fresh signed attachment URLs. */
app.get("/api/admin/bug-reports", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) return res.status(403).json({ error: "Admins only." });
  try {
    const { rows } = await pool.query(
      `SELECT id, user_email, description, attachments, app_version, device_model, android_version, status, created_at
       FROM bug_reports ORDER BY created_at DESC LIMIT 100`
    );
    const reports = [];
    for (const r of rows) {
      const attachments = [];
      const raw = Array.isArray(r.attachments) ? r.attachments : [];
      for (const a of raw) {
        const out = { kind: a.kind, contentType: a.contentType, sizeBytes: a.sizeBytes };
        if (a.r2Key) {
          out.url = await r2.signedImageUrl(a.r2Key, 3600).catch(() => null);
        } else if (a.dataBase64) {
          out.dataUrl = `data:${a.contentType};base64,${a.dataBase64}`;
        }
        attachments.push(out);
      }
      reports.push({
        id: r.id, email: r.user_email, description: r.description,
        appVersion: r.app_version, deviceModel: r.device_model, androidVersion: r.android_version,
        status: r.status, createdAt: r.created_at, attachments
      });
    }
    return res.json({ reports });
  } catch (err) {
    console.error("admin bug-reports failed:", String(err.message || err));
    return res.status(500).json({ error: "Could not load bug reports." });
  }
});

/* ---------- Push notifications (FCM v1) ---------- */

/**
 * Insert an in-app notification row for a user (and optionally everyone).
 * Returns nothing; failures never break the caller.
 */
async function addNotification({ userId, type, title, body, data }) {
  if (!pool) return null;
  try {
    const { rows } = await pool.query(
      `INSERT INTO notifications (user_id, type, title, body, data) VALUES ($1, $2, $3, $4, $5) RETURNING id`,
      [userId, type, title, body, data ? JSON.stringify(data) : null]
    );
    return rows[0]?.id || null;
  } catch (err) {
    console.error("addNotification failed:", String(err.message || err));
    return null;
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
  const notificationId = await addNotification({ userId, type, title, body, data });
  // Every push carries its own notification's id so a tap can open the
  // Notifications screen scrolled straight to that exact message, not just
  // the general feed.
  const pushData = notificationId ? { ...(data || {}), notificationId } : data;
  try {
    const { rows: tokens } = await pool.query(
      `SELECT token FROM push_tokens WHERE user_id = $1`,
      [userId]
    );
    for (const t of tokens) {
      const result = await sendFcm(t.token, { title, body, data: pushData });
      await logPush(userId, t.token, result, title).catch(() => {});
      if (result === "invalid") {
        await pool.query(`DELETE FROM push_tokens WHERE token = $1`, [t.token]);
      }
    }
  } catch (err) {
    console.error("notifyUser failed:", String(err.message || err));
  }
}

/**
 * A live signal just traded through one of its targets — tell every user once.
 * Same delivery pipeline as broadcastNewSignal: in-app notification + FCM push,
 * deep-linking to that signal's detail screen.
 */
async function broadcastTpHit(signalRow, tpLabel, tpLevel, tpsHit, tpsAll) {
  if (!pool) return;
  const hitCount = Array.isArray(tpsHit) ? tpsHit.length : 1;
  const totalCount = Array.isArray(tpsAll) ? tpsAll.length : 1;
  const title = `🎯 ${tpLabel} hit — ${signalRow.instrument_display}`;
  const body = `The ${signalRow.direction === "long" ? "long" : "short"} signal took profit at ${round(tpLevel)} (${hitCount}/${totalCount} targets).`;
  const { rows: users } = await pool.query(`SELECT id FROM users`);
  for (const u of users) {
    await notifyUser(u.id, {
      title, body, type: "signal",
      data: { route: `daily_signal/${signalRow.id}`, signalId: String(signalRow.id) }
    });
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


// --- Price alerts ----------------------------------------------------------
// TradingView-style price alerts: a user picks a Watchlist instrument and a
// direction ("rises above" / "falls below"), the server watches the real
// price feeds every few minutes and pushes a notification when it triggers.

const PRICE_ALERT_SYMBOLS = new Set(WATCHLIST.map((w) => w.id));
const PRICE_ALERT_COOLDOWN_MIN = 10; // one reminder per alert per 10 min

function priceAlertToApi(r) {
  return {
    id: r.id,
    symbol: r.symbol,
    display: r.display,
    direction: r.direction,
    targetPrice: r.target_price,
    status: r.status,
    createdAt: r.created_at,
    triggeredAt: r.triggered_at,
    triggeredPrice: r.triggered_price
  };
}

/** Create a price alert for the signed-in user. */
app.post("/api/price-alerts", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const symbol = String(req.body.symbol || "").toLowerCase().trim();
  const direction = String(req.body.direction || "").toLowerCase().trim();
  const targetPrice = Number(req.body.targetPrice);
  if (!PRICE_ALERT_SYMBOLS.has(symbol)) {
    return res.status(400).json({ error: "Unsupported instrument for alerts." });
  }
  if (direction !== "above" && direction !== "below") {
    return res.status(400).json({ error: "Direction must be 'above' or 'below'." });
  }
  if (!Number.isFinite(targetPrice) || targetPrice <= 0) {
    return res.status(400).json({ error: "A valid target price is required." });
  }
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found." });
    const meta = WATCHLIST.find((w) => w.id === symbol);
    // Replace any existing alert for the same instrument+direction.
    await pool.query(
      `DELETE FROM price_alerts WHERE user_id = $1 AND symbol = $2 AND direction = $3`,
      [me.id, symbol, direction]
    );
    const { rows } = await pool.query(
      `INSERT INTO price_alerts (user_id, symbol, display, direction, target_price)
       VALUES ($1, $2, $3, $4, $5) RETURNING *`,
      [me.id, symbol, meta ? meta.display : symbol.toUpperCase(), direction, targetPrice]
    );
    res.json({ alert: priceAlertToApi(rows[0]) });
  } catch (err) {
    res.status(500).json({ error: "Could not create the alert", detail: String(err.message || err) });
  }
});

/** List the signed-in user's alerts (newest first). */
app.get("/api/price-alerts", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found." });
    const { rows } = await pool.query(
      `SELECT * FROM price_alerts WHERE user_id = $1 ORDER BY created_at DESC LIMIT 50`,
      [me.id]
    );
    res.json({ alerts: rows.map(priceAlertToApi) });
  } catch (err) {
    res.status(500).json({ error: "Could not load alerts", detail: String(err.message || err) });
  }
});

/** Delete one of the signed-in user's alerts. */
app.delete("/api/price-alerts/:id", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found." });
    await pool.query(`DELETE FROM price_alerts WHERE id = $1 AND user_id = $2`, [req.params.id, me.id]);
    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ error: "Could not delete the alert", detail: String(err.message || err) });
  }
});

/**
 * Cron pass: check every active price alert against the real feeds and
 * push a notification when it triggers. Triggered alerts auto-complete (one
 * push each — exactly how the reference behaves), but a re-cross after the
 * cooldown window re-fires so long-running moves are not missed silently.
 */
app.post("/api/cron/price-alerts", async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await requireCronOrAdmin(req, res))) return;
  try {
    const { rows: alerts } = await pool.query(
      `SELECT a.*, u.id AS uid FROM price_alerts a JOIN users u ON u.id = a.user_id
       WHERE a.status = 'active' ORDER BY a.created_at ASC LIMIT 500`
    );
    if (alerts.length === 0) return res.json({ checked: 0, triggered: 0 });

    // One price fetch per distinct symbol.
    const symbols = [...new Set(alerts.map((a) => a.symbol))];
    const prices = {};
    await Promise.all(symbols.map(async (s) => {
      try { prices[s] = await fetchPrice(s); } catch (_e) { prices[s] = null; }
    }));

    let triggered = 0;
    for (const a of alerts) {
      const price = prices[a.symbol];
      if (price == null || !Number.isFinite(price)) continue;
      const hit = a.direction === "above" ? price >= a.target_price : price <= a.target_price;
      if (!hit) continue;

      // Rate limit: skip if we pushed for this alert within the cooldown.
      if (a.triggered_at && Date.now() - new Date(a.triggered_at).getTime() < PRICE_ALERT_COOLDOWN_MIN * 60 * 1000) continue;

      const move = a.direction === "above" ? "risen above" : "fallen below";
      const title = `${a.display} ${move} ${Number(a.target_price)}`;
      const body = `${a.display} is now trading at ${Number(price.toFixed(Math.abs(price) >= 1000 ? 0 : 2)).toLocaleString("en-US")} — your alert level is ${Number(a.target_price).toLocaleString("en-US")}.`;
      await notifyUser(a.uid, {
        title,
        body,
        type: "price_alert",
        data: { route: "market/" + a.symbol, alertId: String(a.id) }
      });
      await pool.query(
        `UPDATE price_alerts SET status = 'triggered', triggered_at = now(), triggered_price = $2 WHERE id = $1`,
        [a.id, price]
      );
      triggered++;
    }
    res.json({ checked: alerts.length, triggered });
  } catch (err) {
    res.status(500).json({ error: "Price alert pass failed", detail: String(err.message || err) });
  }
});

app.post("/api/push/register", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const token = String(req.body.token || "").trim();
  if (!token || token.length > 512) return res.status(400).json({ error: "A valid FCM token is required." });
  const platform = ["android", "ios", "web"].includes(req.body.platform) ? req.body.platform : "android";
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    await pool.query(
      `INSERT INTO push_tokens (user_id, token, platform)
       VALUES ($1, $2, $3)
       ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, last_seen_at = now()`,
      [me.id, token, platform]
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
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    await pool.query(`DELETE FROM push_tokens WHERE user_id = $1 AND token = $2`, [me.id, token]);
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
    const isAdmin = await isAdminRequest(req);
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
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    const { rows } = await pool.query(
      `SELECT id, type, title, body, data, created_at, read_at
       FROM notifications WHERE user_id = $1
       ORDER BY created_at DESC LIMIT 50`,
      [me.id]
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
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    await pool.query(
      `UPDATE notifications SET read_at = now() WHERE user_id = $1 AND read_at IS NULL`,
      [me.id]
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
    // Same avatar resolution as the comment list so the author immediately
    // sees their current profile picture, never a stale Google photo.
    rows[0].author_picture = (await pool.query(
      `SELECT CASE WHEN avatar_key IS NOT NULL THEN 'avatar:' || id ELSE picture END AS p FROM users WHERE id = $1`,
      [me.id]
    )).rows[0]?.p || "";
    rows[0].author_username = (await pool.query(
      `SELECT username FROM users WHERE id = $1`,
      [me.id]
    )).rows[0]?.username || null;
    // Same entitlement rule as the feed: paid sub or an active admin grant.
    rows[0].author_is_premium = (await hasActivePremiumGrant(me.id)) ||
      (await pool.query(`SELECT is_premium FROM users WHERE id = $1`, [me.id])).rows[0]?.is_premium || false;
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
