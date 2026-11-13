package com.example.damthoai.hardware.audio

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Bộ xử lý ngoại lệ chung cho coroutine thuộc phần cứng/audio.
 */
object HardwareCoroutineExceptionHandler {

    private const val TAG = "HardwareCoroutine"

    val handler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine phần cứng/audio gặp lỗi", throwable)
        AudioIncidentBus.report(AudioIncident.Unknown(throwable))
    }
}
