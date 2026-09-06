package com.veltravia.marketai.domain

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Generates the "illustrative 12-trade projection" shown during onboarding.
 *
 * This is MarketScope AI's own original implementation — real math, not hardcoded numbers.
 * It is NOT ported from any third-party app: only the general concept (compound a
 * risk-profile-based equity curve over N trades toward a size-based target growth,
 * search a bounded parameter space if the raw profile falls short) is a generic,
 * well-known trading-education idea, reimplemented independently here.
 */
object ProjectionEngine {

    data class TradeOutcome(
        val index: Int,
        val isWin: Boolean,
        val risk: Double,
        val pnl: Double,
        val equity: Double
    )

    data class ProjectionResult(
        val trades: List<TradeOutcome>,
        val startingEquity: Double,
        val finalEquity: Double,
        val growthPct: Double,
        val targetPct: Double
    )

    private data class RiskProfile(val riskPct: Double, val rMultiple: Double, val winRate: Double)

    private val CONSERVATIVE = RiskProfile(riskPct = 0.010, rMultiple = 1.5, winRate = 0.53)
    private val BALANCED = RiskProfile(riskPct = 0.015, rMultiple = 1.8, winRate = 0.55)
    private val AGGRESSIVE = RiskProfile(riskPct = 0.020, rMultiple = 2.1, winRate = 0.57)

    /** Small accounts get a bolder illustrative target than larger ones. */
    fun targetGrowthFor(capital: Double): Double = if (capital > 0 && capital <= 5000.0) 0.5 else 0.25

    private fun resolveProfile(experience: String, style: String): RiskProfile {
        val exp = experience.lowercase()
        val sty = style.lowercase()
        return when {
            sty.contains("scalp") || sty.contains("aggressive") -> AGGRESSIVE
            exp.contains("beginner") || exp.isBlank() && sty.isBlank() -> BALANCED
            exp.contains("new") -> CONSERVATIVE
            else -> BALANCED
        }
    }

    fun consistencyLine(experience: String, style: String): String {
        val exp = experience.lowercase()
        val sty = style.lowercase()
        return when {
            sty.contains("scalp") || sty.contains("aggressive") ->
                "Slow is smooth, smooth is fast — your edge comes from waiting for A+ setups, not more trades."
            exp.contains("beginner") ->
                "Clarity beats speed. One high-quality setup, repeated with discipline, is how an edge compounds."
            else ->
                "Consistency is a system: same checklist, same risk, same trigger — again and again."
        }
    }

    private fun generateOutcomePattern(seed: Long, winRate: Double, count: Int = 12): MutableList<Boolean> {
        val rnd = Random(seed)
        val winsNeeded = (count * winRate).roundToInt().coerceIn(1, count - 1)
        val outcomes = MutableList(count) { it < winsNeeded }
        outcomes.shuffle(rnd)
        fixConsecutiveLosses(outcomes)
        return outcomes
    }

    /** Reflects the "stop after 2 consecutive losses" discipline rule — never show 3 losses in a row. */
    private fun fixConsecutiveLosses(outcomes: MutableList<Boolean>) {
        var i = 0
        while (i < outcomes.size - 2) {
            if (!outcomes[i] && !outcomes[i + 1] && !outcomes[i + 2]) {
                val swapIdx = (i + 3 until outcomes.size).firstOrNull { outcomes[it] }
                if (swapIdx != null) {
                    outcomes[i + 2] = true
                    outcomes[swapIdx] = false
                } else {
                    outcomes[i + 2] = true
                }
            }
            i++
        }
    }

    private fun equityCurve(
        capital: Double,
        riskPct: Double,
        rMultiple: Double,
        outcomes: List<Boolean>
    ): Pair<List<TradeOutcome>, Double> {
        var equity = capital
        val list = ArrayList<TradeOutcome>(outcomes.size)
        outcomes.forEachIndexed { idx, isWin ->
            val risk = equity * riskPct
            val pnl = if (isWin) risk * rMultiple else -risk
            equity += pnl
            list.add(TradeOutcome(idx + 1, isWin, risk, pnl, equity))
        }
        return list to equity
    }

    fun computeProjection(
        capital: Double,
        experience: String,
        style: String,
        seedKey: String
    ): ProjectionResult {
        val base = resolveProfile(experience, style)
        val target = targetGrowthFor(capital)
        val seed = seedKey.hashCode().toLong() xor capital.toLong()

        var riskPct = base.riskPct
        var rMultiple = base.rMultiple
        val outcomes = generateOutcomePattern(seed, base.winRate)

        fun growthOf(r: Double, m: Double): Double {
            val (_, finalEquity) = equityCurve(capital, r, m, outcomes)
            return finalEquity / capital - 1.0
        }

        var growth = growthOf(riskPct, rMultiple)

        val maxWinsAllowed = (minOf(0.65, base.winRate + 0.06) * outcomes.size).roundToInt()
        val maxRMultiple = base.rMultiple + 0.7
        val maxRiskPct = 0.03
        var guard = 0

        // Bounded, deterministic search: prefer improving win-rate first, then reward ratio,
        // then risk-per-trade, until the illustrative target growth is met (or bounds exhausted).
        while (growth < target && guard < 60) {
            guard++
            val currentWins = outcomes.count { it }
            if (currentWins < maxWinsAllowed) {
                val flipIdx = outcomes.indexOfFirst { !it }
                if (flipIdx >= 0) {
                    outcomes[flipIdx] = true
                    growth = growthOf(riskPct, rMultiple)
                    continue
                }
            }
            if (rMultiple < maxRMultiple) {
                rMultiple = (rMultiple + 0.1).coerceAtMost(maxRMultiple)
                growth = growthOf(riskPct, rMultiple)
                continue
            }
            if (riskPct < maxRiskPct) {
                riskPct = (riskPct + 0.002).coerceAtMost(maxRiskPct)
                growth = growthOf(riskPct, rMultiple)
                continue
            }
            break
        }

        val (trades, finalEquity) = equityCurve(capital, riskPct, rMultiple, outcomes)
        return ProjectionResult(
            trades = trades,
            startingEquity = capital,
            finalEquity = finalEquity,
            growthPct = growth,
            targetPct = target
        )
    }
}
