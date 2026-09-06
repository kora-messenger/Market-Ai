package com.veltravia.marketai.data

/**
 * Tiny key-value session storage, implemented per platform:
 * Android = SharedPreferences, iOS = NSUserDefaults.
 */
interface SessionStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun clear()
}

/** Platform-provided session store instance. */
internal expect fun provideSessionStore(): SessionStore

/** Platform HTTP engine factory (CIO on Android, Darwin on iOS). */
internal expect fun provideHttpClient(): io.ktor.client.HttpClient
