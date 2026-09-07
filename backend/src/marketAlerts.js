/**
 * MarketScope AI — automatic market alert engine.
 *
 * Runs on a 15-minute cron (POST /api/market-alerts/cron) and pushes
 * notifications for:
 *   1. Big price moves — coins / forex / gold / US indices that moved
 *      beyond their threshold over the last 24 hours.
 *   2. Market sessions — London and New York forex session opens/closes.
 *   3. US stock market open/close (NYSE), with weekend + holiday breaks.
 *   4. US federal holidays — "banks and businesses are on break" days,
 *      computed from the real holiday rules (no hardcoded fake dates).
 *   5. Forex weekend break (Fri 21:00 UTC close → Sun 21:00 UTC reopen).
 *
 * Dedupe/state lives in the alert_state table; a user is notified only on
 * a session transition or a mover that hasn't alerted in the last 6 hours.
 * All price data comes from the same public feeds used by daily signals.
 */

const { yahooSymbol, COINGECKO_IDS } = require("./prices");

// Binance 24hr ticker fallback when CoinGecko rate-limits datacenter IPs
const BINANCE_SYMBOLS = {
  btcusd: "BTCUSDT", ethusd: "ETHUSDT", solusd: "SOLUSDT",
  dogeusd: "DOGEUSDT", xrpusd: "XRPUSDT"
};

const MOVER_GAP_MS = 6 * 60 * 60 * 1000; // min time between alerts per instrument

// Watchlist: coins, forex majors, gold, US stock benchmarks.
const MOVER_WATCHLIST = [
  { id: "btcusd", name: "Bitcoin", kind: "crypto", thresholdPct: 4.0 },
  { id: "ethusd", name: "Ethereum", kind: "crypto", thresholdPct: 4.5 },
  { id: "solusd", name: "Solana", kind: "crypto", thresholdPct: 5.0 },
  { id: "dogeusd", name: "Dogecoin", kind: "crypto", thresholdPct: 6.0 },
  { id: "xrpusd", name: "XRP", kind: "crypto", thresholdPct: 5.0 },
  { id: "eurusd", name: "EUR/USD", kind: "forex", thresholdPct: 0.7 },
  { id: "gbpusd", name: "GBP/USD", kind: "forex", thresholdPct: 0.7 },
  { id: "usdjpy", name: "USD/JPY", kind: "forex", thresholdPct: 0.7 },
  { id: "xauusd", name: "Gold (XAU/USD)", kind: "metal", thresholdPct: 1.0 },
  { id: "us500", name: "S&P 500", kind: "index", thresholdPct: 1.0 },
  { id: "nas100", name: "Nasdaq 100", kind: "index", thresholdPct: 1.2 },
  { id: "us30", name: "Dow Jones", kind: "index", thresholdPct: 1.0 }
];

// ---------- alert_state helpers ----------

async function getState(pool, key) {
  const { rows } = await pool.query(`SELECT value FROM alert_state WHERE key = $1`, [key]);
  if (!rows.length) return null;
  const v = rows[0].value;
  // pg returns JSONB parsed, but be defensive in case of string values
  if (typeof v === "string") {
    try { return JSON.parse(v); } catch (_e) { return null; }
  }
  return v;
}

async function setState(pool, key, value) {
  await pool.query(
    `INSERT INTO alert_state (key, value, updated_at) VALUES ($1, $2, now())
     ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = now()`,
    [key, JSON.stringify(value)]
  );
}

// ---------- price movers ----------

