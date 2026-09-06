package com.veltravia.marketai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Debug crash reporter. Design note: we do NOT try to launch a rescue Activity
 * from inside the crash handler itself — if the crash left the app/Looper in a
 * bad state, that risky mid-crash Activity.start can silently fail too, which is
 * exactly what was happening (Android's own crash dialog appeared instead of ours).
 * Instead: write the trace to disk synchronously (nearly always succeeds), then
 * show it as the very first thing on the NEXT cold start — a fresh onCreate that
 * is guaranteed to render normally.
 */
object CrashReporter {

    private const val CRASH_FILE = "last-crash.txt"

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
                File(context.filesDir, CRASH_FILE).writeText(trace)
            } catch (_: Throwable) {
                // never let the reporter itself throw
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Reads and clears the last crash trace, if any — call once on cold start. */
    fun consumeLastCrash(context: Context): String? {
        val file = File(context.filesDir, CRASH_FILE)
        if (!file.exists()) return null
        return try {
            val text = file.readText()
            file.delete()
            text
        } catch (_: Throwable) {
            null
        }
    }

    /** Builds the full-screen crash report view (trace + copy button). */
    fun buildReportView(context: Context, trace: String): LinearLayout {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#14100F"))
        }
        root.addView(TextView(context).apply {
            text = "MARKET AI STOPPED LAST TIME — send a screenshot of this report"
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 16f
            setPadding(48, 64, 48, 32)
        })
        root.addView(ScrollView(context).apply {
            addView(TextView(context).apply {
                text = trace
                setTextColor(Color.parseColor("#E0E0E0"))
                textSize = 12f
                setPadding(48, 16, 48, 16)
            })
        })
        root.addView(Button(context).apply {
            text = "Copy crash report"
            setOnClickListener {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Market Ai crash", trace))
                Toast.makeText(context, "Copied — paste it to the team in chat", Toast.LENGTH_LONG).show()
            }
        })
        root.addView(Button(context).apply {
            text = "Continue to Market Ai"
            setOnClickListener {
                (context as? android.app.Activity)?.recreate()
            }
        })
        return root
    }
}
