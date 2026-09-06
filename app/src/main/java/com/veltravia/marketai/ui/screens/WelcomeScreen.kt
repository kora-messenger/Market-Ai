package com.veltravia.marketai.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketai.BuildConfig
import com.veltravia.marketai.R
import com.veltravia.marketai.auth.GoogleSignIn
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID

    var signingIn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "Market Ai",
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(28.dp))
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = "WELCOME TO MARKET AI",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 2.sp,
                color = TextSecondary
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Let's analyze your chart",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "and make you profitable now.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AccentCyan,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(48.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable(enabled = clientId.isNotBlank() && !signingIn) {
                        signingIn = true
                        error = null
                        scope.launch {
                            try {
                                val user = GoogleSignIn.signIn(context, clientId)
                                SessionManager.saveUser(context, user)
                                onSignedIn()
                            } catch (e: Exception) {
                                error = e.message ?: "Login failed. Please try again."
                            } finally {
                                signingIn = false
                            }
                        }
                    }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (signingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_google_g),
                        contentDescription = "Google",
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Continue with Google",
                    color = Color(0xFF1F1F1F),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (clientId.isBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Google sign-in activates as soon as your Market Ai OAuth client is configured.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            error?.let {
                Spacer(Modifier.height(14.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(28.dp))

            val termsStart = "By continuing, you agree to our ".length
            val termsEnd = termsStart + "Terms of Service".length
            val privacyStart = termsEnd + " and ".length
            val privacyEnd = privacyStart + "Privacy Policy".length
            val legalText = buildAnnotatedString {
                append("By continuing, you agree to our ")
                withStyle(SpanStyle(color = AccentCyan, textDecoration = TextDecoration.Underline)) {
                    append("Terms of Service")
                }
                append(" and ")
                withStyle(SpanStyle(color = AccentCyan, textDecoration = TextDecoration.Underline)) {
                    append("Privacy Policy")
                }
                append(".")
            }
            androidx.compose.foundation.text.ClickableText(
                text = legalText,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth(),
                onClick = { offset ->
                    when {
                        offset in termsStart until termsEnd ->
                            uriHandler.openUri("https://market-ai-api-jwfb.onrender.com/terms")
                        offset in privacyStart until privacyEnd ->
                            uriHandler.openUri("https://market-ai-api-jwfb.onrender.com/privacy")
                    }
                }
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "By Veltravia Technologies",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}
