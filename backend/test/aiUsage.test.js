const test = require('node:test');
const assert = require('node:assert/strict');
const { tokenCount, usageFields, recordCompletedAiCall } = require('../src/aiUsage');

test('extracts actual provider usage without treating missing usage as zero', () => {
  assert.deepEqual(usageFields({ usage: { prompt_tokens: 123, completion_tokens: 45 } }), { inputTokens: 123, outputTokens: 45 });
  assert.deepEqual(usageFields({ usage: { input_tokens: 3, output_tokens: 0 } }), { inputTokens: 3, outputTokens: 0 });
  assert.deepEqual(usageFields({}), { inputTokens: null, outputTokens: null });
  assert.equal(tokenCount(-1), null);
});

test('records provider model, feature and user without consuming the caller response', async () => {
  const queries = [];
  const pool = { async query(sql, params) { queries.push({ sql, params }); return { rows: [] }; } };
  const response = new Response(JSON.stringify({ model: 'google/gemini-test', usage: { prompt_tokens: 1200, completion_tokens: 220 }, choices: [] }));
  await recordCompletedAiCall(pool, response, { userId: 'user-id', feature: 'analysis', provider: 'openrouter', model: 'alias' });
  assert.equal(queries.length, 1);
  assert.deepEqual(queries[0].params, ['user-id', 'analysis', 'openrouter', 'google/gemini-test', 1200, 220]);
  assert.deepEqual((await response.json()).choices, []);
});

test('resolves authenticated user and preserves unknown provider usage', async () => {
  const queries = [];
  const pool = { async query(sql, params) { queries.push({ sql, params }); return sql.startsWith('SELECT') ? { rows: [{ id: 'resolved-id' }] } : { rows: [] }; } };
  await recordCompletedAiCall(pool, new Response(JSON.stringify({ choices: [] })), { userSub: 'sub-id', feature: 'calendar', provider: 'openrouter', model: 'free-model' });
  assert.deepEqual(queries[1].params, ['resolved-id', 'calendar', 'openrouter', 'free-model', null, null]);
});
