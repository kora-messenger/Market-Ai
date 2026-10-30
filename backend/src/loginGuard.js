/**
 * Login protection for the MarketScope AI API.
 *
 * Two independent controls that work together:
 *
 *   1. IP rate limiting — a short sliding window per client IP, per bucket.
 *      Cheap, in-memory, stops floods and scripted guessing from one source.
 *      Buckets are per endpoint so a burst on one login route cannot use up
 *      the budget of another.
 *
 *   2. Account lockout — persisted in Postgres, keyed by the account
 *      identifier (email). Survives restarts, cold starts and multiple
 *      instances, which an in-memory counter would not. Escalates with the
 *      number of consecutive failures: 5 → 15 min, 10 → 1 hour, 15 → 24 h.
 *      A successful sign-in clears the counter.
 *
 * Why both: IP limiting alone can be dodged by rotating IPs and punishes
 * everyone behind a shared NAT. Account lockout alone lets one host attack
 * thousands of accounts in parallel. Together, an attacker needs many IPs
 * AND must stay under the per-account budget, while a locked account stays
 * locked no matter which IP tries it.
 *
 * Nothing here replaces real credential checks: Google ID tokens are still
 * verified cryptographically and owner sign-in codes are still compared in
 * constant time. This layer limits guessing, replay and abuse.
 */

const MINUTE_MS = 60_000;

/* ---------------------------------------------------------------------------
 * IP rate limiting (in-memory sliding window)
 * ------------------------------------------------------------------------- */

/**
 * Sliding-window limiter keyed by client IP.
 *
 * A fixed window would allow a burst of `limit` requests at the very end of
 * one window plus `limit` more at the start of the next, so this keeps the
 * timestamps of recent hits and drops the ones that fell out of the window.
 */
class IpRateLimiter {
  /**
   * @param {object} opts
   * @param {number} opts.limit      allowed hits per window
   * @param {number} opts.windowMs   window length in milliseconds
   * @param {() => number} [opts.now] injectable clock (tests)
   * @param {number} [opts.maxKeys]  cap on tracked keys before pruning
   */
  constructor({ limit, windowMs, now = () => Date.now(), maxKeys = 20_000 }) {
    if (!(limit > 0)) throw new Error("limit must be > 0");
    if (!(windowMs > 0)) throw new Error("windowMs must be > 0");
    this.limit = limit;
    this.windowMs = windowMs;
    this.now = now;
    this.maxKeys = maxKeys;
    /** @type {Map<string, number[]>} ip -> hit timestamps (ascending) */
    this.hits = new Map();
    this.lastSweep = this.now();
  }

  /** Drop expired hits for one key; caller decides whether to keep the entry. */
  pruneKey(key, at) {
    const list = this.hits.get(key);
    if (!list) return [];
    const cutoff = at - this.windowMs;
    let i = 0;
    while (i < list.length && list[i] <= cutoff) i += 1;
    const fresh = i === 0 ? list : list.slice(i);
    if (fresh.length === 0) {
      this.hits.delete(key);
      return [];
    }
    if (i > 0) this.hits.set(key, fresh);
    return fresh;
  }

  /** Occasionally sweep the whole map so idle IPs cannot grow memory forever. */
  maybeSweep(at) {
    if (at - this.lastSweep < this.windowMs) return;
    this.lastSweep = at;
    for (const key of [...this.hits.keys()]) {
      if (this.pruneKey(key, at).length === 0) this.hits.delete(key);
    }
    // Hard cap: if an attacker cycles through IPs, keep the map bounded by
    // dropping the oldest keys. Under normal traffic this never triggers.
    if (this.hits.size > this.maxKeys) {
      const excess = this.hits.size - this.maxKeys;
      let dropped = 0;
      for (const key of this.hits.keys()) {
        this.hits.delete(key);
        if (++dropped >= excess) break;
      }
    }
  }

