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
  btcusd: "BTC-USD", ethusd: "ETH-USD", solusd: "SOL-USD",
  xrpusd: "XRP-USD", adausd: "ADA-USD", dogeusd: "DOGE-USD",
  dotusd: "DOT-USD", linkusd: "LINK-USD", ltcusd: "LTC-USD",
  avaxusd: "AVAX-USD", bnbusd: "BNB-USD"
};

// Any "<base>usd" id that looks like a crypto ticker gets the generic
// treatment: try Coinbase's "<BASE>-USD" product, then Binance klines for
// "<BASE>USDT" (Binance lists almost every trending coin).
const GENERIC_CRYPTO = /^([a-z0-9]{2,10})usd$/;

function cryptoSourcesFor(id) {
  if (COINBASE_PRODUCTS[id]) {
    return { cb: COINBASE_PRODUCTS[id], bn: COINBASE_PRODUCTS[id].replace("-USD", "") + "USDT" };
  }
  const m = GENERIC_CRYPTO.exec(id);
  if (m) {
    const b = m[1].toUpperCase();
    return { cb: `${b}-USD`, bn: `${b}USDT` };
  }
  return null;
}

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
async function coinbaseCandles(product, seconds) {
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

  const cs = cryptoSourcesFor(id);
  let candles = null;
  let source = null;

  // 1) Yahoo — metals + forex (any 6-letter pair). Failures fall through.
  const yahoo = yahooCandleSymbol(id);
  if (yahoo) {
    try {
      if (interval === "4h") {
        candles = aggregateHourlyTo4h(await yahooCandles(id, "60m", "10d"));
      } else {
        const yahooInterval = interval === "5m" ? "5m" : interval === "15m" ? "15m" : interval === "1h" ? "60m" : "1d";
        const range = interval === "5m" ? "2d" : interval === "15m" ? "5d" : interval === "1h" ? "10d" : "3mo";
        candles = await yahooCandles(id, yahooInterval, range);
      }
      source = "yahoo";
    } catch (_e) { candles = null; }
  }

  // 2) Coinbase — known products plus generic "<BASE>-USD".
  if (candles == null && cs) {
    try {
      if (interval === "4h") {
        candles = aggregateHourlyTo4h(await coinbaseCandles(cs.cb, 3600));
      } else {
        candles = await coinbaseCandles(cs.cb, seconds);
      }
      source = "coinbase";
    } catch (_e) { candles = null; }
  }

  // 3) Binance klines — widest coverage (e.g. BNB, SHIB, TON), native 4h/1d.
  //    (Geo-blocked from some datacenters; kept for wherever it is reachable.)
  if (candles == null && cs) {
    try {
      candles = await binanceKlines(cs.bn, seconds);
      source = "binance";
    } catch (_e) { candles = null; }
  }

  // 4) Kraken OHLC — public, no key, no geo-blocks; covers coins Coinbase
  //    does not list (e.g. TRX) and gives up to 720 real bars.
  if (candles == null && cs) {
    try {
      candles = await krakenCandles(`${cs.bn.replace(/USDT$/, "")}USD`, seconds);
      source = "kraken";
    } catch (_e) { candles = null; }
  }

  if (candles == null || !candles.length) {
    throw new Error("No live candle source reachable for this market right now.");
  }

  // Honest class labels for the Market View header.
  const cryptoLike = cs != null && GENERIC_CRYPTO.test(id);
  const display = cryptoLike ? `${id.slice(0, -3).toUpperCase()}USD` : id.toUpperCase();
  const subtitle = cryptoLike
    ? "Cryptocurrency / U.S. Dollar"
    : yahooCandleSymbol(id)
      ? "Foreign exchange pair"
      : "Live market";

  const data = {
    id,
    interval,
    source,
    display,
    subtitle,
    candles: candles.slice(-150)
  };
  candleCache.set(key, { data, at: Date.now() });
  return data;
}

/** Kraken OHLC: [time(s), open, high, low, close, vwap, volume, count]. */
async function krakenCandles(pair, seconds) {
  const interval = seconds === 300 ? 5 : seconds === 900 ? 15 : seconds === 3600 ? 60 : seconds === 14400 ? 240 : 1440;
  const data = await fetchJson(
    `https://api.kraken.com/0/public/OHLC?pair=${pair}&interval=${interval}`
  );
  if (!Array.isArray(data?.error) || data.error.length) throw new Error("Kraken returned an error");
  const key = Object.keys(data.result || {}).find((k) => k !== "last");
  const rows = key ? data.result[key] : null;
  if (!Array.isArray(rows) || !rows.length) throw new Error("Kraken returned no candles");
  return rows
    .map((r) => ({ t: r[0], o: Number(r[1]), h: Number(r[2]), l: Number(r[3]), c: Number(r[4]), v: Number(r[6]) }))
    .filter((c) => Number.isFinite(c.c) && Number.isFinite(c.o));
}

/** Binance klines: [openTime, open, high, low, close, volume, ...]. */
async function binanceKlines(pair, seconds) {
  const interval = seconds === 300 ? "5m" : seconds === 900 ? "15m" : seconds === 3600 ? "1h" : seconds === 14400 ? "4h" : "1d";
  const data = await fetchJson(
    `https://api.binance.com/api/v3/klines?symbol=${pair}&interval=${interval}&limit=200`
  );
  if (!Array.isArray(data) || !data.length) throw new Error("Binance returned no klines");
  return data
    .map((k) => ({ t: k[0] / 1000, o: Number(k[1]), h: Number(k[2]), l: Number(k[3]), c: Number(k[4]), v: Number(k[5]) }))
    .filter((c) => Number.isFinite(c.c) && Number.isFinite(c.o));
}

module.exports = { fetchCandles, INTERVALS };
