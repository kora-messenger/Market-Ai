"use strict";

// Only signed-in users can schedule/cancel deletion. This worker processes
// requests after 30 full days, one locked account per transaction. Content
// objects are queued transactionally and retried until R2 confirms removal.
const OBJECT_QUEUE_DDL = `CREATE TABLE IF NOT EXISTS account_deletion_objects (
  object_key TEXT PRIMARY KEY,
  queued_at TIMESTAMPTZ NOT NULL DEFAULT now()
)`;
const DUE = "deletion_requested_at IS NOT NULL AND deletion_requested_at <= now() - interval '30 days'";
const DIRECT_USER_TABLES = Object.freeze([
  "analyses", "subscription_payments", "trade_plans", "signal_reactions",
  "signal_saves", "signal_comment_reactions", "post_poll_votes",
  "post_reactions", "post_views", "push_log"
]);

async function processExpiredDeletions(pool, r2, { preview = false, limit = 25 } = {}) {
  if (!pool) throw new Error("Database is not configured");
  if (!Number.isInteger(limit) || limit < 1 || limit > 25) throw new Error("Invalid deletion batch limit");
  const due = await pool.query(`SELECT COUNT(*)::int AS count FROM users WHERE ${DUE}`);
  const dueCount = due.rows[0].count;
  if (preview) return { dueCount, processed: 0, preview: true };
  await pool.query(OBJECT_QUEUE_DDL);
  let processed = 0;
  for (let i = 0; i < limit; i++) {
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const { rows } = await client.query(
        `SELECT id, email, avatar_key FROM users WHERE ${DUE}
         ORDER BY deletion_requested_at, id FOR UPDATE SKIP LOCKED LIMIT 1`
      );
      if (!rows.length) {
        await client.query("COMMIT");
        break;
      }
      const { id, email, avatar_key: avatarKey } = rows[0];
      const keys = new Set(avatarKey ? [avatarKey] : []);
      const byEmail = String(email || "").trim().toLowerCase();
      const args = [id, byEmail];

      // Query R2 keys BEFORE relational rows are removed. Reposts have their
      // own independent object keys and are included if the trader authored
      // the original or posted the repost.
      const postImages = await client.query(
        `SELECT i.r2_key FROM community_post_images i JOIN community_posts p ON p.id = i.post_id
         WHERE p.user_id = $1 OR ($2 <> '' AND (lower(p.author_email) = $2 OR lower(COALESCE(p.reposted_by_email,'')) = $2))`, args
      );
      const proofImages = await client.query(
        `SELECT i.r2_key FROM signal_testimonial_images i JOIN signal_testimonials t ON t.id = i.testimonial_id
         WHERE t.user_id = $1 OR ($2 <> '' AND lower(t.author_email) = $2)`, args
      );
      const reports = await client.query(
        `SELECT attachments FROM bug_reports WHERE user_id = $1 OR ($2 <> '' AND lower(user_email) = $2)`, args
      );
      for (const row of [...postImages.rows, ...proofImages.rows]) if (row.r2_key) keys.add(row.r2_key);
      for (const row of reports.rows) {
        const attachments = typeof row.attachments === "string" ? JSON.parse(row.attachments) : row.attachments;
        if (Array.isArray(attachments)) for (const a of attachments) if (a && a.r2Key) keys.add(a.r2Key);
      }
      for (const key of keys) {
        await client.query(`INSERT INTO account_deletion_objects (object_key) VALUES ($1) ON CONFLICT DO NOTHING`, [key]);
      }

      // Remove the member's authored public and private content before the
      // user row. Child images, replies and reactions cascade with each post.
      await client.query(`DELETE FROM signal_updates WHERE $2 <> '' AND lower(author_email) = $2`, args);
      await client.query(`DELETE FROM signal_comments WHERE user_id = $1 OR ($2 <> '' AND lower(author_email) = $2)`, args);
      await client.query(`DELETE FROM signal_testimonials WHERE user_id = $1 OR ($2 <> '' AND lower(author_email) = $2)`, args);
      await client.query(`DELETE FROM post_comments WHERE user_id = $1 OR ($2 <> '' AND lower(author_email) = $2)`, args);
      await client.query(`DELETE FROM community_posts WHERE user_id = $1 OR ($2 <> '' AND (lower(author_email) = $2 OR lower(COALESCE(reposted_by_email,'')) = $2))`, args);
      await client.query(`DELETE FROM bug_reports WHERE user_id = $1 OR ($2 <> '' AND lower(user_email) = $2)`, args);
      await client.query(`DELETE FROM premium_audit WHERE target_user_id = $1 OR admin_user_id = $1 OR ($2 <> '' AND (lower(COALESCE(target_email,'')) = $2 OR lower(COALESCE(admin_email,'')) = $2))`, args);
      // Grants issued to *other* members must remain in force, without the
      // deleted grantor's identity or a dangling foreign key.
      await client.query(`UPDATE premium_grants SET granted_by = NULL, granted_by_email = NULL, revoked_by = NULL WHERE granted_by = $1 OR revoked_by = $1`, [id]);
      for (const table of DIRECT_USER_TABLES) {
        await client.query(`DELETE FROM ${table} WHERE user_id = $1`, [id]);
      }
      // DM threads can contain other members' messages. Remove the deleted
      // member's messages, anonymize their side, preserve other messages.
      const dmExists = await client.query(`SELECT to_regclass('dm_threads') AS table_name`);
      if (dmExists.rows[0].table_name && byEmail) {
        await client.query(`DELETE FROM dm_messages WHERE lower(sender_email) = $1`, [byEmail]);
        await client.query(
          `UPDATE dm_threads SET user_email = CASE WHEN lower(user_email) = $1 THEN 'deleted-' || id::text || '@invalid.local' ELSE user_email END,
             mentor_email = CASE WHEN lower(mentor_email) = $1 THEN 'deleted-' || id::text || '@invalid.local' ELSE mentor_email END,
             last_message = NULL, last_sender = NULL
           WHERE lower(user_email) = $1 OR lower(mentor_email) = $1`, [byEmail]
        );
      }
      const removed = await client.query(`DELETE FROM users WHERE id = $1 AND ${DUE}`, [id]);
      if (removed.rowCount !== 1) throw new Error("Deletion request changed before commit");
      await client.query("COMMIT");
      processed++;
    } catch (err) {
      await client.query("ROLLBACK").catch(() => {});
      throw err; // keep the request for the next run; never claim it was erased
    } finally {
      client.release();
    }
  }
  // R2 failures retain a durable queue entry for a later retry. Do not expose
  // object keys, identities or deleted emails in the endpoint response/logs.
  const { rows: objects } = await pool.query(`SELECT object_key FROM account_deletion_objects ORDER BY queued_at LIMIT 200`);
  let objectsRemoved = 0;
  for (const { object_key: key } of objects) {
    try {
      if (await r2.deleteObject(key)) {
        await pool.query(`DELETE FROM account_deletion_objects WHERE object_key = $1`, [key]);
        objectsRemoved++;
      }
    } catch (err) {
      console.warn("[account-deletion] object removal will retry:", String(err.message || err));
    }
  }
  const pending = await pool.query(`SELECT COUNT(*)::int AS count FROM account_deletion_objects`);
  return { dueCount, processed, objectsRemoved, objectsPending: pending.rows[0].count };
}

module.exports = { processExpiredDeletions };
