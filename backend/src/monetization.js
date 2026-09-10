/**
 * Monetization engine — Free + Premium + Advertising.
 *
 * Everything monetization-related is SERVER-DRIVEN:
 *  - monetization_config: one editable row holding the whole config (ad
 *    placements, frequency, free/premium AI-analysis limits, rewarded-ad
 *    availability, which ad networks are enabled). The admin can change any
 *    value without an app release.
 *  - rewarded_unlocks: server-validated rewarded-ad grants. The client NEVER
 *    decides it earned a reward — the unlock is only stored after the ad SDK
 *    confirms the reward AND this endpoint validates the user's eligibility
 *    and daily cap.
 *  - Ad eligibility is derived from the central Premium entitlement
 *    (paid subscription OR trial OR admin grant): premium users are ad-free.
 *
 * Advertising policy baked in:
 *  - Ads NEVER interrupt an active analysis, chart interaction, or trading
 *    workflow (placements are limited to feed-native + post-result + rewarded).
 *  - Rewarded ads only unlock non-cash features (extra AI analyses).
 *  - The app is a research/education tool; ad copy and product copy must never
 *    imply guaranteed returns (enforced in review, not code).
 */

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * The single source of truth for monetization tuning. The DB row overrides
 * these defaults key-by-key, so new keys ship with sane values automatically.
 */
const DEFAULT_CONFIG = {
  // Master switch — flips the whole advertising system off for everyone.
  adsEnabled: true,

  // Free tier: how many AI analyses per rolling 24h once the trial lapses.
  freeDailyAnalysisLimit: 3,
  // Premium tier: unlimited (subject to AI-provider capacity, guarded server-side).
  premiumAnalysisUnlimited: true,

  // Rewarded ads: free users watch a real ad video to unlock one extra
  // non-cash feature (extra AI analyses). Never money, crypto, or gift cards.
  rewarded: {
    enabled: true,
    bonusPerReward: 1,   // analyses added per rewarded ad watched
    maxUnlocksPerDay: 3 // server-enforced daily cap
  },

  // Where ads may appear. Every placement is independently toggleable.
  placements: {
    // Native ads inside the news feed — clearly labeled "Advertisement".
    nativeNews: { enabled: true, everyNthItem: 6, maxPerFeed: 3 },
    // One interstitial after the user CLOSES an analysis result (never during
    // analysis, charts, or trading actions; never on app open; never during
    // onboarding). Frequency-capped by minIntervalMinutes.
    interstitial: { enabled: true, minIntervalMinutes: 45 },
    // Adaptive banners — off by default so the app never feels overloaded.
    banner: { enabled: false, surfaces: ["saved"] }
  },

  // Ad-network mediation flags. AdMob is the primary network today; the app
  // routes through a network-agnostic adapter, so flipping these on later
  // only requires adding the corresponding adapter + SDK, not a UI rewrite.
  networks: {
    admob: { enabled: true },
    applovin: { enabled: false },
    meta: { enabled: false },
    inmobi: { enabled: false },
    liftoff: { enabled: false },
    pangle: { enabled: false },
    unity: { enabled: false },
    mintegral: { enabled: false },
    smaato: { enabled: false },
    moloco: { enabled: false }
  }
};

// 60s in-process cache — the config is read on every protected request path.
let cachedConfig = null;
let cachedAt = 0;

