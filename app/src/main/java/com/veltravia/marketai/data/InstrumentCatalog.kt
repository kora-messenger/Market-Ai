package com.veltravia.marketai.data

/**
 * Market Ai instrument catalog — mirrors the reference app's full symbol set
 * (27 forex, 2 metals, 20 indices, 33 synthetics, 20 crypto = 102 instruments)
 * with the contract-size / point-size specs used by the risk calculator.
 */
data class Instrument(
    val id: String,
    val display: String,
    val category: String,
    val kind: String,
    val quoteCurrency: String,
    val contractSize: Double,
    val pointSize: Double
)

object InstrumentCatalog {

    private fun forex(symbol: String): Instrument {
        val base = symbol.substring(0, 3)
        val quote = symbol.substring(3, 6)
        return Instrument(
            id = symbol,
            display = "$base/$quote",
            category = "Forex",
            kind = "forex",
            quoteCurrency = quote.uppercase(),
            contractSize = 100_000.0,
            pointSize = if (quote.equals("jpy", ignoreCase = true)) 0.01 else 0.0001
        )
    }

    private val forexPairs = listOf(
        "eurusd", "gbpusd", "usdjpy", "audusd", "usdcad", "usdchf", "nzdusd",
        "eurgbp", "eurjpy", "gbpjpy", "audjpy", "cadjpy", "chfjpy", "nzdjpy",
        "euraud", "eurcad", "gbpaud", "gbpcad", "audcad", "nzdcad", "audnzd",
        "gbpnzd", "gbpchf", "eurnzd", "eurchf", "audchf", "cadchf"
    )

    private fun index(id: String, quoteCcy: String) = Instrument(
        id = id,
        display = id.uppercase(),
        category = "Indices",
        kind = "index",
        quoteCurrency = quoteCcy,
        contractSize = 1.0,
        pointSize = 1.0
    )

    private val indices = listOf(
        index("us30", "USD"), index("us500", "USD"), index("nas100", "USD"),
        index("ger40", "EUR"), index("uk100", "GBP"), index("jp225", "JPY"),
        index("hk50", "HKD"), index("aus200", "AUD"), index("eu50", "EUR"),
        index("fra40", "EUR"), index("spa35", "EUR"), index("it40", "EUR"),
        index("swi20", "CHF"), index("ned25", "EUR"), index("se30", "SEK"),
        index("us2000", "USD"), index("vix", "USD"), index("ndx", "USD"),
        index("spx", "USD"), index("dji", "USD")
    )

    private fun synthetic(id: String, display: String) = Instrument(
        id = id,
        display = display,
        category = "Synthetics",
        kind = "synthetic",
        quoteCurrency = "USD",
        contractSize = 1.0,
        pointSize = 1.0
    )

    private val synthetics = listOf(
        synthetic("volatility10", "Volatility 10"),
        synthetic("volatility25", "Volatility 25"),
        synthetic("volatility50", "Volatility 50"),
        synthetic("volatility75", "Volatility 75"),
        synthetic("volatility100", "Volatility 100"),
        synthetic("volatility10_1s", "Volatility 10 (1s)"),
        synthetic("volatility15_1s", "Volatility 15 (1s)"),
        synthetic("volatility25_1s", "Volatility 25 (1s)"),
        synthetic("volatility30_1s", "Volatility 30 (1s)"),
        synthetic("volatility50_1s", "Volatility 50 (1s)"),
        synthetic("volatility75_1s", "Volatility 75 (1s)"),
        synthetic("volatility90_1s", "Volatility 90 (1s)"),
        synthetic("volatility100_1s", "Volatility 100 (1s)"),
        synthetic("volatility150_1s", "Volatility 150 (1s)"),
        synthetic("volatility250_1s", "Volatility 250 (1s)"),
        synthetic("crash150", "Crash 150"),
        synthetic("crash300", "Crash 300"),
        synthetic("crash500", "Crash 500"),
        synthetic("crash600", "Crash 600"),
        synthetic("crash900", "Crash 900"),
        synthetic("crash1000", "Crash 1000"),
        synthetic("boom150", "Boom 150"),
        synthetic("boom300", "Boom 300"),
        synthetic("boom500", "Boom 500"),
        synthetic("boom600", "Boom 600"),
        synthetic("boom900", "Boom 900"),
        synthetic("boom1000", "Boom 1000"),
        synthetic("jump10", "Jump 10"),
        synthetic("jump25", "Jump 25"),
        synthetic("jump50", "Jump 50"),
        synthetic("rangebreak100", "Range Break 100"),
        synthetic("rangebreak200", "Range Break 200"),
        synthetic("stepindex", "Step Index")
    )

    private fun crypto(id: String) = Instrument(
        id = id,
        display = id.removeSuffix("usd").uppercase() + "/USD",
        category = "Crypto",
        kind = "crypto",
        quoteCurrency = "USD",
        contractSize = 1.0,
        pointSize = 1.0
    )

    private val cryptos = listOf(
        "btcusd", "ethusd", "solusd", "bnbusd", "xrpusd", "adausd", "dogeusd",
        "dotusd", "ltcusd", "bchusd", "avaxusd", "linkusd", "maticusd",
        "trxusd", "xlmusd", "etcusd", "atomusd", "filusd", "uniusd", "aaveusd"
    ).map { crypto(it) }

    val all: List<Instrument> =
        forexPairs.map { forex(it) } +
            listOf(
                Instrument("xauusd", "XAU/USD", "Metals", "metal", "USD", 100.0, 0.01),
                Instrument("xagusd", "XAG/USD", "Metals", "metal", "USD", 5000.0, 0.001)
            ) +
            indices + synthetics + cryptos

    val categories = listOf("Forex", "Metals", "Indices", "Synthetics", "Crypto")
}
