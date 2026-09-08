package com.veltravia.marketscopeai.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout

/**
 * Each platform supplies its engine: OkHttp on Android, Darwin on iOS.
 */
expect fun httpClientEngine(): HttpClientEngine

fun createHttpClient(): HttpClient = HttpClient(httpClientEngine()) {
    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        requestTimeoutMillis = 120_000
    }
}
