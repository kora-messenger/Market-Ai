/**
 * Pure helpers for the Premium news digest — separated from server.js so
 * the selection + copy logic is unit-testable without a database.
 */

/**
 * Pick the headlines worth pushing: newest-first, published within the
 * freshness window, capped at `max`. Items without a parseable publish
 * time are never pushed (no fabricated "news").
 */
function freshHeadlines(items, nowMs, windowMs, max = 3) {
  const cutoff = nowMs - windowMs;
  return items
    .filter((n) => n.publishedAt && !Number.isNaN(Date.parse(n.publishedAt)))
    .filter((n) => Date.parse(n.publishedAt) >= cutoff)
    .sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt))
    .slice(0, max);
}

/** Push body for a digest: the top headline plus an honest count of the rest. */
function digestBody(fresh) {
  if (!fresh.length) return null;
  if (fresh.length === 1) return fresh[0].title;
  const rest = fresh.length - 1;
  return `${fresh[0].title} +${rest} more market headline${rest > 1 ? "s" : ""}`;
}

module.exports = { freshHeadlines, digestBody };
