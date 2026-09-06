/**
 * Live price feeds for Market Ai daily signals — used for (1) automatic
 * outcome resolution (did price hit SL / TP?) and (2) market data for the
 * AI-generated daily signal. Free public APIs, no keys, with fallback
 * chains because Yahoo/CoinGecko sometimes block datacenter IPs:
 *
 *   forex   : Yahoo chart  -> Frankfurter (ECB) / open.er-api spot
 *   crypto  : CoinGecko    -> Binance public API
 *   metals  : Yahoo (GC=F / SI=F futures)
 *   indices : Yahoo        -> Stooq daily CSV
 *
 * Deriv synthetics have no public feed at all — those signals stay open
 * until the author closes them manually (honest fallback, never a fake).
 */

const TIMEOUT_MS = 8000;

const YAHOO_SYMBOLS = {
  // indices
  us30: "^DJI", us500: "^GSPC", nas100: "^NDX", ger40: "^GDAXI", uk100: "^FTSE",
  jp225: "^N225", hk50: "^HSI", aus200: "^AXJO", eu50: "^STOXX50E", fra40: "^FCHI",
  spa35: "^IBEX", it40: "FTSEMIB.MI", swi20: "^SSMI", ned25: "^AEX", se30: "^OMX",
  us2000: "^RUT", vix: "^VIX", ndx: "^NDX", spx: "^GSPC", dji: "^DJI",
  // metals (futures track spot closely for signal resolution)
  xauusd: "GC=F", xagusd: "SI=F"
};

const STOOQ_SYMBOLS = {
  us30: "^dji", us500: "^spx", nas100: "^ndq", ger40: "^dax", uk100: "^ftm",
  jp225: "^nkx", eu50: "^esx", fra40: "^cac", ned25: "^aex", us2000: "^rut",
  ndx: "^ndq", spx: "^spx", dji: "^dji", vix: "^vix", aus200: "^asx"
};

const COINGECKO_IDS = {
  btcusd: "bitcoin", ethusd: "ethereum", solusd: "solana", bnbusd: "binancecoin",
  xrpusd: "ripple", adausd: "cardano", dogeusd: "dogecoin", dotusd: "polkadot",
  ltcusd: "litecoin", bchusd: "bitcoin-cash", avaxusd: "avalanche-2",
  linkusd: "chainlink", maticusd: "matic-network", trxusd: "tron",
  xlmusd: "stellar", etcusd: "ethereum-classic", atomusd: "cosmos",
  filusd: "filecoin", uniusd: "uniswap", aaveusd: "aave"
};

const BINANCE_SYMBOLS = {
  btcusd: "BTCUSDT", ethusd: "ETHUSDT", solusd: "SOLUSDT", bnbusd: "BNBUSDT",
  xrpusd: "XRPUSDT", adausd: "ADAUSDT", dogeusd: "DOGEUSDT", dotusd: "DOTUSDT",
  ltcusd: "LTCUSDT", bchusd: "BCHUSDT", avaxusd: "AVAXUSDT", linkusd: "LINKUSDT",
  maticusd: "MATICUSDT", trxusd: "TRXUSDT", xlmusd: "XLMUSDT", etcusd: "ETCUSDT",
  atomusd: "ATOMUSDT", filusd: "FILUSDT", uniusd: "UNIUSDT", aaveusd: "AAVEUSDT"
};

function yahooSymbol(instrumentId) {
  const id = (instrumentId || "").toLowerCase();
  if (YAHOO_SYMBOLS[id]) return YAHOO_SYMBOLS[id];
  if (/^[a-z]{6}$/.test(id)) return `${id.toUpperCase()}=X`; // forex pair
  return null;
}

async function fetchJson(url) {
  const res = await fetch(url, {
    signal: AbortSignal.timeout(TIMEOUT_MS),
    headers: {
      "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36",
      Accept: "application/json"
    }
  });
  if (!res.ok) throw new Error(`HTTP ${res.status} from ${url.split("?")[0]}`);
  return res.json();
}

async function fetchText(url) {
  const res = await fetch(url, {
    signal: AbortSignal.timeout(TIMEOUT_MS),
    headers: {
      "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36"
    }
  });
  if (!res.ok) throw new Error(`HTTP ${res.status} from ${url.split("?")[0]}`);
  return res.text();
}

// ---------- spot price ----------

const priceCache = new Map(); // id -> { price, at }
const CACHE_MS = 60_000;

async function fetchPrice(instrumentId) {
  const id = (instrumentId || "").toLowerCase();
  const hit = priceCache.get(id);
  if (hit && Date.now() - hit.at < CACHE_MS) return hit.price;

  let price = null;
  for (const fn of priceSources(id)) {
    try {
      const p = await fn();
      if (typeof p === "number" && Number.isFinite(p) && p > 0) {
        price = p;
        break;
      }
    } catch (_e) { /* try next source */ }
  }
  if (price != null) priceCache.set(id, { price, at: Date.now() });
  return price;
}

