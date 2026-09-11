package com.veltravia.marketscopeai.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.QuestionnaireAnswers
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.components.GradientPrimaryButton
import com.veltravia.marketscopeai.ui.components.PremiumChapterLabel
import com.veltravia.marketscopeai.ui.components.PremiumChoicePill
import com.veltravia.marketscopeai.ui.components.PremiumOptionCard
import com.veltravia.marketscopeai.ui.components.PremiumSegmentedProgress
import com.veltravia.marketscopeai.ui.components.PremiumTwoToneHeadline
import com.veltravia.marketscopeai.ui.components.StaggeredBlock
import com.veltravia.marketscopeai.ui.components.pressScale
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.TrendingUp
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.launch

// Reference app's real questionnaire — exactly 3 screens.
private val goalOptions = listOf(
    "Consistent monthly income", "Account growth", "Funded trader status",
    "Retirement savings", "Quit 9-5 job"
)
private val assetOptions = listOf("Forex", "Crypto", "Stocks", "Synthetic", "Indices", "Commodities")
private val riskPerTradeOptions = listOf("0.5%", "1%", "2%", "3%", "5%")
private val targetReturnOptions = listOf("5%", "10%", "15%", "20%", "30%+")

// Quick-add entry setups on page 2: tapping a chip appends the term to the
// free-text entry-criteria field (tapping again removes it) — a fast path
// on top of the fully editable field, never a replacement for it.
private val entryChipOptions = listOf("Break & retest", "Liquidity sweep", "Trendline break", "S/R bounce")

// Experience options rendered as PremiumOptionCard rows on page 1: label,
// short description, and an icon — richer than a plain choice pill.
private val ExperienceCardData = listOf(
    Triple("Beginner", "Under a year in", Icons.Filled.Eco),
    Triple("Intermediate", "Consistent but refining", Icons.Filled.TrendingUp),
    Triple("Advanced", "Edge, rules, journal", Icons.Filled.EmojiEvents)
)
private val styleOptions = listOf("Scalping", "Day Trading", "Swing Trading", "Position Trading")
private val timeframeOptions = listOf("1M", "5M", "15M", "1H", "4H", "1D")
private const val MAX_TIMEFRAMES = 3

/**
 * The real 3-screen questionnaire shown right after sign-in — now with the
 * app's premium motion language: one violet→cyan gradient across every
 * control, springy press physics, selection pops with spring-in check marks,
 * a slow light sweep on the CTA, direction-aware page transitions and
 * staggered question entrances.
 *
 * Screen 1 — "Welcome {NAME}": experience level, primary trading goal,
 * current capital (USD), risk % per trade, target % monthly return.
 * Screen 2 — "PART 02 · BUILD YOUR EDGE": assets (multi-select), style,
 * timeframes (max 3), entry criteria with quick-add setup chips
 * (break & retest, liquidity sweep, trendline break, S/R bounce) above
 * the fully editable field.
 * Screen 3 — "Now lastly {NAME}": emotional struggles, ideal daily
 * routine. CTA reads "Save and Test Analysis Now" instead of "Next".
 */
