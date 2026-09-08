/**
 * MarketScope AI — real OHLC candles for the live Market View screen.
 * Same honesty rules as the rest of the app: every candle comes from a
 * genuine public exchange/market feed, never simulated or interpolated.
 *
 * Sources per asset class (matching the Watchlist's 9 instruments):
 *   crypto : Coinbase Exchange public candles (BTC-USD, ETH-USD, SOL-USD)
 *   forex  : Yahoo v8 chart API (EURUSD=X, …) — plain historical OHLCV
 *   metals : Yahoo v8 chart API (GC=F gold, SI=F silver futures)
 *
 * 4H candles are aggregated server-side from real 1H candles (aligned to
 * epoch), because no free public feed offers native 4H history for all
 * three asset classes.
 */

const TIMEOUT_MS = 8000;

const INTERVALS = { "5m": 300, "15m": 900, "1h": 3600, "4h": 14400, "1d": 86400 };

const COINBASE_PRODUCTS = {
  btcusd: "BTC-USD",
  ethusd: "ETH-USD",
  solusd: "SOL-USD"
};

// Yahoo symbol mapping — forex pairs become "EURUSD=X", metals use the
// same futures contracts the price feed already trusts.
const YAHOO_CANDLE_SYMBOLS = {
  xauusd: "GC=F",
  xagusd: "SI=F"
};

function yahooCandleSymbol(instrumentId) {
  const id = (instrumentId || "").toLowerCase();
  if (YAHOO_CANDLE_SYMBOLS[id]) return YAHOO_CANDLE_SYMBOLS[id];
  if (/^[a-z]{6}$/.test(id)) return `${id.toUpperCase()}=X`; // forex pair
  return null;
}

async function fetchJson(url) {
  const res = await fetch(url, {
    signal: AbortSignal.timeout(TIMEOUT_MS),
    headers: {
      // Plain browser-ish UA; Yahoo 429s obvious bot UAs from datacenters.
      "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
      Accept: "application/json"
    }
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

/** Coinbase candles: [time, low, high, open, close, volume], NEWEST first. */
async function coinbaseCandles(instrumentId, seconds) {
  const product = COINBASE_PRODUCTS[instrumentId];
  if (!product) throw new Error("Not a Coinbase-supported instrument");
  const data = await fetchJson(
    `https://api.exchange.coinbase.com/products/${product}/candles?granularity=${seconds}`
  );
  if (!Array.isArray(data) || !data.length) throw new Error("Coinbase returned no candles");
  return data
    .map((c) => ({ t: c[0], l: c[1], h: c[2], o: c[3], c: c[4], v: c[5] }))
    .sort((a, b) => a.t - b.t); // → oldest first
}

/**
 * Yahoo v8 chart OHLCV. interval: 5m|15m|60m|1d. The chart API returns
 * nulls for market-closed slots (e.g. futures overnight) — filtered out
 * so only genuinely traded bars are returned.
 */
async function yahooCandles(instrumentId, yahooInterval, range) {
  const symbol = yahooCandleSymbol(instrumentId);
  if (!symbol) throw new Error("No Yahoo symbol for instrument");
  const data = await fetchJson(
    `https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(symbol)}?interval=${yahooInterval}&range=${range}`
  );
  const result = data && data.chart && data.chart.result && data.chart.result[0];
  const quote = result && result.indicators && result.indicators.quote && result.indicators.quote[0];
  const ts = result && result.timestamp;
  if (!ts || !quote) throw new Error("Yahoo returned no candle data");
  const out = [];
  for (let i = 0; i < ts.length; i++) {
    const o = quote.open && quote.open[i];
    const h = quote.high && quote.high[i];
    const l = quote.low && quote.low[i];
    const c = quote.close && quote.close[i];
    if (o == null || h == null || l == null || c == null) continue;
    out.push({ t: ts[i], o, h, l, c, v: (quote.volume && quote.volume[i]) || 0 });
  }
  if (!out.length) throw new Error("Yahoo candles were all null");
  return out;
}

/** Aggregate a sorted ascending 1H candle list into aligned 4H candles. */
function aggregateHourlyTo4h(hourly) {
  const buckets = new Map();
  for (const candle of hourly) {
    const bucketStart = candle.t - (candle.t % 14400);
    const b = buckets.get(bucketStart);
    if (!b) {
      buckets.set(bucketStart, { ...candle, t: bucketStart });
    } else {
      b.h = Math.max(b.h, candle.h);
      b.l = Math.min(b.l, candle.l);
      b.c = candle.c;
      b.v = (b.v || 0) + (candle.v || 0);
    }
  }
  return [...buckets.values()].sort((a, b) => a.t - b.t);
}

// Public feeds are rate-limited; cache aggressively enough that a polling
// app never hammers them, but the chart still feels live.
const candleCache = new Map(); // `${id}:${interval}` -> { data, at }
const TTL_MS = { intraday: 20_000, daily: 120_000 };

async function fetchCandles(instrumentId, interval) {
  const id = (instrumentId || "").toLowerCase();
  const seconds = INTERVALS[interval];
  if (!id || !seconds) throw new Error("Unsupported instrument or interval");

  const key = `${id}:${interval}`;
  const hit = candleCache.get(key);
  const ttl = interval === "1d" ? TTL_MS.daily : TTL_MS.intraday;
  if (hit && Date.now() - hit.at < ttl) return hit.data;

  const isCrypto = COINBASE_PRODUCTS[id] != null;

  let candles;
  let source;
  if (isCrypto) {
    if (interval === "4h") {
      const hourly = await coinbaseCandles(id, 3600);
      candles = aggregateHourlyTo4h(hourly);
    } else {
      candles = await coinbaseCandles(id, seconds);
    }
    source = "coinbase";
  } else {
    // Forex & metals via Yahoo. 4H aggregated from real 60m bars.
    if (interval === "4h") {
      const hourly = await yahooCandles(id, "60m", "10d");
      candles = aggregateHourlyTo4h(hourly);
    } else {
      const yahooInterval = interval === "5m" ? "5m" : interval === "15m" ? "15m" : interval === "1h" ? "60m" : "1d";
      const range = interval === "5m" ? "2d" : interval === "15m" ? "5d" : interval === "1h" ? "10d" : "3mo";
      candles = await yahooCandles(id, yahooInterval, range);
    }
    source = "yahoo";
  }

  // Cap the series the app draws (~150 bars keeps the chart readable).
  const data = {
    id,
    interval,
    source,
    candles: candles.slice(-150)
  };
  candleCache.set(key, { data, at: Date.now() });
  return data;
}

module.exports = { fetchCandles, INTERVALS };
