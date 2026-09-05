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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veltravia.marketai.data.QuestionnaireAnswers
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.GoldAmber
import com.veltravia.marketai.ui.theme.TextSecondary

private val experienceOptions = listOf("Beginner", "Intermediate", "Advanced")
private val styleOptions = listOf("Scalping", "Day Trading", "Swing Trading", "Position Trading")
private val goalOptions = listOf(
    "Consistent monthly income", "Account growth", "Funded trader status",
    "Retirement savings", "Quit 9-5 job"
)
private val marketOptions = listOf("Forex", "Crypto", "Stocks", "Synthetics", "Indices", "Commodities")
private val timeframeOptions = listOf("1M", "5M", "15M", "1H", "4H", "1D")
private const val MAX_TIMEFRAMES = 3

@Composable
fun QuestionnaireScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val user = SessionManager.currentUser(context)

    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 6

    var experience by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    val markets = remember { mutableStateListOf<String>() }
    val timeframes = remember { mutableStateListOf<String>() }
    var losingPlan by remember { mutableStateOf("") }
    var emotions by remember { mutableStateOf("") }
    var routine by remember { mutableStateOf("") }
    var avoidConditions by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(40.dp))

        Text(
            "Welcome ${user?.name ?: "Trader"},",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Let us get to understand your trading preferences and perform our first analysis.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "💡 Be honest with your answers, it helps us tailor the app experience for you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GoldAmber.copy(alpha = 0.18f))
                .padding(14.dp)
        )

        Spacer(Modifier.height(20.dp))

        LinearProgressIndicator(
            progress = { (step + 1).toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = AccentCyan,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        when (step) {
            0 -> SingleChoiceStep(
                title = "What is your Experience Level?",
                options = experienceOptions,
                selected = experience,
                onSelect = { experience = it }
            )
            1 -> SingleChoiceStep(
                title = "What's Your Trading Style?",
                options = styleOptions,
                selected = style,
                onSelect = { style = it }
            )
            2 -> SingleChoiceStep(
                title = "What is your Primary Trading Goal?",
                options = goalOptions,
                selected = goal,
                onSelect = { goal = it }
            )
            3 -> MultiChoiceStep(
                title = "Which markets do you trade?",
                subtitle = "Select all that apply",
                options = marketOptions,
                selected = markets,
                onToggle = { option ->
                    if (markets.contains(option)) markets.remove(option) else markets.add(option)
                }
            )
            4 -> MultiChoiceStep(
                title = "Preferred Timeframes",
                subtitle = "Select up to $MAX_TIMEFRAMES timeframes",
                options = timeframeOptions,
                selected = timeframes,
                onToggle = { option ->
                    if (timeframes.contains(option)) {
                        timeframes.remove(option)
                    } else if (timeframes.size < MAX_TIMEFRAMES) {
                        timeframes.add(option)
                    }
                }
            )
            5 -> {
                Text(
                    "A few honest details",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = losingPlan,
                    onValueChange = { losingPlan = it },
                    label = { Text("Losing Streak Plan") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = emotions,
                    onValueChange = { emotions = it },
                    label = { Text("Emotional Challenges") },
                    placeholder = { Text("e.g. Impatience, Fear, Revenge") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = routine,
                    onValueChange = { routine = it },
                    label = { Text("Ideal Daily Routine") },
                    placeholder = { Text("Honestly describe what your usual days are like right now..") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = avoidConditions,
                    onValueChange = { avoidConditions = it },
                    label = { Text("Avoid Trade Conditions") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    coachingPreview(experience, emotions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentCyan,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }) {
                    Text("Back")
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }

            Button(
                onClick = {
                    if (step < totalSteps - 1) {
                        step++
                    } else {
                        SessionManager.saveQuestionnaire(
                            context,
                            QuestionnaireAnswers(
                                experience = experience,
                                style = style,
                                goal = goal,
                                markets = markets.toList(),
                                timeframes = timeframes.toList(),
                                losingPlan = losingPlan,
                                emotions = emotions,
                                routine = routine,
                                avoidConditions = avoidConditions
                            )
                        )
                        onDone()
                    }
                },
                enabled = when (step) {
                    0 -> experience.isNotBlank()
                    1 -> style.isNotBlank()
                    2 -> goal.isNotBlank()
                    3 -> markets.isNotEmpty()
                    4 -> timeframes.isNotEmpty()
                    else -> true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    if (step < totalSteps - 1) "Continue" else "Finish",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SingleChoiceStep(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(Modifier.height(20.dp))
    options.forEach { option ->
        val isSelected = option == selected
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isSelected) AccentCyan.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surface
                )
                .border(
                    width = 1.dp,
                    color = if (isSelected) AccentCyan else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable { onSelect(option) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                option,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) AccentCyan else MaterialTheme.colorScheme.onBackground,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun MultiChoiceStep(
    title: String,
    subtitle: String,
    options: List<String>,
    selected: List<String>,
    onToggle: (String) -> Unit
) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(Modifier.height(4.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    Spacer(Modifier.height(20.dp))
    options.forEach { option ->
        val isSelected = selected.contains(option)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isSelected) AccentCyan.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surface
                )
                .border(
                    width = 1.dp,
                    color = if (isSelected) AccentCyan else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable { onToggle(option) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                option,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) AccentCyan else MaterialTheme.colorScheme.onBackground,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

private fun coachingPreview(experience: String, emotions: String): String {
    val answers = QuestionnaireAnswers(
        experience = experience,
        style = "", goal = "",
        markets = emptyList(), timeframes = emptyList(),
        losingPlan = "", emotions = emotions,
        routine = "", avoidConditions = ""
    )
    return SessionManager.coachingLine(answers)
}
