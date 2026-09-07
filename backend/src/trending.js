/**
 * MarketScope AI — live "Trending" tokens for the Home screen.
 *
 * Pulls the top coins by market cap directly from CoinGecko's public
 * markets endpoint, including each coin's real logo, 24h % change, market
 * cap, 24h volume, and a 7-day sparkline (used to draw the mini chart).
 * No keys required; cached for 90s so a burst of app opens doesn't hit the
 * free CoinGecko rate limit.
 */

const TIMEOUT_MS = 8000;
const CACHE_MS = 3 * 60_000; // 3 min — keeps us well under CoinGecko's free rate limit

let cache = { at: 0, data: null };

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
  return (Array.isArray(rows) ? rows : [])
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
}

/**
 * @returns {Promise<Array<{id,symbol,name,image,price,marketCap,volume24h,change24h,sparkline:number[]}>>}
 * Serves the last good snapshot on a transient CoinGecko failure (e.g. a
 * free-tier 429) rather than erroring the whole Home screen section — a
 * stale trending list beats no trending list.
 */
async function fetchTrending(limit = 15) {
  if (cache.data && Date.now() - cache.at < CACHE_MS) return cache.data;

  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const data = await fetchMarketsOnce(limit);
      if (data.length > 0) {
        cache = { at: Date.now(), data };
        return data;
      }
    } catch (err) {
      console.warn(`trending: coingecko attempt ${attempt + 1} failed (${String(err.message || err)})`);
      if (attempt === 0) await new Promise((r) => setTimeout(r, 1500));
    }
  }
  if (cache.data) return cache.data; // stale-but-real beats a hard error
  throw new Error("CoinGecko is temporarily rate-limiting this server");
}

module.exports = { fetchTrending };
