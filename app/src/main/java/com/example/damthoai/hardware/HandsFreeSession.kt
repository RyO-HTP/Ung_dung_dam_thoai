package com.example.damthoai.hardware

import android.content.Context
import android.util.Log

/**
 * Quản lý định tuyến nút tai nghe trong phiên đàm thoại rảnh tay.
 *
 * ĐIỂM NỐI TV2: Foreground Service gọi [start] khi phiên nền bắt đầu và [stop] khi kết thúc.
 * Trước khi có Service, [PttController.acquireCapture] và [PttController.releaseCapture]
 * tự đồng bộ trạng thái này để phiên MediaSession chỉ chạy trong lúc capture hoạt động.
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
            running = HeadsetButtonManager.isRunning
        } else if (!shouldRun && running) {
            HeadsetButtonManager.stop()
            running = false
        }
    }
}
