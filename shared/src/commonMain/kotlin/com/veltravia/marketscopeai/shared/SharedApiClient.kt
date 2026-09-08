package com.veltravia.marketscopeai.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.core.String
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Multiplatform API client for MarketScope AI — a faithful port of the Android
 * app's data/ApiClient.kt onto Ktor + kotlinx-serialization, so the same network
 * layer serves both Android and iOS.
 *
 * Responses are JsonElement (JsonObject/JsonArray) instead of org.json types:
 * org.json is Android-only. During the Android migration the app can either
 * adapt these or keep delegating per-endpoint.
 */
object SharedApiClient {

    class MarketAiException(message: String) : Exception(message)
    class TrialExpiredException(message: String) : Exception(message)
    class DailyLimitException(message: String) : Exception(message)

    private val json = Json { ignoreUnknownKeys = true }

    private val client: HttpClient by lazy { createHttpClient() }

    // ---------------------------------------------------------------- helpers

    private suspend fun parse(response: HttpResponse): JsonObject {
        val bodyText: String = response.body()
        val obj = json.parseToJsonElement(bodyText)
        val root = obj as? JsonObject ?: JsonObject(emptyMap())
        if (!response.status.isSuccess()) {
            val error = (root["error"] as? JsonPrimitive)?.content
                ?: "Request failed (${response.status.value})"
            val reason = (root["reason"] as? JsonPrimitive)?.content ?: ""
            val message = if (reason.isNotBlank()) "$error: $reason" else error
            val flag = { key: String ->
                (root[key] as? JsonPrimitive)?.content == "true" ||
                    (root[key] as? JsonPrimitive)?.content.toBoolean()
            }
            when {
                response.status == HttpStatusCode.PaymentRequired && flag("trialExpired") ->
                    throw TrialExpiredException(error)
                response.status == HttpStatusCode.TooManyRequests && flag("dailyLimitReached") ->
                    throw DailyLimitException(error)
                else -> throw MarketAiException(message)
            }
        }
        return root
    }

    private suspend fun getJson(pathAndQuery: String, token: String? = null): JsonObject =
        withContext(Dispatchers.Default) {
            val response = client.get(ApiConfig.BASE_URL + pathAndQuery) {
                if (token != null) header("Authorization", "Bearer $token")
            }
            parse(response)
        }

    private suspend fun getArray(pathAndQuery: String, token: String? = null): JsonArray =
        withContext(Dispatchers.Default) {
            val response = client.get(ApiConfig.BASE_URL + pathAndQuery) {
                if (token != null) header("Authorization", "Bearer $token")
            }
            val bodyText: String = response.body()
            json.parseToJsonElement(bodyText) as? JsonArray
                ?: throw MarketAiException("Unexpected non-array response from $pathAndQuery")
        }

    /** GET returning a JSON array nested under [key] (backend wraps feeds in objects). */
    private suspend fun getNestedArray(pathAndQuery: String, key: String, token: String? = null): JsonArray {
        val root = getJson(pathAndQuery, token)
        return root[key] as? JsonArray ?: JsonArray(emptyList())
    }

    private suspend fun postJson(
        path: String,
        payload: JsonObject,
        token: String? = null,
    ): JsonObject = withContext(Dispatchers.Default) {
        val response = client.post(ApiConfig.BASE_URL + path) {
            contentType(ContentType.Application.Json)
            if (token != null) header("Authorization", "Bearer $token")
            setBody(payload.toString())
        }
        parse(response)
    }

    private fun bodyOf(vararg pairs: Pair<String, JsonElement>): JsonObject =
        JsonObject(pairs.toMap())

    private fun text(value: String): JsonPrimitive = JsonPrimitive(value)

    // ------------------------------------------------------------------- auth

    /** Verify a Google ID token server-side; returns the session JWT + profile. */
    suspend fun authenticateWithGoogle(idToken: String): JsonObject =
        postJson("/api/auth/google", bodyOf("idToken" to text(idToken)))

    suspend fun joinCommunity(sessionToken: String): JsonObject =
        postJson("/api/community/join", JsonObject(emptyMap()), token = sessionToken)

