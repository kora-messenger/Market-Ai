package com.veltravia.marketscopeai.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.veltravia.marketscopeai.BuildConfig
import java.io.File
import java.io.FileOutputStream

/**
 * Puts a bitmap on the system clipboard as a real image (not just text), so
 * the user can paste it straight into WhatsApp, Notes, an email, anywhere.
 * Backed by a cache file exposed through our FileProvider — Android grants
 * the pasting app temporary read access to that content:// URI automatically
 * (clipboard URI permission grants have worked this way since API 24, our
 * minSdk), so no extra runtime permission is needed.
 */
object ClipboardImage {

    fun copy(context: Context, bitmap: Bitmap, label: String): Boolean {
        return try {
            val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val file = File(dir, "$label-${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            val clip = ClipData(ClipDescription(label, arrayOf("image/png")), ClipData.Item(uri))
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(clip)
            true
        } catch (_: Exception) {
            false
        }
    }
}