  /**
   * Record one hit for `key`.
   * @returns {{allowed: boolean, remaining: number, retryAfterSec: number}}
   */
  hit(key) {
    const at = this.now();
    this.maybeSweep(at);
    const list = this.pruneKey(key, at);

    if (list.length >= this.limit) {
      // Blocked hits are NOT recorded, so the window keeps draining and the
      // client recovers on its own rather than extending its own lockout.
      const oldest = list[0];
      const retryMs = Math.max(1000, this.windowMs - (at - oldest));
      return {
        allowed: false,
        remaining: 0,
        retryAfterSec: Math.ceil(retryMs / 1000)
      };
    }

    list.push(at);
    this.hits.set(key, list);
    return {
      allowed: true,
      remaining: Math.max(0, this.limit - list.length),
      retryAfterSec: 0
    };
  }

  /** Test/ops helper: forget every tracked key. */
  reset() {
    this.hits.clear();
    this.lastSweep = this.now();
  }
}

/* ---------------------------------------------------------------------------
 * Account lockout policy (pure, so it is trivially testable)
 * ------------------------------------------------------------------------- */

/** Consecutive-failure thresholds → lockout length. Order matters. */
const LOCKOUT_TIERS = Object.freeze([
  Object.freeze({ failures: 5, lockMs: 15 * MINUTE_MS }),        // 15 minutes
  Object.freeze({ failures: 10, lockMs: 60 * MINUTE_MS }),       // 1 hour
  Object.freeze({ failures: 15, lockMs: 24 * 60 * MINUTE_MS })   // 24 hours
]);

/**
 * How long an account is locked after `failedCount` consecutive failures.
 * Returns 0 while the account is still below the first threshold.
 */
function lockDurationFor(failedCount) {
  const count = Number(failedCount) || 0;
  let lockMs = 0;
  for (const tier of LOCKOUT_TIERS) {
    if (count >= tier.failures) lockMs = tier.lockMs;
  }
  return lockMs;
}

/**
 * Evaluate a stored row against `now`.
 * @param {{failed_count?: number|string, locked_until?: Date|string|null}} row
 * @returns {{locked: boolean, retryAfterSec: number, failedCount: number}}
 */
function evaluateLock(row, now = Date.now()) {
  const failedCount = Number((row && row.failed_count) || 0);
  const until = row && row.locked_until ? new Date(row.locked_until).getTime() : 0;
  if (until && until > now) {
    return {
      locked: true,
      retryAfterSec: Math.max(1, Math.ceil((until - now) / 1000)),
      failedCount
    };
  }
  return { locked: false, retryAfterSec: 0, failedCount };
}

/* ---------------------------------------------------------------------------
 * Account lockout store (Postgres-backed)
 * ------------------------------------------------------------------------- */

const TABLE_SQL = `
  CREATE TABLE IF NOT EXISTS login_attempts (
    scope TEXT NOT NULL,
    identifier TEXT NOT NULL,
    failed_count INTEGER NOT NULL DEFAULT 0,
    last_failed_at TIMESTAMPTZ,
    locked_until TIMESTAMPTZ,
    PRIMARY KEY (scope, identifier)
  );
  CREATE INDEX IF NOT EXISTS login_attempts_locked_idx ON login_attempts (locked_until);
`;

function normalizeIdentifier(value) {
  return String(value || "").trim().toLowerCase().slice(0, 254);
}

/**
 * @param {object} opts
 * @param {(sql: string, params?: any[]) => Promise<{rows: any[]}>} opts.query
 * @param {() => number} [opts.now]
 * @param {(msg: string, err?: any) => void} [opts.onError]  logger hook
 */
