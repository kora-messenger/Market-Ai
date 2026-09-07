/**
 * MarketScope AI — live "Trending" tokens for the Home screen.
 *
 * Pulls the top coins by market cap directly from CoinGecko's public
 * markets endpoint, including each coin's real logo, 24h % change, market
 * cap, 24h volume, and a 7-day sparkline (used to draw the mini chart).
 *
 * CoinGecko's free tier sometimes rate-limits Render's shared outbound IP
 * pool even though the same code works fine elsewhere, so there's a real
 * Binance fallback (generous limits, unaffected) that supplies live price /
 * 24h change / volume / a genuine sparkline built from real hourly klines.
 * Binance has no market-cap field, so the fallback reuses the last known
 * CoinGecko market cap if we have one, or reports it honestly as
 * unavailable (never fabricated).
 */

const TIMEOUT_MS = 8000;
const CACHE_MS = 3 * 60_000; // 3 min — keeps us well under CoinGecko's free rate limit

let cache = { at: 0, data: null };
let lastMarketCaps = {}; // id -> last known real market cap, for the Binance fallback

// Fixed watchlist for the Binance fallback: id/symbol/name/logo (the logo
// URL is CoinGecko's static CDN asset per coin, not a live API call) mapped
// to its Binance ticker.
const FALLBACK_WATCHLIST = [
  { id: "bitcoin", paprika: "btc-bitcoin", symbol: "BTC", name: "Bitcoin", pair: "BTCUSDT", coinbase: "BTC-USD", image: "https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png" },
  { id: "ethereum", paprika: "eth-ethereum", symbol: "ETH", name: "Ethereum", pair: "ETHUSDT", coinbase: "ETH-USD", image: "https://coin-images.coingecko.com/coins/images/279/large/ethereum.png" },
  { id: "binancecoin", paprika: "bnb-binance-coin", symbol: "BNB", name: "BNB", pair: "BNBUSDT", coinbase: "BNB-USD", image: "https://coin-images.coingecko.com/coins/images/825/large/bnb-icon2_2x.png" },
  { id: "solana", paprika: "sol-solana", symbol: "SOL", name: "Solana", pair: "SOLUSDT", coinbase: "SOL-USD", image: "https://coin-images.coingecko.com/coins/images/4128/large/solana.png" },
  { id: "ripple", paprika: "xrp-xrp", symbol: "XRP", name: "XRP", pair: "XRPUSDT", coinbase: "XRP-USD", image: "https://coin-images.coingecko.com/coins/images/44/large/xrp-symbol-white-128.png" },
  { id: "cardano", paprika: "ada-cardano", symbol: "ADA", name: "Cardano", pair: "ADAUSDT", coinbase: "ADA-USD", image: "https://coin-images.coingecko.com/coins/images/975/large/cardano.png" },
  { id: "dogecoin", paprika: "doge-dogecoin", symbol: "DOGE", name: "Dogecoin", pair: "DOGEUSDT", coinbase: "DOGE-USD", image: "https://coin-images.coingecko.com/coins/images/5/large/dogecoin.png" },
  { id: "polkadot", paprika: "dot-polkadot", symbol: "DOT", name: "Polkadot", pair: "DOTUSDT", coinbase: "DOT-USD", image: "https://coin-images.coingecko.com/coins/images/12171/large/polkadot.png" },
  { id: "avalanche-2", paprika: "avax-avalanche", symbol: "AVAX", name: "Avalanche", pair: "AVAXUSDT", coinbase: "AVAX-USD", image: "https://coin-images.coingecko.com/coins/images/12559/large/Avalanche_Circle_RedWhite_Trans.png" },
  { id: "chainlink", paprika: "link-chainlink", symbol: "LINK", name: "Chainlink", pair: "LINKUSDT", coinbase: "LINK-USD", image: "https://coin-images.coingecko.com/coins/images/877/large/chainlink-new-logo.png" },
  { id: "litecoin", paprika: "ltc-litecoin", symbol: "LTC", name: "Litecoin", pair: "LTCUSDT", coinbase: "LTC-USD", image: "https://coin-images.coingecko.com/coins/images/2/large/litecoin.png" }
];

