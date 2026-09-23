"use strict";

// Provider usage, not an estimate. Never persist prompts, images, API keys or replies.
function tokenCount(value) {
  return Number.isSafeInteger(value) && value >= 0 ? value : null;
}

function usageFields(body) {
  const usage = body && body.usage || {};
  return {
    inputTokens: tokenCount(usage.prompt_tokens ?? usage.input_tokens),
    outputTokens: tokenCount(usage.completion_tokens ?? usage.output_tokens)
  };
}

async function recordCompletedAiCall(pool, response, { userId = null, userSub = null, feature, provider, model }) {
  if (!pool) return;
  let body;
  try { body = await response.clone().json(); }
  catch (err) { console.error('[ai-usage] provider response could not be read:', String(err.message || err)); }
  const { inputTokens, outputTokens } = usageFields(body);
  const actualModel = typeof body?.model === 'string' ? body.model : model;
  try {
    // Resolve the signed-in user at insertion time if the route did not already
    // load their UUID. An unresolvable account is not quietly called "system".
    if (userSub && !userId) {
      const { rows } = await pool.query('SELECT id FROM users WHERE google_sub = $1', [userSub]);
      if (!rows.length) throw new Error('user not found for completed call');
      userId = rows[0].id;
    }
    await pool.query(
      `INSERT INTO ai_usage_events (user_id, feature, provider, model, input_tokens, output_tokens)
       VALUES ($1, $2, $3, $4, $5, $6)`,
      [userId, feature, provider, actualModel, inputTokens, outputTokens]
    );
  } catch (err) {
    // Logging must never turn a successful analysis into a failed one.
    console.error('[ai-usage] completed call could not be recorded:', String(err.message || err));
  }
}

module.exports = { tokenCount, usageFields, recordCompletedAiCall };
