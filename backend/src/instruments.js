/**
 * MarketScope AI instrument catalog — mirrors the app's picker (102 instruments)
 * with the contract-size / point-size specs used by the risk calculator.
 */

function forex(symbol) {
  const base = symbol.slice(0, 3);
  const quote = symbol.slice(3, 6);
  return {
    id: symbol,
    display: `${base.toUpperCase()}/${quote.toUpperCase()}`,
    category: "Forex",
    kind: "forex",
    quoteCurrency: quote.toUpperCase(),
    contractSize: 100000,
    pointSize: quote === "jpy" ? 0.01 : 0.0001
  };
}

const FOREX = [
  "eurusd", "gbpusd", "usdjpy", "audusd", "usdcad", "usdchf", "nzdusd",
  "eurgbp", "eurjpy", "gbpjpy", "audjpy", "cadjpy", "chfjpy", "nzdjpy",
  "euraud", "eurcad", "gbpaud", "gbpcad", "audcad", "nzdcad", "audnzd",
  "gbpnzd", "gbpchf", "eurnzd", "eurchf", "audchf", "cadchf"
].map(forex);

function index(id, quoteCcy) {
  return {
    id, display: id.toUpperCase(), category: "Indices", kind: "index",
    quoteCurrency: quoteCcy, contractSize: 1, pointSize: 1
  };
}

const INDICES = [
  index("us30", "USD"), index("us500", "USD"), index("nas100", "USD"),
  index("ger40", "EUR"), index("uk100", "GBP"), index("jp225", "JPY"),
  index("hk50", "HKD"), index("aus200", "AUD"), index("eu50", "EUR"),
  index("fra40", "EUR"), index("spa35", "EUR"), index("it40", "EUR"),
  index("swi20", "CHF"), index("ned25", "EUR"), index("se30", "SEK"),
  index("us2000", "USD"), index("vix", "USD"), index("ndx", "USD"),
  index("spx", "USD"), index("dji", "USD")
];

function synthetic(id, display) {
  return {
    id, display, category: "Synthetics", kind: "synthetic",
    quoteCurrency: "USD", contractSize: 1, pointSize: 1
  };
}

const SYNTHETICS = [
  ["volatility10", "Volatility 10"], ["volatility25", "Volatility 25"],
  ["volatility50", "Volatility 50"], ["volatility75", "Volatility 75"],
  ["volatility100", "Volatility 100"], ["volatility10_1s", "Volatility 10 (1s)"],
  ["volatility15_1s", "Volatility 15 (1s)"], ["volatility25_1s", "Volatility 25 (1s)"],
  ["volatility30_1s", "Volatility 30 (1s)"], ["volatility50_1s", "Volatility 50 (1s)"],
  ["volatility75_1s", "Volatility 75 (1s)"], ["volatility90_1s", "Volatility 90 (1s)"],
  ["volatility100_1s", "Volatility 100 (1s)"], ["volatility150_1s", "Volatility 150 (1s)"],
  ["volatility250_1s", "Volatility 250 (1s)"], ["crash150", "Crash 150"],
  ["crash300", "Crash 300"], ["crash500", "Crash 500"], ["crash600", "Crash 600"],
  ["crash900", "Crash 900"], ["crash1000", "Crash 1000"], ["boom150", "Boom 150"],
  ["boom300", "Boom 300"], ["boom500", "Boom 500"], ["boom600", "Boom 600"],
  ["boom900", "Boom 900"], ["boom1000", "Boom 1000"], ["jump10", "Jump 10"],
  ["jump25", "Jump 25"], ["jump50", "Jump 50"], ["rangebreak100", "Range Break 100"],
  ["rangebreak200", "Range Break 200"], ["stepindex", "Step Index"]
].map(([id, d]) => synthetic(id, d));

function crypto(id) {
  return {
    id, display: id.replace(/usd$/, "").toUpperCase() + "/USD",
    category: "Crypto", kind: "crypto",
    quoteCurrency: "USD", contractSize: 1, pointSize: 1
  };
}

const CRYPTOS = [
  "btcusd", "ethusd", "solusd", "bnbusd", "xrpusd", "adausd", "dogeusd",
  "dotusd", "ltcusd", "bchusd", "avaxusd", "linkusd", "maticusd",
  "trxusd", "xlmusd", "etcusd", "atomusd", "filusd", "uniusd", "aaveusd"
].map(crypto);

const METALS = [
  { id: "xauusd", display: "XAU/USD", category: "Metals", kind: "metal", quoteCurrency: "USD", contractSize: 100, pointSize: 0.01 },
  { id: "xagusd", display: "XAG/USD", category: "Metals", kind: "metal", quoteCurrency: "USD", contractSize: 5000, pointSize: 0.001 }
];

const ALL = [...FOREX, ...METALS, ...INDICES, ...SYNTHETICS, ...CRYPTOS];

const byId = Object.fromEntries(ALL.map((i) => [i.id, i]));
const categories = ["Forex", "Metals", "Indices", "Synthetics", "Crypto"];

module.exports = { ALL, byId, categories };
