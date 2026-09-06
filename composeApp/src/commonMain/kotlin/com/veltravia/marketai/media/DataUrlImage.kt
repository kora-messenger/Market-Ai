package com.veltravia.marketai.media

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.veltravia.marketai.data.provideHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.Image

/**
 * Loads an image from a URL or a base64 data URL and renders it with Skia.
 * Works identically on Android and iOS.
 */
@Composable
fun NetworkImage(url: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    var imageBitmap by remember(url) { mutableStateOf<org.jetbrains.skia.Bitmap?>(null) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        imageBitmap = withContext(Dispatchers.Default) {
            runCatching {
                val bytes: ByteArray = if (url.startsWith("data:")) {
                    val b64 = url.substringAfter("base64,", url)
                    base64Decode(b64)
                } else {
                    provideHttpClient().get(url).readBytes()
                }
                org.jetbrains.skia.Bitmap.makeFromImage(Image.makeFromEncoded(bytes))
            }.getOrNull()
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!.asComposeImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

private fun base64Decode(data: String): ByteArray {
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    return kotlin.io.encoding.Base64.decode(data)
}
