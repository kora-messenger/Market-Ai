/**
 * Live price feeds for Market Ai daily signals — used for (1) automatic
 * outcome resolution (did price hit SL / TP?) and (2) market data for the
 * AI-generated daily signal. Free public APIs, no keys.
 *
 * Coverage: forex + metals + major indices via Yahoo Finance's chart API,
 * crypto via CoinGecko. Deriv synthetics have no public price feed — those
 * signals stay open until the author closes them manually (honest fallback,
 * never a fake number).
 */

const YAHOO_SYMBOLS = {
  // indices
  us30: "^DJI", us500: "^GSPC", nas100: "^NDX", ger40: "^GDAXI", uk100: "^FTSE",
  jp225: "^N225", hk50: "^HSI", aus200: "^AXJO", eu50: "^STOXX50E", fra40: "^FCHI",
  spa35: "^IBEX", it40: "FTSEMIB.MI", swi20: "^SSMI", ned25: "^AEX", se30: "^OMX",
  us2000: "^RUT", vix: "^VIX", ndx: "^NDX", spx: "^GSPC", dji: "^DJI",
  // metals (Yahoo spot-style pairs)
  xauusd: "GC=F", xagusd: "SI=F"
};

const COINGECKO_IDS = {
  btcusd: "bitcoin", ethusd: "ethereum", solusd: "solana", bnbusd: "binancecoin",
  xrpusd: "ripple", adausd: "cardano", dogeusd: "dogecoin", dotusd: "polkadot",
  ltcusd: "litecoin", bchusd: "bitcoin-cash", avaxusd: "avalanche-2",
  linkusd: "chainlink", maticusd: "matic-network", trxusd: "tron",
  xlmusd: "stellar", etcusd: "ethereum-classic", atomusd: "cosmos",
  filusd: "filecoin", uniusd: "uniswap", aaveusd: "aave"
};

/** Yahoo symbol for an instrument id, or null if it has no Yahoo feed. */
function yahooSymbol(instrumentId) {
  const id = (instrumentId || "").toLowerCase();
  if (YAHOO_SYMBOLS[id]) return YAHOO_SYMBOLS[id];
  if (id.length === 6) {
    // forex pair like eurusd -> EURUSD=X
    if ( /^[a-z]{6}$/.test(id)) return `${id.toUpperCase()}=X`;
  }
  return null;
}

async function fetchJson(url, headers = {}) {
  const res = await fetch(url, {
    headers: {
      "User-Agent": "Mozilla/5.0 (compatible; MarketAiBot/1.0)",
      Accept: "application/json",
      ...headers
    }
  });
  if (!res.ok) throw new Error(`${url.split("?")[0]} -> HTTP ${res.status}`);
  return res.json();
}

/**
 * Latest price for an instrument, or null when no feed / feed failure.
 * Cached 60s per instrument in-process to keep the 15-minute cron cheap.
 */
const priceCache = new Map(); // id -> { price, at }
const CACHE_MS = 60_000;

async function fetchPrice(instrumentId) {
  const id = (instrumentId || "").toLowerCase();
  const hit = priceCache.get(id);
  if (hit && Date.now() - hit.at < CACHE_MS) return hit.price;

  let price = null;
  const yahoo = yahooSymbol(id);
  if (yahoo) {
    try {
      const data = await fetchJson(
        `https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(yahoo)}?interval=5m&range=1d`
      );
      const result = data?.chart?.result?.[0];
      const closes = result?.indicators?.quote?.[0]?.close || [];
      const meta = result?.meta;
      price = meta?.regularMarketPrice ?? [...closes].reverse().find((c) => typeof c === "number") ?? null;
    } catch (_e) { /* fall through to null */ }
  } else if (COINGECKO_IDS[id]) {
    try {
      const data = await fetchJson(
        `https://api.coingecko.com/api/v3/simple/price?ids=${COINGECKO_IDS[id]}&vs_currencies=usd`
      );
      price = data?.[COINGECKO_IDS[id]]?.usd ?? null;
    } catch (_e) { /* fall through to null */ }
  }

  if (price != null) priceCache.set(id, { price, at: Date.now() });
  return price;
}

/**
 * Compact recent OHLC history (for the AI daily signal), or null.
 * Returns { timeframe, candles: [{t, o, h, l, c}] } — most recent last.
 */
async function fetchHistory(instrumentId, { interval = "1h", range = "5d" } = {}) {
  const id = (instrumentId || "").toLowerCase();
  const yahoo = yahooSymbol(id);
  if (yahoo) {
    try {
      const data = await fetchJson(
        `https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(yahoo)}?interval=${interval}&range=${range}`
      );
      const result = data?.chart?.result?.[0];
      const stamps = result?.timestamp || [];
      const q = result?.indicators?.quote?.[0] || {};
      const candles = [];
      for (let i = 0; i < stamps.length; i++) {
        const o = q.open?.[i], h = q.high?.[i], l = q.low?.[i], c = q.close?.[i];
        if ([o, h, l, c].every((v) => typeof v === "number")) {
          candles.push({ t: stamps[i], o, h, l, c });
        }
      }
      return { timeframe: interval, candles };
    } catch (_e) { return null; }
  }
  if (COINGECKO_IDS[id]) {
    try {
      const data = await fetchJson(
        `https://api.coingecko.com/api/v3/coins/${COINGECKO_IDS[id]}/ohlc?vs_currency=usd&days=5`
      );
      // CoinGecko OHLC rows: [timestamp, o, h, l, c]
      const candles = (data || [])
        .filter((r) => Array.isArray(r) && r.length === 5)
        .map((r) => ({ t: Math.round(r[0] / 1000), o: r[1], h: r[2], l: r[3], c: r[4] }));
      return { timeframe: "4h", candles };
    } catch (_e) { return null; }
  }
  return null;
}

module.exports = { fetchPrice, fetchHistory, yahooSymbol, COINGECKO_IDS };
