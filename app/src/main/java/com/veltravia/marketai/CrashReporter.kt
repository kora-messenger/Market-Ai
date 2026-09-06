package com.veltravia.marketai

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Debug crash reporter: when the app crashes, shows the full stack trace on
 * screen so it can be screenshotted/copied and reported. Debug builds only.
 */
object CrashReporter {
    const val EXTRA_TRACE = "crash_trace"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val trace = buildString {
                    append("Thread: ").append(thread.name).append("\n\n")
                    append(android.util.Log.getStackTraceString(throwable))
                    append("\n\nDevice: Android ")
                    append(android.os.Build.VERSION.RELEASE)
                    append(" (SDK ").append(android.os.Build.VERSION.SDK_INT).append(")\n")
                    append("Model: ").append(android.os.Build.MANUFACTURER)
                    append(" ").append(android.os.Build.MODEL).append("\n")
                    append("App: Market Ai ").append(BuildConfig.VERSION_NAME)
                    append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
                }
                File(context.filesDir, "last-crash.txt").writeText(trace)
                val intent = Intent(context, CrashReportActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(EXTRA_TRACE, trace)
                context.startActivity(intent)
                Thread.sleep(1200) // give the report screen a moment to appear
            } catch (_: Throwable) {
                // never let the reporter itself loop
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}

/** Full-screen report shown after a crash: scrollable trace + copy button. */
class CrashReportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra(CrashReporter.EXTRA_TRACE)
            ?: File(filesDir, "last-crash.txt").readText()

        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#14100F"))
        }
        root.addView(TextView(ctx).apply {
            text = "MARKET AI STOPPED — send a screenshot of this report"
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 16f
            setPadding(48, 64, 48, 32)
        })
        root.addView(ScrollView(ctx).apply {
            addView(TextView(this@CrashReportActivity).apply {
                text = trace
                setTextColor(Color.parseColor("#E0E0E0"))
                textSize = 12f
                setPadding(48, 16, 48, 16)
            })
        })
        root.addView(Button(ctx).apply {
            text = "Copy crash report"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Market Ai crash", trace))
                Toast.makeText(ctx, "Copied — paste it to the team in chat", Toast.LENGTH_LONG).show()
            }
        })
        setContentView(root)

        actionBar?.hide()
        window.statusBarColor = Color.parseColor("#14100F")
    }
}