async function fetchJson(url) {
  const res = await fetch(url, {
    signal: AbortSignal.timeout(TIMEOUT_MS),
    headers: { "User-Agent": "Mozilla/5.0 (compatible; MarketAiBot/1.0)", Accept: "application/json" }
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

async function fetchMarketsOnce(limit) {
  const rows = await fetchJson(
    `https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc` +
      `&per_page=${limit}&page=1&sparkline=true&price_change_percentage=24h`
  );
  const data = (Array.isArray(rows) ? rows : [])
    .map((c) => ({
      id: c.id,
      symbol: String(c.symbol || "").toUpperCase(),
      name: c.name,
      image: c.image,
      price: c.current_price,
      marketCap: c.market_cap,
      volume24h: c.total_volume,
      change24h: c.price_change_percentage_24h_in_currency ?? c.price_change_percentage_24h ?? null,
      sparkline: (c.sparkline_in_7d && c.sparkline_in_7d.price) || []
    }))
    .filter((c) => c.id && typeof c.price === "number");
  for (const c of data) {
    if (typeof c.marketCap === "number" && c.marketCap > 0) lastMarketCaps[c.id] = c.marketCap;
  }
  return data;
}

/**
 * Best-effort market-cap enrichment via CoinPaprika (EU-friendly, free).
 * Only fills gaps — never overwrites a market cap we already have.
 */
async function enrichMarketCaps(rows) {
  const missing = rows.filter((r) => !r.marketCap);
  for (const row of missing) {
    const coin = FALLBACK_WATCHLIST.find((c) => c.id === row.id);
    if (!coin || !coin.paprika) continue;
    try {
      const t = await fetchJson(`https://api.coinpaprika.com/v1/tickers/${coin.paprika}`);
      const mcap = t && t.quotes && t.quotes.USD && Number(t.quotes.USD.market_cap);
      if (Number.isFinite(mcap) && mcap > 0) {
        row.marketCap = mcap;
        lastMarketCaps[row.id] = mcap;
      }
    } catch (_e) { /* enrichment is best-effort */ }
  }
  return rows;
}

/**
 * Real Coinbase Exchange-derived trending rows. Preferred fallback from
 * EU servers (Render/Frankfurt): Binance answers 451 there due to MiCA
 * geo-blocking, while Coinbase serves the EU fine.
 */
async function fetchCoinbaseFallback(limit) {
  const list = FALLBACK_WATCHLIST.filter((c) => c.coinbase).slice(0, limit);
  const rows = [];
  for (const c of list) {
    try {
      const stats = await fetchJson(`https://api.exchange.coinbase.com/products/${c.coinbase}/stats`);
      const open = Number(stats.open);
      const last = Number(stats.last);
      const volumeBase = Number(stats.volume);
      if (!Number.isFinite(last) || !Number.isFinite(open) || last <= 0 || open <= 0) continue;
      let sparkline = [];
      try {
        // hourly candles, most-recent-first; take the oldest 168 for a 7-day view
        const candles = await fetchJson(`https://api.exchange.coinbase.com/products/${c.coinbase}/candles?granularity=3600`);
        sparkline = (Array.isArray(candles) ? candles : [])
          .slice(0, 168)
          .map((k) => Number(k[4])) // close price
          .reverse();
      } catch (_e) { /* sparkline is best-effort; row still shows without it */ }
      rows.push({
        id: c.id,
        symbol: c.symbol,
        name: c.name,
        image: c.image,
        price: last,
        marketCap: lastMarketCaps[c.id] ?? null, // honest — null renders as "—", never fabricated
        volume24h: volumeBase * last, // base volume x last price = approximate USD volume
        change24h: ((last - open) / open) * 100,
        sparkline
      });
    } catch (err) {
      console.warn(`trending: coinbase ${c.symbol} failed (${String(err.message || err)})`);
    }
  }
  return rows;
}

/** Real Binance-derived trending rows — Americas/Asia hosting tier. */
async function fetchBinanceFallback(limit) {
  const list = FALLBACK_WATCHLIST.slice(0, limit);
  const symbols = list.map((c) => c.pair);
  const tickers = await fetchJson(
    `https://api.binance.com/api/v3/ticker/24hr?symbols=${encodeURIComponent(JSON.stringify(symbols))}`
  );
  const byPair = {};
  for (const t of Array.isArray(tickers) ? tickers : []) byPair[t.symbol] = t;

  const out = [];
  for (const c of list) {
    const t = byPair[c.pair];
    if (!t) continue;
    let sparkline = [];
    try {
      // 1h candles, last 7 days = 168 points
      const klines = await fetchJson(`https://api.binance.com/api/v3/klines?symbol=${c.pair}&interval=1h&limit=168`);
      sparkline = (Array.isArray(klines) ? klines : []).map((k) => Number(k[4])); // close price
    } catch (_e) { /* sparkline is best-effort; row still shows without it */ }
    out.push({
      id: c.id,
      symbol: c.symbol,
      name: c.name,
      image: c.image,
      price: Number(t.lastPrice),
      marketCap: lastMarketCaps[c.id] ?? null, // honest — null renders as "—", never fabricated
      volume24h: Number(t.quoteVolume),
      change24h: Number(t.priceChangePercent),
      sparkline
    });
  }
  return out.filter((c) => Number.isFinite(c.price));
}

/**
 * @returns {Promise<Array<{id,symbol,name,image,price,marketCap,volume24h,change24h,sparkline:number[]}>>}
 * Tier 1: fresh CoinGecko (has everything). Tier 2: fresh Coinbase Exchange
 * fallback (EU-friendly, accurate live data). Tier 3: Binance (works off-EU
 * hosting). Tier 4: last good snapshot of any, however old — a stale
 * trending list beats a broken section.
 */
async function fetchTrending(limit = 15) {
  if (cache.data && Date.now() - cache.at < CACHE_MS) return cache.data;
  const errs = [];

  try {
    const data = await fetchMarketsOnce(limit);
    if (data.length > 0) {
      cache = { at: Date.now(), data, source: "coingecko" };
      return data;
    }
    errs.push("coingecko returned no rows");
  } catch (err) {
    errs.push(`coingecko: ${String(err.message || err)}`);
    console.warn(`trending: coingecko failed (${String(err.message || err)})`);
  }

  try {
    const data = await enrichMarketCaps(await fetchCoinbaseFallback(limit));
    if (data.length > 0) {
      cache = { at: Date.now(), data, source: "coinbase" };
      return data;
    }
    errs.push("coinbase returned no rows");
  } catch (err) {
    errs.push(`coinbase: ${String(err.message || err)}`);
    console.warn(`trending: coinbase fallback failed (${String(err.message || err)})`);
  }

  try {
    const data = await fetchBinanceFallback(limit);
    if (data.length > 0) {
      cache = { at: Date.now(), data, source: "binance" };
      return data;
    }
    errs.push("binance returned no rows");
  } catch (err) {
    errs.push(`binance: ${String(err.message || err)}`);
    console.warn(`trending: binance fallback failed (${String(err.message || err)})`);
  }

  if (cache.data) return cache.data; // stale-but-real beats a hard error
  const failures = errs.filter(Boolean).join(" | ");
  throw new Error(failures || "Trending feeds are temporarily unavailable");
}

module.exports = { fetchTrending };
