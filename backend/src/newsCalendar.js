/**
 * MarketScope AI — real Economic Calendar + Market News for the Calendar
 * screen (replaces the old placeholder "Journal" tile).
 *
 * Economic calendar: TradingView's public economic-calendar API (the same
 * endpoint that powers TradingView's own embeddable economic calendar
 * widget — free, no key). Real scheduled events (NFP, CPI, rate decisions,
 * etc.) with country, importance, forecast/previous/actual. ForexFactory's
 * public calendar JSON feed is a fallback source if TradingView's endpoint
 * is ever unreachable from Render's shared IP (same defensive-fallback
 * pattern as src/prices.js).
 *
 * Market news: real RSS feeds, aggregated and deduped —
 *   forex  : Investing.com Forex News
 *   crypto : Investing.com Cryptocurrency News + Cointelegraph
 *   stocks : Investing.com Stock Market News + Yahoo Finance
 *
 * No AI, no fabrication — every headline/event here is exactly what the
 * source published, just normalized into one shape for the app.
 */

const TIMEOUT_MS = 8000;
const CALENDAR_CACHE_MS = 10 * 60_000; // 10 min
const NEWS_CACHE_MS = 5 * 60_000; // 5 min

const UA = "Mozilla/5.0 (compatible; MarketScopeAiBot/1.0)";

async function fetchText(url, extraHeaders) {
  const res = await fetch(url, {
    signal: AbortSignal.timeout(TIMEOUT_MS),
    headers: { "User-Agent": UA, Accept: "*/*", ...extraHeaders }
  });
  if (!res.ok) throw new Error(`HTTP ${res.status} from ${url}`);
  return res.text();
}

async function fetchJson(url, extraHeaders) {
  const res = await fetch(url, {
    signal: AbortSignal.timeout(TIMEOUT_MS),
    headers: { "User-Agent": UA, Accept: "application/json", ...extraHeaders }
  });
  if (!res.ok) throw new Error(`HTTP ${res.status} from ${url}`);
  return res.json();
}

// ---------- Economic calendar ----------

let calendarCache = { at: 0, data: null };

const CALENDAR_COUNTRIES = "US,GB,EU,JP,AU,CA,CN,CH,NZ,DE,FR";

// TradingView importance: 1 = High, 0 = Medium, -1 = Low
function tvImportanceToLabel(n) {
  if (n >= 1) return "High";
  if (n === 0) return "Medium";
  return "Low";
}

async function fetchFromTradingView() {
  const from = new Date(Date.now() - 24 * 3600_000).toISOString();
  const to = new Date(Date.now() + 8 * 24 * 3600_000).toISOString();
  const url = `https://economic-calendar.tradingview.com/events?from=${from}&to=${to}&countries=${CALENDAR_COUNTRIES}`;
  const data = await fetchJson(url, {
    Origin: "https://www.tradingview.com",
    Referer: "https://www.tradingview.com/"
  });
  const rows = Array.isArray(data?.result) ? data.result : [];
  return rows
    .map((e) => {
      const date = new Date(e.date);
      if (!e.title || Number.isNaN(date.getTime())) return null;
      return {
        id: `tv-${e.id}`,
        title: String(e.title),
        country: e.country || "",
        impact: tvImportanceToLabel(Number(e.importance)),
        forecast: e.forecast != null ? String(e.forecast) + (e.unit || "") : null,
        previous: e.previous != null ? String(e.previous) + (e.unit || "") : null,
        actual: e.actual != null ? String(e.actual) + (e.unit || "") : null,
        timestamp: date.toISOString()
      };
    })
    .filter(Boolean);
}

async function fetchFromForexFactory() {
  const raw = await fetchJson("https://nfs.faireconomy.media/ff_calendar_thisweek.json");
  return (Array.isArray(raw) ? raw : [])
    .map((e, idx) => {
      const date = new Date(e.date);
      if (!e.title || Number.isNaN(date.getTime())) return null;
      return {
        id: `ff-${idx}-${date.getTime()}`,
        title: String(e.title),
        country: e.country || "",
        impact: ["High", "Medium", "Low", "Holiday"].includes(e.impact) ? e.impact : "Low",
        forecast: e.forecast || null,
        previous: e.previous || null,
        actual: e.actual || null,
        timestamp: date.toISOString()
      };
    })
    .filter(Boolean);
}

