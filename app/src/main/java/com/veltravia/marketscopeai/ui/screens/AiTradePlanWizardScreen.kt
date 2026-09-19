package com.veltravia.marketscopeai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.components.GradientPrimaryButton
import com.veltravia.marketscopeai.ui.components.PremiumChoicePill
import com.veltravia.marketscopeai.ui.components.PremiumOptionCard
import com.veltravia.marketscopeai.ui.components.PremiumSegmentedProgress
import com.veltravia.marketscopeai.ui.components.StaggeredBlock
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// AI Trade Plan wizard — 5 steps (Profile, Strategy, Risk Rules, Psychology,
// Review) building a personalized trading playbook. Wherever the trader has
// already told us the answer in their onboarding questionnaire, it arrives
// pre-filled so nothing gets asked twice.
// ---------------------------------------------------------------------------

private const val MAX_PLANS = 3
private const val MAX_TIMEFRAMES = 3

private val experienceOptions = listOf(
    Triple("Beginner", "Still building the foundations", Icons.Filled.Eco),
    Triple("Intermediate", "Trading regularly, refining the process", Icons.Filled.TrendingUp),
    Triple("Advanced", "Proven edge with firm rules", Icons.Filled.EmojiEvents)
)
private val goalOptions = listOf(
    "Consistent monthly income", "Account growth", "Funded trader status",
    "Long-term wealth building", "Trade alongside another career"
)
private val assetOptions = listOf("Forex", "Crypto", "Stocks", "Synthetic", "Indices", "Commodities")
private val styleOptions = listOf("Scalping", "Day Trading", "Swing Trading", "Position Trading")
private val timeframeOptions = listOf("1M", "5M", "15M", "1H", "4H", "1D")
private val riskPctOptions = listOf("0.5%", "1%", "2%", "3%", "5%")
private val rrRatioOptions = listOf("1:1", "1:2", "1:3", "1:5")
private val entryChipOptions = listOf("Break and retest", "Liquidity sweep", "Trendline break", "Support and resistance bounce")
private val avoidChipOptions = listOf("Major news events", "Tired or distracted", "After a loss", "Choppy markets")
private val emotionChipOptions = listOf(
    "FOMO entries", "Revenge trading", "Closing winners too early",
    "Holding losers too long", "Overtrading slow days", "Hesitating on the best setups"
)
private val routineChipOptions = listOf("Morning review", "London session", "New York session", "Evening journaling")

/** Appends [term] to [current] (comma-joined) if absent, removes it if present. */
private fun toggleTermInList(current: String, term: String): String {
    val parts = current.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    if (parts.contains(term)) parts.remove(term) else parts.add(term)
    return parts.joinToString(", ")
}

@Composable
private fun PremiumFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = AccentCyan,
    cursorColor = AccentCyan
)

@Composable
private fun WizardLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary
    )
}

@Composable
private fun WizardHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = TextMuted)
}

@Composable
private fun WizardField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = if (minLines > 1) (minLines * 56).dp else 0.dp),
        placeholder = { Text(placeholder, color = TextMuted) },
        singleLine = minLines == 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = PremiumFieldColors()
    )
}

