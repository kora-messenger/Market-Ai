package com.veltravia.marketai

import android.os.Bundle
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
            val lastCrash = CrashReporter.consumeLastCrash(applicationContext)
            if (lastCrash != null) {
                // Show the report as the very first thing — guaranteed to render
                // since this is a fresh cold start, not a rescue mid-crash.
                setContentView(CrashReporter.buildReportView(this, lastCrash))
                return
            }
        }
        enableEdgeToEdge()
        setContent {
            MarketAiTheme {
                MarketAiApp()
            }
        }
    }
}
