package com.veltravia.marketai.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Real HTTP client for the Market Ai backend.
 * All calls run on Dispatchers.IO; failures throw MarketAiException with the server message.
 */
object ApiClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    class MarketAiException(message: String) : Exception(message)

    private fun request(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            if (!response.isSuccessful) {
                throw MarketAiException(
                    json.optString("error", "Request failed (${response.code})")
                )
            }
            return json
        }
    }

    /**
     * Run a chart-pair analysis on the backend.
     * @return full analysis result JSON: { id, instrument, instrumentId, mode, model, analysis, analyzedAt }
     */
    suspend fun analyze(
        instrumentId: String,
        mode: String,
        imageH4DataUrl: String,
        imageM15DataUrl: String
    ): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("instrumentId", instrumentId)
            .put("mode", mode)
            .put("imageH4", imageH4DataUrl)
            .put("imageM15", imageM15DataUrl)
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/analyze")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Recent analyses, newest first. */
    suspend fun fetchAnalyses(limit: Int = 30): JSONArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/analyses?limit=$limit")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            if (!response.isSuccessful) {
                throw MarketAiException(
                    json.optString("error", "Could not load history (${response.code})")
                )
            }
            json.optJSONArray("analyses") ?: JSONArray()
        }
    }

    /** Single stored analysis by id. */
    suspend fun fetchAnalysis(id: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/analyses/$id")
            .get()
            .build()
        request(request)
    }

    /**
     * Reads a chart screenshot from the photo picker, downscales it so the upload stays
     * light while remaining readable, and returns a base64 JPEG data URL.
     */
    suspend fun prepareChartImage(context: Context, uri: Uri): String =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver

            // Read bounds first to compute a sample size.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Could not read image" }

            val maxDim = 1600
            var sample = 1
            var w = bounds.outWidth
            var h = bounds.outHeight
            while (maxOf(w, h) / 2 >= maxDim) {
                sample *= 2
                w /= 2
                h /= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: throw MarketAiException("Could not decode image")

            // Second pass: exact-scale if still larger than maxDim on the long edge.
            val longEdge = maxOf(bitmap.width, bitmap.height)
            val scaled = if (longEdge > maxDim) {
                val ratio = maxDim.toFloat() / longEdge
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else bitmap

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (scaled !== bitmap) bitmap.recycle()
            scaled.recycle()
            "data:image/jpeg;base64,$base64"
        }
}
