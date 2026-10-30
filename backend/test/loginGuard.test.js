const test = require('node:test');
const assert = require('node:assert/strict');
const {
  IpRateLimiter,
  lockDurationFor,
  evaluateLock,
  createAccountLockout,
  LOCKOUT_TIERS,
  ipRateLimit,
  sendAccountLocked
} = require('../src/loginGuard');

/* ---------------------------- IP rate limiting --------------------------- */

test('ip limiter allows up to the limit, then blocks with a retry hint', () => {
  let clock = 1_000_000;
  const limiter = new IpRateLimiter({ limit: 3, windowMs: 60_000, now: () => clock });

  assert.equal(limiter.hit('1.1.1.1').allowed, true);
  assert.equal(limiter.hit('1.1.1.1').allowed, true);
  const third = limiter.hit('1.1.1.1');
  assert.equal(third.allowed, true);
  assert.equal(third.remaining, 0);

  const blocked = limiter.hit('1.1.1.1');
  assert.equal(blocked.allowed, false);
  assert.equal(blocked.retryAfterSec, 60);
});

test('ip limiter tracks each IP independently', () => {
  let clock = 1_000_000;
  const limiter = new IpRateLimiter({ limit: 1, windowMs: 60_000, now: () => clock });
  assert.equal(limiter.hit('a').allowed, true);
  assert.equal(limiter.hit('a').allowed, false);
  // A different client is unaffected.
  assert.equal(limiter.hit('b').allowed, true);
});

test('ip limiter frees the slot once the window has passed', () => {
  let clock = 1_000_000;
  const limiter = new IpRateLimiter({ limit: 2, windowMs: 10_000, now: () => clock });

  limiter.hit('ip');
  limiter.hit('ip');
  assert.equal(limiter.hit('ip').allowed, false);

  clock += 10_000;                 // exactly one window later
  assert.equal(limiter.hit('ip').allowed, true);
});

test('ip limiter uses a sliding window, not a fixed one', () => {
  let clock = 0;
  const limiter = new IpRateLimiter({ limit: 2, windowMs: 10_000, now: () => clock });

  limiter.hit('ip');               // t=0
  clock = 9_000;
  limiter.hit('ip');               // t=9000
  clock = 10_500;                  // first hit expired, second still counts
  assert.equal(limiter.hit('ip').allowed, true);
  assert.equal(limiter.hit('ip').allowed, false);
});

test('blocked hits do not extend the lockout window', () => {
  let clock = 0;
  const limiter = new IpRateLimiter({ limit: 1, windowMs: 10_000, now: () => clock });
  limiter.hit('ip');
  for (let i = 0; i < 20; i += 1) limiter.hit('ip');  // hammering while blocked
  clock = 10_000;
  assert.equal(limiter.hit('ip').allowed, true);
});

/* ------------------------------ lockout policy --------------------------- */

test('lockout escalates at 5 / 10 / 15 consecutive failures', () => {
  assert.equal(lockDurationFor(0), 0);
  assert.equal(lockDurationFor(4), 0);
  assert.equal(lockDurationFor(5), 15 * 60_000);
  assert.equal(lockDurationFor(9), 15 * 60_000);
  assert.equal(lockDurationFor(10), 60 * 60_000);
  assert.equal(lockDurationFor(14), 60 * 60_000);
  assert.equal(lockDurationFor(15), 24 * 60 * 60_000);
  assert.equal(lockDurationFor(99), 24 * 60 * 60_000);
  assert.equal(LOCKOUT_TIERS.length, 3);
});

test('evaluateLock reports remaining time and unlocks once it lapses', () => {
  const now = Date.parse('2026-10-30T12:00:00Z');
  const locked = evaluateLock(
    { failed_count: 5, locked_until: '2026-10-30T12:15:00Z' },
    now
  );
  assert.equal(locked.locked, true);
  assert.equal(locked.retryAfterSec, 900);
  assert.equal(locked.failedCount, 5);

  const expired = evaluateLock(
    { failed_count: 5, locked_until: '2026-10-30T11:59:00Z' },
    now
  );
  assert.equal(expired.locked, false);
  assert.equal(expired.retryAfterSec, 0);

  // A row with no lock at all (or no row) is not locked.
  assert.equal(evaluateLock(undefined, now).locked, false);
  assert.equal(evaluateLock({ failed_count: 2, locked_until: null }, now).locked, false);
});

/* --------------------------- lockout store (fake DB) --------------------- */

/**
 * Minimal fake of the three SQL shapes loginGuard issues, enough to exercise
 * the real code paths (upsert, escalating update, delete) without Postgres.
 */
function fakeDb() {
  const rows = new Map(); // "scope|identifier" -> { failed_count, locked_until }
  let clock = Date.parse('2026-10-30T12:00:00Z');
  const calls = [];

  async function query(sql, params = []) {
    calls.push({ sql, params });
    const s = sql.replace(/\s+/g, ' ').trim();

    if (s.startsWith('CREATE TABLE') || s.startsWith('CREATE INDEX')) return { rows: [] };

    const key = `${params[0]}|${params[1]}`;

    if (s.startsWith('SELECT failed_count, locked_until')) {
      const row = rows.get(key);
      return { rows: row ? [{ failed_count: row.failed_count, locked_until: new Date(row.locked_until) }] : [] };
    }

    if (s.startsWith('INSERT INTO login_attempts')) {
      const existing = rows.get(key);
      const next = existing
        ? { failed_count: existing.failed_count + 1, locked_until: existing.locked_until }
        : { failed_count: 1, locked_until: null };
      rows.set(key, next);
      return { rows: [{ failed_count: next.failed_count }] };
    }

    if (s.startsWith('UPDATE login_attempts')) {
      const row = rows.get(key);
      if (!row) return { rows: [] };
      // params[2] is an absolute deadline Date (see createAccountLockout.fail)
      row.locked_until = new Date(params[2]).getTime();
      return { rows: [{ failed_count: row.failed_count, locked_until: new Date(row.locked_until) }] };
    }

    if (s.startsWith('DELETE FROM login_attempts')) {
      rows.delete(key);
      return { rows: [] };
    }

    throw new Error('unexpected SQL: ' + s.slice(0, 60));
  }

  return {
    query,
    rows,
    calls,
    advance(ms) { clock += ms; },
    now: () => clock
  };
}