@Composable
private fun WizardPillRow(
    options: List<String>,
    selected: List<String>,
    onSelect: (String) -> Unit
) {
    val visible = options.filter { it.isNotBlank() }
    var index = 0
    while (index < visible.size) {
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

@Composable
fun AiTradePlanWizardScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Pre-fill everything the onboarding questionnaire already answered.
    val saved = remember { SessionManager.questionnaireAnswers(context) }
    var name by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf(saved?.experience ?: "") }
    var goal by remember { mutableStateOf(saved?.goal ?: "") }
    var capital by remember { mutableStateOf(saved?.capitalUsd ?: "") }
    val assets = remember { mutableStateListOf<String>() }
    var style by remember { mutableStateOf(saved?.style ?: "") }
    val timeframes = remember { mutableStateListOf<String>() }
    var entryCriteria by remember { mutableStateOf(saved?.entryCriteria ?: "") }
    var riskPct by remember {
        mutableStateOf((saved?.riskPerTrade ?: "").takeIf { it.isNotBlank() && riskPctOptions.contains(it) } ?: "")
    }
    var customRisk by remember { mutableStateOf("") }
    var rrRatio by remember { mutableStateOf("") }
    var avoidConditions by remember { mutableStateOf("") }
    val emotions = remember { mutableStateListOf<String>() }
    var emotionsOther by remember { mutableStateOf("") }
    var losingPlan by remember { mutableStateOf("") }
    var idealRoutine by remember { mutableStateOf(saved?.dailyRoutine ?: "") }

    saved?.assets?.forEach { if (assetOptions.contains(it)) assets.add(it) }
    saved?.timeframes?.forEach { if (timeframeOptions.contains(it) && timeframes.size < MAX_TIMEFRAMES) timeframes.add(it) }

    var page by remember { mutableStateOf(0) }
    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var existingCount by remember { mutableStateOf(-1) } // -1 = still loading

    LaunchedEffect(Unit) {
        val token = SessionManager.sessionToken(context) ?: return@LaunchedEffect
        try {
            val resp = ApiClient.fetchAiTradePlans(token)
            existingCount = resp.optJSONArray("plans")?.length() ?: 0
        } catch (_: Exception) {
            existingCount = 0 // don't block the wizard on a count check
        }
    }

    val effectiveRisk = if (riskPct.isNotBlank()) riskPct else customRisk.takeIf { it.isNotBlank() }

    val pageValid = when (page) {
        0 -> name.isNotBlank() && experience.isNotBlank() && goal.isNotBlank() &&
            capital.toDoubleOrNull() != null && capital.toDoubleOrNull()!! > 0
        1 -> assets.isNotEmpty() && style.isNotBlank() && timeframes.isNotEmpty() && entryCriteria.isNotBlank()
        2 -> effectiveRisk != null && rrRatio.isNotBlank()
        else -> true
    }

    val limitReached = existingCount >= MAX_PLANS

    // Back at step 0 leaves the wizard; later steps go back a page.
    BackHandler(enabled = page > 0) { page-- }

    val handleBack: () -> Unit = if (page > 0) {
        { page-- }
    } else {
        onBack
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = handleBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Trade Plan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Spacer(Modifier.height(14.dp))
        PremiumSegmentedProgress(current = page, total = 5)
        Spacer(Modifier.height(6.dp))
        Text(
            "Step ${page + 1} of 5",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.align(Alignment.End)
        )
        Spacer(Modifier.height(18.dp))

        when (page) {
            // ------------------------------------------------ Step 1: Profile
            0 -> {
                StaggeredBlock(key = page, index = 0) {
                    WizardLabel("Plan name")
                    Spacer(Modifier.height(6.dp))
                    WizardHint("Give this plan a label you'll recognize, like \"London scalps\" or \"Swing gold\".")
                    Spacer(Modifier.height(8.dp))
                    WizardField(name, { name = it }, "e.g. Day trading")
                }
                Spacer(Modifier.height(24.dp))
                StaggeredBlock(key = page, index = 1) {
                    WizardLabel("Experience level")
                    Spacer(Modifier.height(10.dp))
                    experienceOptions.forEach { (label, subtitle, icon) ->
                        PremiumOptionCard(
                            title = label,
                            subtitle = subtitle,
                            icon = icon,
                            isSelected = experience == label,
                            onSelect = { experience = label }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                StaggeredBlock(key = page, index = 2) {
                    WizardLabel("Main trading goal")
                    Spacer(Modifier.height(10.dp))
                    WizardPillRow(options = goalOptions, selected = listOf(goal), onSelect = { goal = it })
                }
                Spacer(Modifier.height(24.dp))
                StaggeredBlock(key = page, index = 3) {
                    WizardLabel("Trading capital (USD)")
                    Spacer(Modifier.height(6.dp))
                    WizardHint("The balance this plan should be sized around.")
                    Spacer(Modifier.height(8.dp))
                    WizardField(capital, { capital = it }, "e.g. 500", keyboardType = KeyboardType.Number)
                }
            }
            // ----------------------------------------------- Step 2: Strategy
            1 -> {
                StaggeredBlock(key = page, index = 0) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        WizardLabel("Assets traded")
                        Spacer(Modifier.width(6.dp))
                        Text("— every market this plan covers", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Spacer(Modifier.height(10.dp))
                    WizardPillRow(
                        options = assetOptions,
                        selected = assets.toList(),
                        onSelect = { if (assets.contains(it)) assets.remove(it) else assets.add(it) }
                    )
                }
                Spacer(Modifier.height(24.dp))
                StaggeredBlock(key = page, index = 1) {
                    WizardLabel("Trading style")
                    Spacer(Modifier.height(10.dp))
                    WizardPillRow(options = styleOptions, selected = listOf(style), onSelect = { style = it })
                }
                Spacer(Modifier.height(24.dp))
                StaggeredBlock(key = page, index = 2) {
                    WizardLabel("Timeframes — choose up to $MAX_TIMEFRAMES")
                    Spacer(Modifier.height(10.dp))
                    WizardPillRow(
                        options = timeframeOptions,
                        selected = timeframes.toList(),
                        onSelect = {
                            if (timeframes.contains(it)) {
                                timeframes.remove(it)
                            } else if (timeframes.size < MAX_TIMEFRAMES) {
                                timeframes.add(it)
                            }
                        }
                    )
                }
                Spacer(Modifier.height(24.dp))
                StaggeredBlock(key = page, index = 3) {
                    WizardLabel("Entry criteria")
                    Spacer(Modifier.height(6.dp))
                    WizardHint("In your own words: what has to be true before you click buy or sell.")
                    Spacer(Modifier.height(8.dp))
                    WizardField(
                        entryCriteria, { entryCriteria = it },
                        "Describe how you enter trades", minLines = 3
                    )
                    Spacer(Modifier.height(10.dp))
                    WizardHint("Quick add")
                    Spacer(Modifier.height(8.dp))
                    WizardPillRow(
                        options = entryChipOptions,
                        selected = entryCriteria.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        onSelect = { entryCriteria = toggleTermInList(entryCriteria, it) }
                    )
                }
            }
            // -------------------------------------------------- Step 3: Risk
            2 -> {
                StaggeredBlock(key = page, index = 0) {
                    WizardLabel("Max risk per trade")
                    Spacer(Modifier.height(6.dp))
                    WizardHint("The most you're willing to lose on a single trade.")
                    Spacer(Modifier.height(10.dp))
                    WizardPillRow(
                        options = riskPctOptions,
                        selected = listOf(riskPct),
                        onSelect = {
                            riskPct = if (riskPct == it) "" else it
                            if (riskPct.isNotBlank()) customRisk = ""
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    WizardField(
                        customRisk, { customRisk = it; if (it.isNotBlank()) riskPct = "" },
                        "Or enter a custom %", keyboardType = KeyboardType.Decimal
                    )
                }
                Spacer(Modifier.height(24.dp))
                StaggeredBlock(key = page, index = 1) {
                    WizardLabel("Risk-to-reward ratio")
                    Spacer(Modifier.height(6.dp))
                    WizardHint("The minimum reward you target for every unit you risk.")
                    Spacer(Modifier.height(10.dp))
                    WizardPillRow(
                        options = rrRatioOptions,
                        selected = listOf(rrRatio),
                        onSelect = { rrRatio = if (rrRatio == it) "" else it }
                    )
                }
                Spacer(Modifier.height(24.dp))
                StaggeredBlock(key = page, index = 2) {
                    WizardLabel("When you stay out of the market")
                    Spacer(Modifier.height(6.dp))
                    WizardHint("Conditions that mean no trade, no matter how good the setup looks.")
                    Spacer(Modifier.height(8.dp))
                    WizardField(
                        avoidConditions, { avoidConditions = it },
                        "e.g. during major news releases", minLines = 3
                    )
                    Spacer(Modifier.height(10.dp))
                    WizardHint("Quick add")
                    Spacer(Modifier.height(8.dp))
                    WizardPillRow(
                        options = avoidChipOptions,
                        selected = avoidConditions.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        onSelect = { avoidConditions = toggleTermInList(avoidConditions, it) }
                    )
                }
            }
            // ------------------------------------------- Step 4: Psychology
            3 -> {
                StaggeredBlock(key = page, index = 0) {
                    WizardLabel("Emotional challenges")
                    Spacer(Modifier.height(6.dp))
                    WizardHint("The mental habits that tend to cost you money. Honest answers make stronger plans.")
                    Spacer(Modifier.height(10.dp))
                    WizardPillRow(
                        options = emotionChipOptions,
                        selected = emotions.toList(),
                        onSelect = { if (emotions.contains(it)) emotions.remove(it) else emotions.add(it) }
                    )
                    Spacer(Modifier.height(8.dp))
                    WizardField(emotionsOther, { emotionsOther = it }, "Anything else worth mentioning")
                }
                Spacer(Modifier.height(24.dp))
                StaggeredBlock(key = page, index = 1) {
                    WizardLabel("Losing-streak plan")
                    Spacer(Modifier.height(6.dp))
                    WizardHint("What you'll actually do after two or three losses in a row.")
                    Spacer(Modifier.height(8.dp))
                    WizardField(
                        losingPlan, { losingPlan = it },
                        "e.g. Stop for the day, review the journal, halve my size tomorrow", minLines = 3
                    )
                }
                Spacer(Modifier.height(24.dp))
                StaggeredBlock(key = page, index = 2) {
                    WizardLabel("Ideal daily routine")
                    Spacer(Modifier.height(6.dp))
                    WizardHint("What a disciplined trading day looks like for you.")
                    Spacer(Modifier.height(8.dp))
                    WizardField(
                        idealRoutine, { idealRoutine = it },
                        "Your perfect trading-day routine", minLines = 3
                    )
                    Spacer(Modifier.height(10.dp))
                    WizardHint("Quick add")
                    Spacer(Modifier.height(8.dp))
                    WizardPillRow(
                        options = routineChipOptions,
                        selected = idealRoutine.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        onSelect = { idealRoutine = toggleTermInList(idealRoutine, it) }
                    )
                }
            }
            // ------------------------------------------------ Step 5: Review
            else -> {
                StaggeredBlock(key = page, index = 0) {
                    Text(
                        "Review your inputs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    WizardHint("Your plan is built from exactly what's listed below.")
                }
                Spacer(Modifier.height(16.dp))
                ReviewRow("Plan name", name)
                ReviewRow("Experience", experience)
                ReviewRow("Goal", goal)
                ReviewRow("Capital", if (capital.isNotBlank()) "$$capital" else "")
                ReviewRow("Assets", assets.joinToString(", "))
                ReviewRow("Style", style)
                ReviewRow("Timeframes", timeframes.joinToString(", "))
                ReviewEntry("Entry criteria", entryCriteria)
                ReviewRow("Max risk per trade", effectiveRisk ?: "")
                ReviewRow("Risk-to-reward", rrRatio)
                ReviewEntry("Avoid trading when", avoidConditions)
                ReviewRow("Emotional challenges", (emotions + listOf(emotionsOther).filter { it.isNotBlank() }).joinToString(", "))
                ReviewEntry("Losing-streak plan", losingPlan)
                ReviewEntry("Ideal routine", idealRoutine)

                if (limitReached) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "You already have $MAX_PLANS saved trade plans. Delete one from your Saved tab before creating another.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BearRed
                    )
                }
                error?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, color = BearRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        GradientPrimaryButton(
            text = if (page == 4) "Create Trade Plan" else "Continue",
            enabled = if (page == 4) pageValid && !limitReached else pageValid,
            loading = generating,
            showArrow = page != 4,
            onClick = {
                if (page < 4) {
                    page++
                } else if (!generating) {
                    generating = true
                    error = null
                    scope.launch {
                        try {
                            val token = SessionManager.sessionToken(context)
                                ?: throw IllegalStateException("Not signed in")
                            val emotionsJoined = (emotions.toList() + listOf(emotionsOther).filter { it.isNotBlank() })
                                .joinToString(", ")
                            val resp = ApiClient.generateAiTradePlan(
                                sessionToken = token,
                                name = name.trim(),
                                experience = experience,
                                goal = goal,
                                capital = capital,
                                assets = assets.toList(),
                                style = style,
                                timeframes = timeframes.toList(),
                                entryCriteria = entryCriteria.trim(),
                                riskPerTrade = (effectiveRisk ?: "").removeSuffix("%"),
                                rrRatio = rrRatio,
                                avoidConditions = avoidConditions.trim(),
                                emotions = emotionsJoined,
                                losingPlan = losingPlan.trim(),
                                idealRoutine = idealRoutine.trim(),
                                notes = ""
                            )
                            onCreated(resp.optString("id"))
                        } catch (e: ApiClient.TradePlanLimitException) {
                            existingCount = MAX_PLANS
                            error = e.message
                        } catch (e: Exception) {
                            error = e.message ?: "Could not create your trade plan. Try again."
                        } finally {
                            generating = false
                        }
                    }
                }
            }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Your plan is written by our AI from your answers, your profile and your real activity, then saved to your account.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    StaggeredBlock(key = label, index = 1) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceLight)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(
                value.ifBlank { "Not set" },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (value.isBlank()) TextMuted else TextPrimary,
                modifier = Modifier.weight(1.4f),
                maxLines = 2
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReviewEntry(label: String, value: String) {
    StaggeredBlock(key = label, index = 1) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceLight)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(Modifier.height(4.dp))
            Text(
                value.ifBlank { "Not set" },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (value.isBlank()) TextMuted else TextPrimary
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
