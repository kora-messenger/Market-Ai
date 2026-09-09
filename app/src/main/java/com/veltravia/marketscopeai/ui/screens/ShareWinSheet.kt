package com.veltravia.marketscopeai.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * "Share your win" composer - opened from a closed, won signal card.
 * Traders add a comment and optionally attach their proof screenshot; the
 * result goes to the mentor desk for review before it appears publicly.
 */
@Composable
fun ShareWinSheet(
    signalId: String,
    instrument: String,
    direction: String,
    entry: Double,
    exitPrice: Double,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val token = remember { SessionManager.sessionToken(context) }

    var input by remember { mutableStateOf(TextFieldValue("")) }
    var attachedImage by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    attachedImage = ApiClient.prepareChartImage(context, uri)
                } catch (_: Exception) { /* picker cancelled or unreadable */ }
            }
        }
    }

    fun submit() {
        val text = input.text.trim()
        if (token == null || sending) return
        if (text.isEmpty() && attachedImage == null) {
            errorMsg = "Add a comment or attach your proof screenshot first."
            return
        }
        sending = true
        errorMsg = null
        scope.launch {
            try {
                ApiClient.shareSignalWin(token, signalId, text, attachedImage)
                android.widget.Toast.makeText(
                    context,
                    "Thanks. Your result has been sent for review.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                onSubmitted()
                onDismiss()
            } catch (e: Exception) {
                errorMsg = e.message ?: "Could not share your win"
            } finally {
                sending = false
            }
        }
    }

    fun shareToApps() {
        val dir = if (direction.equals("long", true)) "LONG" else "SHORT"
        val entryTxt = if (entry.isFinite()) entry.toString() else "-"
        val exitTxt = if (exitPrice.isFinite()) exitPrice.toString() else "-"
        val text = buildString {
            append("Took this $instrument $dir signal with MarketScope AI - entry $entryTxt, closed at $exitTxt. Target hit.")
            if (input.text.isNotBlank()) {
                append("\n\n")
                append(input.text.trim())
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share my win"))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Share your result",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextMuted)
                }
            }
            Text(
                "$instrument ${if (direction.equals("long", true)) "LONG" else "SHORT"} - take profit hit",
                fontSize = 12.sp,
                color = BullGreen,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    errorMsg = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                placeholder = {
                    Text("Share your comment about this signal...", color = TextMuted, fontSize = 13.sp)
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceLight,
                    unfocusedContainerColor = SurfaceLight,
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = BorderSubtle
                )
            )
            Spacer(Modifier.height(10.dp))

            // --- Trader proof screenshot attach ---
            if (attachedImage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceLight)
                ) {
                    AsyncImage(
                        model = attachedImage,
                        contentDescription = "Trader proof",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { attachedImage = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove image", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceLight)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                        .clickable {
                            pickImage.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Attach trader proof screenshot", fontSize = 13.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Proofs are reviewed by the mentor desk before they appear publicly.",
                fontSize = 11.sp,
                color = TextMuted
            )

            if (errorMsg != null) {
                Spacer(Modifier.height(8.dp))
                Text(errorMsg ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { submit() },
                enabled = !sending,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BullGreen)
            ) {
                if (sending) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Share now", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { shareToApps() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = AccentCyan)
                Spacer(Modifier.width(6.dp))
                Text("Share my win to apps", color = AccentCyan, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
