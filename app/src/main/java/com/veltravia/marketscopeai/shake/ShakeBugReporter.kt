package com.veltravia.marketscopeai.shake

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * An opt-in shake gesture. Only signed-in accounts that enabled the Settings
 * switch can capture. Each successful capture is saved to Photos and copied
 * to the clipboard; the existing bug-report navigation remains available.
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
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun canCapture(activity: Activity): Boolean {
        val ctx = activity.applicationContext
        return SessionManager.sessionToken(ctx) != null && SessionManager.shakeToReportBug(ctx) &&
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }

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
            val activity = activityRef?.get() ?: return
            if (!canCapture(activity) || now - lastTriggerAt < MIN_INTERVAL_MS) return
            lastTriggerAt = now
            captureAndSaveScreenshot(activity)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Capture the current window, then save to the shared gallery and clipboard. */
    private fun captureAndSaveScreenshot(activity: Activity) {
        val decorView = activity.window?.decorView ?: return
        if (decorView.width <= 0 || decorView.height <= 0) return

        fun onDone(bitmap: Bitmap?) {
            if (bitmap == null) {
                Toast.makeText(activity, "Could not capture the screenshot", Toast.LENGTH_LONG).show()
                return
            }
            // The switch can be disabled or the account can sign out while
            // PixelCopy is in flight. Never save a frame after opt-out.
            if (!canCapture(activity)) { bitmap.recycle(); return }
            // PixelCopy has captured the original screen. Only now navigate.
            PushRouter.pendingBugReport = true
            val copied = ClipboardImage.copy(activity, bitmap, "marketscope_screenshot")
            val ctx = activity.applicationContext
            saveScope.launch {
                val saved = if (canCapture(activity)) ScreenshotGallery.save(ctx, bitmap) else false
                bitmap.recycle()
                withContext(Dispatchers.Main) {
                    if (!canCapture(activity)) return@withContext
                    val message = when {
                        saved && copied -> "Screenshot saved to Photos and copied"
                        saved -> "Screenshot saved to Photos"
                        copied -> "Screenshot copied, but could not save to Photos"
                        else -> "Could not save the screenshot"
                    }
                    Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // PixelCopy reads the real Window surface — correct even over
            // hardware-accelerated content the crash screen's plain Views use.
            val bitmap = Bitmap.createBitmap(decorView.width, decorView.height, Bitmap.Config.ARGB_8888)
            try {
                PixelCopy.request(activity.window, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) onDone(bitmap)
                    else { bitmap.recycle(); onDone(null) }
                }, mainHandler)
            } catch (_: Exception) {
                bitmap.recycle()
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
        if (!canCapture(activity)) {
            stop(activity)
            return
        }
        activityRef = WeakReference(activity)
        if (registered) return
        val sm = activity.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager ?: return
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
        registered = true
    }

    /** Stop listening (called on background). */
    fun stop(activity: Activity) {
        activityRef = null
        if (!registered) return
        val sm = activity.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager ?: return
        sm.unregisterListener(listener)
        registered = false
    }
}
