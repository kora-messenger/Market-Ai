package com.veltravia.marketai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veltravia.marketai.data.ApiClient
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.data.Instrument
import com.veltravia.marketai.data.InstrumentCatalog
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.SurfaceDark
import com.veltravia.marketai.ui.theme.TextMuted
import kotlinx.coroutines.launch

/**
 * Real chart-upload flow: user picks a 4H and a 15M screenshot of the SAME instrument,
 * chooses scalp/swing, and the backend runs the AI analysis over both images.
 */
@Composable
fun ChartUploadScreen(
    instrumentId: String,
    onBack: () -> Unit,
    onAnalysisComplete: (String) -> Unit
) {
    val instrument = remember(instrumentId) { InstrumentCatalog.byId(instrumentId) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imageH4 by remember { mutableStateOf<Uri?>(null) }
    var imageM15 by remember { mutableStateOf<Uri?>(null) }
    var mode by remember { mutableStateOf("swing") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickH4 = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { imageH4 = it }
    }
    val pickM15 = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { imageM15 = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    instrument?.display ?: instrumentId,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Upload two chart screenshots and let AI break them down",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("4H CHART", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(Modifier.height(8.dp))
        ChartSlot(
            imageUri = imageH4,
            label = "Higher timeframe (4H)",
            onPick = {
                pickH4.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onClear = { imageH4 = null }
        )

        Spacer(Modifier.height(20.dp))

        Text("15M CHART", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(Modifier.height(8.dp))
        ChartSlot(
            imageUri = imageM15,
            label = "Lower timeframe (15M)",
            onPick = {
                pickM15.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onClear = { imageM15 = null }
        )

        Spacer(Modifier.height(24.dp))

        Text("ANALYSIS STYLE", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeOption(
                title = "Swing",
                subtitle = "4H-biased, wider targets",
                selected = mode == "swing",
                onClick = { mode = "swing" },
                modifier = Modifier.weight(1f)
            )
            ModeOption(
                title = "Scalp",
                subtitle = "15M-biased, quick moves",
                selected = mode == "scalp",
                onClick = { mode = "scalp" },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(24.dp))

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                val h4 = imageH4
                val m15 = imageM15
                if (h4 == null || m15 == null) return@Button
                loading = true
                error = null
                scope.launch {
                    try {
                        val token = SessionManager.sessionToken(context)
                        if (token == null) {
                            loading = false
                            error = "Not signed in"
                            return@launch
                        }
                        val dataH4 = ApiClient.prepareChartImage(context, h4)
                        val dataM15 = ApiClient.prepareChartImage(context, m15)
                        val result = ApiClient.analyze(token, instrumentId, mode, dataH4, dataM15)
                        val id = result.optString("id", "")
                        loading = false
                        if (id.isNotEmpty()) onAnalysisComplete(id)
                        else error = "Analysis completed but was not saved"
                    } catch (e: ApiClient.TrialExpiredException) {
                        loading = false
                        error = e.message ?: "Your free trial has ended."
                    } catch (e: Exception) {
                        loading = false
                        error = e.message ?: "Analysis failed"
                    }
                }
            },
            enabled = !loading && imageH4 != null && imageM15 != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentCyan,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text("Analyzing charts…", fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Run AI Analysis", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "AI reads structure, sweeps and key levels from your screenshots. Make sure both charts show the same instrument.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ChartSlot(
    imageUri: Uri?,
    label: String,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .clickable(enabled = imageUri == null) { onPick() },
        contentAlignment = Alignment.Center
    ) {
        if (imageUri == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.AddAPhoto,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap to add $label screenshot",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        } else {
            AsyncImage(
                model = imageUri,
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AccentCyan.copy(alpha = 0.14f) else SurfaceDark)
            .border(
                1.dp,
                if (selected) AccentCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}