    suspend fun saveQuestionnaire(sessionToken: String, answers: JsonObject): JsonObject =
        postJson("/api/profile/questionnaire", answers, token = sessionToken)

    // -------------------------------------------------------------- analysis

    suspend fun fetchAnalyses(sessionToken: String, limit: Int = 30): JsonArray =
        getNestedArray("/api/analyses?limit=$limit", "analyses", token = sessionToken)

    suspend fun fetchAnalysis(sessionToken: String, id: String): JsonObject =
        getJson("/api/analyses/$id", token = sessionToken)

    /**
     * Run a chart-pair analysis on the backend. [imageH4]/[imageM15] are base64
     * JPEG data URLs, produced by the platform image pipeline (photo picker +
     * downscale stays platform-side).
     */
    suspend fun analyze(
        sessionToken: String,
        instrumentId: String,
        mode: String,
        imageH4: String,
        imageM15: String,
    ): JsonObject = postJson(
        "/api/analyze",
        bodyOf(
            "instrumentId" to text(instrumentId),
            "mode" to text(mode),
            "imageH4" to text(imageH4),
            "imageM15" to text(imageM15),
        ),
        token = sessionToken,
    )

    // ------------------------------------------------------- trial & billing

    suspend fun fetchTrialStatus(sessionToken: String): JsonObject =
        getJson("/api/trial/status", token = sessionToken)

    suspend fun fetchSubscriptionPlans(): JsonObject =
        getJson("/api/subscription/plans")

    suspend fun startSubscriptionCheckout(sessionToken: String): JsonObject =
        postJson("/api/subscription/checkout", JsonObject(emptyMap()), token = sessionToken)

    suspend fun fetchSubscriptionStatus(sessionToken: String): JsonObject =
        getJson("/api/subscription/status", token = sessionToken)

    // --------------------------------------------------------- daily signals

    suspend fun fetchSignalStats(range: String): JsonObject =
        getJson("/api/daily-signals/stats?range=$range")

    suspend fun fetchSignalAccess(sessionToken: String): JsonObject =
        getJson("/api/daily-signals/access", token = sessionToken)

    suspend fun fetchDailySignalsFeed(sessionToken: String, limit: Int = 50): JsonObject =
        getJson("/api/daily-signals?limit=$limit", token = sessionToken)

    suspend fun reactToSignal(sessionToken: String, signalId: String, emoji: String): JsonObject =
        postJson(
            "/api/daily-signals/$signalId/react",
            bodyOf("emoji" to text(emoji)),
            token = sessionToken,
        )

    suspend fun toggleSavedSignal(sessionToken: String, signalId: String): JsonObject =
        postJson(
            "/api/daily-signals/$signalId/save",
            JsonObject(emptyMap()),
            token = sessionToken,
        )

    suspend fun fetchSignalComments(sessionToken: String, signalId: String): JsonArray =
        getNestedArray("/api/daily-signals/$signalId/comments", "comments", token = sessionToken)

    suspend fun addSignalComment(sessionToken: String, signalId: String, body: String): JsonObject =
        postJson(
            "/api/daily-signals/$signalId/comments",
            bodyOf("body" to text(body)),
            token = sessionToken,
        )

    // ---------------------------------------------------------- trade plans

    suspend fun fetchTradePlans(sessionToken: String): JsonArray =
        getArray("/api/trade-plans", token = sessionToken)

    suspend fun createTradePlan(
        sessionToken: String,
        instrumentId: String,
        direction: String,
        entry: Double,
        stopLoss: Double,
        takeProfit: Double,
        riskPct: Double,
        notes: String?,
    ): JsonObject = postJson(
        "/api/trade-plans",
        buildJsonObject {
            put("instrumentId", instrumentId)
            put("direction", direction)
            put("entry", entry)
            put("stopLoss", stopLoss)
            put("takeProfit", takeProfit)
            put("riskPct", riskPct)
            notes?.let { put("notes", it) }
        },
        token = sessionToken,
    )

