package com.veltravia.marketai.domain

import kotlin.random.Random

/**
 * Deterministic candle data used ONLY to render the "good vs bad screenshot"
 * examples on the chart-upload guide screen. This is illustrative UI content
 * (showing what a crisp vs. blurry screenshot looks like) — not a market
 * signal or live price feed, so a seeded synthetic price path is appropriate
 * and honest here (labeled as an example in the UI copy).
 */
object IllustrativeChart {

    data class Candle(val open: Double, val high: Double, val low: Double, val close: Double)

    fun generate(seed: Long, count: Int = 26, startPrice: Double = 1_040.0): List<Candle> {
        val rnd = Random(seed)
        var price = startPrice
        val candles = ArrayList<Candle>(count)
        repeat(count) {
            val drift = (rnd.nextDouble() - 0.42) * 14.0
            val open = price
            val close = (open + drift).coerceAtLeast(startPrice * 0.5)
            val wickUp = rnd.nextDouble() * 6.0
            val wickDown = rnd.nextDouble() * 6.0
            val high = maxOf(open, close) + wickUp
            val low = minOf(open, close) - wickDown
            candles.add(Candle(open, high, low, close))
            price = close
        }
        return candles
    }
}
