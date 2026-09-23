"use strict";

// Separate owner-only web console. The consumer APK never receives a console
// token: this cookie is scoped to /api/owner, HttpOnly, Secure and SameSite.
const crypto = require("crypto");
const path = require("path");
const jwt = require("jsonwebtoken");

const COOKIE = "market_owner";
const AUDIENCE = "marketscope-owner-console";
const SESSION_SECONDS = 8 * 3600;
const DEFAULT_ORIGIN = "https://market-ai-api-jwfb.onrender.com";

function digest(email, code, secret) {
  return crypto.createHmac("sha256", secret)
    .update(`owner-login:${email}:${code}`).digest("hex");
}

function parseCookie(header, name) {
  const item = String(header || "").split(";").map(s => s.trim())
    .find(s => s.startsWith(`${name}=`));
  return item ? item.slice(name.length + 1) : null;
}

function installOwnerConsole(app, { pool, jwtSecret, adminEmails, r2, sendCode, origin = DEFAULT_ORIGIN }) {
  const root = path.join(__dirname, "..", "public", "owner");
  const ownerEmails = new Set(adminEmails.map(e => String(e).toLowerCase()));
  const bursts = new Map();
  const noStore = (_req, res, next) => {
    res.set("Cache-Control", "no-store, private");
    res.set("Referrer-Policy", "no-referrer");
    res.set("X-Content-Type-Options", "nosniff");
    res.set("X-Robots-Tag", "noindex, nofollow");
    next();
  };
  const sameOriginPost = (req, res, next) => {
    if (req.get("Origin") !== origin || !req.is("application/json")) {
      return res.status(403).json({ error: "Request origin is not allowed." });
    }
    next();
  };
  const secureCookie = (value, maxAge) =>
    `${COOKIE}=${value}; HttpOnly; Secure; SameSite=Strict; Path=/api/owner; Max-Age=${maxAge}`;
  const requireOwner = async (req, res, next) => {
    if (!pool || !jwtSecret || ownerEmails.size === 0) return res.status(503).json({ error: "Owner console is unavailable." });
    try {
      const token = parseCookie(req.headers.cookie, COOKIE);
      if (!token) return res.status(401).json({ error: "Sign in to view the owner console." });
      const identity = jwt.verify(token, jwtSecret, { audience: AUDIENCE });
      if (identity.kind !== "owner-console" || !ownerEmails.has(String(identity.email).toLowerCase())) {
        return res.status(403).json({ error: "Owner access only." });
      }
      // Recheck the configured email and Google account on every request,
      // so account removal or a changed owner email immediately denies access.
      const { rows } = await pool.query(
        `SELECT id FROM users WHERE google_sub = $1 AND lower(email) = $2 LIMIT 1`,
        [identity.sub, String(identity.email).toLowerCase()]
      );
      if (!rows.length) return res.status(403).json({ error: "Owner access only." });
      req.ownerEmail = String(identity.email).toLowerCase();
      next();
    } catch (_err) {
      res.status(401).json({ error: "Session expired. Sign in again." });
    }
  };

  app.get("/owner", noStore, (_req, res) => {
    res.set("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' https: data:; media-src 'self' https: data:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
    res.type("html").sendFile(path.join(root, "index.html"));
  });
  app.get("/owner/console.js", noStore, (_req, res) => res.type("application/javascript").sendFile(path.join(root, "console.js")));
  app.get("/owner/console.css", noStore, (_req, res) => res.type("text/css").sendFile(path.join(root, "console.css")));
  app.use("/api/owner", noStore);

  app.post("/api/owner/request-code", sameOriginPost, async (req, res) => {
    if (!pool || !jwtSecret || ownerEmails.size === 0) return res.status(503).json({ error: "Owner console is unavailable." });
    const email = String(req.body?.email || "").trim().toLowerCase();
    if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return res.status(400).json({ error: "Enter a valid email address." });
    }
    // Bound abusive requests without trusting a client-supplied IP header.
    const ip = req.ip || "unknown";
    const now = Date.now();
    for (const [key, state] of bursts) if (now - state.start > 900000) bursts.delete(key);
    const state = bursts.get(ip);
    if (state && now - state.start < 900000 && state.count >= 30) {
      return res.status(429).json({ error: "Too many attempts. Try again later." });
    }
    bursts.set(ip, { start: state && now - state.start < 900000 ? state.start : now,
      count: state && now - state.start < 900000 ? state.count + 1 : 1 });
    const generic = { ok: true, message: "If this is an owner account, check its email for a sign-in code." };
    if (!ownerEmails.has(email)) return res.json(generic);
    try {
      const { rows: members } = await pool.query(
        `SELECT id FROM users WHERE lower(email) = $1 LIMIT 1`, [email]
      );
      if (!members.length) return res.json(generic);
      const { rows: recent } = await pool.query(
        `SELECT created_at FROM owner_console_codes
         WHERE email = $1 AND created_at > now() - interval '15 minutes'
         ORDER BY created_at DESC`, [email]
      );
      if (recent.length >= 3 || (recent.length && now - new Date(recent[0].created_at).getTime() < 60000)) {
        return res.json(generic);
      }
      const code = String(crypto.randomInt(0, 1_000_000)).padStart(6, "0");
      const { rows: issued } = await pool.query(
        `INSERT INTO owner_console_codes (email, code_hash, expires_at)
         VALUES ($1, $2, now() + interval '10 minutes') RETURNING id`,
        [email, digest(email, code, jwtSecret)]
      );
      try {
        await sendCode({ to: email, code });
      } catch (_err) {
        await pool.query(`DELETE FROM owner_console_codes WHERE id = $1`, [issued[0].id]);
        return res.status(503).json({ error: "Sign-in email could not be delivered. Try again later." });
      }
      return res.json(generic);
    } catch (err) {
      console.error("[owner-console] code request failed:", String(err.message || err));
      return res.status(500).json({ error: "Could not start sign-in." });
    }
  });

  app.post("/api/owner/verify-code", sameOriginPost, async (req, res) => {
    if (!pool || !jwtSecret || ownerEmails.size === 0) return res.status(503).json({ error: "Owner console is unavailable." });
    const email = String(req.body?.email || "").trim().toLowerCase();
    const code = String(req.body?.code || "").trim();
    if (!ownerEmails.has(email) || !/^\d{6}$/.test(code)) return res.status(401).json({ error: "Invalid or expired code." });
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const { rows } = await client.query(
        `SELECT id, code_hash, expires_at, attempts FROM owner_console_codes
         WHERE email = $1 AND used_at IS NULL ORDER BY created_at DESC LIMIT 1 FOR UPDATE`, [email]
      );
      const attempt = rows[0];
      if (!attempt || new Date(attempt.expires_at).getTime() <= Date.now() || attempt.attempts >= 5) {
        await client.query("COMMIT");
        return res.status(401).json({ error: "Invalid or expired code." });
      }
      const expected = Buffer.from(attempt.code_hash, "hex");
      const actual = Buffer.from(digest(email, code, jwtSecret), "hex");
      if (expected.length !== actual.length || !crypto.timingSafeEqual(expected, actual)) {
        await client.query(`UPDATE owner_console_codes SET attempts = attempts + 1 WHERE id = $1`, [attempt.id]);
        await client.query("COMMIT");
        return res.status(401).json({ error: "Invalid or expired code." });
      }
      const { rows: accounts } = await client.query(
        `SELECT google_sub FROM users WHERE lower(email) = $1 LIMIT 1`, [email]
      );
      if (!accounts.length) {
        await client.query("COMMIT");
        return res.status(403).json({ error: "Owner access only." });
      }
      await client.query(`UPDATE owner_console_codes SET used_at = now() WHERE id = $1`, [attempt.id]);
      await client.query("COMMIT");
      const token = jwt.sign({ kind: "owner-console", email, sub: accounts[0].google_sub }, jwtSecret,
        { expiresIn: SESSION_SECONDS, audience: AUDIENCE });
      res.set("Set-Cookie", secureCookie(token, SESSION_SECONDS));
      return res.json({ ok: true, email });
    } catch (err) {
      await client.query("ROLLBACK").catch(() => {});
      console.error("[owner-console] code verification failed:", String(err.message || err));
      return res.status(500).json({ error: "Could not verify code." });
    } finally {
      client.release();
    }
  });

  app.get("/api/owner/me", requireOwner, (req, res) => res.json({ email: req.ownerEmail }));
  app.post("/api/owner/logout", sameOriginPost, (_req, res) => {
    res.set("Set-Cookie", secureCookie("", 0));
    res.json({ ok: true });
  });

  // Private provider-measured token usage. Days use the owner's Lagos local
  // calendar dates; totals include every recorded response, not just saved analyses.
  app.get("/api/owner/ai-usage", requireOwner, async (req, res) => {
    const days = Number(req.query.days || 7);
    if (!Number.isInteger(days) || days < 1 || days > 31) {
      return res.status(400).json({ error: "Days must be between 1 and 31." });
    }
    try {
      const { rows } = await pool.query(
        `SELECT (e.completed_at AT TIME ZONE 'Africa/Lagos')::date::text AS day,
                e.user_id, u.email, u.name,
                COUNT(*)::int AS calls,
                COUNT(*) FILTER (WHERE e.input_tokens IS NULL OR e.output_tokens IS NULL)::int AS unknown_calls,
                COALESCE(SUM(e.input_tokens), 0)::bigint::text AS input_tokens,
                COALESCE(SUM(e.output_tokens), 0)::bigint::text AS output_tokens
         FROM ai_usage_events e
         LEFT JOIN users u ON u.id = e.user_id
         WHERE (e.completed_at AT TIME ZONE 'Africa/Lagos')::date >=
               (now() AT TIME ZONE 'Africa/Lagos')::date - ($1::int - 1)
         GROUP BY day, e.user_id, u.email, u.name
         ORDER BY day DESC, (COALESCE(SUM(e.input_tokens), 0) + COALESCE(SUM(e.output_tokens), 0)) DESC`, [days]
      );
      return res.json({ timezone: "Africa/Lagos", days, daily: rows.map(r => ({
        day: r.day, userId: r.user_id, email: r.email, name: r.name,
        calls: r.calls, unknownCalls: r.unknown_calls,
        inputTokens: Number(r.input_tokens), outputTokens: Number(r.output_tokens)
      })) });
    } catch (err) {
      console.error("[owner-console] AI usage totals failed:", String(err.message || err));
      return res.status(500).json({ error: "Could not load AI usage." });
    }
  });

  app.get("/api/owner/ai-usage/calls", requireOwner, async (req, res) => {
    const date = String(req.query.date || "");
    const userId = String(req.query.userId || "");
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || Number.isNaN(Date.parse(`${date}T00:00:00Z`)) ||
        (userId !== "system" && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(userId))) {
      return res.status(400).json({ error: "Choose a valid date and user." });
    }
    try {
      const { rows } = await pool.query(
        `SELECT id, feature, provider, model, input_tokens, output_tokens, completed_at
         FROM ai_usage_events
         WHERE (completed_at AT TIME ZONE 'Africa/Lagos')::date = $1::date
           AND (($2::text = 'system' AND user_id IS NULL) OR user_id::text = $2::text)
         ORDER BY completed_at DESC, id DESC LIMIT 200`, [date, userId]
      );
      return res.json({ date, userId, timezone: "Africa/Lagos", calls: rows.map(r => ({
        id: r.id, feature: r.feature, provider: r.provider, model: r.model,
        inputTokens: r.input_tokens, outputTokens: r.output_tokens, completedAt: r.completed_at
      })) });
    } catch (err) {
      console.error("[owner-console] AI usage calls failed:", String(err.message || err));
      return res.status(500).json({ error: "Could not load AI calls." });
    }
  });

  app.get("/api/owner/opinions", requireOwner, async (_req, res) => {
    try {
      const { rows } = await pool.query(
        `SELECT id, user_email, message, attachments, created_at
         FROM user_feedback ORDER BY created_at DESC LIMIT 100`
      );
      const feedback = await Promise.all(rows.map(async row => ({
        id: row.id, email: row.user_email, message: row.message, createdAt: row.created_at,
        images: await Promise.all((Array.isArray(row.attachments) ? row.attachments : []).map(async a => ({
          url: await r2.signedImageUrl(a.r2Key, 900).catch(() => null), sizeBytes: a.sizeBytes
        })))
      })));
      res.json({ feedback });
    } catch (err) {
      console.error("[owner-console] opinions failed:", String(err.message || err));
      res.status(500).json({ error: "Could not load opinions." });
    }
  });
  app.get("/api/owner/bug-reports", requireOwner, async (_req, res) => {
    try {
      const { rows } = await pool.query(
        `SELECT id, user_email, description, attachments, app_version, device_model, android_version, status, created_at
         FROM bug_reports ORDER BY created_at DESC LIMIT 100`
      );
      const reports = await Promise.all(rows.map(async row => ({
        id: row.id, email: row.user_email, description: row.description,
        appVersion: row.app_version, deviceModel: row.device_model,
        androidVersion: row.android_version, status: row.status, createdAt: row.created_at,
        attachments: await Promise.all((Array.isArray(row.attachments) ? row.attachments : []).map(async a => ({
          kind: a.kind, contentType: a.contentType, sizeBytes: a.sizeBytes,
          ...(a.r2Key ? { url: await r2.signedImageUrl(a.r2Key, 900).catch(() => null) }
            : a.dataBase64 ? { dataUrl: `data:${a.contentType};base64,${a.dataBase64}` } : {})
        })))
      })));
      res.json({ reports });
    } catch (err) {
      console.error("[owner-console] bug reports failed:", String(err.message || err));
      res.status(500).json({ error: "Could not load bug reports." });
    }
  });
}

module.exports = { installOwnerConsole, digest, parseCookie };
