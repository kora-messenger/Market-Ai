package com.veltravia.marketai.auth

import android.content.Context
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.veltravia.marketai.data.GoogleUser
import org.json.JSONObject

/** Raw Google ID token plus the profile decoded from it, before server verification. */
data class GoogleSignInResult(
    val idToken: String,
    val user: GoogleUser
)

/**
 * Real Google Sign-In via Android Credential Manager (Google ID token).
 * Requires the MarketScope AI Web OAuth client ID (Google Cloud Console) at build time.
 * The returned idToken must be sent to the backend (/api/auth/google) for verification —
 * this class only reads the local credential, it never trusts the token itself.
 */
object GoogleSignIn {

    suspend fun signIn(context: Context, webClientId: String): GoogleSignInResult {
        require(webClientId.isNotBlank()) {
            "Google sign-in is not configured yet — missing the MarketScope AI OAuth client ID."
        }

        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = credentialManager.getCredential(context, request)
        val credential = response.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken
            return GoogleSignInResult(idToken = idToken, user = parseIdToken(idToken))
        }
        throw IllegalStateException("Unexpected credential type: ${credential.type}")
    }

    /** Decode the ID token payload (JWT) to read the signed-in profile for immediate UI use. */
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
