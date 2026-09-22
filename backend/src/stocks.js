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

const COMPANY_STOPWORDS = new Set([
  "the", "and", "of", "for", "with", "company", "co", "corp", "corporation",
  "inc", "incorporated", "limited", "ltd", "plc", "holdings", "holding", "group",
  "sa", "ag", "nv", "adr", "stock", "share", "shares", "equity", "listed"
]);
const INTENT_STOPWORDS = new Set(["ipo", "preipo", "pre", "offer", "offering", "listing"]);

const compact = (s) => String(s || "").toLowerCase().replace(/[^a-z0-9]/g, "");
const words = (s) => String(s || "").toLowerCase().match(/[a-z0-9]+/g) || [];
const significantWords = (s) => words(s)
  .filter((w) => w.length >= 3 && !COMPANY_STOPWORDS.has(w) && !INTENT_STOPWORDS.has(w));
const sameStem = (a, b) => a === b || (a.length > 3 && b.length > 3 && (a.startsWith(b) || b.startsWith(a)));

function tokenCoverage(candidateText, queryTokens) {
  const candidateTokens = words(candidateText);
  if (!queryTokens.length) return 0;
  const matched = queryTokens.filter((q) => candidateTokens.some((c) => sameStem(c, q))).length;
  return matched / queryTokens.length;
}

/** Pick the best match only when it is a faithful stock match.
 * TradingView's symbol search is intentionally fuzzy and can return a
 * similarly named but different issuer. We must never silently substitute a
 * company, so multi-word company queries require full significant-token
 * coverage unless the ticker or normalized description is exact. */
function bestMatch(matches, query) {
  if (!matches.length) return null;
  const q = String(query || "").trim();
  const qCompact = compact(q);
  const qTokens = significantWords(q);
  const score = (m) => {
    const ticker = m.ticker || "";
    const description = m.description || "";
    const t = ticker.toLowerCase();
    const d = description.toLowerCase();
    const tCompact = compact(ticker);
    const dCompact = compact(description);
    if (t === q.toLowerCase() || d === q.toLowerCase()) return 100;
    if (tCompact === qCompact || dCompact === qCompact) return 98;
    if (qTokens.length >= 2 && tokenCoverage(`${ticker} ${description}`, qTokens) < 1) return -1;
    if (qTokens.length === 1 && tokenCoverage(`${ticker} ${description}`, qTokens) < 1 && !tCompact.startsWith(qCompact)) return -1;
    if (tCompact.startsWith(qCompact)) return 92;
    if (dCompact.startsWith(qCompact)) return 88;
    if (qTokens.length && tokenCoverage(`${ticker} ${description}`, qTokens) === 1) return 82;
    if (tCompact.includes(qCompact) || dCompact.includes(qCompact)) return 65;
    return -1;
  };
  const ranked = matches
    .map((m) => ({ m, s: score(m) }))
    .filter((x) => x.s >= 0)
    .sort((a, b) => b.s - a.s);
  return ranked.length ? ranked[0].m : null;
}


