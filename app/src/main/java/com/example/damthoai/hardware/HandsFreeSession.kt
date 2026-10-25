package com.example.damthoai.hardware

import android.content.Context
import android.util.Log

/**
 * Owns headset button routing for an active hands-free capture session.
 *
 * ĐIỂM NỐI TV2: the Foreground Service should call [start] when its session begins and [stop]
 * when it ends. Capture-owner transitions also keep section A active until their capture ends.
 */
object HandsFreeSession {

    private const val TAG = "HandsFreeSession"

    private val lock = Any()
    private var applicationContext: Context? = null
    private var captureActive = false
    private var explicitlyStarted = false
    private var running = false

    val isRunning: Boolean
        get() = synchronized(lock) { running }

    fun initialize(context: Context) {
        synchronized(lock) {
            applicationContext = context.applicationContext
            captureActive = PttController.isCaptureActive.value
            reconcile()
        }
    }

    fun start(context: Context) {
        synchronized(lock) {
            applicationContext = context.applicationContext
            explicitlyStarted = true
            reconcile()
        }
    }

    fun stop() {
        synchronized(lock) {
            explicitlyStarted = false
            reconcile()
        }
    }

    internal fun onCaptureActiveChanged(active: Boolean) {
        synchronized(lock) {
            captureActive = active
            reconcile()
        }
    }

    private fun reconcile() {
        val shouldRun = explicitlyStarted || captureActive
        if (shouldRun && !running) {
            val context = applicationContext
            if (context == null) {
                Log.e(TAG, "Cannot start headset routing before a Context is initialized")
                return
            }
            HeadsetButtonManager.start(context)
            running = true
        } else if (!shouldRun && running) {
            HeadsetButtonManager.stop()
            running = false
        }
    }
}