function createAccountLockout({ query, now = () => Date.now(), onError = () => {} }) {
  if (typeof query !== "function") throw new Error("query function is required");

  return {
    async ensureTable() {
      await query(TABLE_SQL);
    },

    /**
     * Is this account currently locked?
     * Fails OPEN on a database error: lockout is defense in depth and must
     * never take sign-in down on its own. The error is logged so it is visible.
     */
    async check(scope, identifier) {
      const id = normalizeIdentifier(identifier);
      if (!id) return { locked: false, retryAfterSec: 0, failedCount: 0 };
      try {
        const { rows } = await query(
          `SELECT failed_count, locked_until FROM login_attempts
           WHERE scope = $1 AND identifier = $2`,
          [scope, id]
        );
        return evaluateLock(rows[0], now());
      } catch (err) {
        onError("lockout check failed", err);
        return { locked: false, retryAfterSec: 0, failedCount: 0 };
      }
    },

    /**
     * Record one failure and lock the account if a tier was reached.
     * @returns {{locked: boolean, retryAfterSec: number, failedCount: number}}
     */
    async fail(scope, identifier) {
      const id = normalizeIdentifier(identifier);
      if (!id) return { locked: false, retryAfterSec: 0, failedCount: 0 };
      try {
        const { rows } = await query(
          `INSERT INTO login_attempts (scope, identifier, failed_count, last_failed_at)
           VALUES ($1, $2, 1, now())
           ON CONFLICT (scope, identifier) DO UPDATE
             SET failed_count = login_attempts.failed_count + 1,
                 last_failed_at = now()
           RETURNING failed_count`,
          [scope, id]
        );
        const failedCount = Number(rows[0]?.failed_count || 1);
        const lockMs = lockDurationFor(failedCount);
        if (lockMs > 0) {
          // The deadline is computed here and passed as a timestamp rather
          // than assembled with interval arithmetic in SQL: one plain
          // parameter, no dialect traps, and the same clock that `check`
          // compares against.
          const lockedUntil = new Date(now() + lockMs);
          const { rows: locked } = await query(
            `UPDATE login_attempts
             SET locked_until = $3
             WHERE scope = $1 AND identifier = $2
             RETURNING failed_count, locked_until`,
            [scope, id, lockedUntil]
          );
          return evaluateLock(locked[0], now());
        }
        return { locked: false, retryAfterSec: 0, failedCount };
      } catch (err) {
        onError("lockout record failed", err);
        return { locked: false, retryAfterSec: 0, failedCount: 0 };
      }
    },

    /** Clear the counter after a successful sign-in. */
    async clear(scope, identifier) {
      const id = normalizeIdentifier(identifier);
      if (!id) return;
      try {
        await query(
          `DELETE FROM login_attempts WHERE scope = $1 AND identifier = $2`,
          [scope, id]
        );
      } catch (err) {
        onError("lockout clear failed", err);
      }
    }
  };
}

/* ---------------------------------------------------------------------------
 * Express helpers
 * ------------------------------------------------------------------------- */

/**
 * Express middleware enforcing one IP bucket.
 * Sets Retry-After so clients (and our Android app) can wait intelligently.
 * @param {IpRateLimiter} limiter
 * @param {string} bucket  label used in the response, e.g. "login"
 */
function ipRateLimit(limiter, bucket) {
  return function enforceIpLimit(req, res, next) {
    // req.ip respects "trust proxy", so on Render this is the real client IP
    // (X-Forwarded-For), never a client-supplied value.
    const key = req.ip || req.socket?.remoteAddress || "unknown";
    const result = limiter.hit(key);
    res.set("X-RateLimit-Limit", String(limiter.limit));
    res.set("X-RateLimit-Remaining", String(result.remaining));
    if (!result.allowed) {
      res.set("Retry-After", String(result.retryAfterSec));
      return res.status(429).json({
        error: "Too many attempts from this network. Please wait and try again.",
        bucket,
        retryAfterSec: result.retryAfterSec
      });
    }
    return next();
  };
}

/**
 * Reusable 429 response for a locked account.
 * Deliberately does not reveal whether the email exists.
 */
function sendAccountLocked(res, retryAfterSec) {
  const seconds = Math.max(1, Math.ceil(retryAfterSec || 0));
  res.set("Retry-After", String(seconds));
  return res.status(429).json({
    error: "Too many failed attempts for this account. Please wait before trying again.",
    retryAfterSec: seconds
  });
}

module.exports = {
  MINUTE_MS,
  LOCKOUT_TIERS,
  IpRateLimiter,
  lockDurationFor,
  evaluateLock,
  normalizeIdentifier,
  createAccountLockout,
  ipRateLimit,
  sendAccountLocked
};
