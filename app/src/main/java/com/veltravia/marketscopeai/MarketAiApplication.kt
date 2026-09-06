package com.veltravia.marketscopeai

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.veltravia.marketscopeai.data.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * App-wide Coil image loader that speaks the API's auth protocol, so post
 * images served from the backend (Bearer-token protected) render in feed.
 */
class MarketAiApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val token = SessionManager.sessionToken(this)
                val request = if (token.isNullOrBlank()) chain.request()
                else chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            })
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }
}
