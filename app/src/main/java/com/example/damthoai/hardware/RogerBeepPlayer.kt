package com.example.damthoai.hardware

import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

object RogerBeepPlayer {
    private const val TAG = "RogerBeepPlayer"
    private const val TONE_DURATION_MS = 120

    private var toneGenerator: ToneGenerator? = null

    @Synchronized
    fun play() {
        if (!HandsFreeSettings.beepHapticEnabled.value) return

        val generator = try {
            toneGenerator ?: ToneGenerator(AudioManager.STREAM_SYSTEM, 80).also {
                toneGenerator = it
            }
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Không thể khởi tạo âm báo PTT", exception)
            return
        }

        if (!generator.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_DURATION_MS)) {
            Log.w(TAG, "Không thể phát âm báo PTT")
        }
    }

    @Synchronized
    fun playTalkPermit(enabled: Boolean = false) {
        if (!enabled || !HandsFreeSettings.beepHapticEnabled.value) return
        val generator = try {
            toneGenerator ?: ToneGenerator(AudioManager.STREAM_SYSTEM, 80).also {
                toneGenerator = it
            }
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Không thể khởi tạo âm báo cho phép nói", exception)
            return
        }

        generator.startTone(ToneGenerator.TONE_PROP_ACK, 80)
    }

    @Synchronized
    fun release() {
        toneGenerator?.release()
        toneGenerator = null
    }
}