async function fetchEconomicCalendar() {
  const now = Date.now();
  if (calendarCache.data && now - calendarCache.at < CALENDAR_CACHE_MS) {
    return calendarCache.data;
  }
  let events = null;
  for (const fn of [fetchFromTradingView, fetchFromForexFactory]) {
    try {
      const rows = await fn();
      if (rows.length) {
        events = rows;
        break;
      }
    } catch (_e) { /* try next source */ }
  }
  if (!events) events = [];
  events.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
  calendarCache = { at: now, data: events };
  return events;
}

// ---------- News (lightweight RSS parsing, no extra deps) ----------

function decodeEntities(s) {
  return String(s || "")
    .replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"')
    .replace(/&#0?39;/g, "'").replace(/&apos;/g, "'").replace(/&amp;/g, "&")
    .trim();
}

function stripCdata(s) {
  const m = /^<!\[CDATA\[([\s\S]*)\]\]>$/.exec((s || "").trim());
  return decodeEntities(m ? m[1] : s);
}

function tagValue(itemXml, tag) {
  const m = new RegExp(`<${tag}[^>]*>([\\s\\S]*?)<\\/${tag}>`, "i").exec(itemXml);
  return m ? stripCdata(m[1]) : null;
}

function parseRss(xml, source, category) {
  const items = [];
  const itemBlocks = xml.match(/<item[\s\S]*?<\/item>/gi) || [];
  for (const block of itemBlocks) {
    const title = tagValue(block, "title");
    const link = tagValue(block, "link");
    const pubDateRaw = tagValue(block, "pubDate") || tagValue(block, "dc:date");
    if (!title || !link) continue;
    const pubDate = pubDateRaw ? new Date(pubDateRaw) : null;
    const imgMatch = /<enclosure[^>]*url="([^"]+)"/i.exec(block) || /<media:content[^>]*url="([^"]+)"/i.exec(block);
    items.push({
      title,
      link: link.trim(),
      source,
      category,
      imageUrl: imgMatch ? imgMatch[1] : null,
      publishedAt: pubDate && !Number.isNaN(pubDate.getTime()) ? pubDate.toISOString() : null
    });
  }
  return items;
}

const NEWS_FEEDS = [
  { url: "https://www.investing.com/rss/news_1.rss", source: "Investing.com", category: "forex" },
  { url: "https://www.investing.com/rss/news_301.rss", source: "Investing.com", category: "crypto" },
  { url: "https://cointelegraph.com/rss", source: "Cointelegraph", category: "crypto" },
  { url: "https://www.investing.com/rss/news_25.rss", source: "Investing.com", category: "stocks" },
  { url: "https://finance.yahoo.com/news/rssindex", source: "Yahoo Finance", category: "stocks" }
];

let newsCache = { at: 0, data: null };

async function fetchMarketNews() {
  const now = Date.now();
  if (newsCache.data && now - newsCache.at < NEWS_CACHE_MS) {
    return newsCache.data;
  }

  const results = await Promise.allSettled(
    NEWS_FEEDS.map(async (feed) => {
      const xml = await fetchText(feed.url);
      return parseRss(xml, feed.source, feed.category);
    })
  );

  const seen = new Set();
  const merged = [];
  for (const r of results) {
    if (r.status !== "fulfilled") continue;
    for (const item of r.value) {
      const key = item.title.toLowerCase().replace(/\s+/g, " ").trim();
      if (seen.has(key)) continue;
      seen.add(key);
      merged.push(item);
    }
  }
  merged.sort((a, b) => new Date(b.publishedAt || 0) - new Date(a.publishedAt || 0));

  newsCache = { at: now, data: merged };
  return merged;
}

module.exports = { fetchEconomicCalendar, fetchMarketNews };
