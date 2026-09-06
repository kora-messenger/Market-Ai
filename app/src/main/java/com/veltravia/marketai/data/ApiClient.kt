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
                val baseError = json.optString("error", "Request failed (${response.code})")
                val reason = json.optString("reason", "")
                throw MarketAiException(
                    if (reason.isNotBlank()) "$baseError: $reason" else baseError
                )
            }
            return json
        }
    }

    /**
     * Run a chart-pair analysis on the backend.
     * @return full analysis result JSON: { id, instrument, instrumentId, mode, model, analysis, analyzedAt }
     */
    class TrialExpiredException(message: String) : Exception(message)

    suspend fun analyze(
        sessionToken: String,
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
            .addHeader("Authorization", "Bearer $sessionToken")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            if (response.code == 402 && json.optBoolean("trialExpired", false)) {
                throw TrialExpiredException(
                    json.optString("error", "Your free trial has ended.")
                )
            }
            if (!response.isSuccessful) {
                throw MarketAiException(json.optString("error", "Request failed (${response.code})"))
            }
            json
        }
    }

    /**
     * Verifies the Google ID token on the backend, upserts the user row, and returns
     * the server-issued session JWT plus the current community-membership state.
     */
    suspend fun authenticateWithGoogle(idToken: String): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("idToken", idToken)
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/auth/google")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Marks the signed-in user as a community member (real, persisted server-side). */
    suspend fun joinCommunity(sessionToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/community/join")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Recent analyses for the signed-in user, newest first. */
    suspend fun fetchAnalyses(sessionToken: String, limit: Int = 30): JSONArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/analyses?limit=$limit")
            .addHeader("Authorization", "Bearer $sessionToken")
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

    /** Single stored analysis by id, scoped to the signed-in user. */
    suspend fun fetchAnalysis(sessionToken: String, id: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/analyses/$id")
            .addHeader("Authorization", "Bearer $sessionToken")
            .get()
            .build()
        request(request)
    }

    /** Community feed page: { posts: [...], total, hasMore } — newest first. */
    suspend fun fetchCommunityFeed(
        sessionToken: String,
        offset: Int = 0,
        limit: Int = 20
    ): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${'$'}{ApiConfig.BASE_URL}/api/community/feed?offset=${'$'}offset&limit=${'$'}limit")
            .addHeader("Authorization", "Bearer ${'$'}sessionToken")
            .get()
            .build()
        request(request)
    }

    /** Publish a text post to the community. Returns { post: {...} }. */
    suspend fun createCommunityPost(sessionToken: String, body: String): JSONObject =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("body", body)
            val request = Request.Builder()
                .url("${'$'}{ApiConfig.BASE_URL}/api/community/posts")
                .addHeader("Authorization", "Bearer ${'$'}sessionToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            request(request)
        }

    /** Toggle one emoji reaction on a post. Returns { emoji, active }. */
    suspend fun toggleCommunityReaction(
        sessionToken: String,
        postId: String,
        emoji: String
    ): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("emoji", emoji)
        val request = Request.Builder()
            .url("${'$'}{ApiConfig.BASE_URL}/api/community/posts/${'$'}postId/react")
            .addHeader("Authorization", "Bearer ${'$'}sessionToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** All comments on a post (flat; the caller nests by parentId). */
    suspend fun fetchPostComments(sessionToken: String, postId: String): JSONArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${'$'}{ApiConfig.BASE_URL}/api/community/posts/${'$'}postId/comments")
                .addHeader("Authorization", "Bearer ${'$'}sessionToken")
                .get()
                .build()
            val json = request(request)
            json.optJSONArray("comments") ?: JSONArray()
        }

    /** Add a comment or reply (parentId nullable). Returns { comment: {...} }. */
    suspend fun addPostComment(
        sessionToken: String,
        postId: String,
        body: String,
        parentId: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("body", body)
        if (parentId != null) payload.put("parentId", parentId)
        val request = Request.Builder()
            .url("${'$'}{ApiConfig.BASE_URL}/api/community/posts/${'$'}postId/comments")
            .addHeader("Authorization", "Bearer ${'$'}sessionToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Public real total of users who have joined the community (no auth needed). */
    suspend fun fetchCommunityStats(): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/community/stats")
            .get()
            .build()
        request(request)
    }

    /** Trial status for the signed-in user: trialActive, trialDaysRemaining, isPremium, etc. */
    suspend fun fetchTrialStatus(sessionToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/trial/status")
            .addHeader("Authorization", "Bearer $sessionToken")
            .get()
            .build()
        request(request)
    }

    /** Real, user-authored trade plans for the signed-in user, newest first. */
    suspend fun fetchTradePlans(sessionToken: String): JSONArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/trade-plans")
            .addHeader("Authorization", "Bearer $sessionToken")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            if (!response.isSuccessful) {
                throw MarketAiException(
                    json.optString("error", "Could not load trade plans (${response.code})")
                )
            }
            json.optJSONArray("plans") ?: JSONArray()
        }
    }

    /** Creates a real, persisted trade plan for the signed-in user. */
    suspend fun createTradePlan(
        sessionToken: String,
        instrumentId: String,
        instrument: String,
        direction: String,
        entry: Double?,
        stopLoss: Double?,
        takeProfit: Double?,
        notes: String?
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("instrumentId", instrumentId)
            put("instrument", instrument)
            put("direction", direction)
            if (entry != null) put("entry", entry)
            if (stopLoss != null) put("stopLoss", stopLoss)
            if (takeProfit != null) put("takeProfit", takeProfit)
            if (!notes.isNullOrBlank()) put("notes", notes)
        }
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/trade-plans")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Public daily-signals aggregate stats ("at a glance" card). */
    suspend fun fetchSignalStats(range: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/daily-signals/stats?range=$range")
            .get()
            .build()
        request(request)
    }

    /** Admin flag + feed entitlement for the signed-in user. */
    suspend fun fetchSignalAccess(sessionToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/daily-signals/access")
            .addHeader("Authorization", "Bearer $sessionToken")
            .get()
            .build()
        request(request)
    }

    /** The daily signals feed (entitled users only — 402 MarketAiException when locked). */
    suspend fun fetchDailySignals(sessionToken: String, limit: Int = 50): JSONArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/daily-signals?limit=$limit")
            .addHeader("Authorization", "Bearer $sessionToken")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            if (!response.isSuccessful) {
                throw MarketAiException(
                    json.optString("error", "Could not load daily signals (${response.code})")
                )
            }
            json.optJSONArray("signals") ?: JSONArray()
        }
    }

    /** Admin: publish a curated daily signal. */
    suspend fun publishDailySignal(
        sessionToken: String,
        instrumentId: String,
        direction: String,
        entry: Double,
        stopLoss: Double,
        takeProfits: List<Double>,
        thesis: String?,
        strength: String
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("instrumentId", instrumentId)
            put("direction", direction)
            put("entry", entry)
            put("stopLoss", stopLoss)
            put("takeProfits", JSONArray(takeProfits))
            if (!thesis.isNullOrBlank()) put("thesis", thesis)
            put("strength", strength)
        }
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/daily-signals")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Admin: manually close a signal with an outcome (for no-feed instruments). */
    suspend fun closeDailySignal(sessionToken: String, id: String, outcome: String): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("outcome", outcome)
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/daily-signals/$id/close")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Deletes a trade plan owned by the signed-in user. */
    suspend fun deleteTradePlan(sessionToken: String, id: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/trade-plans/$id")
            .addHeader("Authorization", "Bearer $sessionToken")
            .delete()
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
