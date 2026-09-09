package com.veltravia.marketscopeai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.Instrument
import com.veltravia.marketscopeai.data.InstrumentCatalog
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.RoleBadge
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Team-only screen: publish curated daily signals and manually close the
 * ones that have no public price feed (Deriv synthetics). Regular users
 * never see this route — the "Post" pill only appears for the admin.
 */
@Composable
fun AdminSignalsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var feed by remember { mutableStateOf<JSONArray?>(null) }
    var feedError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    // pending screenshot-comment moderation queue
    var pendingComments by remember { mutableStateOf<JSONArray?>(null) }

    // form state
    var query by remember { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }
    var instrument by remember { mutableStateOf<Instrument?>(null) }
    var direction by remember { mutableStateOf("long") }
    var entry by remember { mutableStateOf("") }
    var stopLoss by remember { mutableStateOf("") }
    var tp1 by remember { mutableStateOf("") }
    var tp2 by remember { mutableStateOf("") }
    var tp3 by remember { mutableStateOf("") }
    var thesis by remember { mutableStateOf("") }
    var strength by remember { mutableStateOf("moderate") }
    var mode by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var members by remember { mutableStateOf<org.json.JSONArray?>(null) }
    var memberQuery by remember { mutableStateOf("") }
    var busyMemberId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reloadKey) {
        val token = SessionManager.sessionToken(context) ?: return@LaunchedEffect
        try {
            feed = ApiClient.fetchDailySignalsFeed(token, 100).optJSONArray("signals") ?: org.json.JSONArray()
            feedError = null
        } catch (e: Exception) {
            feedError = e.message ?: "Could not load signals"
        }
        try {
            pendingComments = ApiClient.fetchPendingSignalComments(token)
        } catch (_: Exception) {
            pendingComments = null // not admin / offline — section simply stays hidden
        }
        try {
            members = ApiClient.fetchAdminMembers(token).optJSONArray("members") ?: org.json.JSONArray()
        } catch (_: Exception) {
            members = null
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
                "Team Console",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Text(
            "Publish a curated signal, or close one manually (needed for synthetics — they have no public price feed).",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(16.dp))

        // ---------- pending screenshot-comment reviews ----------
        if (pendingComments != null && pendingComments!!.length() > 0) {
            Text("Comment reviews (${pendingComments!!.length()})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GoldAmber)
            Spacer(Modifier.height(4.dp))
            Text(
                "Trader comments with screenshots stay hidden from everyone else until you approve them.",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            for (i in 0 until pendingComments!!.length()) {
                val pc = pendingComments!!.optJSONObject(i) ?: continue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceLight)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(pc.optString("authorName"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text(pc.optString("instrument"), fontSize = 11.sp, color = TextMuted)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(pc.optString("body"), fontSize = 12.sp, color = TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        val token = SessionManager.sessionToken(context) ?: return@OutlinedButton
                        scope.launch {
                            try {
                                ApiClient.rejectSignalComment(token, pc.optString("id"))
                                reloadKey++
                            } catch (ex: Exception) {
                                Toast.makeText(context, ex.message ?: "Could not reject", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, modifier = Modifier.height(32.dp)) {
                        Text("Reject", fontSize = 11.sp, color = BearRed, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = {
                        val token = SessionManager.sessionToken(context) ?: return@OutlinedButton
                        scope.launch {
                            try {
                                ApiClient.approveSignalComment(token, pc.optString("id"))
                                reloadKey++
                            } catch (ex: Exception) {
                                Toast.makeText(context, ex.message ?: "Could not approve", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, modifier = Modifier.height(32.dp)) {
                        Text("Approve", fontSize = 11.sp, color = BullGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ---------- publish form ----------
        Text("Publish new signal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(10.dp))

        Text("Instrument", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceLight)
                .clickable { pickerOpen = !pickerOpen }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                instrument?.display ?: "Select an instrument",
                color = if (instrument != null) TextPrimary else TextMuted,
                fontWeight = FontWeight.Medium
            )
            Text(if (pickerOpen) "▲" else "▼", color = TextMuted)
        }
        if (pickerOpen) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search instruments", color = TextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentCyan, cursorColor = AccentCyan
                )
            )
            Spacer(Modifier.height(6.dp))
            val filtered = remember(query) {
                InstrumentCatalog.all.filter { query.isBlank() || it.display.contains(query, ignoreCase = true) }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceLight)
            ) {
                LazyColumn {
                    items(filtered) { item ->
                        Text(
                            item.display,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { instrument = item; pickerOpen = false; query = "" }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StrengthChip("Long", direction == "long", BullGreen) { direction = "long" }
            StrengthChip("Short", direction == "short", BearRed) { direction = "short" }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Mode:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            StrengthChip("Scalp", mode == "scalp", AccentCyan) { mode = if (mode == "scalp") null else "scalp" }
            StrengthChip("Swing", mode == "swing", AccentViolet) { mode = if (mode == "swing") null else "swing" }
        }

        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminField(entry, { entry = it }, "Entry", Modifier.weight(1f))
            AdminField(stopLoss, { stopLoss = it }, "Stop loss", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminField(tp1, { tp1 = it }, "TP1", Modifier.weight(1f))
            AdminField(tp2, { tp2 = it }, "TP2", Modifier.weight(1f))
            AdminField(tp3, { tp3 = it }, "TP3", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Conviction:", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
            StrengthChip("Strong", strength == "strong", BullGreen) { strength = "strong" }
            StrengthChip("Moderate", strength == "moderate", GoldAmber) { strength = "moderate" }
            StrengthChip("Weak", strength == "weak", BearRed) { strength = "weak" }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = thesis,
            onValueChange = { thesis = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
            placeholder = { Text("Thesis — what's the setup and why (shown to members).", color = TextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                focusedBorderColor = AccentCyan, cursorColor = AccentCyan
            )
        )

        formError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = BearRed, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val inst = instrument ?: return@Button
                val e = entry.toDoubleOrNull()
                val sl = stopLoss.toDoubleOrNull()
                val tps = listOfNotNull(tp1.toDoubleOrNull(), tp2.toDoubleOrNull(), tp3.toDoubleOrNull())
                if (e == null || sl == null || tps.isEmpty()) {
                    formError = "Entry, stop loss and at least one TP are required"
                    return@Button
                }
                formError = null
                saving = true
                scope.launch {
                    try {
                        val token = SessionManager.sessionToken(context)
                            ?: throw IllegalStateException("Not signed in")
                        ApiClient.publishDailySignal(token, inst.id, direction, e, sl, tps, thesis.ifBlank { null }, strength, mode)
                        Toast.makeText(context, "Signal published", Toast.LENGTH_SHORT).show()
                        entry = ""; stopLoss = ""; tp1 = ""; tp2 = ""; tp3 = ""; thesis = ""; mode = null
                        reloadKey++
                    } catch (ex: Exception) {
                        formError = ex.message ?: "Could not publish"
                    } finally {
                        saving = false
                    }
                }
            },
            enabled = instrument != null && !saving,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
        ) {
            if (saving) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
            else Text("Publish Signal", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(28.dp))

        // ---------- live/recent signals + manual close ----------
        Text("All signals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Outcomes are marked automatically by the AI price monitor (every 15 min, against candle highs/lows). Manual close is only an override for special cases.",
            style = MaterialTheme.typography.bodySmall, color = TextMuted
        )
        Spacer(Modifier.height(10.dp))
        when {
            feedError != null -> Text(feedError!!, color = BearRed, style = MaterialTheme.typography.bodySmall)
            feed == null -> CircularProgressIndicator(color = AccentCyan)
            feed!!.length() == 0 -> Text("Nothing published yet.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            else -> {
                val list = feed!!
                for (i in 0 until list.length()) {
                    val item = list.optJSONObject(i) ?: continue
                    AdminSignalRow(item) { outcome ->
                        scope.launch {
                            try {
                                val token = SessionManager.sessionToken(context) ?: return@launch
                                ApiClient.closeDailySignal(token, item.optString("id"), outcome)
                                Toast.makeText(context, "Closed as $outcome", Toast.LENGTH_SHORT).show()
                                reloadKey++
                            } catch (ex: Exception) {
                                Toast.makeText(context, ex.message ?: "Could not close", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        Spacer(Modifier.height(28.dp))

        // ---------- members & roles (mentor manager) ----------
        if (members != null) {
            Text("Members & roles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Promote trusted members to mentor — their name gets the violet Mentor badge everywhere. Admin roles are locked to your admin email(s).",
                style = MaterialTheme.typography.bodySmall, color = TextMuted
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = memberQuery,
                onValueChange = { memberQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search name or email", color = TextMuted) },
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(Modifier.height(10.dp))
            val q = memberQuery.trim().lowercase()
            var shown = 0
            for (i in 0 until members!!.length()) {
                val m = members!!.optJSONObject(i) ?: continue
                val name = m.optString("name", "")
                val email = m.optString("email", "")
                if (q.isNotBlank() && !name.lowercase().contains(q) && !email.lowercase().contains(q)) continue
                shown++
                MemberRoleRow(m, busyMemberId == m.optString("id")) { role ->
                    val token = SessionManager.sessionToken(context) ?: return@MemberRoleRow
                    scope.launch {
                        busyMemberId = m.optString("id")
                        try {
                            ApiClient.setAdminMemberRole(token, m.optString("id"), role)
                            // refresh the member list so the badge/state is server-truth
                            members = ApiClient.fetchAdminMembers(token).optJSONArray("members") ?: org.json.JSONArray()
                            Toast.makeText(context, if (role == "mentor") "Promoted to mentor" else "Mentor role removed", Toast.LENGTH_SHORT).show()
                        } catch (ex: Exception) {
                            Toast.makeText(context, ex.message ?: "Could not update role", Toast.LENGTH_SHORT).show()
                        } finally {
                            busyMemberId = null
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (shown == 0) Text("No members match that search.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(32.dp))
    }
}

/** One member row of the mentor manager: presence dot, name/email, role chip,
 *  and the promote/demote action. Admins are shown but locked. */
@Composable
private fun MemberRoleRow(m: JSONObject, busy: Boolean, onSetRole: (String) -> Unit) {
    val name = m.optString("name", "Member")
    val email = m.optString("email", "")
    val role = m.optString("role", "member")
    val online = m.optBoolean("online", false)
    val isAdmin = role.equals("admin", ignoreCase = true)
    val isMentor = role.equals("mentor", ignoreCase = true)
    val initials = name.trim().split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceLight)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(AccentCyan.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(initials.ifBlank { "?" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.width(6.dp))
                RoleBadge(role)
                if (online) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(BullGreen)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("online", fontSize = 9.5.sp, color = BullGreen, fontWeight = FontWeight.Medium)
                }
            }
            Text(email, fontSize = 11.sp, color = TextMuted)
        }
        Spacer(Modifier.weight(1f))
        when {
            busy -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentCyan)
            isAdmin -> Text("Admin", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
            isMentor -> OutlinedButton(
                onClick = { onSetRole("member") },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.heightIn(min = 32.dp)
            ) { Text("Remove mentor", fontSize = 11.sp, color = BearRed, fontWeight = FontWeight.SemiBold) }
            else -> Button(
                onClick = { onSetRole("mentor") },
                colors = ButtonDefaults.buttonColors(containerColor = AccentViolet, contentColor = androidx.compose.ui.graphics.Color.White),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.heightIn(min = 32.dp)
            ) { Text("Make mentor", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun AdminSignalRow(item: JSONObject, onClose: (String) -> Unit) {
    val instrument = item.optString("instrument", "")
    val direction = item.optString("direction", "long")
    val isLong = direction.equals("long", ignoreCase = true)
    val status = item.optString("status", "live")
    val outcome = item.optString("outcome", "")
    val publishedAt = item.optString("publishedAt", "")
    val author = if (item.optString("author", "") == "ai") "AI" else "Team"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceLight)
            .padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$instrument ${if (isLong) "▲" else "▼"}", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
            val resolvedBy = item.optString("resolvedBy", "")
            val resolvedTag = when (resolvedBy) {
                "auto" -> " · auto"
                "manual" -> " · manual"
                else -> ""
            }
            Text(
                if (status == "closed") "CLOSED · ${outcome.replace('_', ' ')}$resolvedTag" else "OPEN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (status == "closed") TextMuted else AccentCyan
            )
        }
        Text(
            "$author · ${fmtDate(publishedAt)}",
            fontSize = 11.sp,
            color = TextMuted
        )
        if (status != "closed") {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onClose("successful") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BullGreen)
                ) { Text("Win", fontSize = 11.sp) }
                OutlinedButton(
                    onClick = { onClose("invalidated_sl") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BearRed)
                ) { Text("Loss", fontSize = 11.sp) }
                OutlinedButton(
                    onClick = { onClose("breakeven") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAmber)
                ) { Text("Breakeven", fontSize = 11.sp) }
                OutlinedButton(
                    onClick = { onClose("expired") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                ) { Text("Expired", fontSize = 11.sp) }
            }
        }
    }
}

private fun fmtDate(iso: String): String = try {
    val z = Instant.parse(iso).atZone(ZoneId.systemDefault())
    z.format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
} catch (_: Exception) { "" }

@Composable
private fun StrengthChip(label: String, selected: Boolean, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) color else SurfaceLight)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = if (selected) androidx.compose.ui.graphics.Color.White else TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun AdminField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(12)) },
        modifier = modifier,
        placeholder = { Text(placeholder, color = TextMuted, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
            focusedBorderColor = AccentCyan, cursorColor = AccentCyan
        )
    )
}
