package com.veltravia.marketai.auth

import android.app.Activity
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.veltravia.marketai.data.GoogleUser
import org.json.JSONObject

/**
 * Real Google Sign-In via Android Credential Manager (Google ID token).
 * Requires the Market Ai Web OAuth client ID (Google Cloud Console) at build time.
 */
object GoogleSignIn {

    suspend fun signIn(activity: Activity, webClientId: String): GoogleUser {
        require(webClientId.isNotBlank()) {
            "Google sign-in is not configured yet — missing the Market Ai OAuth client ID."
        }

        val credentialManager = CredentialManager.create(activity)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = credentialManager.getCredential(activity, request)
        val credential = response.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            return parseIdToken(googleIdTokenCredential.idToken)
        }
        throw IllegalStateException("Unexpected credential type: ${credential.type}")
    }

    /** Decode the ID token payload (JWT) to read the signed-in profile. */
    private fun parseIdToken(idToken: String): GoogleUser {
        val parts = idToken.split(".")
        val payload = String(
            Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            ),
            Charsets.UTF_8
        )
        val json = JSONObject(payload)
        return GoogleUser(
            name = json.optString("name").ifBlank { "Trader" },
            email = json.optString("email"),
            picture = json.optString("picture")
        )
    }
}