    suspend fun deleteTradePlan(sessionToken: String, id: String): JsonObject =
        withContext(Dispatchers.Default) {
            val response = client.delete(ApiConfig.BASE_URL + "/api/trade-plans/$id") {
                header("Authorization", "Bearer $sessionToken")
            }
            parse(response)
        }

    // ------------------------------------------------------------- community

    suspend fun fetchCommunityStats(): JsonObject =
        getJson("/api/community/stats")

    suspend fun fetchCommunityFeed(
        sessionToken: String,
        offset: Int = 0,
        limit: Int = 20,
    ): JsonObject =
        getJson("/api/community/feed?offset=$offset&limit=$limit", token = sessionToken)

    suspend fun fetchPinnedPosts(sessionToken: String): JsonObject =
        getJson("/api/community/pinned", token = sessionToken)

    suspend fun fetchLeaderboard(sessionToken: String): JsonObject =
        getJson("/api/community/leaderboard", token = sessionToken)

    /** Direct URL for an auth-gated community post image (attach the Bearer token
     *  via the platform's image loader, e.g. URLSession with headers on iOS). */
    fun communityImageUrl(postId: String, position: Int): String =
        "${ApiConfig.BASE_URL}/api/community/posts/$postId/images/$position"

    suspend fun createCommunityPost(
        sessionToken: String,
        body: String,
        images: List<String> = emptyList(),
        outcomeTag: String? = null,
    ): JsonObject = postJson(
        "/api/community/posts",
        buildJsonObject {
            put("body", body)
            if (images.isNotEmpty()) put("images", JsonArray(images.map { text(it) }))
            if (outcomeTag != null) put("outcomeTag", outcomeTag)
        },
        token = sessionToken,
    )

    suspend fun createCommunityPoll(
        sessionToken: String,
        question: String,
        options: List<String>,
        allowComments: Boolean,
    ): JsonObject = postJson(
        "/api/community/posts",
        buildJsonObject {
            put("body", question)
            put("postType", "poll")
            put(
                "pollOptions",
                JsonArray(
                    options.map { label ->
                        buildJsonObject {
                            put("id", newUuid())
                            put("label", label)
                        }
                    }
                ),
            )
            put("allowComments", allowComments)
        },
        token = sessionToken,
    )

    /** Cast or switch a poll vote. Returns { optionId, counts, totalVotes, myVote }. */
    suspend fun votePoll(sessionToken: String, postId: String, optionId: String): JsonObject =
        postJson(
            "/api/community/posts/$postId/vote",
            bodyOf("optionId" to text(optionId)),
            token = sessionToken,
        )

    /** Admin: pin/unpin a community post. Returns { pinned }. */
    suspend fun pinCommunityPost(sessionToken: String, postId: String): JsonObject =
        postJson("/api/community/posts/$postId/pin", JsonObject(emptyMap()), token = sessionToken)

    /** Registers a real, deduped view of a post. Returns { viewCount }. Fire-and-forget. */
    suspend fun registerPostView(sessionToken: String, postId: String): JsonObject =
        postJson("/api/community/posts/$postId/view", JsonObject(emptyMap()), token = sessionToken)

    /** Toggle one emoji reaction on a post. Returns { emoji, active }. */
    suspend fun toggleCommunityReaction(sessionToken: String, postId: String, emoji: String): JsonObject =
        postJson(
            "/api/community/posts/$postId/react",
            bodyOf("emoji" to text(emoji)),
            token = sessionToken,
        )

    /** All comments on a post (flat; the caller nests by parentId). */
    suspend fun fetchPostComments(sessionToken: String, postId: String): JsonArray {
        val root = getJson("/api/community/posts/$postId/comments", token = sessionToken)
        return root["comments"] as? JsonArray ?: JsonArray(emptyList())
    }

    /** Add a comment or reply (parentId nullable). Returns { comment: {...} }. */
    suspend fun addPostComment(
        sessionToken: String,
        postId: String,
        body: String,
        parentId: String? = null,
    ): JsonObject = postJson(
        "/api/community/posts/$postId/comments",
        buildJsonObject {
            put("body", body)
            if (parentId != null) put("parentId", parentId)
        },
        token = sessionToken,
    )

    // -------------------------------------------------------------- markets

