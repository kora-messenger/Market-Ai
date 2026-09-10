/**
 * Real stock market data (global + Nigerian Exchange) via TradingView's
 * public endpoints — the same public API family the economic calendar uses.
 * No API key, server-side only, with timeouts + null-safe parsing.
 */

const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";
const ORIGIN = "https://www.tradingview.com";

async function fetchWithTimeout(url, opts, ms) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), ms);
  try {
    return await fetch(url, { ...opts, signal: ctrl.signal });
  } finally {
    clearTimeout(t);
  }
}

/**
 * Search a stock by free-text name or ticker ("Accesscorp", "AAPL",
 * "Dangote Sugar"). Returns matches with TradingView symbols.
 */
async function searchStock(query) {
  const text = String(query || "").trim();
  if (!text) return [];
  // No hl param: hl=1 makes the API wrap matches in <em> tags, which would
  // corrupt the symbol. Sanitize anyway — treat the response as untrusted.
  const url = `https://symbol-search.tradingview.com/symbol_search/?text=${encodeURIComponent(text)}&lang=en`;
  const res = await fetchWithTimeout(
    url,
    { headers: { "User-Agent": UA, Origin: ORIGIN, Accept: "application/json" } },
    10_000
  );
  if (!res.ok) throw new Error(`symbol search HTTP ${res.status}`);
  const rows = await res.json();
  if (!Array.isArray(rows)) return [];
  const clean = (s) => String(s).replace(/<[^>]*>/g, "").trim();
  return rows
    .filter((r) => r && r.type === "stock" && r.symbol && r.exchange)
    .map((r) => ({
      symbol: `${clean(r.exchange)}:${clean(r.symbol)}`,
      ticker: clean(r.symbol),
      description: clean(r.description || r.symbol),
      exchange: clean(r.exchange),
      currency: r.currency_code || null
    }));
}

/** Pick the best match for the user's query (exact > startswith > contains). */
function bestMatch(matches, query) {
  if (!matches.length) return null;
  const q = String(query).trim().toLowerCase();
  const score = (m) => {
    const t = (m.ticker || "").toLowerCase();
    const d = (m.description || "").toLowerCase();
    if (t === q || d === q) return 100;
    if (t.replace(/[^a-z0-9]/g, "") === q.replace(/[^a-z0-9]/g, "")) return 95;
    if (t.startsWith(q) || d.startsWith(q)) return 80;
    if (t.includes(q) || d.includes(q)) return 60;
    return 10;
  };
  return matches.reduce((best, m) => (score(m) > score(best) ? m : best), matches[0]);
}

/** The performance snapshot we feed the AI — all real market numbers. */
const SCAN_COLUMNS = [
  "close", "name", "description", "change", "currency",
  "Perf.W", "Perf.1M", "Perf.3M", "Perf.6M", "Perf.Y", "Perf.YTD",
  "price_52_week_high", "price_52_week_low",
  "volume", "average_volume_10d_calc", "Volatility.D",
  "market_cap_basic", "RSI", "Recommend.All", "price_earnings_ttm",
  "earnings_per_share_basic_ttm", "sector"
];

async function fetchStockStats(tvSymbol) {
  const body = JSON.stringify({
    symbols: { tickers: [tvSymbol], query: { types: [] } },
    columns: SCAN_COLUMNS
  });
  const res = await fetchWithTimeout(
    "https://scanner.tradingview.com/global/scan",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "User-Agent": UA,
        Origin: ORIGIN,
        Accept: "application/json"
      },
      body
    },
    12_000
  );
  if (!res.ok) throw new Error(`scanner HTTP ${res.status}`);
  const data = await res.json();
  const row = data && Array.isArray(data.data) ? data.data[0] : null;
  if (!row || !Array.isArray(row.d)) return null;
  const v = {};
  SCAN_COLUMNS.forEach((col, i) => { v[col] = row.d[i]; });
  if (v.close == null) return null;
  const num = (x) => (typeof x === "number" && isFinite(x) ? x : null);
  const pct = (x) => (typeof x === "number" && isFinite(x) ? Math.round(x * 100) / 100 : null);
  return {
    symbol: tvSymbol,
    ticker: v.name || tvSymbol.split(":")[1],
    company: v.description || v.name || tvSymbol.split(":")[1],
    currency: v.currency || null,
    price: v.close,
    changePctToday: pct(v.change),
    perf1W: pct(v["Perf.W"]),
    perf1M: pct(v["Perf.1M"]),
    perf3M: pct(v["Perf.3M"]),
    perf6M: pct(v["Perf.6M"]),
    perf1Y: pct(v["Perf.Y"]),
    perfYTD: pct(v["Perf.YTD"]),
    high52w: num(v.price_52_week_high),
    low52w: num(v.price_52_week_low),
    volume: num(v.volume),
    avgVolume10d: num(v.average_volume_10d_calc),
    dailyVolatilityPct: pct(v["Volatility.D"]),
    marketCap: num(v.market_cap_basic),
    rsi: num(v.RSI),
    tvRecommendation: typeof v["Recommend.All"] === "number" ? Math.round(v["Recommend.All"] * 100) / 100 : null,
    peRatio: num(v.price_earnings_ttm),
    eps: num(v.earnings_per_share_basic_ttm),
    sector: typeof v.sector === "string" ? v.sector : null
  };
}

module.exports = { searchStock, bestMatch, fetchStockStats };
