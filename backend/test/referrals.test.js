const test = require('node:test');
const assert = require('node:assert/strict');
const {
  applyReferralCode, grantBonusDays, rewardFirstAnalysis, rewardFirstSubscription, assignReferralCode
} = require('../src/referrals');

function fakePool(overrides = {}) {
  const calls = [];
  const state = Object.assign({
    users: {}, // id -> row
    analysesCountByUser: {},
    grants: {} // userId -> grant row
  }, overrides);
  const query = async (sql, params = []) => {
    calls.push({ sql, params });
    if (/UPDATE users SET referral_code = \$1 WHERE id = \$2 AND referral_code IS NULL/.test(sql)) {
      const [code, id] = params;
      if (state.users[id] && !state.users[id].referral_code) {
        state.users[id].referral_code = code;
        return { rows: [{ referral_code: code }] };
      }
      return { rows: [] };
    }
    if (/SELECT referral_code FROM users WHERE id = \$1/.test(sql)) {
      return { rows: [{ referral_code: state.users[params[0]]?.referral_code }] };
    }
    if (/SELECT id, referred_by, created_at FROM users WHERE id = \$1/.test(sql)) {
      const u = state.users[params[0]];
      return { rows: u ? [u] : [] };
    }
    if (/SELECT EXISTS\(SELECT 1 FROM analyses/.test(sql)) {
      const id = params[0];
      return { rows: [{ has_analysis: (state.analysesCountByUser[id] || 0) > 0, has_paid: !!state.users[id]?.is_premium }] };
    }
    if (/SELECT id FROM users WHERE referral_code = \$1/.test(sql)) {
      const found = Object.values(state.users).find(u => u.referral_code === params[0]);
      return { rows: found ? [{ id: found.id }] : [] };
    }
    if (/UPDATE users SET referred_by = \$1 WHERE id = \$2 AND referred_by IS NULL/.test(sql)) {
      const u = state.users[params[1]];
      if (u && !u.referred_by) { u.referred_by = params[0]; return { rowCount: 1 }; }
      return { rowCount: 0 };
    }
    if (/^(BEGIN|COMMIT|ROLLBACK)$/.test(sql)) return { rows: [] };
    if (/SELECT id, referred_by, referral_(analysis|subscription)_rewarded_at FROM users WHERE id = \$1 FOR UPDATE/.test(sql)) {
      const u = state.users[params[0]];
      return { rows: u ? [u] : [] };
    }
    if (/SELECT id FROM users WHERE id = \$1 FOR UPDATE/.test(sql)) return { rows: [{ id: params[0] }] };
    if (/SELECT COUNT\(\*\)::int AS c FROM analyses WHERE user_id = \$1/.test(sql)) {
      return { rows: [{ c: state.analysesCountByUser[params[0]] || 0 }] };
    }
    if (/UPDATE users SET referral_(analysis|subscription)_rewarded_at = now\(\)/.test(sql)) {
      const u = state.users[params[0]];
      const field = sql.includes('analysis') ? 'referral_analysis_rewarded_at' : 'referral_subscription_rewarded_at';
      u[field] = new Date();
      return { rowCount: 1 };
    }
    if (/SELECT premium_expires_at FROM users/.test(sql)) {
      const u = state.users[params[0]];
      return { rows: u?.is_premium && u?.premium_expires_at ? [{ premium_expires_at: u.premium_expires_at }] : [] };
    }
    if (/SELECT id, duration_type, expires_at FROM premium_grants/.test(sql)) {
      const g = state.grants[params[0]];
      return { rows: g ? [g] : [] };
    }
    if (/UPDATE premium_grants SET expires_at/.test(sql)) {
      const [id, expiresAt] = params;
      const g = Object.values(state.grants).find(x => x.id === id);
      g.expires_at = new Date(expiresAt);
      return { rowCount: 1 };
    }
    if (/UPDATE premium_grants SET revoked_at = now\(\)/.test(sql)) return { rowCount: 0 };
    if (/INSERT INTO premium_grants/.test(sql)) {
      const [userId, days, expiresAt] = params;
      state.grants[userId] = { id: `grant-${userId}`, duration_type: 'days', expires_at: new Date(expiresAt) };
      return { rowCount: 1 };
    }
    throw new Error('unhandled query: ' + sql);
  };
  return { query, connect: async () => ({ query, release() {} }), calls, state };
}

test('applyReferralCode rejects self, invalid and already-attributed', async () => {
  const pool = fakePool({ users: {
    alice: { id: 'alice', referral_code: 'ALICE01', referred_by: null, created_at: new Date() },
    bob: { id: 'bob', referral_code: 'BOB0001', referred_by: null, created_at: new Date() }
  } });
  assert.equal((await applyReferralCode(pool, { userId: 'alice', code: 'ALICE01' })).reason, 'self');
  assert.equal((await applyReferralCode(pool, { userId: 'bob', code: 'NOPE999' })).reason, 'invalid_code');
  const ok = await applyReferralCode(pool, { userId: 'bob', code: 'ALICE01' });
  assert.equal(ok.applied, true);
  assert.equal(ok.referrerId, 'alice');
  const again = await applyReferralCode(pool, { userId: 'bob', code: 'ALICE01' });
  assert.equal(again.reason, 'already_attributed');
});

test('applyReferralCode enforces the manual signup window', async () => {
  const old = new Date(Date.now() - 30 * 86400000);
  const pool = fakePool({ users: {
    alice: { id: 'alice', referral_code: 'ALICE01', referred_by: null, created_at: new Date() },
    old_user: { id: 'old_user', referral_code: null, referred_by: null, created_at: old }
  } });
  const res = await applyReferralCode(pool, { userId: 'old_user', code: 'ALICE01', requireWithinSignupWindow: true });
  assert.equal(res.reason, 'window_expired');
});

test('grantBonusDays creates then extends, and skips an active lifetime grant', async () => {
  const pool = fakePool();
  const first = await grantBonusDays(pool, 'alice', 1, 'r1');
  assert.equal(first.created, true);
  const before = pool.state.grants.alice.expires_at.getTime();
  const second = await grantBonusDays(pool, 'alice', 7, 'r2');
  assert.equal(second.extended, true);
  assert.equal(pool.state.grants.alice.expires_at.getTime(), before + 7 * 86400000);

  pool.state.grants.lifetime_user = { id: 'g2', duration_type: 'lifetime', expires_at: null };
  const skipped = await grantBonusDays(pool, 'lifetime_user', 1, 'r3');
  assert.equal(skipped.alreadyLifetime, true);
});

test('rewardFirstAnalysis only fires on the very first analysis, once, and grants the referrer', async () => {
  const pool = fakePool({
    users: { bob: { id: 'bob', referred_by: 'alice', referral_analysis_rewarded_at: null } },
    analysesCountByUser: { bob: 1 }
  });
  await rewardFirstAnalysis(pool, 'bob');
  assert.ok(pool.state.grants.alice, 'referrer should receive a bonus grant');
  assert.equal(pool.state.grants.alice.duration_type, 'days');
  const grantsAfterFirst = JSON.stringify(pool.state.grants.alice);

  pool.state.analysesCountByUser.bob = 2; // a later analysis must never re-trigger
  await rewardFirstAnalysis(pool, 'bob');
  assert.equal(JSON.stringify(pool.state.grants.alice), grantsAfterFirst);
});

test('rewardFirstAnalysis does nothing without a referrer', async () => {
  const pool = fakePool({ users: { solo: { id: 'solo', referred_by: null } }, analysesCountByUser: { solo: 1 } });
  await rewardFirstAnalysis(pool, 'solo');
  assert.deepEqual(pool.state.grants, {});
});

test('rewardFirstSubscription grants once and is idempotent', async () => {
  const pool = fakePool({ users: { bob: { id: 'bob', referred_by: 'alice', referral_subscription_rewarded_at: null } } });
  await rewardFirstSubscription(pool, 'bob');
  assert.ok(pool.state.grants.alice);
  const snap = JSON.stringify(pool.state.grants.alice);
  await rewardFirstSubscription(pool, 'bob'); // renewal / re-verify must not pay out twice
  assert.equal(JSON.stringify(pool.state.grants.alice), snap);
});

test('assignReferralCode retries past a collision', async () => {
  let calls = 0;
  const pool = { query: async (sql, params) => {
    if (/UPDATE users SET referral_code/.test(sql)) {
      calls++;
      if (calls === 1) { const e = new Error('dup'); e.code = '23505'; throw e; }
      return { rows: [{ referral_code: params[0] }] };
    }
    throw new Error('unexpected: ' + sql);
  } };
  const code = await assignReferralCode(pool, 'x');
  assert.equal(calls, 2);
  assert.equal(code.length, 7);
});

test('paid referrer bonus starts after the current paid entitlement', async () => {
  const paidUntil = new Date(Date.now() + 30 * 86400000);
  const pool = fakePool({ users: { alice: { id: 'alice', is_premium: true, premium_expires_at: paidUntil } } });
  await grantBonusDays(pool, 'alice', 7, 'referral paid');
  assert.ok(pool.state.grants.alice.expires_at.getTime() >= paidUntil.getTime() + 7 * 86400000 - 1000);
});

test('invite code cannot be retroactively attached after analysis or purchase', async () => {
  const pool = fakePool({ users: {
    alice: { id: 'alice', referral_code: 'ALICE01', created_at: new Date() },
    analyzed: { id: 'analyzed', created_at: new Date() },
    subscribed: { id: 'subscribed', created_at: new Date(), is_premium: true }
  }, analysesCountByUser: { analyzed: 1 } });
  assert.equal((await applyReferralCode(pool, { userId: 'analyzed', code: 'ALICE01', requireWithinSignupWindow: true })).reason, 'already_active');
  assert.equal((await applyReferralCode(pool, { userId: 'subscribed', code: 'ALICE01', requireWithinSignupWindow: true })).reason, 'already_active');
});