function plainText(html) {
  return String(html || "")
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;|&#160;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&#8217;|&rsquo;/gi, "'")
    .replace(/&#8211;|&ndash;/gi, "-")
    .replace(/&#8358;|&naira;/gi, "N")
    .replace(/\s+/g, " ")
    .trim();
}

const firstMatch = (text, re) => {
  const m = String(text || "").match(re);
  return m ? m[1].trim() : null;
};
const amountNumber = (s) => {
  if (!s) return null;
  const n = Number(String(s).replace(/,/g, ""));
  return Number.isFinite(n) ? n : null;
};

/** Discover a currently open Nigerian IPO from NGX's official publication
 * feed. This covers the gap before a new issue receives a trading ticker and
 * appears in TradingView. Only ngxgroup.com is queried; returned facts retain
 * the official source URL and no live-price history is fabricated. */
async function searchNgxIpo(query) {
  const text = String(query || "").trim();
  if (!text) return null;
  const url = `https://ngxgroup.com/wp-json/wp/v2/posts?search=${encodeURIComponent(text)}&per_page=10`;
  const res = await fetchWithTimeout(url, { headers: { "User-Agent": UA, Accept: "application/json" } }, 10_000);
  if (!res.ok) throw new Error(`NGX IPO search HTTP ${res.status}`);
  const posts = await res.json();
  if (!Array.isArray(posts)) return null;
  const qTokens = significantWords(text);
  const ranked = posts.map((post) => {
    const title = plainText(post?.title?.rendered);
    const body = plainText(post?.content?.rendered);
    const haystack = `${title} ${body}`;
    const coverage = tokenCoverage(haystack, qTokens);
    const isOffer = /\b(initial public offering|\bipo\b|public offer)\b/i.test(haystack);
    const termSignals = [
      /per share/i, /subscription window/i, /ordinary shares/i, /offer price/i,
      /scheduled to close/i, /minimum subscription/i, /profit after tax/i,
      /implied market capitalisation/i
    ].filter((re) => re.test(body)).length;
    const hasTerms = termSignals > 0;
    return {
      post, title, body, coverage,
      score: (isOffer ? 3 : 0) + (hasTerms ? 2 : 0) + coverage * 5 + termSignals * 0.75
    };
  }).filter((x) => x.coverage >= (qTokens.length >= 2 ? 0.75 : 1) && x.score >= 7)
    .sort((a, b) => b.score - a.score || String(b.post.date).localeCompare(String(a.post.date)));
  if (!ranked.length) return null;

  const { post, title, body } = ranked[0];
  const company = firstMatch(body, /Initial Public Offering of\s+(.+?)\s+(?:commenced|opened|has opened)/i)
    || firstMatch(body, /(?:IPO|public offer) of\s+(.+?)(?:\.|,|\s+opened)/i)
    || text.replace(/\bIPO\b/gi, "").trim();
  const offerPriceText = firstMatch(body, /(?:offered at|offer price(?: of| at)?|subscription price(?: of| at)?)\s*(?:N|₦)\s*([\d,.]+)\s*per share/i)
    || firstMatch(body, /(?:N|₦)\s*([\d,.]+)\s*per share/i);
  const sharesText = firstMatch(body, /([\d,.]+\s*(?:billion|million))\s+ordinary shares/i);
  const minimumText = firstMatch(body, /minimum subscription of\s+([\d,.]+\s+shares[^.]{0,100})/i);
  const openedAt = firstMatch(body, /(?:subscription window|offer) opened on\s+((?:(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),\s*)?[A-Z][a-z]+\s+\d{1,2},\s*\d{4})/i);
  const closesAt = firstMatch(body, /(?:scheduled to close|closes?) on\s+((?:(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),\s*)?[A-Z][a-z]+\s+\d{1,2},\s*\d{4})/i);
  const offerSize = firstMatch(body, /(?:formally open(?:ed)?|open(?:ed)?)\s+(?:the\s+)?((?:N|₦)[\d,.]+\s*(?:tn|trillion|bn|billion))\s+IPO/i)
    || firstMatch(body, /((?:N|₦)[\d,.]+\s*(?:tn|trillion|bn|billion))\s+(?:IPO|public offer)/i);
  const revenue = firstMatch(body, /generated approximately\s+((?:N|₦)[\d,.]+\s*(?:tn|trillion|bn|billion))\s+in revenue/i);
  const profitAfterTax = firstMatch(body, /profit after tax stood at\s+((?:N|₦)[\d,.]+\s*(?:tn|trillion|bn|billion))/i);
  const impliedMarketCap = firstMatch(body, /implied market capitalisation of about\s+((?:N|₦)[\d,.]+\s*(?:tn|trillion|bn|billion))/i);

  return {
    kind: "ipo",
    company,
    exchange: "NGX",
    ticker: null,
    offerPrice: amountNumber(offerPriceText),
    currency: "NGN",
    sharesOffered: sharesText,
    minimumSubscription: minimumText,
    openedAt,
    closesAt,
    offerSize,
    revenue,
    profitAfterTax,
    impliedMarketCap,
    announcedAt: post.date || null,
    sourceTitle: title,
    sourceUrl: post.link,
    slug: post.slug || String(post.id)
  };
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

module.exports = { searchStock, bestMatch, fetchStockStats, searchNgxIpo };
