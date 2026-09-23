package com.veltravia.marketscopeai.shake

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Save shake screenshots to the owner's visible Photos/Gallery library, not app cache. */
internal object ScreenshotGallery {
    fun save(context: Context, bitmap: Bitmap): Boolean {
        val resolver = context.contentResolver
        val filename = "MarketScopeAI_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MarketScope AI")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val saved = resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
            if (!saved) throw IllegalStateException("Could not encode screenshot")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) < 1) {
                    throw IllegalStateException("Could not publish screenshot")
                }
            }
            true
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }
}
