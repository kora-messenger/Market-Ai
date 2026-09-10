package com.veltravia.marketscopeai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.components.GradientPrimaryButton
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Admin: Premium Management — search a user, inspect their Premium status,
 * grant free Premium (lifetime / N months / N years) or revoke an admin grant.
 * Paid subscriptions are never touched by a revoke; the backend always
 * recalculates effective access from every entitlement source.
 */
@Composable
fun AdminPremiumScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<org.json.JSONArray?>(null) }
    var selectedUser by remember { mutableStateOf<JSONObject?>(null) }
    var selectedBusy by remember { mutableStateOf(false) }
    var grantDialogOpen by remember { mutableStateOf(false) }
    var revokeDialogOpen by remember { mutableStateOf(false) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var auditEvents by remember { mutableStateOf<org.json.JSONArray?>(null) }

    fun refreshAudit(token: String) {
        scope.launch {
            try {
                auditEvents = ApiClient.adminFetchPremiumAudit(token, 20).optJSONArray("events")
            } catch (_: Exception) {
                // Activity section just stays hidden when unavailable.
            }
        }
    }

    fun loadUser(token: String, userId: String) {
        scope.launch {
            selectedBusy = true
            try {
                selectedUser = ApiClient.adminGetPremiumStatus(token, userId)
            } catch (_: Exception) {
                Toast.makeText(context, "Could not load premium status", Toast.LENGTH_SHORT).show()
            } finally {
                selectedBusy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val token = SessionManager.sessionToken(context) ?: return@LaunchedEffect
        refreshAudit(token)
    }

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        val token = SessionManager.sessionToken(context) ?: return
        scope.launch {
            searching = true
            searchError = null
            try {
                results = ApiClient.adminSearchPremiumUsers(token, q).optJSONArray("users")
                if (results == null || results!!.length() == 0) searchError = "No MarketScope AI users match \"$q\"."
            } catch (_: Exception) {
                searchError = "Search failed — check your connection and try again."
            } finally {
                searching = false
            }
        }
    }

    fun grant(durationType: String, durationCount: Int, reason: String?) {
        val user = selectedUser ?: return
        val token = SessionManager.sessionToken(context) ?: return
        scope.launch {
            actionBusy = true
            actionError = null
            try {
                val resp = ApiClient.adminGrantPremium(token, user.optString("id"), durationType, durationCount, reason)
                if (resp.has("error")) {
                    actionError = resp.optString("error", "Could not grant Premium.")
                    if (resp.optBoolean("alreadyGranted", false)) loadUser(token, user.optString("id"))
                } else {
                    selectedUser = resp.optJSONObject("user") ?: selectedUser
                    grantDialogOpen = false
                    Toast.makeText(context, "Premium granted", Toast.LENGTH_SHORT).show()
                    refreshAudit(token)
                }
            } catch (_: Exception) {
                actionError = "Could not grant Premium — try again."
            } finally {
                actionBusy = false
            }
        }
    }

    fun revoke() {
        val user = selectedUser ?: return
        val token = SessionManager.sessionToken(context) ?: return
        scope.launch {
            actionBusy = true
            actionError = null
            try {
                val resp = ApiClient.adminRevokePremium(token, user.optString("id"))
                if (resp.has("error")) {
                    actionError = resp.optString("error", "Could not revoke Premium.")
                    if (resp.optBoolean("noGrant", false)) loadUser(token, user.optString("id"))
                } else {
                    selectedUser = resp.optJSONObject("user") ?: selectedUser
                    revokeDialogOpen = false
                    Toast.makeText(context, "Admin grant revoked", Toast.LENGTH_SHORT).show()
                    refreshAudit(token)
                }
            } catch (_: Exception) {
                actionError = "Could not revoke Premium — try again."
            } finally {
                actionBusy = false
            }
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
                "Premium Management",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Text(
            "Manage MarketScope AI Premium access",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(16.dp))

        // ---------- search ----------
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by email or name") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors()
        )
        Spacer(Modifier.height(10.dp))
        GradientPrimaryButton(
            text = "Search users",
            enabled = !searching && query.isNotBlank(),
            loading = searching,
            showArrow = false,
            leadingIcon = Icons.Filled.Search,
            onClick = { doSearch() }
        )
        if (searchError != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                searchError!!,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // ---------- results ----------
        val users = results
        if (users != null && users.length() > 0) {
            Spacer(Modifier.height(20.dp))
            Text(
                "Users (${users.length()})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            for (i in 0 until users.length()) {
                val u = users.optJSONObject(i) ?: continue
                PremiumUserCard(
                    user = u,
                    expanded = selectedUser?.optString("id") == u.optString("id") && selectedUser != null,
                    onClick = {
                        if (selectedUser?.optString("id") == u.optString("id")) {
                            selectedUser = null
                        } else {
                            selectedUser = u
                            SessionManager.sessionToken(context)?.let { loadUser(it, u.optString("id")) }
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // ---------- selected user premium detail ----------
        val sel = selectedUser
        if (sel != null) {
            Spacer(Modifier.height(12.dp))
            if (selectedBusy) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                PremiumStatusCard(
                    user = sel,
                    onGrant = { grantDialogOpen = true },
                    onRevoke = { revokeDialogOpen = true }
                )
                if (actionError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(actionError!!, color = BearRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ---------- premium activity ----------
        val audit = auditEvents
        if (audit != null && audit.length() > 0) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Premium Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            for (i in 0 until audit.length()) {
                val e = audit.optJSONObject(i) ?: continue
                PremiumActivityRow(event = e)
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    // ---------- grant dialog ----------
    if (grantDialogOpen) {
        GrantPremiumDialog(
            busy = actionBusy,
            onDismiss = { if (!actionBusy) grantDialogOpen = false },
            onConfirm = { durationType, count, reason -> grant(durationType, count, reason) }
        )
    }

    // ---------- revoke dialog ----------
    val selForRevoke = selectedUser
    if (revokeDialogOpen && selForRevoke != null) {
        val stillPaid = selForRevoke.optJSONObject("paidSubscription")?.optBoolean("active", false) == true
        AlertDialog(
            onDismissRequest = { if (!actionBusy) revokeDialogOpen = false },
            title = { Text("Revoke Admin-Granted Premium?") },
            text = {
                Text(
                    if (stillPaid)
                        "This will remove the administrator-granted Premium entitlement. This user has a paid subscription, so they will remain Premium."
                    else
                        "This will remove the administrator-granted Premium entitlement. The user will lose Premium access unless another entitlement exists."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { revoke() },
                    enabled = !actionBusy
                ) { Text("Revoke", color = BearRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { revokeDialogOpen = false },
                    enabled = !actionBusy
                ) { Text("Cancel") }
            }
        )
    }
}

// -----------------------------------------------------------------------------
// pieces
// -----------------------------------------------------------------------------

@Composable
private fun PremiumUserCard(user: JSONObject, expanded: Boolean, onClick: () -> Unit) {
    val name = user.optString("name", "User")
    val email = user.optString("email", "")
    val plan = user.optString("plan", "free")
    val grant = user.optJSONObject("adminGrant")
    val paid = user.optJSONObject("paidSubscription")?.optBoolean("active", false) == true
    val trial = user.optJSONObject("trial")?.optBoolean("active", false) == true

    val (badgeBg, badgeFg, badgeText) = when {
        grant != null && grant.optString("kind") == "lifetime" ->
            Triple(AccentViolet.copy(alpha = 0.12f), AccentViolet, "Lifetime")
        grant != null -> Triple(AccentViolet.copy(alpha = 0.12f), AccentViolet, grant.optString("label", "Premium"))
        paid -> Triple(GoldAmber.copy(alpha = 0.15f), GoldAmber, "Premium")
        trial -> Triple(Color(0xFF0891B2).copy(alpha = 0.12f), Color(0xFF0891B2), "Trial")
        else -> Triple(BearRed.copy(alpha = 0.1f), BearRed, "Free")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF3F4F7))
            .border(
                width = if (expanded) 1.5.dp else 1.dp,
                color = if (expanded) AccentViolet.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(AccentViolet.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    name.trim().split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2)
                        .joinToString("").ifEmpty { "?" },
                    color = AccentViolet,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    email,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBg)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(badgeText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badgeFg)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (grant != null)
                    (grant.optString("grantedBy", "Admin").let { "Granted by $it" }) + (if (grant.has("expiresAt") && !grant.isNull("expiresAt")) " · until ${formatDate(grant.optString("expiresAt"))}" else " · never expires")
                else if (paid) "Active paid subscription"
                else if (trial) "Free trial active"
                else "No active Premium entitlement",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun PremiumStatusCard(user: JSONObject, onGrant: () -> Unit, onRevoke: () -> Unit) {
    val grant = user.optJSONObject("adminGrant")
    val paid = user.optJSONObject("paidSubscription")?.optBoolean("active", false) == true
    val premium = user.optJSONObject("premium")
    val active = premium?.optBoolean("active", false) == true
    val sources = premium?.optJSONArray("sources")
    val trial = user.optJSONObject("trial")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF3F4F7))
            .padding(16.dp)
    ) {
        Text("Premium Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(12.dp))
        StatusRow(
            icon = if (active) Icons.Filled.CheckCircle else Icons.Filled.WorkspacePremium,
            tint = if (active) BullGreen else TextSecondary,
            label = "Status",
            value = if (active) "Active" else "None"
        )
        Spacer(Modifier.height(8.dp))
        StatusRow(
            icon = Icons.Filled.Verified,
            tint = AccentViolet,
            label = "Sources",
            value = if (sources != null && sources.length() > 0)
                (0 until sources.length()).joinToString(" + ") { sources.optString(it) }
            else "—"
        )
        Spacer(Modifier.height(8.dp))
        StatusRow(
            icon = Icons.Filled.WorkspacePremium,
            tint = GoldAmber,
            label = "Admin grant",
            value = when {
                grant == null -> "None"
                grant.optString("kind") == "lifetime" -> "Lifetime — never expires"
                else -> "${grant.optString("label")} · expires ${formatDate(grant.optString("expiresAt"))}"
            }
        )

        if (grant != null && !grant.optString("reason").isNullOrEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Reason: ${grant.optString("reason")}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        if (grant != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Granted ${formatDate(grant.optString("grantedAt"))} by ${grant.optString("grantedBy", "admin")}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        if (trial != null && trial.optBoolean("active", false)) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Trial ends ${formatDate(trial.optString("endsAt"))}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        Spacer(Modifier.height(16.dp))
        if (grant != null) {
            Button(
                onClick = onRevoke,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BearRed.copy(alpha = 0.12f),
                    contentColor = BearRed
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text("Revoke Admin-Granted Premium", fontWeight = FontWeight.Bold)
            }
        } else {
            GradientPrimaryButton(
                text = "Grant Free Premium",
                enabled = true,
                showArrow = false,
                leadingIcon = Icons.Filled.WorkspacePremium,
                onClick = onGrant
            )
        }
    }
}

@Composable
private fun GrantPremiumDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (durationType: String, durationCount: Int, reason: String?) -> Unit
) {
    var durationType by remember { mutableStateOf("lifetime") }
    var countText by remember { mutableStateOf("1") }
    var reason by remember { mutableStateOf<String?>(null) }
    var customReason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Grant Free Premium") },
        text = {
            Column {
                Text(
                    "This will give this user free Premium access with the same features as a paid subscriber. Lifetime Premium does not expire automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = durationType == "lifetime",
                        onClick = { durationType = "lifetime" },
                        label = { Text("Lifetime") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentViolet.copy(alpha = 0.15f),
                            selectedLabelColor = AccentViolet
                        )
                    )
                    FilterChip(
                        selected = durationType == "months",
                        onClick = { durationType = "months" },
                        label = { Text("Months") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentViolet.copy(alpha = 0.15f),
                            selectedLabelColor = AccentViolet
                        )
                    )
                    FilterChip(
                        selected = durationType == "years",
                        onClick = { durationType = "years" },
                        label = { Text("Years") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentViolet.copy(alpha = 0.15f),
                            selectedLabelColor = AccentViolet
                        )
                    )
                }
                if (durationType != "lifetime") {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { v -> countText = v.filter { it.isDigit() }.take(2) },
                        label = { Text("How many ${if (durationType == "months") "months" else "years"}? (1–50)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Grant reason (optional)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = reason == null && customReason.isBlank(),
                        onClick = { reason = null },
                        label = { Text("None") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0891B2).copy(alpha = 0.12f),
                            selectedLabelColor = Color(0xFF0891B2)
                        )
                    )
                    FilterChip(
                        selected = reason == "Beta tester",
                        onClick = { reason = "Beta tester" },
                        label = { Text("Beta tester") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentViolet.copy(alpha = 0.15f),
                            selectedLabelColor = AccentViolet
                        )
                    )
                    FilterChip(
                        selected = reason == "VIP user",
                        onClick = { reason = "VIP user" },
                        label = { Text("VIP") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentViolet.copy(alpha = 0.15f),
                            selectedLabelColor = AccentViolet
                        )
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = reason == "Contest winner",
                        onClick = { reason = "Contest winner" },
                        label = { Text("Contest") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentViolet.copy(alpha = 0.15f),
                            selectedLabelColor = AccentViolet
                        )
                    )
                    FilterChip(
                        selected = reason == "Partner",
                        onClick = { reason = "Partner" },
                        label = { Text("Partner") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentViolet.copy(alpha = 0.15f),
                            selectedLabelColor = AccentViolet
                        )
                    )
                    FilterChip(
                        selected = customReason.isNotBlank(),
                        onClick = { },
                        label = {
                            OutlinedTextField(
                                value = customReason,
                                onValueChange = { customReason = it.take(80); reason = null },
                                placeholder = { Text("Custom reason…", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.width(120.dp).height(40.dp),
                                shape = RoundedCornerShape(10.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentViolet.copy(alpha = 0.15f),
                            selectedLabelColor = AccentViolet
                        )
                    )
                }
            }
        },
        confirmButton = {
            val count = countText.toIntOrNull() ?: 1
            val effectiveCount = count.coerceIn(1, 50)
            TextButton(
                enabled = !busy && (durationType == "lifetime" || (countText.toIntOrNull() ?: 0) >= 1),
                onClick = {
                    onConfirm(
                        durationType,
                        if (durationType == "lifetime") 0 else effectiveCount,
                        customReason.trim().ifBlank { reason }
                    )
                }
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text("Grant Premium", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PremiumActivityRow(event: JSONObject) {
    val granted = event.optString("action", "") == "GRANTED"
    val kind = event.optString("kind", "lifetime")
    val kindLabel = if (kind == "lifetime") "Lifetime Premium" else if (kind == "months") "Premium (months)" else "Premium (years)"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF3F4F7))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = if (granted) AccentViolet else BearRed,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (granted) "$kindLabel granted" else "$kindLabel revoked",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        val target = event.optJSONObject("target")
        Text(
            "User: ${target?.optString("name").takeIf { !it.isNullOrEmpty() } ?: target?.optString("email") ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Text("Admin: ${event.optString("admin", "—")}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        if (!event.optString("reason").isNullOrEmpty()) {
            Text("Reason: ${event.optString("reason")}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Text(formatDate(event.optString("at")), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

private fun formatDate(iso: String): String {
    if (iso.isNullOrEmpty()) return "—"
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(iso.take(16))
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US).format(parsed ?: Date())
    } catch (_: Exception) {
        iso.take(10)
    }
}
