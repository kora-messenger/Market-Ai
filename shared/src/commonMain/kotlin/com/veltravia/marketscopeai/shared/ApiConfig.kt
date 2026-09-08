package com.veltravia.marketscopeai.shared

/**
 * Single source of truth for the MarketScope AI backend location — shared by
 * every platform. When Veltravia moves to its own domain, change BASE_URL here
 * (and in the Android app's data/ApiConfig.kt until the app migrates onto
 * this shared module).
 */
object ApiConfig {
    const val BASE_URL = "https://market-ai-api-jwfb.onrender.com"
}