async function ensureMonetizationTables(pool) {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS monetization_config (
      id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
      config JSONB NOT NULL,
      updated_by TEXT,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    INSERT INTO monetization_config (id, config)
    VALUES (1, '{}'::jsonb)
    ON CONFLICT (id) DO NOTHING;

    CREATE TABLE IF NOT EXISTS rewarded_unlocks (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      kind TEXT NOT NULL DEFAULT 'extra_analysis',
      bonus INTEGER NOT NULL DEFAULT 1,
      network TEXT NOT NULL DEFAULT 'admob',
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    CREATE INDEX IF NOT EXISTS idx_rewarded_unlocks_user_time
      ON rewarded_unlocks(user_id, created_at DESC);

    ALTER TABLE users ADD COLUMN IF NOT EXISTS premium_started_at TIMESTAMPTZ;
    ALTER TABLE users ADD COLUMN IF NOT EXISTS premium_platform TEXT;
  `);
}

/** Deep-merge stored config over the defaults (defaults win for missing keys). */
function mergeConfig(stored) {
  const out = JSON.parse(JSON.stringify(DEFAULT_CONFIG));
  if (!stored || typeof stored !== "object") return out;
  for (const key of Object.keys(out)) {
    const s = stored[key];
    if (s === undefined || s === null) continue;
    if (typeof out[key] === "object" && !Array.isArray(out[key]) && typeof s === "object") {
      out[key] = { ...out[key], ...s };
    } else {
      out[key] = s;
    }
  }
  return out;
}

async function getMonetizationConfig(pool) {
  if (cachedConfig && Date.now() - cachedAt < 60_000) return cachedConfig;
  const { rows } = await pool.query(
    `SELECT config, updated_at FROM monetization_config WHERE id = 1`
  );
  cachedConfig = mergeConfig(rows[0] && rows[0].config);
  cachedAt = Date.now();
  return cachedConfig;
}

/** Admin-only update: partial config is merged into the stored row. */
async function updateMonetizationConfig(pool, partial, updatedBy) {
  const { rows } = await pool.query(
    `SELECT config FROM monetization_config WHERE id = 1`
  );
  const stored = (rows[0] && rows[0].config) || {};
  const next = { ...stored, ...partial };
  const { rows: written } = await pool.query(
    `UPDATE monetization_config
     SET config = $1::jsonb, updated_by = $2, updated_at = now()
     WHERE id = 1
     RETURNING config, updated_at`,
    [JSON.stringify(next), updatedBy || null]
  );
  cachedConfig = null; // invalidate cache
  return { config: mergeConfig(written[0].config), updatedAt: written[0].updated_at };
}

/** Rewarded unlocks granted to this user inside the rolling 24h window. */
async function rewardedUnlockCount(pool, userId) {
  const { rows } = await pool.query(
    `SELECT count(*)::int AS n FROM rewarded_unlocks
     WHERE user_id = $1 AND created_at > now() - interval '24 hours'`,
    [userId]
  );
  return rows[0].n;
}

/**
 * Validate + store a rewarded-ad unlock. Called ONLY after the ad SDK has
 * confirmed the reward was earned (the client cannot mint unlocks by itself).
 * Returns { granted, usage } or throws a typed error the route maps to HTTP.
 */
async function grantRewardedUnlock(pool, userId, { isPremium, network } = {}) {
  const cfg = await getMonetizationConfig(pool);
  if (!cfg.rewarded || cfg.rewarded.enabled === false) {
    const err = new Error("Rewarded ads are currently unavailable.");
    err.statusCode = 403;
    throw err;
  }
  if (isPremium) {
    // Premium users never see ads, so a reward claim is never legitimate.
    const err = new Error("Premium members already have unlimited analyses — no ad needed.");
    err.statusCode = 409;
    throw err;
  }
  const used = await rewardedUnlockCount(pool, userId);
  if (used >= cfg.rewarded.maxUnlocksPerDay) {
    const err = new Error(
      `Daily rewarded-ad bonus reached (${cfg.rewarded.maxUnlocksPerDay}/day). Premium gives you unlimited analyses with no ads at all.`
    );
    err.statusCode = 429;
    throw err;
  }
  await pool.query(
    `INSERT INTO rewarded_unlocks (user_id, kind, bonus, network)
     VALUES ($1, 'extra_analysis', $2, $3)`,
    [userId, cfg.rewarded.bonusPerReward, (network || "admob").slice(0, 20)]
  );
  return { granted: true, bonus: cfg.rewarded.bonusPerReward };
}

/**
 * Effective daily allowance for a FREE user: base limit + rewarded bonuses
 * earned in the same rolling window. Premium is unlimited (usage still
 * recorded for capacity monitoring).
 */
async function analysisAllowance(pool, userId, cfg) {
  const { rows } = await pool.query(
    `SELECT count(*)::int AS used FROM analyses
     WHERE user_id = $1 AND created_at > now() - interval '24 hours'`,
    [userId]
  );
  const used = rows[0].used;
  const unlocks = await rewardedUnlockCount(pool, userId);
  const limit = cfg.freeDailyAnalysisLimit + unlocks * cfg.rewarded.bonusPerReward;
  return { used, limit, remaining: Math.max(0, limit - used), unlocks };
}

/** Ad eligibility snapshot attached to entitlement responses. */
function adEligibility(cfg, effectivePremium) {
  return {
    adsEnabled: Boolean(cfg.adsEnabled) && !effectivePremium,
    rewardedAvailable: Boolean(cfg.adsEnabled) && Boolean(cfg.rewarded && cfg.rewarded.enabled) && !effectivePremium
  };
}

module.exports = {
  DEFAULT_CONFIG,
  ensureMonetizationTables,
  getMonetizationConfig,
  updateMonetizationConfig,
  grantRewardedUnlock,
  analysisAllowance,
  adEligibility,
  rewardedUnlockCount
};
