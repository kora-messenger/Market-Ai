package com.veltravia.marketai.data

import com.veltravia.marketai.json.JSONArray
import com.veltravia.marketai.json.JSONObject
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real HTTP client for the Market Ai backend (ktor — works on Android and iOS).
 * Failures throw MarketAiException with the server message.
 */
object ApiClient {

    class MarketAiException(message: String) : Exception(message)

    private val client: HttpClient by lazy { provideHttpClient() }

    private suspend fun readError(status: HttpStatusCode, body: String): Nothing {
        val message = runCatching { JSONObject(body).optString("error") }
            .getOrNull()?.ifBlank { null } ?: "Request failed (${status.value})"
        throw MarketAiException(message)
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
    ): JSONObject = withContext(Dispatchers.Default) {
        val payload = JSONObject()
            .put("instrumentId", instrumentId)
            .put("mode", mode)
            .put("imageH4", imageH4DataUrl)
            .put("imageM15", imageM15DataUrl)
        val response = client.post("${ApiConfig.BASE_URL}/api/analyze") {
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) readError(response.status, body)
        JSONObject(body)
    }

    /** Recent analyses, newest first. */
    suspend fun fetchAnalyses(limit: Int = 30): JSONArray = withContext(Dispatchers.Default) {
        val response = client.get("${ApiConfig.BASE_URL}/api/analyses?limit=$limit")
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val message = runCatching { JSONObject(body).optString("error") }
                .getOrNull()?.ifBlank { null } ?: "Could not load history (${response.status.value})"
            throw MarketAiException(message)
        }
        JSONObject(body).optJSONArray("analyses") ?: JSONArray()
    }

    /** Single stored analysis by id. */
    suspend fun fetchAnalysis(id: String): JSONObject = withContext(Dispatchers.Default) {
        val response = client.get("${ApiConfig.BASE_URL}/api/analyses/$id")
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) readError(response.status, body)
        JSONObject(body)
    }
}
