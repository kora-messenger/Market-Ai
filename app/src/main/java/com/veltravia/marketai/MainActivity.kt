package com.veltravia.marketai

import android.os.Bundle
import com.veltravia.marketai.BuildConfig
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.veltravia.marketai.ui.MarketAiApp
import com.veltravia.marketai.ui.theme.MarketAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            CrashReporter.install(applicationContext)
        }
        enableEdgeToEdge()
        setContent {
            MarketAiTheme {
                MarketAiApp()
            }
        }
    }
}
