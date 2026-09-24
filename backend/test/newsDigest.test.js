const test = require('node:test');
const assert = require('node:assert/strict');
const { freshHeadlines, digestBody } = require('../src/newsDigest');

const H = 3600 * 1000;
const now = Date.parse('2026-09-24T12:00:00Z');

test('only keeps headlines inside the freshness window, newest first, capped', () => {
  const items = [
    { title: 'Old news', publishedAt: new Date(now - 30 * H).toISOString() },
    { title: 'Fresh A', publishedAt: new Date(now - 1 * H).toISOString() },
    { title: 'Fresh B', publishedAt: new Date(now - 3 * H).toISOString() },
    { title: 'Fresh C', publishedAt: new Date(now - 2 * H).toISOString() },
    { title: 'Fresh D', publishedAt: new Date(now - 7.9 * H).toISOString() },
    { title: 'No timestamp' },
    { title: 'Bad timestamp', publishedAt: 'not-a-date' }
  ];
  const fresh = freshHeadlines(items, now, 8 * H, 3);
  assert.deepEqual(fresh.map((n) => n.title), ['Fresh A', 'Fresh C', 'Fresh B']);
});

test('items exactly at the window edge are still fresh', () => {
  const items = [{ title: 'Edge', publishedAt: new Date(now - 8 * H).toISOString() }];
  assert.equal(freshHeadlines(items, now, 8 * H, 3).length, 1);
});

test('empty feed or stale feed selects nothing', () => {
  assert.deepEqual(freshHeadlines([], now, 8 * H, 3), []);
  const stale = [{ title: 'Yesterday', publishedAt: new Date(now - 24 * H).toISOString() }];
  assert.deepEqual(freshHeadlines(stale, now, 8 * H, 3), []);
});

test('digest body is honest about the count', () => {
  const one = [{ title: 'Fed holds rates' }];
  assert.equal(digestBody(one), 'Fed holds rates');
  const two = [{ title: 'Fed holds rates' }, { title: 'Gold rallies' }];
  assert.equal(digestBody(two), 'Fed holds rates +1 more market headline');
  const three = [{ title: 'A' }, { title: 'B' }, { title: 'C' }];
  assert.equal(digestBody(three), 'A +2 more market headlines');
  assert.equal(digestBody([]), null);
});
