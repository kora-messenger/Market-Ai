const test = require('node:test');
const assert = require('node:assert/strict');
const {
  computeR,
  validateJournalInput,
  recomputeR,
  journalToApi,
  computeJournalStats
} = require('../src/tradeJournal');

test('computes R from the trader\'s own stop distance in both directions', () => {
  assert.equal(computeR('BUY', 100, 95, 110), 2);       // +5 risk, +10 reward
  assert.equal(computeR('SELL', 100, 105, 92.5), 1.5);   // 5 risk, 7.5 reward
  assert.equal(computeR('buy', 100, 95, 95), -1);      // full stop-out
  assert.equal(computeR('BUY', 100, null, 110), null); // no stop recorded
  assert.equal(computeR('BUY', 100, 105, 110), null);  // stop on wrong side = data error
  assert.equal(computeR('BUY', null, 95, 110), null);  // missing legs
});

test('validates and normalizes a full journal payload', () => {
  const ok = validateJournalInput({
    instrument: '  XAU/USD ', direction: 'buy', entryPrice: '2400.5',
    exitPrice: 2450, stopLoss: 2390, takeProfit: 2450, positionSize: '0.5',
    pnl: 250, riskAmount: 50, assetClass: 'FX',
    setupTag: ' Breakout ', notes: 'notes', lesson: 'lesson'
  });
  assert.ok(!ok.error, ok.error);
  assert.deepEqual(ok.fields, {
    instrument: 'XAU/USD',
    direction: 'BUY',
    entry_price: 2400.5,
    exit_price: 2450,
    stop_loss: 2390,
    take_profit: 2450,
    position_size: 0.5,
    pnl: 250,
    risk_amount: 50,
    asset_class: 'fx',
    setup_tag: 'Breakout',
    notes: 'notes',
    lesson: 'lesson',
    status: 'closed'
  });
});

test('an entry without an exit price stays open and rejects junk numbers', () => {
  const open = validateJournalInput({ instrument: 'AAPL', direction: 'SELL', entryPrice: 220 });
  assert.ok(!open.error, open.error);
  assert.equal(open.fields.status, 'open');
  assert.equal(open.fields.exit_price, undefined);
  assert.equal(open.fields.asset_class, 'other');

  assert.ok(validateJournalInput({ instrument: '', direction: 'BUY', entryPrice: 1 }).error);
  assert.ok(validateJournalInput({ instrument: 'X', direction: 'HOLD', entryPrice: 1 }).error);
  assert.ok(validateJournalInput({ instrument: 'X', direction: 'BUY', entryPrice: 0 }).error);
  assert.ok(validateJournalInput({ instrument: 'X', direction: 'BUY', entryPrice: 'abc' }).error);
  assert.ok(validateJournalInput({ instrument: 'X', direction: 'BUY', entryPrice: 1, exitPrice: -2 }).error);
  assert.ok(validateJournalInput({ instrument: 'X', direction: 'BUY', entryPrice: 1, assetClass: 'options' }).error);
});

test('partial updates only touch provided fields and recompute R', () => {
  const partial = validateJournalInput({ exitPrice: 110 }, { partial: true });
  assert.ok(!partial.error, partial.error);
  assert.equal(partial.fields.instrument, undefined);
  assert.equal(partial.fields.status, 'closed');

  const row = { direction: 'BUY', entry_price: 100, stop_loss: 95, exit_price: null };
  const fields = recomputeR(row, partial.fields);
  assert.equal(fields.r_multiple, 2);
  assert.equal(recomputeR({ ...row, exit_price: 110 }, { exit_price: null }).r_multiple, null);
});

test('clearing the exit reopens the trade', () => {
  const cleared = validateJournalInput({ exitPrice: '' }, { partial: true });
  assert.ok(!cleared.error, cleared.error);
  assert.equal(cleared.fields.exit_price, null);
  assert.equal(cleared.fields.status, 'open');
});

test('stats never turn an undecided closed trade into a win or loss', () => {
  const rows = [
    { status: 'open', r_multiple: null, pnl: null },
    { status: 'closed', r_multiple: 2, pnl: null },
    { status: 'closed', r_multiple: -1, pnl: null },
    { status: 'closed', r_multiple: null, pnl: 40 },
    { status: 'closed', r_multiple: null, pnl: -10 },
    { status: 'closed', r_multiple: null, pnl: null } // no R, no PnL -> undecided
  ];
  assert.deepEqual(computeJournalStats(rows), {
    total: 6, openTrades: 1, closedTrades: 5,
    wins: 2, losses: 2, winRate: 50,
    avgR: 0.5, bestR: 2, worstR: -1, totalPnl: 30
  });
  assert.equal(computeJournalStats([]).winRate, null);
  assert.equal(computeJournalStats([{ status: 'closed', r_multiple: null, pnl: null }]).closedTrades, 1);
});

test('api shape uses camelCase and passes values through untouched', () => {
  const api = journalToApi({
    id: 'id-1', instrument: 'EUR/USD', asset_class: 'fx', direction: 'BUY',
    entry_price: 1.1, exit_price: 1.12, stop_loss: 1.09, take_profit: 1.12,
    position_size: 1, risk_amount: 100, pnl: 200, r_multiple: 2, status: 'closed',
    setup_tag: 't', notes: 'n', lesson: 'l', opened_at: 'o', closed_at: 'c', created_at: 'cr'
  });
  assert.deepEqual(api, {
    id: 'id-1', instrument: 'EUR/USD', assetClass: 'fx', direction: 'BUY',
    entryPrice: 1.1, exitPrice: 1.12, stopLoss: 1.09, takeProfit: 1.12,
    positionSize: 1, riskAmount: 100, pnl: 200, rMultiple: 2, status: 'closed',
    setupTag: 't', notes: 'n', lesson: 'l', openedAt: 'o', closedAt: 'c', createdAt: 'cr'
  });
});
