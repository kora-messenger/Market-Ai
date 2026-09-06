package com.veltravia.marketai.auth

import com.veltravia.marketai.data.GoogleUser

/**
 * Platform sign-in:
 *  - Android: Google Sign-In via Credential Manager (Google ID token)
 *  - iOS: Sign in with Apple (required by App Store when third-party login exists)
 */
expect object PlatformAuth {
    /** Button label shown on the welcome screen. */
    val label: String

    /** Whether sign-in is configured and ready on this platform. */
    val configured: Boolean

    /** Runs the native platform sign-in flow and returns the signed-in profile. */
    suspend fun signIn(): GoogleUser
}
