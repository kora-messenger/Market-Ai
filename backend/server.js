/**
 * Market Ai API — Google auth verification, instrument catalog, AI chart analysis.
 * Runs server-side so the app holds zero AI provider keys.
 */
const express = require("express");
const { OAuth2Client } = require("google-auth-library");
const jwt = require("jsonwebtoken");
const { Pool } = require("pg");
const { ALL, byId, categories } = require("./src/instruments");
const { termsOfServiceHtml, privacyPolicyHtml } = require("./src/legalPages");
const { fetchPrice, fetchHistory } = require("./src/prices");

const app = express();
app.use(express.json({ limit: "25mb" }));

const PORT = process.env.PORT || 3000;
const OPENROUTER_API_KEY = process.env.OPENROUTER_API_KEY || "";
const GOOGLE_WEB_CLIENT_ID = process.env.GOOGLE_WEB_CLIENT_ID || "";
const JWT_SECRET = process.env.SESSION_JWT_SECRET || "";
const ANALYSIS_MODEL = process.env.ANALYSIS_MODEL || "google/gemini-3.8-flash";
const ADMIN_EMAIL = (process.env.ADMIN_EMAIL || "").toLowerCase();
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
    ALTER TABLE users ADD COLUMN IF NOT EXISTS is_premium BOOLEAN NOT NULL DEFAULT false;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS community_joined BOOLEAN NOT NULL DEFAULT false;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS community_joined_at TIMESTAMPTZ;
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
    CREATE TABLE IF NOT EXISTS post_reactions (
      post_id UUID REFERENCES community_posts(id) ON DELETE CASCADE,
      user_id UUID NOT NULL,
      emoji TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      PRIMARY KEY (post_id, user_id, emoji)
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
  `);
}

const TRIAL_DAYS = 7;

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
app.get("/health", (_req, res) => {
  res.json({
    ok: true,
    service: "market-ai-api",
    by: "Veltravia Technologies",
    time: new Date().toISOString(),
    config: {
      database: Boolean(pool),
      googleAuth: Boolean(GOOGLE_WEB_CLIENT_ID),
      analysis: Boolean(OPENROUTER_API_KEY)
    }
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
                   trial_started_at, is_premium`,
        [payload.sub, user.email, user.name, user.picture]
      );
      user = {
        id: rows[0].id,
        googleSub: rows[0].google_sub,
        email: rows[0].email,
        name: rows[0].name,
        picture: rows[0].picture,
        communityJoined: rows[0].community_joined,
        communityJoinedAt: rows[0].community_joined_at,
        ...trialInfo(rows[0])
      };
    }

    const sessionToken = JWT_SECRET
      ? jwt.sign({ sub: payload.sub, email: user.email }, JWT_SECRET, { expiresIn: "30d" })
      : null;

    return res.json({ user, sessionToken });
  } catch (err) {
    console.error("[auth/google] verification failed:", err && err.message, err && err.stack);
    return res.status(401).json({
      error: "Google token verification failed",
      reason: err && err.message ? String(err.message) : "unknown"
    });
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

// Public: total real member count (used on the Home screen community card —
// no fabricated numbers, this is a literal COUNT of users who have joined).
app.get("/api/community/stats", async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const { rows } = await pool.query(
      `SELECT COUNT(*)::int AS total FROM users WHERE community_joined = true`
    );
    const posts = await pool.query(`SELECT COUNT(*)::int AS total FROM community_posts`);
    return res.json({
      totalMembers: rows[0]?.total ?? 0,
      totalPosts: posts.rows[0]?.total ?? 0
    });
  } catch (err) {
    return res.status(500).json({ error: "Could not load community stats", detail: String(err.message || err) });
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
app.get("/api/trial/status", requireAuth, async (req, res) => {
  if (!pool) {
    return res.status(503).json({ error: "Database is not configured." });
  }
  try {
    const { rows } = await pool.query(
      `SELECT trial_started_at, is_premium FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!rows.length) {
      return res.status(404).json({ error: "User not found" });
    }
    return res.json(trialInfo(rows[0]));
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
  if (!trial.trialActive) {
    return res.status(402).json({
      error: "Your 7-day free trial has ended. Premium plans are coming soon \u2014 stay tuned!",
      trialExpired: true,
      ...trial
    });
  }

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
                text: `Instrument: ${instrument.display}. Mode: ${mode === "scalp" ? "Scalp (15M-biased)" : "Swing (4H-biased)"}.`
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
// Daily Signals — curated signals published by the Market Ai team
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
  if (ADMIN_EMAIL && String(req.session.email || "").toLowerCase() === ADMIN_EMAIL) return true;
  const adminSub = await getAdminSub();
  return !!adminSub && String(req.session.sub) === adminSub;
}

/** Cron endpoints are callable by the admin or with the CRON_SECRET header. */
function isCronRequest(req) {
  if (!CRON_SECRET) return false;
  const header = req.headers["x-cron-secret"] || "";
  return typeof header === "string" && header.length > 20 && header === CRON_SECRET;
}

function signalToApi(r) {
  return {
    id: r.id,
    author: r.author,
    instrumentId: r.instrument_id,
    instrument: r.instrument_display,
    direction: r.direction,
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
    lastPriceAt: r.last_price_at
  };
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
      `SELECT trial_started_at, is_premium FROM users WHERE google_sub = $1`,
      [req.session.sub]
    );
    if (!userRows.length) return res.status(404).json({ error: "User not found" });
    const trial = trialInfo(userRows[0]);
    const admin = await isAdminRequest(req);
    const entitled = trial.trialActive || userRows[0].is_premium || admin;
    if (!entitled) {
      return res.status(402).json({
        error: "Daily Signals is part of Market Ai Premium. Your free trial has ended.",
        locked: true,
        trialExpired: true
      });
    }
    const limit = Math.min(parseInt(req.query.limit, 10) || 50, 100);
    const { rows } = await pool.query(
      `SELECT * FROM daily_signals ORDER BY published_at DESC LIMIT $1`,
      [limit]
    );
    res.json({ signals: rows.map(signalToApi) });
  } catch (err) {
    res.status(500).json({ error: "Could not load signals", detail: String(err.message || err) });
  }
});

/** Owner publishes a curated signal manually. */
app.post("/api/daily-signals", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  if (!(await isAdminRequest(req))) {
    return res.status(403).json({ error: "Only the Market Ai team can publish daily signals." });
  }
  const { instrumentId, direction, entry, stopLoss, takeProfits, thesis, strength } = req.body || {};
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
  try {
    const rr = tps.length ? Number((Math.abs(tps[tps.length - 1] - entryNum) / Math.abs(entryNum - slNum)).toFixed(2)) : null;
    const { rows } = await pool.query(
      `INSERT INTO daily_signals
         (author, instrument_id, instrument_display, direction, entry, stop_loss, take_profits, risk_reward, thesis, strength, status)
       VALUES ('owner', $1, $2, $3, $4, $5, $6, $7, $8, $9, 'live')
       RETURNING *`,
      [
        instrument.id, instrument.display, String(direction).toLowerCase(),
        entryNum, slNum, JSON.stringify(tps), rr,
        thesis ? String(thesis).slice(0, 2000) : null,
        ["strong", "moderate", "weak"].includes(String(strength).toLowerCase()) ? String(strength).toLowerCase() : "moderate"
      ]
    );
    res.status(201).json(signalToApi(rows[0]));
  } catch (err) {
    res.status(500).json({ error: "Could not publish signal", detail: String(err.message || err) });
  }
});

const DAILY_SIGNAL_SYSTEM_PROMPT = `You are the senior market analyst behind Market Ai's Daily Signals. You receive recent OHLC candles for a set of instruments from live public market data. Pick the single best trade setup among them — one with a clearly-defined invalidation (tight, logical stop) and realistic targets. Respond ONLY with JSON:
{
  "instrumentId": "one of the provided ids",
  "direction": "long" | "short",
  "entry": number,
  "stopLoss": number,
  "takeProfits": [number, number, number],
  "thesis": "2-3 sentences grounded in the price action shown (structure, momentum, key levels). No generic filler.",
  "strength": "strong" | "moderate" | "weak"
}
Rules: entry must sit within a few percent of the latest close; stop loss must be on the wrong side of entry (below for long, above for short); every take profit must be on the profitable side, ordered nearest first; risk:reward to the final target should be at least 1.5. If nothing qualifies, set strength "weak" and pick the least-bad setup anyway — never invent prices outside the data range shown.`;

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
    const { rows } = await pool.query(
      `INSERT INTO daily_signals
         (author, instrument_id, instrument_display, direction, entry, stop_loss, take_profits, risk_reward, thesis, strength, status)
       VALUES ('ai', $1, $2, $3, $4, $5, $6, $7, $8, $9, 'live')
       RETURNING *`,
      [
        inst.id, inst.display, dir, entryNum, slNum, JSON.stringify(tps), rr,
        signal.thesis ? String(signal.thesis).slice(0, 2000) : null,
        ["strong", "moderate", "weak"].includes(String(signal.strength).toLowerCase()) ? String(signal.strength).toLowerCase() : "moderate"
      ]
    );
    res.status(201).json(signalToApi(rows[0]));
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
      const price = await fetchPrice(r.instrument_id);
      if (price == null) {
        results.push({ id: r.id, instrument: r.instrument_display, note: "no public feed — manual close required" });
        continue;
      }
      const tps = Array.isArray(r.take_profits) ? r.take_profits.map(Number).filter(Number.isFinite) : [];
      const finalTp = tps.length ? Math.max(...tps) : null;
      const isLong = r.direction === "long";
      let outcome = null;
      let status = r.status;
      if (isLong ? price <= r.stop_loss : price >= r.stop_loss) {
        outcome = "invalidated_sl";
        status = "closed";
      } else if (finalTp != null && (isLong ? price >= finalTp : price <= finalTp)) {
        outcome = "successful";
        status = "closed";
      } else if (isLong ? price >= r.entry : price <= r.entry) {
        outcome = "triggered_active";
        status = "live";
      }
      const ageMs = Date.now() - new Date(r.published_at).getTime();
      const triggered = r.triggered_at ? true : (outcome === "triggered_active");
      if (status !== "closed" && ageMs > SIGNAL_DAYS) {
        outcome = triggered ? "expired_partial" : "expired";
        status = "closed";
      }
      await pool.query(
        `UPDATE daily_signals
         SET last_price = $1, last_price_at = now(), status = $2, outcome = $3,
             triggered_at = COALESCE(triggered_at, CASE WHEN $4 THEN now() ELSE NULL END),
             closed_at = CASE WHEN $2 = 'closed' THEN COALESCE(closed_at, now()) ELSE closed_at END
         WHERE id = $5`,
        [price, status, outcome, outcome === "triggered_active" || r.triggered_at != null, r.id]
      );
      results.push({ id: r.id, instrument: r.instrument_display, price, status, outcome: outcome || r.outcome });
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
    return res.status(403).json({ error: "Only the Market Ai team can close signals." });
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
      `UPDATE daily_signals SET status = 'closed', outcome = $1, closed_at = now()
       WHERE id = $2 RETURNING *`,
      [outcome, req.params.id]
    );
    if (!rows.length) return res.status(404).json({ error: "Signal not found" });
    res.json(signalToApi(rows[0]));
  } catch (err) {
    res.status(500).json({ error: "Could not close signal", detail: String(err.message || err) });
  }
});


// ---------------------------------------------------------------------------
// Community feed — posts, reactions, threaded comments (all requireAuth).
// ---------------------------------------------------------------------------

const REACTION_EMOJIS = ["\u{1F44D}", "\u{2764}\u{FE0F}", "\u{1F525}", "\u{1F680}", "\u{1F4B0}", "\u{1F4C8}", "\u{1F4C9}", "\u{1F4AF}", "\u{1F44F}", "\u{1F602}", "\u{1F62E}", "\u{1F64F}"];

async function currentUser(req) {
  const { rows } = await pool.query(
    `SELECT id, name, email, picture FROM users WHERE google_sub = $1`,
    [req.session.sub]
  );
  return rows[0] || null;
}

function postToApi(row, reactions, commentCount) {
  return {
    id: row.id,
    authorName: row.author_name,
    authorEmail: row.author_email,
    isTeam: row.is_team,
    body: row.body,
    createdAt: row.created_at,
    commentCount,
    reactions: reactions.map((r) => ({ emoji: r.emoji, count: r.count, mine: r.mine }))
  };
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
      `SELECT * FROM community_posts ORDER BY created_at DESC LIMIT $1 OFFSET $2`,
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

    const byPost = {};
    for (const r of reactions) (byPost[r.post_id] = byPost[r.post_id] || []).push(r);
    const cByPost = {};
    for (const c of comments) cByPost[c.post_id] = c.c;

    return res.json({
      posts: posts.map((p) => postToApi(p, byPost[p.id] || [], cByPost[p.id] || 0)),
      total: total[0].c,
      hasMore: offset + posts.length < total[0].c
    });
  } catch (err) {
    return res.status(500).json({ error: "Could not load the feed", detail: String(err.message || err) });
  }
});

// Create a text post. Posts by the admin email are flagged as team posts.
app.post("/api/community/posts", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const body = String(req.body.body || "").trim();
  if (!body) return res.status(400).json({ error: "Write something first." });
  if (body.length > 2000) return res.status(400).json({ error: "Posts are limited to 2000 characters." });
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    const isTeam = ADMIN_EMAIL && (me.email || "").toLowerCase() === ADMIN_EMAIL;
    const { rows } = await pool.query(
      `INSERT INTO community_posts (user_id, author_name, author_email, body, is_team)
       VALUES ($1, $2, $3, $4, $5) RETURNING *`,
      [me.id, me.name || "Trader", me.email || "", body, isTeam]
    );
    return res.json({ post: postToApi(rows[0], [], 0) });
  } catch (err) {
    return res.status(500).json({ error: "Could not publish the post", detail: String(err.message || err) });
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
      `SELECT id, author_name, author_email, body, parent_id, created_at
       FROM post_comments WHERE post_id = $1::uuid
       ORDER BY created_at ASC LIMIT 300`,
      [req.params.id]
    );
    return res.json({ comments: rows });
  } catch (err) {
    return res.status(500).json({ error: "Could not load comments", detail: String(err.message || err) });
  }
});

// Add a comment (optionally a reply via parent_id).
app.post("/api/community/posts/:id/comments", requireAuth, async (req, res) => {
  if (!pool) return res.status(503).json({ error: "Database is not configured." });
  const body = String(req.body.body || "").trim();
  if (!body) return res.status(400).json({ error: "Write a comment first." });
  if (body.length > 1000) return res.status(400).json({ error: "Comments are limited to 1000 characters." });
  const parentId = req.body.parentId || null;
  try {
    const me = await currentUser(req);
    if (!me) return res.status(404).json({ error: "User not found" });
    const { rows } = await pool.query(
      `INSERT INTO post_comments (post_id, user_id, author_name, author_email, body, parent_id)
       VALUES ($1::uuid, $2, $3, $4, $5, $6)
       RETURNING id, author_name, author_email, body, parent_id, created_at`,
      [req.params.id, me.id, me.name || "Trader", me.email || "", body, parentId]
    );
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
