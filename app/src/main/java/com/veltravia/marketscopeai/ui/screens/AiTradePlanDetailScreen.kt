package com.veltravia.marketscopeai.ui.screens

import com.veltravia.marketscopeai.t

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// AI Trade Plan detail — the full generated playbook: snapshot, execution
// framework, risk rules, mindset guidance, the pre-market checklist and the
// bottom line. Deleting asks first, then returns to the caller.
// ---------------------------------------------------------------------------

@Composable
fun AiTradePlanDetailScreen(
    planId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var plan by remember { mutableStateOf<JSONObject?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(planId) {
        val token = SessionManager.sessionToken(context)
        if (token == null) {
            loadError = "Not signed in"
            return@LaunchedEffect
        }
        try {
            plan = ApiClient.fetchAiTradePlan(token, planId)
        } catch (e: Exception) {
            loadError = e.message ?: "Could not load this trade plan"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                plan?.optString("name") ?: "Trade Plan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
            if (plan != null && !deleting) {
                IconButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete plan", tint = BearRed)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            loadError != null -> {
                Spacer(Modifier.height(40.dp))
                Text(
                    loadError ?: "",
                    color = BearRed,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            plan == null -> {
                Spacer(Modifier.height(60.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan)
                }
            }
            else -> {
                val p = plan!!
                val content = p.optJSONObject("content") ?: JSONObject()
                val createdAt = runCatching {
                    val instant = Instant.parse(p.optString("createdAt"))
                    DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.of("UTC")).format(instant)
                }.getOrNull()
                createdAt?.let {
                    Text(
                        "Created $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(20.dp))
                }

                content.optString("snapshot").takeIf { it.isNotBlank() }?.let {
                    PlanSection(null, "Snapshot", it)
                    Spacer(Modifier.height(16.dp))
                }
                content.optString("execution").takeIf { it.isNotBlank() }?.let {
                    PlanSection(Icons.AutoMirrored.Filled.MenuBook, "Execution framework", it)
                    Spacer(Modifier.height(16.dp))
                }
                content.optString("riskRules").takeIf { it.isNotBlank() }?.let {
                    PlanSection(Icons.Filled.Rule, "Risk rules", it)
                    Spacer(Modifier.height(16.dp))
                }
                content.optString("mindset").takeIf { it.isNotBlank() }?.let {
                    PlanSection(Icons.Filled.Psychology, "Mindset", it)
                    Spacer(Modifier.height(16.dp))
                }

                val checklist = content.optJSONArray("checklist")
                if (checklist != null && checklist.length() > 0) {
                    Text(t("Pre-market checklist"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    for (i in 0 until checklist.length()) {
                        ChecklistRow(i + 1, checklist.optString(i))
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                }

                content.optString("bottomLine").takeIf { it.isNotBlank() }?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.horizontalGradient(listOf(AccentViolet, AccentCyan)))
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Column {
                            Text(t("Bottom line"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Text(t("This plan is educational guidance generated from your own inputs, not financial advice."),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (confirmDelete && plan != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(t("Delete this plan?")) },
            text = { Text("\"${plan!!.optString("name")}\" will be removed permanently. You can create a new one afterwards.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    deleting = true
                    scope.launch {
                        try {
                            val token = SessionManager.sessionToken(context)
                            if (token != null) ApiClient.deleteAiTradePlan(token, planId)
                        } catch (_: Exception) {
                            // Even if the network call failed, leaving a deleted
                            // plan screen is worse — return; the list will
                            // re-fetch and restore the row if it still exists.
                        } finally {
                            deleting = false
                            onBack()
                        }
                    }
                }) { Text(t("Delete"), color = BearRed, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(t("Cancel")) }
            }
        )
    }
}

@Composable
private fun PlanSection(icon: ImageVector?, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLight)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun ChecklistRow(number: Int, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceLight)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(50))
                .background(AccentViolet.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AccentViolet
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}
