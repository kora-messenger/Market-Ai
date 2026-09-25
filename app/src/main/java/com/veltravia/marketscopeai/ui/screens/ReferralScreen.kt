package com.veltravia.marketscopeai.ui.screens

import com.veltravia.marketscopeai.t

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.components.GradientPrimaryButton
import com.veltravia.marketscopeai.ui.components.PremiumSecondaryButton
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import org.json.JSONArray

private data class Invitee(val name: String, val email: String, val status: String, val earnedDays: Int)

/** Full-screen, server-backed invite program. Only a new invitee's first
 *  saved analysis earns one bonus day. Their first verified Google Play paid
 *  subscription earns seven more days, once for that invitee. */
@Composable
fun ReferralScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = remember { SessionManager.sessionToken(context) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var invitees by remember { mutableStateOf<List<Invitee>>(emptyList()) }
    var enteredCode by remember { mutableStateOf("") }
    var applying by remember { mutableStateOf(false) }
    var applyMessage by remember { mutableStateOf<String?>(null) }
    var applied by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(token) {
        if (token.isNullOrBlank()) { error = "Please sign in to invite friends."; loading = false; return@LaunchedEffect }
        try {
            val response = ApiClient.fetchReferrals(token)
            code = response.optString("code")
            link = response.optString("link")
            val list: JSONArray = response.optJSONArray("referrals") ?: JSONArray()
            invitees = (0 until list.length()).mapNotNull { i ->
                list.optJSONObject(i)?.let { Invitee(it.optString("name", "Trader"), it.optString("email"), it.optString("status"), it.optInt("earnedDays")) }
            }
        } catch (t: Exception) { error = t.message ?: "Could not load your invite code." }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(t("Invite friends"), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(76.dp).background(
                Brush.linearGradient(listOf(AccentViolet, AccentCyan)), RoundedCornerShape(24.dp)
            ), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text(t("Share the advantage"), color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(t("Invite a new trader. When they use your code and finish their first analysis, your account earns an extra day of Premium access."),
                color = TextSecondary, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))

            RewardCard("1 extra day", "Your friend signs up with your code and completes their first analysis.", Icons.Filled.Groups)
            Spacer(Modifier.height(10.dp))
            RewardCard("7 extra days", "Your friend starts a paid Google Play subscription. You earn this bonus once per invited friend.", Icons.Filled.WorkspacePremium)
            Spacer(Modifier.height(24.dp))

            if (loading) CircularProgressIndicator(color = AccentViolet)
            if (error != null) {
                Text(error!!, color = Color(0xFFB91C1C), textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 12.dp))
                PremiumSecondaryButton(text = t("Try again"), onClick = {
                    error = null; loading = true
                    scope.launch {
                        try {
                            val response = ApiClient.fetchReferrals(token ?: "")
                            code = response.optString("code")
                            link = response.optString("link")
                            val list = response.optJSONArray("referrals") ?: JSONArray()
                            invitees = (0 until list.length()).mapNotNull { i -> list.optJSONObject(i)?.let { Invitee(it.optString("name", "Trader"), it.optString("email"), it.optString("status"), it.optInt("earnedDays")) } }
                        } catch (t: Exception) { error = t.message ?: "Could not load your invite code." }
                        loading = false
                    }
                }, modifier = Modifier.fillMaxWidth())
            }
            if (code.isNotBlank() && link.isNotBlank()) {
                Column(Modifier.fillMaxWidth().background(SurfaceLight, RoundedCornerShape(20.dp)).padding(18.dp)) {
                    Text(t("YOUR INVITE CODE"), color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(code, color = TextPrimary, fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(t("MarketScope AI invite code"), code))
                            copied = true
                        }) { Icon(if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy, contentDescription = "Copy invite code", tint = AccentViolet) }
                    }
                    Text(t("Friends enter this code in Invite friends after signing in. They must do so before their first analysis or purchase."),
                        color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                }
                Spacer(Modifier.height(16.dp))
                GradientPrimaryButton("Share invite link", enabled = true, onClick = {
                    val message = "Join me on MarketScope AI. Download the app and enter invite code $code before your first analysis: $link"
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message)
                    }, "Share your MarketScope AI invite"))
                }, modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Filled.Share, showArrow = false)
            }

            Spacer(Modifier.height(30.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(t("Have a friend's code?"), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Spacer(Modifier.height(6.dp))
                Text(t("If you're new, add it within 7 days of joining, before completing an analysis or buying a plan."),
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = enteredCode,
                    onValueChange = { enteredCode = it.uppercase().take(12); applyMessage = null },
                    enabled = !applied && !applying,
                    label = { Text(t("Enter invite code")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                PremiumSecondaryButton("Apply code", onClick = {
                    applying = true; applyMessage = null
                    scope.launch {
                        try {
                            ApiClient.applyReferralCode(token ?: "", enteredCode.trim())
                            applied = true
                            applyMessage = "Invite code linked. Your friend will earn a bonus after your first analysis."
                        } catch (t: Exception) { applyMessage = t.message ?: "Couldn't apply this code." }
                        applying = false
                    }
                }, enabled = enteredCode.isNotBlank() && !applying && !applied && !token.isNullOrBlank(),
                    loading = applying, modifier = Modifier.fillMaxWidth())
                applyMessage?.let { Text(it, color = if (applied) AccentCyan else Color(0xFFB91C1C), fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)) }
            }

            Spacer(Modifier.height(30.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(t("Your invitations"), color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (!loading && error == null && invitees.isEmpty()) {
                    Text(t("No one has used your code yet. Share it with a friend to get started."), color = TextSecondary,
                        fontSize = 14.sp, modifier = Modifier.fillMaxWidth().background(SurfaceLight, RoundedCornerShape(16.dp)).padding(20.dp))
                }
                invitees.forEach { invitee ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(AccentViolet.copy(alpha = .1f), CircleShape), contentAlignment = Alignment.Center) {
                            Text(invitee.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = AccentViolet)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(invitee.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                            Text(invitee.email, color = TextMuted, fontSize = 12.sp)
                        }
                        Text(if (invitee.earnedDays > 0) "${invitee.earnedDays} ${if (invitee.earnedDays == 1) "day" else "days"} earned" else "Awaiting milestone",
                            color = if (invitee.earnedDays == 0) TextMuted else AccentViolet, fontSize = 11.sp)
                    }
                    Spacer(Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(t("Bonus days give Premium access, not cash or community posting privileges. Each milestone rewards you once per eligible new account."),
                color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun RewardCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().border(1.dp, BorderSubtle, RoundedCornerShape(18.dp)).padding(17.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).background(AccentViolet.copy(alpha = .10f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(25.dp))
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(description, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}
