/**
 * Force-update gate — SERVER-DRIVEN, like monetization.
 *
 * One editable row (app_version_config) holds the minimum app version
 * MarketScope AI will allow to run. The admin can raise minVersionCode any
 * time (no app release needed to flip the switch) to force everyone below
 * it onto the Play Store update screen. Defaults ship with enforced=false
 * so brand-new installs are never blocked before the admin decides to.
 */

const DEFAULT_CONFIG = {
  enforced: false,
  minVersionCode: 1,
  minVersionName: "1.5.0",
  latestVersionName: "1.5.0",
  updateMessage:
    "New version available. We strongly recommend installing the update before using the app. This release contains important improvements to functionality and stability.",
  playStoreUrl: "https://play.google.com/store/apps/details?id=com.veltravia.marketscopeai",
  // App Store link for the iOS build — stays empty until the App Store
  // listing is real. The admin sets it (PUT config) the moment an Apple
  // app id exists; until then an iOS client has no honest link to open.
  appStoreUrl: ""
};

let cachedConfig = null;
let cachedAt = 0;

async function ensureAppVersionTable(pool) {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS app_version_config (
      id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
      config JSONB NOT NULL,
      updated_by TEXT,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    INSERT INTO app_version_config (id, config)
    VALUES (1, '{}'::jsonb)
    ON CONFLICT (id) DO NOTHING;
  `);
}

function mergeConfig(stored) {
  const out = { ...DEFAULT_CONFIG };
  if (!stored || typeof stored !== "object") return out;
  for (const key of Object.keys(out)) {
    if (stored[key] !== undefined && stored[key] !== null) out[key] = stored[key];
  }
  return out;
}

async function getAppVersionConfig(pool) {
  if (cachedConfig && Date.now() - cachedAt < 60_000) return cachedConfig;
  const { rows } = await pool.query(`SELECT config FROM app_version_config WHERE id = 1`);
  cachedConfig = mergeConfig(rows[0] && rows[0].config);
  cachedAt = Date.now();
  return cachedConfig;
}

async function updateAppVersionConfig(pool, partial, updatedBy) {
  const { rows } = await pool.query(`SELECT config FROM app_version_config WHERE id = 1`);
  const stored = (rows[0] && rows[0].config) || {};
  const next = { ...stored, ...partial };
  const { rows: written } = await pool.query(
    `UPDATE app_version_config
     SET config = $1::jsonb, updated_by = $2, updated_at = now()
     WHERE id = 1
     RETURNING config, updated_at`,
    [JSON.stringify(next), updatedBy || null]
  );
  cachedConfig = null;
  return { config: mergeConfig(written[0].config), updatedAt: written[0].updated_at };
}

module.exports = {
  DEFAULT_CONFIG,
  ensureAppVersionTable,
  getAppVersionConfig,
  updateAppVersionConfig
};
