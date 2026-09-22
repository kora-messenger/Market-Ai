package com.veltravia.marketscopeai.shake

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.widget.Toast
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.PushRouter
import com.veltravia.marketscopeai.util.ClipboardImage
import java.lang.ref.WeakReference

/**
 * Shake gesture handler. A firm shake (roughly a hard flick — not pocket
 * noise) always captures whatever is on screen right now — including a
 * classic-View screen like the crash report page, not just Compose — and
 * copies it to the clipboard so the user can paste it straight into a chat
 * to send it. This works even signed out and even on the crash screen,
 * which is exactly when a screenshot is most useful.
 *
 * If the "shake to report a bug" preference is on AND a session exists, the
 * same shake additionally opens the in-app bug report screen (mirroring how
 * push-notification taps route the nav graph).
 *
 * Deliberate choices:
 *  - Registered only while the app is in the foreground (no background
 *    sensor drain) — started/stopped from the Activity lifecycle.
 *  - Threshold is in g-force above gravity, debounced to one trigger per
 *    2.5s so a single shake gesture can't fire twice.
 *  - Screenshot capture needs the Activity's Window, not just a Context, so
 *    start()/stop() take the Activity itself and hold only a WeakReference.
 */
object ShakeBugReporter {

    /** g-force (beyond resting gravity) that counts as a shake. */
    private const val SHAKE_GFORCE = 2.7f

    /** Minimum time between triggers. */
    private const val MIN_INTERVAL_MS = 2500L

    @Volatile
    private var registered = false
    private var lastTriggerAt = 0L

    private var activityRef: WeakReference<Activity>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
            val gX = event.values[0] / SensorManager.GRAVITY_EARTH
            val gY = event.values[1] / SensorManager.GRAVITY_EARTH
            val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
            // Resting on a table = 1g. A firm wrist flick spikes past ~3g.
            val gForce = Math.sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat() - 1f
            if (gForce < SHAKE_GFORCE) return

            val now = System.currentTimeMillis()
            if (now - lastTriggerAt < MIN_INTERVAL_MS) return
            lastTriggerAt = now

            val activity = activityRef?.get() ?: return
            captureAndCopyScreenshot(activity)

            // Reporting a bug additionally needs a signed-in session — that
            // part only fires when both the pref is on and a session exists.
            val ctx = activity.applicationContext
            if (SessionManager.shakeToReportBug(ctx) && SessionManager.sessionToken(ctx) != null) {
                PushRouter.pendingBugReport = true
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Grabs whatever is currently on screen and puts it on the clipboard. */
    private fun captureAndCopyScreenshot(activity: Activity) {
        val decorView = activity.window?.decorView ?: return
        if (decorView.width <= 0 || decorView.height <= 0) return

        fun onDone(bitmap: Bitmap?) {
            val ok = bitmap != null && ClipboardImage.copy(activity, bitmap, "marketscope_screenshot")
            Toast.makeText(
                activity,
                if (ok) "Screenshot copied — paste it anywhere to share" else "Could not capture the screenshot",
                Toast.LENGTH_LONG
            ).show()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // PixelCopy reads the real Window surface — correct even over
            // hardware-accelerated content the crash screen's plain Views use.
            val bitmap = Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(activity.window, bitmap, { result ->
                    onDone(if (result == PixelCopy.SUCCESS) bitmap else null)
                }, mainHandler)
            } catch (_: Exception) {
                onDone(null)
            }
        } else {
            // Pre-O fallback: draw the decor view's own canvas synchronously.
            try {
                val bitmap = Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888)
                decorView.draw(Canvas(bitmap))
                onDone(bitmap)
            } catch (_: Exception) {
                onDone(null)
            }
        }
    }

    /** Start listening. Safe to call repeatedly (e.g. on every onStart). */
    fun start(activity: Activity) {
        activityRef = WeakReference(activity)
        if (registered) return
        val sm = activity.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager ?: return
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
        registered = true
    }

    /** Stop listening (called on background). */
    fun stop(activity: Activity) {
        if (!registered) return
        val sm = activity.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager ?: return
        sm.unregisterListener(listener)
        registered = false
    }
}
