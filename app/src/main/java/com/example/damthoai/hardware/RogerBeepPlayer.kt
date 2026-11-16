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
            toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 80).also {
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

    // Tiếng "được lượt nói" phát trên luồng âm thanh cuộc gọi: ra đúng tai nghe / Bluetooth / loa đang dùng
    private var talkPermitGenerator: ToneGenerator? = null

    /**
     * Tiếng bíp cao, ngắn (1200 Hz) khi server cho lượt nói — khác tiếng roger trầm khi nhả PTT.
     * Kiểm tra cờ bật/tắt âm báo nằm ở phía gọi (DamThoaiCallManager).
     */
    @Synchronized
    fun playTalkPermit() {
        val generator = try {
            talkPermitGenerator ?: ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80).also {
                talkPermitGenerator = it
            }
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Không thể khởi tạo âm báo được lượt nói", exception)
            return
        }
        if (!generator.startTone(ToneGenerator.TONE_PROP_ACK, TALK_PERMIT_DURATION_MS)) {
            Log.w(TAG, "Không thể phát âm báo được lượt nói")
        }
    }

    private const val TALK_PERMIT_DURATION_MS = 100

    @Synchronized
    fun release() {
        toneGenerator?.release()
        toneGenerator = null
        talkPermitGenerator?.release()
        talkPermitGenerator = null
    }
}