test('account lockout locks after five failures and refuses further attempts', async () => {
  const db = fakeDb();
  const lockout = createAccountLockout({ query: db.query, now: db.now });

  // Four failures: still allowed through to the credential check.
  for (let i = 1; i <= 4; i += 1) {
    const res = await lockout.fail('owner-verify', 'Owner@Example.com ');
    assert.equal(res.locked, false, `failure ${i} should not lock`);
  }

  // Fifth failure trips the first tier (15 minutes).
  const fifth = await lockout.fail('owner-verify', 'owner@example.com');
  assert.equal(fifth.locked, true);
  assert.equal(fifth.retryAfterSec, 900);

  // The account is now locked regardless of source IP.
  const check = await lockout.check('owner-verify', 'owner@example.com');
  assert.equal(check.locked, true);
  assert.equal(check.retryAfterSec, 900);

  // Same address with different casing/whitespace is the same account.
  const mixed = await lockout.check('owner-verify', '  OWNER@example.COM');
  assert.equal(mixed.locked, true);
});

test('account lockout escalates and clears on success', async () => {
  const db = fakeDb();
  const lockout = createAccountLockout({ query: db.query, now: db.now });

  for (let i = 0; i < 10; i += 1) await lockout.fail('owner-verify', 'a@b.com');
  const tenth = await lockout.check('owner-verify', 'a@b.com');
  assert.equal(tenth.locked, true);
  assert.equal(tenth.retryAfterSec, 3600); // second tier: 1 hour

  // A successful sign-in clears the counter entirely.
  await lockout.clear('owner-verify', 'a@b.com');
  const after = await lockout.check('owner-verify', 'a@b.com');
  assert.equal(after.locked, false);
  assert.equal(after.failedCount, 0);
});

test('lockout expires by itself and the next failure starts from the tier again', async () => {
  const db = fakeDb();
  const lockout = createAccountLockout({ query: db.query, now: db.now });

  for (let i = 0; i < 5; i += 1) await lockout.fail('owner-verify', 'c@d.com');
  assert.equal((await lockout.check('owner-verify', 'c@d.com')).locked, true);

  db.advance(15 * 60_000 + 1); // 15 minutes later
  assert.equal((await lockout.check('owner-verify', 'c@d.com')).locked, false);
});

test('lockout scopes are isolated: a Google account lock never blocks owner sign-in', async () => {
  const db = fakeDb();
  const lockout = createAccountLockout({ query: db.query, now: db.now });

  for (let i = 0; i < 5; i += 1) await lockout.fail('google', 'same@trader.com');
  assert.equal((await lockout.check('google', 'same@trader.com')).locked, true);
  assert.equal((await lockout.check('owner-verify', 'same@trader.com')).locked, false);
});

test('lockout fails open (and reports) when the database errors', async () => {
  const errors = [];
  const lockout = createAccountLockout({
    query: async () => { throw new Error('db down'); },
    onError: (msg) => errors.push(msg)
  });

  const check = await lockout.check('owner-verify', 'x@y.com');
  assert.equal(check.locked, false);
  const fail = await lockout.fail('owner-verify', 'x@y.com');
  assert.equal(fail.locked, false);
  assert.ok(errors.length >= 2, 'database errors should be surfaced to the logger');
});

/* ------------------------------ middleware ------------------------------- */

function fakeReqRes(ip) {
  const headers = {};
  const req = { ip, headers: {}, socket: {} };
  const res = {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    set(k, v) { headers[k] = v; return this; },
    json(payload) { this.body = payload; return this; },
    headers
  };
  return { req, res };
}

test('ip middleware passes allowed requests through exactly once', async () => {
  const limiter = new IpRateLimiter({ limit: 2, windowMs: 60_000 });
  const mw = ipRateLimit(limiter, 'login');
  const { req, res } = fakeReqRes('10.0.0.1');

  let passed = 0;
  mw(req, res, () => { passed += 1; });
  assert.equal(passed, 1);
  assert.equal(res.headers['X-RateLimit-Remaining'], '1');
  assert.equal(res.headers['Retry-After'], undefined);
});

test('ip middleware returns 429 with Retry-After once the limit is hit', async () => {
  const limiter = new IpRateLimiter({ limit: 1, windowMs: 60_000 });
  const mw = ipRateLimit(limiter, 'login');
  const { req, res } = fakeReqRes('10.0.0.2');

  let passed = 0;
  mw(req, res, () => { passed += 1; });
  mw(req, res, () => { passed += 1; });

  assert.equal(passed, 1, 'second request must not reach the route');
  assert.equal(res.statusCode, 429);
  assert.equal(res.body.bucket, 'login');
  assert.ok(Number(res.headers['Retry-After']) > 0);
});

test('sendAccountLocked replies 429 without revealing account existence', () => {
  const { res } = fakeReqRes('10.0.0.3');
  sendAccountLocked(res, 42);
  assert.equal(res.statusCode, 429);
  assert.equal(res.headers['Retry-After'], '42');
  assert.equal(res.body.retryAfterSec, 42);
  assert.match(res.body.error, /Too many failed attempts/i);
  assert.doesNotMatch(res.body.error, /exist|unknown|not found/i);
});
