const test = require('node:test');
const assert = require('node:assert/strict');
const { processExpiredDeletions } = require('../src/accountDeletion');

function fakeDb({ dueCount = 0, user = null, failDelete = false } = {}) {
  const queries = [];
  let selected = false;
  const query = async (sql, params) => {
    queries.push({ sql, params });
    if (/COUNT\(\*\).*users/.test(sql)) return { rows: [{ count: dueCount }] };
    if (/SELECT id, email, avatar_key FROM users/.test(sql)) {
      if (selected || !user) return { rows: [] };
      selected = true;
      return { rows: [user] };
    }
    if (/to_regclass/.test(sql)) return { rows: [{ table_name: null }] };
    if (/SELECT object_key/.test(sql)) return { rows: [{ object_key: 'avatars/test.jpg' }] };
    if (/COUNT\(\*\).*account_deletion_objects/.test(sql)) return { rows: [{ count: 0 }] };
    if (/DELETE FROM users/.test(sql)) {
      if (failDelete) throw new Error('fk test failure');
      return { rowCount: 1, rows: [] };
    }
    return { rows: [], rowCount: 0 };
  };
  const client = { query, release() {} };
  return { db: { query, connect: async () => client }, queries };
}

test('preview counts only due requests and cannot mutate', async () => {
  const { db, queries } = fakeDb({ dueCount: 2 });
  const result = await processExpiredDeletions(db, {}, { preview: true });
  assert.deepEqual(result, { dueCount: 2, processed: 0, preview: true });
  assert.equal(queries.length, 1);
  assert.match(queries[0].sql, /deletion_requested_at <= now\(\) - interval '30 days'/);
});

test('deletes one due user and its authored content but not all users', async () => {
  const { db, queries } = fakeDb({ dueCount: 1, user: { id: 'user-1', email: '', avatar_key: 'avatars/test.jpg' } });
  const r2 = { deleteObject: async key => key === 'avatars/test.jpg' };
  const result = await processExpiredDeletions(db, r2);
  assert.equal(result.processed, 1);
  assert.equal(result.objectsRemoved, 1);
  const sql = queries.map(q => q.sql).join('\n');
  assert.match(sql, /FOR UPDATE SKIP LOCKED/);
  assert.match(sql, /WHERE \$2 <> '' AND lower\(author_email\) = \$2/);
  assert.match(sql, /DELETE FROM users WHERE id = \$1 AND deletion_requested_at/);
  assert.ok(queries.some(q => /COMMIT/.test(q.sql)));
});

test('failed deletion rolls back and keeps the request to retry', async () => {
  const { db, queries } = fakeDb({ dueCount: 1, user: { id: 'user-1', email: 'a@example.com', avatar_key: null }, failDelete: true });
  await assert.rejects(() => processExpiredDeletions(db, { deleteObject: async () => true }), /fk test failure/);
  assert.ok(queries.some(q => q.sql === 'ROLLBACK'));
  assert.ok(!queries.some(q => q.sql === 'COMMIT'));
});
