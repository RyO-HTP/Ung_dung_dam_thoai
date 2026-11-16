package com.example.damthoai.hardware.emergency

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.damthoai.R

internal data class EmergencyVibrationStartResult(
    val started: Boolean,
    val unavailableReason: String? = null
)

/**
 * Điều khiển bộ rung cho cảnh báo khẩn cấp.
 *
 * ĐIỂM NỐI: Chỉ [EmergencyAlertManager] gọi bộ rung; các nguồn khẩn cấp khác gọi manager.
 */
internal object EmergencyVibrator {

    private const val TAG = "EmergencyVibrator"
    private val lock = Any()
    private var activeVibrator: Vibrator? = null

    fun start(context: Context): EmergencyVibrationStartResult = synchronized(lock) {
        try {
            val vibrator = getVibrator(context)
                ?: return@synchronized unavailable(context)
            if (!vibrator.hasVibrator()) {
                return@synchronized unavailable(context)
            }

            val timings = longArrayOf(0L, VIBRATION_ON_MS, VIBRATION_OFF_MS)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(
                        timings,
                        intArrayOf(0, MAX_AMPLITUDE, 0),
                        REPEAT_INDEX
                    )
                } else {
                    VibrationEffect.createWaveform(timings, REPEAT_INDEX)
                }
                vibrator.vibrate(effect, attributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, REPEAT_INDEX, attributes)
            }

            activeVibrator = vibrator
            EmergencyVibrationStartResult(started = true)
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không có quyền khởi động bộ rung cảnh báo", exception)
            EmergencyVibrationStartResult(
                started = false,
                unavailableReason = context.getString(R.string.emergency_vibration_reason_security)
            )
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Bộ rung không thể khởi động ở trạng thái hiện tại", exception)
            EmergencyVibrationStartResult(
                started = false,
                unavailableReason = context.getString(R.string.emergency_vibration_reason_error)
            )
        }
    }

    fun stop() {
        synchronized(lock) {
            val vibrator = activeVibrator
            activeVibrator = null
            if (vibrator != null) {
                try {
                    vibrator.cancel()
                } catch (exception: SecurityException) {
                    Log.e(TAG, "Không thể dừng bộ rung cảnh báo", exception)
                } catch (exception: IllegalStateException) {
                    Log.e(TAG, "Bộ rung không thể dừng ở trạng thái hiện tại", exception)
                }
            }
        }
    }

    private fun getVibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun unavailable(context: Context) = EmergencyVibrationStartResult(
        started = false,
        unavailableReason = context.getString(R.string.emergency_vibration_reason_unavailable)
    )

    private const val VIBRATION_ON_MS = 700L
    private const val VIBRATION_OFF_MS = 300L
    private const val REPEAT_INDEX = 0
    private const val MAX_AMPLITUDE = 255
}
