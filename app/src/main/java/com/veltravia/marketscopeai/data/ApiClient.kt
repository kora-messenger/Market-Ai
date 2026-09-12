package com.veltravia.marketscopeai.data

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
 * Real HTTP client for the MarketScope AI backend.
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

    /** Thrown when a free-tier user exhausts the daily chart-analysis allowance. */
    class DailyLimitException(message: String) : Exception(message)

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
            if (response.code == 429 && json.optBoolean("dailyLimitReached", false)) {
                throw DailyLimitException(
                    json.optString("error", "You've used all free analyses for today.")
                )
            }
            if (!response.isSuccessful) {
                throw MarketAiException(json.optString("error", "Request failed (${response.code})"))
            }
            json
        }
    }

    /**
     * Stock analysis (3rd analyze flow): the user types a company name or
     * attaches a stock screenshot; the backend resolves the real listed
     * stock, fetches its live performance and returns a BUY/SELL/HOLD
     * verdict with a confidence percentage. Same trial/limit semantics.
     */
    suspend fun analyzeStock(
        sessionToken: String,
        name: String,
        imageDataUrl: String?
    ): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("name", name)
        if (imageDataUrl != null) payload.put("image", imageDataUrl)
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/analyze/stock")
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
            if (response.code == 429 && json.optBoolean("dailyLimitReached", false)) {
                throw DailyLimitException(
                    json.optString("error", "You've used all free analyses for today.")
                )
            }
            if (!response.isSuccessful) {
                throw MarketAiException(json.optString("error", "Request failed (" + response.code + ")"))
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

    /** Pin or unpin a community post (admin only). Returns { id, isPinned }. */
    suspend fun pinCommunityPost(sessionToken: String, postId: String): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/community/posts/$postId/pin")
                .addHeader("Authorization", "Bearer $sessionToken")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            request(request)
        }

    /** Auth-protected URL of a post image at [position] (loaded via the Coil loader with the session token). */
    fun communityImageUrl(postId: String, position: Int): String =
        "${ApiConfig.BASE_URL}/api/community/posts/$postId/images/$position"

    /** Publish a poll: 2-6 options with labels. Returns { post: {...} }. */
    suspend fun createCommunityPoll(
        sessionToken: String,
        question: String,
        options: List<String>,
        allowComments: Boolean
    ): JSONObject = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        options.forEach { label -> arr.put(JSONObject().put("id", java.util.UUID.randomUUID().toString()).put("label", label)) }
        val payload = JSONObject()
            .put("body", question)
            .put("postType", "poll")
            .put("pollOptions", arr)
            .put("allowComments", allowComments)
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/community/posts")
            .addHeader("Authorization", "Bearer $sessionToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Cast or switch a poll vote. Returns { optionId, counts, totalVotes, myVote }. */
    suspend fun votePoll(sessionToken: String, postId: String, optionId: String): JSONObject =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("optionId", optionId)
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/community/posts/$postId/vote")
                .addHeader("Authorization", "Bearer $sessionToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            request(request)
        }

    /** Weekly competition standings: { weekStart, nextReset, standings, myRank, myScore, lastWeekWinners }. */
    suspend fun fetchLeaderboard(sessionToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/community/leaderboard")
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
            .url("${ApiConfig.BASE_URL}/api/community/feed?offset=$offset&limit=$limit")
            .addHeader("Authorization", "Bearer $sessionToken")
            .get()
            .build()
        request(request)
    }

    /** Publish a text post to the community. [outcomeTag] ("win"/"loss") is optional and
     * only meaningful when [images] is non-empty — it's the author's own self-reported
     * trade outcome, never inferred by the app. Returns { post: {...} }. */
    suspend fun createCommunityPost(
        sessionToken: String,
        body: String,
        images: List<String> = emptyList(),
        outcomeTag: String? = null
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("body", body)
            if (images.isNotEmpty()) payload.put("images", JSONArray().apply { images.forEach { put(it) } })
            if (outcomeTag != null) payload.put("outcomeTag", outcomeTag)
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/community/posts")
                .addHeader("Authorization", "Bearer $sessionToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            request(request)
        }

    /** Real curated pinned-posts list (most-recently-pinned first). Returns { pinned: [...] }. */
    suspend fun fetchPinnedPosts(sessionToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/community/pinned")
            .addHeader("Authorization", "Bearer $sessionToken")
            .get()
            .build()
        request(request)
    }

    /** Registers a real, deduped view of a post. Returns { viewCount }. Fire-and-forget. */
    suspend fun registerPostView(sessionToken: String, postId: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/community/posts/$postId/view")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post("{}".toRequestBody("application/json".toMediaType()))
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
            .url("${ApiConfig.BASE_URL}/api/community/posts/$postId/react")
            .addHeader("Authorization", "Bearer $sessionToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** All comments on a post (flat; the caller nests by parentId). */
    suspend fun fetchPostComments(sessionToken: String, postId: String): JSONArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/community/posts/$postId/comments")
                .addHeader("Authorization", "Bearer $sessionToken")
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
            .url("${ApiConfig.BASE_URL}/api/community/posts/$postId/comments")
            .addHeader("Authorization", "Bearer $sessionToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Public, live top-market-cap tokens for the Home screen "Trending" section. */
    suspend fun fetchTrending(): JSONArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/trending")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            if (!response.isSuccessful) {
                throw MarketAiException(json.optString("error", "Could not load trending tokens (${response.code})"))
            }
            json.optJSONArray("tokens") ?: JSONArray()
        }
    }

    /** Fast live spot prices for the given symbols (e.g. ["BTC","ETH"]) — powers the
     * Trending section's real-time tick between full refreshes. Returns only the
     * symbols that resolved to a real price; missing ones are simply absent. */
    suspend fun fetchTrendingQuotes(symbols: List<String>): Map<String, Double> = withContext(Dispatchers.IO) {
        if (symbols.isEmpty()) return@withContext emptyMap()
        val qs = symbols.joinToString(",")
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/trending/quotes?symbols=$qs")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            if (!response.isSuccessful) return@withContext emptyMap()
            val quotes = json.optJSONObject("quotes") ?: JSONObject()
            val map = mutableMapOf<String, Double>()
            quotes.keys().forEach { key -> map[key] = quotes.optDouble(key) }
            map
        }
    }

    /** Saves the onboarding questionnaire answers server-side so completion
     *  survives sign-out / reinstall / new devices. Auth-gated (Bearer token).
     *  The sign-in response's questionnaireCompleted then routes returning
     *  users straight to Home — only genuinely-new users see the questionnaire. */
    suspend fun saveQuestionnaire(sessionToken: String, answers: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("answers", answers)
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/profile/questionnaire")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Always-live multi-asset watchlist (Futures/Forex/Crypto) — real feeds, no auth needed. */
    suspend fun fetchMarketsWatchlist(): JSONArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/markets/watchlist")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            if (!response.isSuccessful) {
                throw MarketAiException(json.optString("error", "Could not load live market data (${response.code})"))
            }
            json.optJSONArray("rows") ?: JSONArray()
        }
    }

    /** Public real economic calendar (NFP, CPI, rate decisions, etc.) — no auth needed. */
    suspend fun fetchEconomicCalendar(): JSONArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/calendar/economic")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            if (!response.isSuccessful) {
                throw MarketAiException(json.optString("error", "Could not load the economic calendar (${response.code})"))
            }
            json.optJSONArray("events") ?: JSONArray()
        }
    }

    /** Public real Forex/Crypto/Stocks news — no auth needed. category: "all"|"forex"|"crypto"|"stocks". */
    suspend fun fetchMarketNews(category: String = "all", limit: Int = 40): JSONArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/calendar/news?category=$category&limit=$limit")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: "{}"
            val json = JSONObject(body)
            if (!response.isSuccessful) {
                throw MarketAiException(json.optString("error", "Could not load market news (${response.code})"))
            }
            json.optJSONArray("items") ?: JSONArray()
        }
    }

    /** Public real total of users who have joined the community (no auth needed). */
    suspend fun fetchCommunityStats(): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/community/stats")
            .get()
            .build()
        request(request)
    }

    /** Public subscription plan info (price, currency, payments live or not). */
    suspend fun fetchSubscriptionPlans(): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/subscription/plans")
            .get()
            .build()
        request(request)
    }

    /** Start a Premium checkout. Returns { authorizationUrl, reference } on success;
     *  throws MarketAiException with the server's honest message if payments are not live yet. */
    suspend fun startSubscriptionCheckout(sessionToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/subscription/checkout")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Current premium/trial state for the signed-in user. */
    suspend fun fetchSubscriptionStatus(sessionToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/subscription/status")
            .addHeader("Authorization", "Bearer $sessionToken")
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

    /** Public monetization config: ad placements, free/premium limits, rewarded
     *  availability, enabled ad networks. No auth needed — nothing sensitive. */
    suspend fun fetchMonetizationConfig(): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/monetization/config")
            .get()
            .build()
        request(request)
    }

    /** Server-validated rewarded-ad unlock. Called ONLY after the ad SDK
     *  confirmed the reward was earned — the backend re-checks eligibility
     *  and the daily cap. Returns { granted, bonus, usage }. */
    suspend fun postRewardedUnlock(sessionToken: String, network: String = "admob"): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("network", network)
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/monetization/rewarded-unlock")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
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
    suspend fun fetchDailySignalsFeed(sessionToken: String, limit: Int = 50): JSONObject = withContext(Dispatchers.IO) {
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
            // { signals: [...], locked: bool, premiumSignalCount: int } — the
            // free tier gets the latest signal + the lock state so the app can
            // render an honest upgrade card instead of a blank wall.
            json
        }
    }

    /** Admin: publish a curated daily signal. [mode] is "scalp" | "swing" | null. */
    suspend fun publishDailySignal(
        sessionToken: String,
        instrumentId: String,
        direction: String,
        entry: Double,
        stopLoss: Double,
        takeProfits: List<Double>,
        thesis: String?,
        strength: String,
        mode: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("instrumentId", instrumentId)
            put("direction", direction)
            put("entry", entry)
            put("stopLoss", stopLoss)
            put("takeProfits", JSONArray(takeProfits))
            if (!thesis.isNullOrBlank()) put("thesis", thesis)
            put("strength", strength)
            if (!mode.isNullOrBlank()) put("mode", mode)
        }
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/daily-signals")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Presence heartbeat — keeps "online now" truthful while the app is open. */
    suspend fun presencePing(sessionToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/presence/ping")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Admin: the Team Console overview dashboard (real aggregate numbers). */
    suspend fun fetchAdminOverview(sessionToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/admin/overview")
            .addHeader("Authorization", "Bearer $sessionToken")
            .get()
            .build()
        request(request)
    }

    /** Admin: list members with roles + presence, for the mentor manager. */
    suspend fun fetchAdminMembers(sessionToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/admin/members")
            .addHeader("Authorization", "Bearer $sessionToken")
            .get()
            .build()
        request(request)
    }

    /** Admin: set a member's role ('member' or 'mentor'). Admins are locked. */
    suspend fun setAdminMemberRole(sessionToken: String, memberId: String, role: String): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("role", role)
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/admin/members/$memberId/role")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Admin: search users for premium management (email / name / id). */
    suspend fun adminSearchPremiumUsers(sessionToken: String, search: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/admin/premium/users?search=${java.net.URLEncoder.encode(search, "UTF-8")}")
            .addHeader("Authorization", "Bearer $sessionToken")
            .get()
            .build()
        request(request)
    }

    /** Admin: one user's full premium status snapshot. */
    suspend fun adminGetPremiumStatus(sessionToken: String, userId: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/admin/premium/status/$userId")
            .addHeader("Authorization", "Bearer $sessionToken")
            .get()
            .build()
        request(request)
    }

    /** Admin: grant free Premium — lifetime, or N months / N years (reason optional). */
    suspend fun adminGrantPremium(sessionToken: String, userId: String, durationType: String, durationCount: Int, reason: String?): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("userId", userId).put("durationType", durationType)
        if (durationType != "lifetime") body.put("durationCount", durationCount)
        if (!reason.isNullOrBlank()) body.put("reason", reason.trim())
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/admin/premium/grant")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Admin: revoke the admin-granted entitlement (paid subscriptions untouched). */
    suspend fun adminRevokePremium(sessionToken: String, userId: String): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("userId", userId)
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/admin/premium/revoke")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** Admin: recent premium grant/revoke activity (audit feed). */
    suspend fun adminFetchPremiumAudit(sessionToken: String, limit: Int = 30): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/admin/premium/audit?limit=$limit")
            .addHeader("Authorization", "Bearer $sessionToken")
            .get()
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

    /** Toggle one of the 5 fixed reaction emoji on a daily signal. Returns { emoji, active }. */
    suspend fun reactToSignal(sessionToken: String, signalId: String, emoji: String): JSONObject =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("emoji", emoji)
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/daily-signals/$signalId/react")
                .addHeader("Authorization", "Bearer $sessionToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            request(request)
        }

    /** Toggle bookmarking a daily signal. Returns { saved: bool }. */
    suspend fun toggleSavedSignal(sessionToken: String, signalId: String): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/daily-signals/$signalId/save")
                .addHeader("Authorization", "Bearer $sessionToken")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            request(request)
        }

    /** Flat, oldest-first comment list on a daily signal. */
    suspend fun fetchSignalComments(sessionToken: String, signalId: String): JSONArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/daily-signals/$signalId/comments")
                .addHeader("Authorization", "Bearer $sessionToken")
                .get()
                .build()
            val json = request(request)
            json.optJSONArray("comments") ?: JSONArray()
        }

    /** Add a comment on a daily signal, optionally with an attached trade screenshot (jpg/png/webp data URL). Image comments stay pending until the mentor desk approves them. */
    suspend fun addSignalComment(sessionToken: String, signalId: String, body: String, imageDataUrl: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("body", body)
            if (imageDataUrl != null) payload.put("image", imageDataUrl)
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/daily-signals/$signalId/comments")
                .addHeader("Authorization", "Bearer $sessionToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            request(request)
        }

    /** Full URL of a comment's attached screenshot — auth'd by the app-wide Coil loader. */
    fun signalCommentImageUrl(commentId: String): String =
        "${ApiConfig.BASE_URL}/api/daily-signals/comments/$commentId/image"

    /** Toggle one of the 4 fixed reaction emoji on a signal comment (returns { mine }). */
    suspend fun reactToSignalComment(sessionToken: String, commentId: String, emoji: String): JSONObject =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("emoji", emoji)
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/daily-signals/comments/$commentId/react")
                .addHeader("Authorization", "Bearer $sessionToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            request(request)
        }

    /** Mentor-desk live updates on a signal, each with nested follow-up replies. */
    suspend fun fetchSignalUpdates(sessionToken: String, signalId: String): JSONArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/daily-signals/$signalId/updates")
                .addHeader("Authorization", "Bearer $sessionToken")
                .get()
                .build()
            val json = request(request)
            json.optJSONArray("updates") ?: JSONArray()
        }

    /** Mentor desk only: post a live update (or a follow-up, with parentId). */
    suspend fun addSignalUpdate(sessionToken: String, signalId: String, body: String, parentId: String? = null, authorName: String): JSONObject =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("body", body).put("authorName", authorName)
            if (parentId != null) payload.put("parentId", parentId)
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/daily-signals/$signalId/updates")
                .addHeader("Authorization", "Bearer $sessionToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            request(request)
        }

    /** "I took this signal" — current user's taken state for a signal. */
    suspend fun fetchSignalTake(sessionToken: String, signalId: String): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/daily-signals/$signalId/take")
                .addHeader("Authorization", "Bearer $sessionToken")
                .get()
                .build()
            request(request)
        }

    /** "I took this signal" — toggle for the signed-in user (returns { taken, takerCount }). */
    suspend fun toggleSignalTake(sessionToken: String, signalId: String): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/daily-signals/$signalId/take")
                .addHeader("Authorization", "Bearer $sessionToken")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            request(request)
        }

    /** Approved win testimonials for a signal (+ the caller's own pending ones). */
    suspend fun fetchSignalTestimonials(sessionToken: String, signalId: String): JSONArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/daily-signals/$signalId/testimonials")
                .addHeader("Authorization", "Bearer $sessionToken")
                .get()
                .build()
            val json = request(request)
            json.optJSONArray("testimonials") ?: JSONArray()
        }

    /** Share your win on a signal — text comment + optional proof screenshot; goes to review. */
    suspend fun shareSignalWin(sessionToken: String, signalId: String, comment: String, imageDataUrl: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("comment", comment)
            if (imageDataUrl != null) payload.put("image", imageDataUrl)
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/daily-signals/$signalId/testimonials")
                .addHeader("Authorization", "Bearer $sessionToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            request(request)
        }

    /** Full URL of a testimonial's proof screenshot — auth'd by the app-wide Coil loader. */
    fun signalTestimonialImageUrl(testimonialId: String): String =
        "${ApiConfig.BASE_URL}/api/daily-signals/testimonials/$testimonialId/image"

    /** Featured (latest approved) win testimonials across all signals. */
    suspend fun fetchFeaturedTestimonials(sessionToken: String): JSONArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/daily-signals/testimonials/featured")
                .addHeader("Authorization", "Bearer $sessionToken")
                .get()
                .build()
            val json = request(request)
            json.optJSONArray("featured") ?: JSONArray()
        }

    /** Wall of Wins — paginated approved win proofs + wall stats. */
    suspend fun fetchWinsWall(sessionToken: String, offset: Int = 0, limit: Int = 12): org.json.JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/wins/wall?limit=$limit&offset=$offset")
                .addHeader("Authorization", "Bearer $sessionToken")
                .get()
                .build()
            request(request)
        }

    /** Admin: win testimonial review queue. */
    suspend fun fetchAdminTestimonials(sessionToken: String, status: String = "pending"): JSONArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/admin/testimonials?status=$status")
                .addHeader("Authorization", "Bearer $sessionToken")
                .get()
                .build()
            val json = request(request)
            json.optJSONArray("testimonials") ?: JSONArray()
        }

    /** Admin: approve or reject a win testimonial. */
    suspend fun reviewTestimonial(sessionToken: String, testimonialId: String, decision: String): JSONObject =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("decision", decision)
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/admin/testimonials/$testimonialId/review")
                .addHeader("Authorization", "Bearer $sessionToken")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            request(request)
        }

    /** Admin: pending signal-comment screenshots awaiting review. */
    suspend fun fetchPendingSignalComments(sessionToken: String): JSONArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/admin/signal-comments/pending")
                .addHeader("Authorization", "Bearer $sessionToken")
                .get()
                .build()
            val json = request(request)
            json.optJSONArray("pending") ?: JSONArray()
        }

    /** Admin: approve a pending screenshot comment so everyone can see it. */
    suspend fun approveSignalComment(sessionToken: String, commentId: String): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/admin/signal-comments/$commentId/approve")
                .addHeader("Authorization", "Bearer $sessionToken")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            request(request)
        }

    /** Admin: reject and delete a pending screenshot comment. */
    suspend fun rejectSignalComment(sessionToken: String, commentId: String): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${ApiConfig.BASE_URL}/api/admin/signal-comments/$commentId/reject")
                .addHeader("Authorization", "Bearer $sessionToken")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            request(request)
        }

        /** Real OHLC candles for the live Market View chart (Coinbase / Yahoo). */
    suspend fun fetchCandles(instrumentId: String, interval: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/markets/candles?id=$instrumentId&interval=$interval")
            .get()
            .build()
        request(request)
    }

    /** Real spot price for one Watchlist instrument (Market View's ticking price). */
    suspend fun fetchMarketPrice(instrumentId: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/markets/price?id=$instrumentId")
            .get()
            .build()
        request(request)
    }

    /** Real account status for the Settings screen — is a deletion pending? */
    suspend fun fetchAccountStatus(sessionToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/account/status")
            .addHeader("Authorization", "Bearer $sessionToken")
            .get()
            .build()
        request(request)
    }

    /** Requests account deletion (support erases the account within 30 days; cancellable). */
    suspend fun requestAccountDeletion(sessionToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/account/delete-request")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post("".toRequestBody(null))
            .build()
        request(request)
    }

    /** Cancels a pending account deletion request. */
    suspend fun cancelAccountDeletion(sessionToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/account/delete-request/cancel")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post("".toRequestBody(null))
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

    /** Register this device's FCM push token with the signed-in account. */
    suspend fun registerPushToken(sessionToken: String, fcmToken: String): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("token", fcmToken).put("platform", "android")
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/push/register")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /** In-app notification feed: { notifications: [...], unread: n } */
    suspend fun fetchNotifications(sessionToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/notifications")
            .addHeader("Authorization", "Bearer $sessionToken")
            .build()
        request(request)
    }

    /** Mark every notification as read. */
    suspend fun markNotificationsRead(sessionToken: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/notifications/read-all")
            .addHeader("Authorization", "Bearer $sessionToken")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        request(request)
    }

    /**
     * Force-update gate — called BEFORE sign-in, at app launch. The admin
     * controls this server-side (no app release needed to start enforcing
     * a minimum version). No auth header: this must work even for a user
     * who has never signed in yet. The platform ("android"/"ios") makes the
     * server pick the right store: Play Store link vs App Store link.
     * @return { updateRequired, storeLabel, storeUrl, ... }
     */
    suspend fun checkAppVersion(versionCode: Int, platform: String = "android"): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/app-version/check?versionCode=$versionCode&platform=$platform")
            .get()
            .build()
        request(request)
    }
}