function priceSources(id) {
  const sources = [];
  const yahoo = yahooSymbol(id);
  if (yahoo) {
    sources.push(async () => {
      const data = await fetchJson(
        `https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(yahoo)}?interval=5m&range=1d`
      );
      const result = data?.chart?.result?.[0];
      const closes = result?.indicators?.quote?.[0]?.close || [];
      return result?.meta?.regularMarketPrice ?? [...closes].reverse().find((c) => typeof c === "number") ?? null;
    });
  }
  if (STOOQ_SYMBOLS[id]) {
    sources.push(async () => {
      const csv = await fetchText(`https://stooq.com/q/l/?s=${STOOQ_SYMBOLS[id]}&f=sd2t2ohlcv&h&e=csv`);
      const row = csv.trim().split("\n")[1] || "";
      const close = Number(row.split(",")[6]);
      return Number.isFinite(close) ? close : null;
    });
  }
  if (COINGECKO_IDS[id]) {
    sources.push(async () => {
      const data = await fetchJson(
        `https://api.coingecko.com/api/v3/simple/price?ids=${COINGECKO_IDS[id]}&vs_currencies=usd`
      );
      return data?.[COINGECKO_IDS[id]]?.usd ?? null;
    });
  }
  if (BINANCE_SYMBOLS[id]) {
    sources.push(async () => {
      const data = await fetchJson(
        `https://api.binance.com/api/v3/ticker/price?symbol=${BINANCE_SYMBOLS[id]}`
      );
      return Number(data?.price) || null;
    });
  }
  if (/^[a-z]{6}$/.test(id)) {
    // forex spot via Frankfurter (ECB), then open.er-api
    const base = id.slice(0, 3).toUpperCase();
    const quote = id.slice(3).toUpperCase();
    sources.push(async () => {
      const data = await fetchJson(`https://api.frankfurter.app/latest?base=${base}&symbols=${quote}`);
      return data?.rates?.[quote] ?? null;
    });
    sources.push(async () => {
      const data = await fetchJson(`https://open.er-api.com/v6/latest/${base}`);
      return data?.rates?.[quote] ?? null;
    });
  }
  return sources;
}

// ---------- OHLC history (for the AI daily signal) ----------

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
      if (candles.length >= 20) return { timeframe: interval, candles };
    } catch (_e) { /* fall through */ }
  }
  if (BINANCE_SYMBOLS[id]) {
    try {
      const data = await fetchJson(
        `https://api.binance.com/api/v3/klines?symbol=${BINANCE_SYMBOLS[id]}&interval=1h&limit=120`
      );
      const candles = (data || []).map((k) => ({
        t: Math.round(Number(k[0]) / 1000), o: Number(k[1]), h: Number(k[2]), l: Number(k[3]), c: Number(k[4])
      })).filter((c) => c.o > 0);
      if (candles.length >= 20) return { timeframe: "1h", candles };
    } catch (_e) { /* fall through */ }
  }
  if (COINGECKO_IDS[id]) {
    try {
      const data = await fetchJson(
        `https://api.coingecko.com/api/v3/coins/${COINGECKO_IDS[id]}/ohlc?vs_currency=usd&days=5`
      );
      const candles = (data || [])
        .filter((r) => Array.isArray(r) && r.length === 5)
        .map((r) => ({ t: Math.round(r[0] / 1000), o: r[1], h: r[2], l: r[3], c: r[4] }));
      if (candles.length >= 20) return { timeframe: "4h", candles };
    } catch (_e) { /* fall through */ }
  }
  if (STOOQ_SYMBOLS[id]) {
    try {
      const csv = await fetchText(`https://stooq.com/q/d/l/?s=${STOOQ_SYMBOLS[id]}&i=d`);
      const rows = csv.trim().split("\n").slice(1).slice(-30);
      const candles = rows.map((row) => {
        const [d, o, h, l, c] = row.split(",");
        return { t: Math.round(new Date(`${d}T12:00:00Z`).getTime() / 1000), o: +o, h: +h, l: +l, c: +c };
      }).filter((c) => Number.isFinite(c.c) && c.c > 0);
      if (candles.length >= 10) return { timeframe: "1d", candles };
    } catch (_e) { /* fall through */ }
  }
  if (/^[a-z]{6}$/.test(id)) {
    // forex daily history via Frankfurter (ECB) — daily granularity
    try {
      const base = id.slice(0, 3).toUpperCase();
      const quote = id.slice(3).toUpperCase();
      const end = new Date().toISOString().slice(0, 10);
      const start = new Date(Date.now() - 30 * 864e5).toISOString().slice(0, 10);
      const data = await fetchJson(
        `https://api.frankfurter.app/${start}..${end}?base=${base}&symbols=${quote}`
      );
      const candles = Object.entries(data?.rates || {})
        .sort(([a], [b]) => (a < b ? -1 : 1))
        .map(([day, rates]) => {
          const c = rates[quote];
          return { t: Math.round(new Date(`${day}T12:00:00Z`).getTime() / 1000), o: c, h: c * 1.0, l: c, c };
        })
        .filter((c) => Number.isFinite(c.c) && c.c > 0);
      if (candles.length >= 10) return { timeframe: "1d", candles };
    } catch (_e) { /* fall through */ }
  }
  return null;
}

module.exports = { fetchPrice, fetchHistory, yahooSymbol, COINGECKO_IDS };