@Composable
fun QuestionnaireScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val user = SessionManager.currentUser(context)
    val firstName = remember(user) {
        (user?.name ?: "trader").trim().split(" ").first().ifBlank { "trader" }
    }
    val scrollState = rememberScrollState()

    // --- Resume-where-you-stopped: if this user previously left the
    // questionnaire part-way (closed the app, got a call, anything), their
    // last page and every typed answer were persisted — restore them all
    // here so they continue exactly where they stopped, never from page 1.
    val savedProgress = remember { SessionManager.questionnaireProgress(context) }
    val savedPage = savedProgress?.first ?: 0
    val savedAnswers = savedProgress?.second

    var page by remember { mutableStateOf(savedPage) }

    // Screen 1 answers
    var experience by remember { mutableStateOf(savedAnswers?.optString("experience") ?: "") }
    var goal by remember { mutableStateOf(savedAnswers?.optString("goal") ?: "") }
    var capital by remember { mutableStateOf(savedAnswers?.optString("capital") ?: "") }
    var riskPerTrade by remember { mutableStateOf(savedAnswers?.optString("riskPerTrade") ?: "") }
    var targetReturn by remember { mutableStateOf(savedAnswers?.optString("targetReturn") ?: "") }

    // Screen 2 answers
    val assets = remember { mutableStateListOf<String>() }
    var style by remember { mutableStateOf(savedAnswers?.optString("style") ?: "") }
    val timeframes = remember { mutableStateListOf<String>() }
    var entryCriteria by remember { mutableStateOf(savedAnswers?.optString("entryCriteria") ?: "") }

    // Screen 3 answers
    var emotionalStruggles by remember { mutableStateOf(savedAnswers?.optString("emotionalStruggles") ?: "") }
    var dailyRoutine by remember { mutableStateOf(savedAnswers?.optString("dailyRoutine") ?: "") }

    // Restore the multi-select answers into the lists
    savedAnswers?.optJSONArray("assets")?.let { arr ->
        repeat(arr.length()) { assets.add(arr.optString(it)) }
    }
    savedAnswers?.optJSONArray("timeframes")?.let { arr ->
        repeat(arr.length()) { timeframes.add(arr.optString(it)) }
    }

    // --- Persist progress on every change: the instant the user types,
    // selects, or moves to the next page, the resume point updates. */
    LaunchedEffect(
        page, experience, goal, capital, riskPerTrade, targetReturn,
        assets.joinToString(","), style,
        timeframes.joinToString(","), entryCriteria,
        emotionalStruggles, dailyRoutine
    ) {
        val progress = JSONObject()
            .put("experience", experience)
            .put("goal", goal)
            .put("capital", capital)
            .put("riskPerTrade", riskPerTrade)
            .put("targetReturn", targetReturn)
            .put("assets", JSONArray(assets.toList()))
            .put("style", style)
            .put("timeframes", JSONArray(timeframes.toList()))
            .put("entryCriteria", entryCriteria)
            .put("emotionalStruggles", emotionalStruggles)
            .put("dailyRoutine", dailyRoutine)
        SessionManager.saveQuestionnaireProgress(context, page, progress)
    }

    // Every page starts from the top; the entrance animation covers the jump.
    LaunchedEffect(page) { scrollState.scrollTo(0) }

    val page1Valid = experience.isNotBlank() && goal.isNotBlank() && capital.isNotBlank() &&
        riskPerTrade.isNotBlank() && targetReturn.isNotBlank()
    val page2Valid = assets.isNotEmpty() && style.isNotBlank() && timeframes.isNotEmpty()
    val page3Valid = emotionalStruggles.isNotBlank() && dailyRoutine.isNotBlank()

    // Shake used when the user tries to select a 4th timeframe — the pill
    // row physically refuses, with a heavier haptic than a normal selection.
    val shake = remember { Animatable(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        if (page > 0) {
            val backInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .pressScale(backInteraction, downScale = 0.85f)
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = backInteraction,
                        indication = rememberRipple(bounded = false)
                    ) { page -= 1 }
                    .size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
        } else {
            Spacer(Modifier.height(20.dp))
        }

        val sectionLabel = when (page) {
            0 -> "About you"
            1 -> "Your approach"
            else -> "Mindset"
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PremiumSegmentedProgress(current = page, total = 3, modifier = Modifier.weight(1f))
            Text(
                sectionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(24.dp))

        // Direction-aware page transition: forward slides in from the right,
        // back from the left — with a spring, so the whole page settles
        // instead of snapping.
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState > initialState
                val enter = slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                    initialOffsetX = { if (forward) it / 5 else -it / 5 }
                ) + fadeIn(tween(280))
                val exit = slideOutHorizontally(
                    animationSpec = tween(240),
                    targetOffsetX = { if (forward) -it / 6 else it / 6 }
                ) + fadeOut(tween(160))
                enter togetherWith exit
            },
            label = "questionnairePage"
        ) { pageIdx ->
            Column(Modifier.fillMaxWidth()) {
                if (pageIdx == 0) {
                    StaggeredBlock(key = pageIdx, index = 0) {
                        PremiumChapterLabel(chapterNumber = 1, chapterTitle = "KNOW THE TRADER", name = firstName)
                        Spacer(Modifier.height(10.dp))
                        PremiumTwoToneHeadline(
                            line1 = "Every trader starts somewhere.",
                            line2 = "Where are you on the path?"
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "No judgment here — honest answers are how we tailor every analysis to you.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.height(32.dp))

                    StaggeredBlock(key = pageIdx, index = 1) {
                        QuestionLabel("Experience")
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ExperienceCardData.forEach { (label, subtitle, icon) ->
                                PremiumOptionCard(
                                    title = label,
                                    subtitle = subtitle,
                                    icon = icon,
                                    isSelected = experience == label,
                                    onSelect = { experience = label }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))
                    StaggeredBlock(key = pageIdx, index = 2) {
                        QuestionLabel("Main goal")
                        Spacer(Modifier.height(12.dp))
                        PremiumPillRow(
                            options = goalOptions,
                            selected = listOf(goal),
                            onSelect = { goal = it }
                        )
                    }

                    Spacer(Modifier.height(28.dp))
                    StaggeredBlock(key = pageIdx, index = 3) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            QuestionLabel("Trading capital")
                            Spacer(Modifier.width(6.dp))
                            Text("(USD)", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        Spacer(Modifier.height(12.dp))
                        PremiumCapitalField(
                            capital = capital,
                            onCapitalChange = { capital = it }
                        )
                    }

                    Spacer(Modifier.height(28.dp))
                    StaggeredBlock(key = pageIdx, index = 4) {
                        QuestionLabel("How much % are you willing to risk per trade?")
                        Spacer(Modifier.height(12.dp))
                        PremiumPillRow(
                            options = riskPerTradeOptions,
                            selected = listOf(riskPerTrade),
                            onSelect = { riskPerTrade = it }
                        )
                    }

                    Spacer(Modifier.height(28.dp))
                    StaggeredBlock(key = pageIdx, index = 5) {
                        QuestionLabel("What % monthly return are you aiming to get?")
                        Spacer(Modifier.height(12.dp))
                        PremiumPillRow(
                            options = targetReturnOptions,
                            selected = listOf(targetReturn),
                            onSelect = { targetReturn = it }
                        )
                    }
                } else if (pageIdx == 1) {
                    StaggeredBlock(key = pageIdx, index = 0) {
                        PremiumChapterLabel(chapterNumber = 2, chapterTitle = "BUILD YOUR EDGE", name = firstName)
                        Spacer(Modifier.height(10.dp))
                        PremiumTwoToneHeadline(
                            line1 = "Real edges aren't found.",
                            line2 = "They're built — let's build yours."
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "The way you trade informs every idea, level and timeframe we send your way.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.height(32.dp))

                    StaggeredBlock(key = pageIdx, index = 1) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            QuestionLabel("Assets")
                            Spacer(Modifier.width(6.dp))
                            Text("— pick every market you trade", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        Spacer(Modifier.height(12.dp))
                        PremiumPillRow(
                            options = assetOptions,
                            selected = assets,
                            onSelect = { option ->
                                if (assets.contains(option)) assets.remove(option) else assets.add(option)
                            }
                        )
                    }

                    Spacer(Modifier.height(28.dp))
                    StaggeredBlock(key = pageIdx, index = 2) {
                        QuestionLabel("Style")
                        Spacer(Modifier.height(12.dp))
                        PremiumPillRow(
                            options = styleOptions,
                            selected = listOf(style),
                            onSelect = { style = it }
                        )
                    }

                    Spacer(Modifier.height(28.dp))
                    StaggeredBlock(key = pageIdx, index = 3) {
                        QuestionLabel("Timeframes — choose up to $MAX_TIMEFRAMES")
                        Spacer(Modifier.height(6.dp))
                        if (timeframes.isNotEmpty()) {
                            Text(
                                timeframes.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentViolet,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                "Select timeframes (max $MAX_TIMEFRAMES)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        // The pill row shakes when a 4th timeframe is refused.
                        Box(
                            modifier = Modifier.graphicsLayer {
                                translationX = shake.value
                            }
                        ) {
                            PremiumPillRow(
                                options = timeframeOptions,
                                selected = timeframes,
                                onSelect = { option ->
                                    if (timeframes.contains(option)) {
                                        timeframes.remove(option)
                                    } else if (timeframes.size < MAX_TIMEFRAMES) {
                                        timeframes.add(option)
                                    } else {
                                        scope.launch {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            shake.snapTo(0f)
                                            shake.animateTo(
                                                0f,
                                                keyframes {
                                                    durationMillis = 340
                                                    -12f at 70
                                                    9f at 150
                                                    -5f at 230
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(28.dp))
                    StaggeredBlock(key = pageIdx, index = 4) {
                        QuestionLabel("How do you take your entries?")
                        Spacer(Modifier.height(12.dp))
                        // Quick-add chips: tap to append a setup you use, tap
                        // again to remove it — the field below stays fully
                        // editable for anything the chips don't cover.
                        PremiumPillRow(
                            options = entryChipOptions.map { "+ $it" },
                            selected = entryChipOptions.filter { entryCriteria.contains(it, ignoreCase = true) }.map { "+ $it" },
                            onSelect = { option ->
                                val term = option.removePrefix("+ ")
                                val terms = entryCriteria
                                    .split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .toMutableList()
                                val existing = terms.firstOrNull { it.equals(term, ignoreCase = true) }
                                if (existing != null) terms.remove(existing) else terms.add(term)
                                entryCriteria = terms.joinToString(", ")
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = entryCriteria,
                            onValueChange = { entryCriteria = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            placeholder = { Text("Describe your entry routine", color = TextMuted) },
                            colors = PremiumFieldColors()
                        )
                    }
                } else {
                    StaggeredBlock(key = pageIdx, index = 0) {
                        Text(
                            "Now lastly $firstName,",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Before our first analysis, let's understand your psychology and routine so we can help you better.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.height(32.dp))

                    StaggeredBlock(key = pageIdx, index = 1) {
                        QuestionLabel("What are some of your emotional struggles?")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = emotionalStruggles,
                            onValueChange = { emotionalStruggles = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. Impatience, Fear, Revenge", color = TextMuted) },
                            singleLine = true,
                            colors = PremiumFieldColors()
                        )
                    }

                    Spacer(Modifier.height(28.dp))
                    StaggeredBlock(key = pageIdx, index = 2) {
                        QuestionLabel("What is your ideal daily routine?")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = dailyRoutine,
                            onValueChange = { dailyRoutine = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            placeholder = { Text("Honestly describe what your usual days are like right now..", color = TextMuted) },
                            colors = PremiumFieldColors()
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(36.dp))

        GradientPrimaryButton(
            text = if (page == 2) "Save and Test Analysis Now" else "Next",
            enabled = when (page) {
                0 -> page1Valid
                1 -> page2Valid
                else -> page3Valid && !saving
            },
            onClick = {
                when (page) {
                    0 -> page = 1
                    1 -> page = 2
                    else -> if (saving) {
                        // Already persisting to the server — ignore extra taps.
                    } else {
                        val answers = QuestionnaireAnswers(
                            experience = experience,
                            goal = goal,
                            capitalUsd = capital,
                            riskPerTrade = riskPerTrade,
                            targetReturn = targetReturn,
                            assets = assets.toList(),
                            style = style,
                            timeframes = timeframes.toList(),
                            entryCriteria = entryCriteria.trim(),
                            emotionalStruggles = emotionalStruggles.trim(),
                            dailyRoutine = dailyRoutine.trim()
                        )
                        // Save locally first so this device routes correctly even
                        // if the network hiccups, then persist to the backend so
                        // completion survives sign-out / reinstall — sign-in checks
                        // questionnaireCompleted server-side to skip the onboarding
                        // questionnaire for returning users.
                        SessionManager.saveQuestionnaire(context, answers)
                        // Completed for real — the resume point is no longer
                        // needed and must never override the completed state.
                        SessionManager.clearQuestionnaireProgress(context)
                        val token = SessionManager.sessionToken(context)
                        if (token.isNullOrBlank()) {
                            onDone()
                        } else {
                            saving = true
                            scope.launch {
                                runCatching { ApiClient.saveQuestionnaire(token, answers.toJson()) }
                                    .onFailure {
                                        // Local save already succeeded — a transient
                                        // network failure never blocks onboarding.
                                        android.util.Log.w("Questionnaire", "Server save failed", it)
                                    }
                                saving = false
                                onDone()
                            }
                        }
                    }
                }
            }
        )

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun QuestionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary
    )
}

/**
 * Unified text field styling: the same violet accent the gradient starts
 * with, so typing feels part of the same control family as the pills.
 */
@Composable
private fun PremiumFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentViolet,
    unfocusedBorderColor = BorderSubtle,
    cursorColor = AccentViolet,
    // Explicit text colors — don't rely on the M3 default,
    // which was rendering typed digits invisible against the
    // background on Ijezie's device.
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    disabledTextColor = TextMuted
)

/** Capital input with digit-only filtering, kept from the original flow. */
@Composable
private fun PremiumCapitalField(capital: String, onCapitalChange: (String) -> Unit) {
    OutlinedTextField(
        value = capital,
        onValueChange = { value ->
            onCapitalChange(value.filter { it.isDigit() }.take(12))
        },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Text(
                "$",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = AccentViolet
            )
        },
        placeholder = { Text("e.g. 500", color = TextMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = PremiumFieldColors()
    )
}

/**
 * Wrapping rows of premium choice pills: 3 per row for short labels,
 * 2 for long ones — same layout rule as before, new motion.
 */
@Composable
private fun PremiumPillRow(
    options: List<String>,
    selected: List<String>,
    onSelect: (String) -> Unit
) {
    val visible = options.filter { it.isNotBlank() }
    var index = 0
    while (index < visible.size) {
        // fit roughly 3 pills per row for long labels, 2 for short ones
        val rowSize = if (visible.any { it.length > 12 }) 2 else 3
        val row = visible.subList(index, minOf(index + rowSize, visible.size))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { option ->
                PremiumChoicePill(
                    option = option,
                    isSelected = selected.contains(option),
                    onSelect = { onSelect(option) }
                )
            }
        }
        index += row.size
        Spacer(Modifier.height(10.dp))
    }
}
