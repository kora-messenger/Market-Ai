"use strict";

/**
 * Trade journal domain helpers — pure functions shared by the /api/journal
 * routes and the unit tests. The journal closes the loop the research
 * identified: analysis -> actual trade -> outcome -> lesson. Nothing here
 * invents numbers; R-multiple is only computed when the trader recorded a
 * real stop distance and exit.
 */

// Number(null) === 0 in JS, so blank values must be excluded before
// coercion or a missing stop/exit silently becomes a real 0.
function coerceNum(value) {
  if (value === null || value === undefined || value === "" || typeof value === "boolean") return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

/** Finite positive number or null. Never coerces junk into 0. */
function positiveNum(value) {
  const n = coerceNum(value);
  return n != null && n > 0 ? n : null;
}

/** Finite number (any sign) or null. */
function anyNum(value) {
  return coerceNum(value);
}

/**
 * R-multiple of a CLOSED trade, from the trader's own numbers.
 *   BUY:  risk = entry - stop, R = (exit - entry) / risk
 *   SELL: risk = stop - entry, R = (entry - exit) / risk
 * Returns null whenever any leg is missing or the stop sits on the wrong
 * side (risk <= 0) — a wrong-side stop is a data error, not a zero-R trade.
 */
function computeR(direction, entry, stop, exit) {
  const dir = String(direction || "").toUpperCase();
  const e = anyNum(entry);
  const s = anyNum(stop);
  const x = anyNum(exit);
  if (e == null || s == null || x == null) return null;
  const risk = dir === "BUY" ? e - s : dir === "SELL" ? s - e : NaN;
  if (!Number.isFinite(risk) || risk <= 0) return null;
  const r = dir === "BUY" ? (x - e) / risk : (e - x) / risk;
  return Number.isFinite(r) ? Math.round(r * 100) / 100 : null;
}

const ASSET_CLASSES = new Set(["fx", "crypto", "stock", "other"]);

/**
 * Normalize + validate a journal entry payload. Returns
 * { error } on failure, or { fields } with the exact columns to write.
 * When the payload carries an exit price the trade is closed; otherwise it
 * stays open. A closed trade's R-multiple is always recomputed server-side.
 */
function validateJournalInput(body, { partial = false } = {}) {
  const b = body || {};
  const out = {};

  if (!partial || b.instrument !== undefined) {
    const instrument = String(b.instrument || "").trim();
    if (!instrument) return { error: "Instrument is required." };
    if (instrument.length > 60) return { error: "Instrument name is too long (max 60 characters)." };
    out.instrument = instrument;
  }

  if (!partial || b.direction !== undefined) {
    const direction = String(b.direction || "").trim().toUpperCase();
    if (direction !== "BUY" && direction !== "SELL") {
      return { error: "Direction must be buy or sell." };
    }
    out.direction = direction;
  }

  if (!partial || b.entryPrice !== undefined) {
    const entry = positiveNum(b.entryPrice);
    if (entry == null) return { error: "A valid entry price is required." };
    out.entry_price = entry;
  }

  for (const [field, column] of [
    ["exitPrice", "exit_price"],
    ["stopLoss", "stop_loss"],
    ["takeProfit", "take_profit"],
    ["positionSize", "position_size"]
  ]) {
    if (b[field] !== undefined && b[field] !== null && String(b[field]).trim() !== "") {
      const n = positiveNum(b[field]);
      if (n == null) return { error: `${field} must be a positive number.` };
      out[column] = n;
    } else if (b[field] !== undefined) {
      // explicit null/"" clears the field on update
      out[column] = null;
    }
  }

  for (const [field, column] of [["pnl", "pnl"], ["riskAmount", "risk_amount"]]) {
    if (b[field] !== undefined && b[field] !== null && String(b[field]).trim() !== "") {
      const n = anyNum(b[field]);
      if (n == null) return { error: `${field} must be a number.` };
      out[column] = n;
    } else if (b[field] !== undefined) {
      out[column] = null;
    }
  }

  if (b.assetClass !== undefined) {
    const assetClass = String(b.assetClass || "other").toLowerCase().trim();
    if (!ASSET_CLASSES.has(assetClass)) return { error: "Asset class must be fx, crypto, stock or other." };
    out.asset_class = assetClass;
  } else if (!partial) {
    out.asset_class = "other";
  }

  for (const [field, column, max] of [
    ["setupTag", "setup_tag", 60],
    ["notes", "notes", 2000],
    ["lesson", "lesson", 2000]
  ]) {
    if (b[field] !== undefined) {
      const v = String(b[field] || "").trim();
      out[column] = v ? v.slice(0, max) : null;
    }
  }

  // Status is derived, never trusted from the client: a trade with an exit
  // price is closed, without one it is still open.
  if ("exit_price" in out) {
    out.status = out.exit_price != null ? "closed" : "open";
  } else if (!partial) {
    out.status = out.exit_price != null ? "closed" : "open";
  }

  return { fields: out };
}

/** Merge the validated fields with the stored row for R recomputation. */
function recomputeR(row, fields) {
  const merged = { ...row, ...fields };
  const r = computeR(merged.direction, merged.entry_price, merged.stop_loss, merged.exit_price);
  fields.r_multiple = r;
  return fields;
}

/** One journal row in the API shape consumed by the Android app. */
function journalToApi(row) {
  return {
    id: row.id,
    instrument: row.instrument,
    assetClass: row.asset_class,
    direction: row.direction,
    entryPrice: row.entry_price,
    exitPrice: row.exit_price,
    stopLoss: row.stop_loss,
    takeProfit: row.take_profit,
    positionSize: row.position_size,
    riskAmount: row.risk_amount,
    pnl: row.pnl,
    rMultiple: row.r_multiple,
    status: row.status,
    setupTag: row.setup_tag,
    notes: row.notes,
    lesson: row.lesson,
    openedAt: row.opened_at,
    closedAt: row.closed_at,
    createdAt: row.created_at
  };
}

/**
 * Honest stats over the trader's own journal — the "outcome" half of the
 * loop. Wins are decided by R-multiple when it exists, otherwise by recorded
 * P&L; a closed trade without either is counted as undecided, never silently
 * turned into a win or a loss.
 */
function computeJournalStats(rows) {
  const entries = Array.isArray(rows) ? rows : [];
  const open = entries.filter((r) => r.status === "open").length;
  const closed = entries.filter((r) => r.status === "closed");
  const decided = closed.filter((r) => {
    const r2 = anyNum(r.r_multiple);
    if (r2 != null) return true;
    const p = anyNum(r.pnl);
    return p != null && p !== 0;
  });
  const wins = decided.filter((r) => {
    const r2 = anyNum(r.r_multiple);
    if (r2 != null) return r2 > 0;
    return anyNum(r.pnl) > 0;
  }).length;
  const losses = decided.length - wins;
  const rValues = closed.map((r) => anyNum(r.r_multiple)).filter((v) => v != null);
  const pnlValues = closed.map((r) => anyNum(r.pnl)).filter((v) => v != null && v !== 0);
  const avg = (list) => (list.length ? Number((list.reduce((a, b) => a + b, 0) / list.length).toFixed(2)) : null);
  return {
    total: entries.length,
    openTrades: open,
    closedTrades: closed.length,
    wins,
    losses,
    winRate: decided.length ? Math.round((wins / decided.length) * 100) : null,
    avgR: avg(rValues),
    bestR: rValues.length ? Math.max(...rValues) : null,
    worstR: rValues.length ? Math.min(...rValues) : null,
    totalPnl: pnlValues.length ? Number(pnlValues.reduce((a, b) => a + b, 0).toFixed(2)) : null
  };
}

module.exports = {
  positiveNum,
  anyNum,
  computeR,
  validateJournalInput,
  recomputeR,
  journalToApi,
  computeJournalStats,
  ASSET_CLASSES
};
