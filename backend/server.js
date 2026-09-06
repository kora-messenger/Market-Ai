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

const app = express();
app.use(express.json({ limit: "25mb" }));

const PORT = process.env.PORT || 3000;
const OPENROUTER_API_KEY = process.env.OPENROUTER_API_KEY || "";
const GOOGLE_WEB_CLIENT_ID = process.env.GOOGLE_WEB_CLIENT_ID || "";
const JWT_SECRET = process.env.SESSION_JWT_SECRET || "";
const ANALYSIS_MODEL = process.env.ANALYSIS_MODEL || "google/gemini-3.8-flash";

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
  "thesis": "3-5 sentence reasoning grounded in what is visible on the charts",
  "invalidation": "what would invalidate this setup",
  "keyLevels": [number]
}
Prices must be plausible for the instrument shown on the charts. If the setup is not clean, choose NO_TRADE with a clear thesis.`;

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
        max_tokens: 2000,
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
