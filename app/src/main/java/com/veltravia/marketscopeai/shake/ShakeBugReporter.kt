package com.veltravia.marketscopeai.shake

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.PushRouter

/**
 * Shake-to-report-bug. Watches the accelerometer while the app is in the
 * foreground; a firm shake (roughly a hard flick — not pocket noise) opens
 * the bug report screen via PushRouter, mirroring how push-notification
 * taps route the nav graph.
 *
 * Deliberate choices:
 *  - Registered only while the pref is ON (no background sensor drain).
 *  - Threshold is in g-force above gravity, debounced to one trigger per
 *    2.5s so a single shake gesture can't fire twice.
 *  - Requires a signed-in session — the report screen needs a token.
 */
object ShakeBugReporter {

    /** g-force (beyond resting gravity) that counts as a shake. */
    private const val SHAKE_GFORCE = 2.7f

    /** Minimum time between triggers. */
    private const val MIN_INTERVAL_MS = 2500L

    @Volatile
    private var registered = false
    private var lastTriggerAt = 0L

    // Application context (safe to hold for the app's lifetime) — lets the
    // listener check that a session exists before opening the report screen.
    @Volatile
    private var appContext: Context? = null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
            val gX = event.values[0] / SensorManager.GRAVITY_EARTH
            val gY = event.values[1] / SensorManager.GRAVITY_EARTH
            val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
            // Resting on a table = 1g. A firm wrist flick spikes past ~3g.
            val gForce = Math.sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat() - 1f
            if (gForce < SHAKE_GFORCE) return

            val ctx = appContext ?: return
            // Reporting needs a signed-in session — ignore shakes when signed out.
            if (SessionManager.sessionToken(ctx) == null) return

            val now = System.currentTimeMillis()
            if (now - lastTriggerAt < MIN_INTERVAL_MS) return
            lastTriggerAt = now

            // Handed to the nav graph, which opens the report screen.
            PushRouter.pendingBugReport = true
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Start listening if the pref is on. Safe to call repeatedly. */
    fun start(context: Context) {
        appContext = context.applicationContext
        if (registered || !SessionManager.shakeToReportBug(context)) return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
        registered = true
    }

    /** Stop listening (called on background and when the pref is switched off). */
    fun stop(context: Context) {
        if (!registered) return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sm.unregisterListener(listener)
        registered = false
    }
}
