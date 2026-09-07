/**
 * MarketScope AI — always-live multi-asset Watchlist (Futures/Forex/Crypto),
 * mirroring TradingView's watchlist layout. Reuses the same real price feeds
 * as the AI-signal engine (src/prices.js — Yahoo/Frankfurter/CoinGecko/
 * Binance/Stooq, per-instrument fallback chains, 60s cache) so every quote
 * here is a genuine live market price, never simulated or fabricated.
 *
 * "changePct" is computed against a real reference price captured at first
 * fetch each UTC day (reset at midnight) — an honest best-effort "today's
 * change" that resets if the server restarts, same as any lightweight
 * ticker. If no reference exists yet, changePct is null (never guessed).
 */

const { fetchPrice } = require("./prices");

const WATCHLIST = [
  { section: "Futures", id: "xauusd", display: "Gold", subtitle: "CFDs on Gold (US$ / OZ)" },
  { section: "Futures", id: "xagusd", display: "Silver", subtitle: "CFDs on Silver (US$ / OZ)" },
  { section: "Forex", id: "eurusd", display: "EURUSD", subtitle: "Euro / U.S. Dollar" },
  { section: "Forex", id: "gbpusd", display: "GBPUSD", subtitle: "British Pound / U.S. Dollar" },
  { section: "Forex", id: "usdjpy", display: "USDJPY", subtitle: "U.S. Dollar / Japanese Yen" },
  { section: "Forex", id: "audusd", display: "AUDUSD", subtitle: "Australian Dollar / U.S. Dollar" },
  { section: "Crypto", id: "btcusd", display: "BTCUSD", subtitle: "Bitcoin / U.S. Dollar" },
  { section: "Crypto", id: "ethusd", display: "ETHUSD", subtitle: "Ethereum / U.S. Dollar" },
  { section: "Crypto", id: "solusd", display: "SOLUSD", subtitle: "Solana / U.S. Dollar" }
];

const dayRef = new Map(); // id -> { day: "YYYY-MM-DD", price }

function todayKey() {
  return new Date().toISOString().slice(0, 10);
}

async function fetchWatchlist() {
  const today = todayKey();
  const rows = await Promise.all(
    WATCHLIST.map(async (item) => {
      let price = null;
      try {
        price = await fetchPrice(item.id);
      } catch (_e) { /* honestly unavailable — never fabricated */ }

      let changePct = null;
      if (typeof price === "number" && Number.isFinite(price)) {
        const ref = dayRef.get(item.id);
        if (!ref || ref.day !== today) {
          dayRef.set(item.id, { day: today, price });
        } else if (ref.price > 0) {
          changePct = ((price - ref.price) / ref.price) * 100;
        }
      }

      return {
        section: item.section,
        id: item.id,
        display: item.display,
        subtitle: item.subtitle,
        price,
        changePct
      };
    })
  );
  return rows;
}

module.exports = { fetchWatchlist, WATCHLIST };
