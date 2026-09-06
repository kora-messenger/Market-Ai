package com.veltravia.marketai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.veltravia.marketai.data.QuestionnaireAnswers
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.BorderSubtle
import com.veltravia.marketai.ui.theme.SurfaceDark
import com.veltravia.marketai.ui.theme.TextMuted
import com.veltravia.marketai.ui.theme.TextPrimary
import com.veltravia.marketai.ui.theme.TextSecondary

// Reference app's real questionnaire — exactly 3 screens.
private val experienceOptions = listOf("Beginner", "Intermediate", "Advanced")
private val goalOptions = listOf(
    "Consistent monthly income", "Account growth", "Funded trader status",
    "Retirement savings", "Quit 9-5 job"
)
private val assetOptions = listOf("Forex", "Crypto", "Stocks", "Synthetic", "Indices", "Commodities")
private val styleOptions = listOf("Scalping", "Day Trading", "Swing Trading", "Position Trading")
private val timeframeOptions = listOf("1M", "5M", "15M", "1H", "4H", "1D")
private const val MAX_TIMEFRAMES = 3

/**
 * The real 3-screen questionnaire shown right after sign-in.
 *
 * Screen 1 — "Welcome {NAME}": experience level, primary trading goal,
 * current capital (USD).
 * Screen 2 — "Nice! {NAME}": assets traded, trading style, preferred
 * timeframes (max 3), entry criteria.
 * Screen 3 — "Now lastly {NAME}": emotional struggles, ideal daily
 * routine. CTA reads "Save and Test Analysis Now" instead of "Next".
 */
@Composable
fun QuestionnaireScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val user = SessionManager.currentUser(context)
    val firstName = remember(user) {
        (user?.name ?: "trader").trim().split(" ").first().ifBlank { "trader" }
    }

    var page by remember { mutableStateOf(0) }

    // Screen 1 answers
    var experience by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var capital by remember { mutableStateOf("") }

    // Screen 2 answers
    val assets = remember { mutableStateListOf<String>() }
    var style by remember { mutableStateOf("") }
    val timeframes = remember { mutableStateListOf<String>() }
    var entryCriteria by remember { mutableStateOf("") }

    // Screen 3 answers
    var emotionalStruggles by remember { mutableStateOf("") }
    var dailyRoutine by remember { mutableStateOf("") }

    val page1Valid = experience.isNotBlank() && goal.isNotBlank() && capital.isNotBlank()
    val page2Valid = assets.isNotEmpty() && style.isNotBlank() && timeframes.isNotEmpty()
    val page3Valid = emotionalStruggles.isNotBlank() && dailyRoutine.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        if (page > 0) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { page -= 1 }
            )
            Spacer(Modifier.height(16.dp))
        } else {
            Spacer(Modifier.height(20.dp))
        }

        if (page == 0) {
            Text(
                "Welcome $firstName,",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Let us get to understand your trading preferences and perform our first analysis!",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(32.dp))

            QuestionLabel("What is your Experience Level?")
            Spacer(Modifier.height(12.dp))
            PillRow(
                options = experienceOptions,
                selected = listOf(experience),
                singleSelect = true,
                onSelect = { experience = it }
            )

            Spacer(Modifier.height(28.dp))
            QuestionLabel("What is your Primary Trading Goal?")
            Spacer(Modifier.height(12.dp))
            PillRow(
                options = goalOptions,
                selected = listOf(goal),
                singleSelect = true,
                onSelect = { goal = it }
            )

            Spacer(Modifier.height(28.dp))
            QuestionLabel("How much capital do you currently have? (USD)")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = capital,
                onValueChange = { value ->
                    capital = value.filter { it.isDigit() }.take(12)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. 500", color = TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = BorderSubtle,
                    cursorColor = AccentCyan
                )
            )
        } else if (page == 1) {
            Text(
                "Nice! $firstName,",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Now tell us about your current approach to trading so that we can tailor your experience.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(32.dp))

            QuestionLabel("Assets Traded")
            Spacer(Modifier.height(12.dp))
            PillRow(
                options = assetOptions,
                selected = assets,
                singleSelect = false,
                onSelect = { option ->
                    if (assets.contains(option)) assets.remove(option) else assets.add(option)
                }
            )

            Spacer(Modifier.height(28.dp))
            QuestionLabel("Trading Style")
            Spacer(Modifier.height(12.dp))
            PillRow(
                options = styleOptions,
                selected = listOf(style),
                singleSelect = true,
                onSelect = { style = it }
            )

            Spacer(Modifier.height(28.dp))
            QuestionLabel("Preferred Timeframe(s)")
            Spacer(Modifier.height(6.dp))
            if (timeframes.isNotEmpty()) {
                Text(
                    timeframes.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan,
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
            PillRow(
                options = timeframeOptions,
                selected = timeframes,
                singleSelect = false,
                onSelect = { option ->
                    if (timeframes.contains(option)) {
                        timeframes.remove(option)
                    } else if (timeframes.size < MAX_TIMEFRAMES) {
                        timeframes.add(option)
                    }
                }
            )

            Spacer(Modifier.height(28.dp))
            QuestionLabel("What are your trading Entry Criteria")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = entryCriteria,
                onValueChange = { entryCriteria = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("Describe how you enter trades", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = BorderSubtle,
                    cursorColor = AccentCyan
                )
            )
        } else {
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
            Spacer(Modifier.height(32.dp))

            QuestionLabel("What are some of your emotional struggles?")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = emotionalStruggles,
                onValueChange = { emotionalStruggles = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Impatience, Fear, Revenge", color = TextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = BorderSubtle,
                    cursorColor = AccentCyan
                )
            )

            Spacer(Modifier.height(28.dp))
            QuestionLabel("What is your ideal daily routine?")
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = dailyRoutine,
                onValueChange = { dailyRoutine = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("Honestly describe what your usual days are like right now..", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = BorderSubtle,
                    cursorColor = AccentCyan
                )
            )
        }

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = {
                when (page) {
                    0 -> page = 1
                    1 -> page = 2
                    else -> {
                        SessionManager.saveQuestionnaire(
                            context,
                            QuestionnaireAnswers(
                                experience = experience,
                                goal = goal,
                                capitalUsd = capital,
                                assets = assets.toList(),
                                style = style,
                                timeframes = timeframes.toList(),
                                entryCriteria = entryCriteria.trim(),
                                emotionalStruggles = emotionalStruggles.trim(),
                                dailyRoutine = dailyRoutine.trim()
                            )
                        )
                        onDone()
                    }
                }
            },
            enabled = when (page) {
                0 -> page1Valid
                1 -> page2Valid
                else -> page3Valid
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentCyan,
                contentColor = Color(0xFF06202A),
                disabledContainerColor = SurfaceDark,
                disabledContentColor = TextMuted
            )
        ) {
            Text(
                if (page == 2) "Save and Test Analysis Now" else "Next",
                fontWeight = FontWeight.Bold
            )
        }


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
 * Rounded option pills like the reference app. Handles wrapping rows,
 * single- vs multi-select, and ignores empty option labels.
 */
@Composable
private fun PillRow(
    options: List<String>,
    selected: List<String>,
    singleSelect: Boolean,
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
                val isSelected = selected.contains(option)
                Text(
                    option,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color(0xFF06202A) else TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) AccentCyan else SurfaceDark)
                        .border(
                            1.dp,
                            if (isSelected) AccentCyan else BorderSubtle,
                            RoundedCornerShape(50)
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
        index += row.size
        Spacer(Modifier.height(10.dp))
    }
}