    suspend fun fetchTrending(): JsonArray =
        getNestedArray("/api/trending", "tokens")

    suspend fun fetchMarketsWatchlist(): JsonArray =
        getNestedArray("/api/markets/watchlist", "rows")

    suspend fun fetchEconomicCalendar(): JsonArray =
        getNestedArray("/api/calendar/economic", "events")

    suspend fun fetchMarketNews(category: String = "all", limit: Int = 40): JsonArray =
        getNestedArray("/api/calendar/news?category=$category&limit=$limit", "items")

    /** Batched live quotes for the watchlist sparklines. Map symbol -> price. */
    suspend fun fetchTrendingQuotes(symbols: List<String>): Map<String, Double> {
        val qs = symbols.joinToString(",") { urlEncode(it) }
        val root = getJson("/api/trending/quotes?symbols=$qs")
        val quotes = root["quotes"] as? JsonObject ?: return emptyMap()
        val out = linkedMapOf<String, Double>()
        for ((sym, el) in quotes) {
            val price = (el as? JsonPrimitive)?.content?.toDoubleOrNull() ?: continue
            out[sym] = price
        }
        return out
    }

    // ------------------------------------------------------- admin console

    /** Admin: publish a curated signal (the Team Console). Mode is scalp/swing, optional. */
    suspend fun publishDailySignal(
        sessionToken: String,
        instrumentId: String,
        direction: String,
        entry: Double,
        stopLoss: Double,
        takeProfits: List<Double>,
        thesis: String?,
        strength: String,
        mode: String? = null,
    ): JsonObject = postJson(
        "/api/daily-signals",
        buildJsonObject {
            put("instrumentId", instrumentId)
            put("direction", direction)
            put("entry", entry)
            put("stopLoss", stopLoss)
            put("takeProfits", JsonArray(takeProfits.map { JsonPrimitive(it) }))
            if (!thesis.isNullOrBlank()) put("thesis", thesis)
            put("strength", strength)
            if (!mode.isNullOrBlank()) put("mode", mode)
        },
        token = sessionToken,
    )

    /** Admin: manually close a signal with an outcome (for no-feed instruments). */
    suspend fun closeDailySignal(sessionToken: String, id: String, outcome: String): JsonObject =
        postJson(
            "/api/daily-signals/$id/close",
            bodyOf("outcome" to text(outcome)),
            token = sessionToken,
        )

    // -------------------------------------------------------- notifications

    suspend fun registerPushToken(sessionToken: String, pushToken: String): JsonObject =
        postJson(
            "/api/push/register",
            bodyOf("token" to text(pushToken), "platform" to text("ios")),
            token = sessionToken,
        )

    suspend fun fetchNotifications(sessionToken: String): JsonObject =
        getJson("/api/notifications", token = sessionToken)

    suspend fun markNotificationsRead(sessionToken: String): JsonObject =
        postJson("/api/notifications/read-all", JsonObject(emptyMap()), token = sessionToken)

    // --------------------------------------------------------------- helpers

    /** Minimal RFC-3986 percent-encoder (common code has no java.net). */
    private fun urlEncode(raw: String): String {
        val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
        val out = StringBuilder()
        for (ch in raw) {
            if (ch.toString() in allowed) {
                out.append(ch)
            } else {
                for (b in ch.toString().encodeToByteArray()) {
                    out.append('%').append("0123456789ABCDEF"[b.toInt() ushr 4 and 0xF])
                        .append("0123456789ABCDEF"[b.toInt() and 0xF])
                }
            }
        }
        return out.toString()
    }

    /** UUID v4 for poll option ids (common code has no java.util.UUID). */
    private fun newUuid(): String {
        val hex = "0123456789abcdef"
        val r = kotlin.random.Random
        val sb = StringBuilder(36)
        for (i in 0 until 36) {
            when (i) {
                8, 13, 18, 23 -> sb.append('-')
                14 -> sb.append('4')
                19 -> sb.append(hex[(r.nextInt(4) + 8)])
                else -> sb.append(hex[r.nextInt(16)])
            }
        }
        return sb.toString()
    }
}