async function fetchJson(url) {
  const res = await fetch(url, {
    signal: AbortSignal.timeout(8000),
    headers: { "User-Agent": "Mozilla/5.0 (compatible; MarketAiBot/1.0)", Accept: "application/json" }
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

/** 24h change (%) + current price for a coin. CoinGecko -> Binance fallback. */
async function coinMoverData(id) {
  const coin = COINGECKO_IDS[id];
  if (coin) {
    try {
      const data = await fetchJson(
        `https://api.coingecko.com/api/v3/simple/price?ids=${coin}&vs_currencies=usd&include_24hr_change=true`
      );
      const node = data && data[coin];
      if (node && typeof node.usd === "number" && typeof node.usd_24h_change === "number") {
        return { price: node.usd, pct: node.usd_24h_change };
      }
    } catch (e) {
      console.warn(`mover ${id}: coingecko failed (${String(e.message || e)})`);
    }
  }
  const bns = BINANCE_SYMBOLS[id];
  if (!bns) return null;
  try {
    const t = await fetchJson(`https://api.binance.com/api/v3/ticker/24hr?symbol=${bns}`);
    const price = Number(t.lastPrice);
    const pct = Number(t.priceChangePercent);
    if (Number.isFinite(price) && Number.isFinite(pct)) return { price, pct };
  } catch (e) {
    console.warn(`mover ${id}: binance failed (${String(e.message || e)})`);
  }
  return null;
}

/** Forex daily change fallback via Frankfurter (ECB) time series. */
async function frankfurterMoverData(id) {
  const base = id.slice(0, 3).toUpperCase();
  const quote = id.slice(3).toUpperCase();
  const yesterday = new Date(Date.now() - 36 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const data = await fetchJson(`https://api.frankfurter.app/${yesterday}..?base=${base}&symbols=${quote}`);
  const dates = Object.keys((data && data.rates) || {}).sort();
  if (dates.length === 0) return null;
  const first = data.rates[dates[0]][quote];
  const last = data.rates[dates[dates.length - 1]][quote];
  if (!first || !last) return null;
  return { price: last, pct: ((last - first) / first) * 100 };
}

/** Daily change (%) + current price for forex / gold / indices. Yahoo primary. */
async function yahooMoverData(id) {
  const symbol = yahooSymbol(id);
  if (!symbol) return null;
  try {
    const data = await fetchJson(
      `https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(symbol)}?interval=5m&range=1d`
    );
    const meta = data && data.chart && data.chart.result && data.chart.result[0] && data.chart.result[0].meta;
    if (!meta || typeof meta.regularMarketPrice !== "number") throw new Error("no meta");
    const prev = meta.chartPreviousClose ?? meta.previousClose;
    if (typeof prev !== "number" || prev <= 0) throw new Error("no previous close");
    return { price: meta.regularMarketPrice, pct: ((meta.regularMarketPrice - prev) / prev) * 100 };
  } catch (e) {
    console.warn(`mover ${id}: yahoo failed (${String(e.message || e)})`);
    if (/^[a-z]{6}$/.test(id)) {
      try { return await frankfurterMoverData(id); } catch (_e) { /* give up this run */ }
    }
    return null;
  }
}

function formatPrice(kind, price) {
  if (kind === "forex") return price.toFixed(4);
  if (kind === "metal") return "$" + price.toFixed(2);
  if (kind === "index") return price.toLocaleString("en-US", { maximumFractionDigits: 2 });
  if (price >= 100) return "$" + price.toFixed(2);
  return "$" + price.toFixed(4);
}

async function checkMovers(pool, notify) {
  const events = [];
  for (const m of MOVER_WATCHLIST) {
    let data = null;
    try {
      data = m.kind === "crypto" ? await coinMoverData(m.id) : await yahooMoverData(m.id);
    } catch (_e) { /* feed hiccup — skip this run */ }
    if (!data || data.pct == null || !Number.isFinite(data.pct)) continue;

    const state = await getState(pool, `mover:${m.id}`);
    const lastAlertAt = state && state.lastAlertAt ? new Date(state.lastAlertAt).getTime() : 0;
    if (Date.now() - lastAlertAt < MOVER_GAP_MS) continue;

    const up = data.pct > 0;
    if (Math.abs(data.pct) < m.thresholdPct) continue;

    const arrow = up ? "\u2191" : "\u2193";
    const verb = up ? "up" : "down";
    notify(
      `Market Move: ${m.name} ${arrow} ${Math.abs(data.pct).toFixed(1)}%`,
      `${m.name} is ${verb} ${Math.abs(data.pct).toFixed(2)}% over the last 24 hours — now ${formatPrice(m.kind, data.price)}.`
    );
    await setState(pool, `mover:${m.id}`, { lastAlertAt: new Date().toISOString(), pct: data.pct });
    events.push({ id: m.id, pct: Number(data.pct.toFixed(2)), price: data.price });
  }
  return events;
}

// ---------- US market calendar ----------

function isoDate(d) {
  return d.toISOString().slice(0, 10);
}

function nthWeekdayOfMonth(year, month, weekday, n) {
  const d = new Date(Date.UTC(year, month, 1));
  let count = 0;
  while (true) {
    if (d.getUTCDay() === weekday) {
      count += 1;
      if (count === n) return d;
    }
    d.setUTCDate(d.getUTCDate() + 1);
  }
}

function lastWeekdayOfMonth(year, month, weekday) {
  const d = new Date(Date.UTC(year, month + 1, 0));
  while (d.getUTCDay() !== weekday) d.setUTCDate(d.getUTCDate() - 1);
  return d;
}

function observed(date) {
  const d = new Date(date);
  if (d.getUTCDay() === 6) d.setUTCDate(d.getUTCDate() - 1); // Sat -> Fri
  else if (d.getUTCDay() === 0) d.setUTCDate(d.getUTCDate() + 1); // Sun -> Mon
  return d;
}

function easterSunday(year) {
  const a = year % 19;
  const b = Math.floor(year / 100);
  const c = year % 100;
  const d = Math.floor(b / 4);
  const e = b % 4;
  const f = Math.floor((b + 8) / 25);
  const g = Math.floor((b - f + 1) / 3);
  const h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4);
  const k = c % 4;
  const l = (32 + 2 * e + 2 * i - h - k) % 7;
  const m = Math.floor((a + 11 * h + 22 * l) / 451);
  const month = Math.floor((h + l - 7 * m + 114) / 31);
  const day = ((h + l - 7 * m + 114) % 31) + 1;
  return new Date(Date.UTC(year, month - 1, day));
}

/**
 * Days the US stock market AND banks are closed — the standard NYSE closure
 * calendar (all federal holidays except Columbus Day / Veterans Day, plus
 * Good Friday). Computed from the real rules so it's correct every year.
 */
function usMarketHolidays(year) {
  const days = [];
  const add = (date, name) => days.push({ date: isoDate(date), name });
  add(observed(new Date(Date.UTC(year, 0, 1))), "New Year's Day");
  add(nthWeekdayOfMonth(year, 0, 1, 3), "Martin Luther King Jr. Day");
  add(nthWeekdayOfMonth(year, 1, 1, 3), "Presidents' Day");
  const goodFriday = new Date(easterSunday(year));
  goodFriday.setUTCDate(goodFriday.getUTCDate() - 2);
  add(goodFriday, "Good Friday");
  add(lastWeekdayOfMonth(year, 4, 1), "Memorial Day");
  add(observed(new Date(Date.UTC(year, 5, 19))), "Juneteenth");
  add(observed(new Date(Date.UTC(year, 6, 4))), "Independence Day");
  add(nthWeekdayOfMonth(year, 8, 1, 1), "Labor Day");
  add(nthWeekdayOfMonth(year, 10, 4, 4), "Thanksgiving Day");
  add(observed(new Date(Date.UTC(year, 11, 25))), "Christmas Day");
  return days;
}

function holidayForToday(now) {
  const today = isoDate(now);
  for (const year of [now.getUTCFullYear(), now.getUTCFullYear() + 1]) {
    const hit = usMarketHolidays(year).find((h) => h.date === today);
    if (hit) return hit;
  }
  return null;
}

/** US Eastern offset: EDT (UTC-4) mid-March to early Nov, else EST (UTC-5). */
function usEasternOffsetHours(now) {
  const year = now.getUTCFullYear();
  // DST starts 2nd Sunday of March, ends 1st Sunday of November (local 2:00)
  const start = nthWeekdayOfMonth(year, 2, 0, 2); // 2nd Sun Mar
  const end = nthWeekdayOfMonth(year, 10, 0, 1); // 1st Sun Nov
  const s = new Date(start); s.setUTCHours(7); // 02:00 EST = 07:00 UTC
  const e = new Date(end); e.setUTCHours(6); // 02:00 EDT = 06:00 UTC
  return now >= s && now < e ? -4 : -5;
}

/** NYSE trading window in UTC for the current moment (Mon-Fri only). */
function nyseWindowUtc(now) {
  const offset = usEasternOffsetHours(now);
  // 09:30-16:00 Eastern
  return { open: 9.5 - offset, close: 16 - offset };
}

// ---------- session checks ----------

/**
 * Notify on transitions only. Sessions:
 *  - London forex  07:00-16:00 UTC (Mon-Fri)
 *  - New York forex 12:00-21:00 UTC (Mon-Fri, closes 21:00 Fri for weekend)
 *  - NYSE stocks   09:30-16:00 Eastern, Mon-Fri except holidays
 *  - Forex weekend Fri 21:00 UTC -> Sun 21:00 UTC
 */
async function checkMarketStatus(pool, notify) {
  const events = [];
  const now = new Date();
  const hourUtc = now.getUTCHours() + now.getUTCMinutes() / 60;
  const dow = now.getUTCDay(); // 0 Sun .. 6 Sat
  const isWeekday = dow >= 1 && dow <= 5;

  const transition = async (key, openNow, onOpen, onClose) => {
    const state = await getState(pool, `session:${key}`);
    const prev = state ? state.open : null;
    if (prev === null) {
      // first run — just record, don't spam on deploy
      await setState(pool, `session:${key}`, { open: openNow, at: now.toISOString() });
      return;
    }
    if (prev !== openNow) {
      if (openNow && onOpen) { notify(onOpen.title, onOpen.body); events.push(key + ":open"); }
      else if (!openNow && onClose) { notify(onClose.title, onClose.body); events.push(key + ":close"); }
      await setState(pool, `session:${key}`, { open: openNow, at: now.toISOString() });
    }
  };

  // Forex weekend break: closed from Fri 21:00 UTC until Sun 21:00 UTC
  const forexWeekend =
    dow === 6 || (dow === 5 && hourUtc >= 21) || (dow === 0 && hourUtc < 21);
  await transition("forex_weekend", !forexWeekend,
    { title: "Forex Market Reopens", body: "The forex market is back open for the new trading week. Sunday 21:00 UTC (10:00 PM Lagos time)." },
    { title: "Forex Closes for the Weekend", body: "The forex market is now closed for the weekend. It reopens Sunday 10:00 PM Lagos time. Crypto stays open 24/7." }
  );

  if (isWeekday) {
    const londonOpen = hourUtc >= 7 && hourUtc < 16;
    await transition("london", londonOpen,
      { title: "London Session Open", body: "The London forex session is now open — usually the biggest moves of the day start here." },
      { title: "London Session Closed", body: "The London forex session has closed. New York is still live until 10:00 PM Lagos time." }
    );

    const nyOpen = hourUtc >= 12 && hourUtc < 21;
    await transition("ny_fx", nyOpen,
      { title: "New York Session Open", body: "The New York forex session is now open — London + New York overlap means peak volatility." },
      { title: "New York Session Closed", body: "The New York forex session has closed. Tokyo opens 1:00 AM Lagos time." }
    );

    // US stocks — holiday-aware
    const win = nyseWindowUtc(now);
    const holiday = holidayForToday(now);
    const stocksOpen = !holiday && hourUtc >= win.open && hourUtc < win.close;
    await transition("nyse", stocksOpen,
      holiday
        ? { title: "US Market Closed Today", body: `US stock market and banks are on break today for ${holiday.name}. Forex sessions run as normal, crypto trades 24/7.` }
        : { title: "US Stock Market Open", body: "The New York Stock Exchange and Nasdaq are now open for trading." },
      { title: "US Stock Market Closed", body: "The US stock market has closed for the day. After-hours moves can still shift tomorrow's open." }
    );
  }
  return events;
}

// ---------- cron entry point ----------

async function runAlertCron(pool, notify) {
  if (!pool) return { error: "no database" };
  const movers = await checkMovers(pool, notify);
  const sessions = await checkMarketStatus(pool, notify);
  return { movers, sessions, checkedAt: new Date().toISOString() };
}

module.exports = { runAlertCron, usMarketHolidays, holidayForToday };
