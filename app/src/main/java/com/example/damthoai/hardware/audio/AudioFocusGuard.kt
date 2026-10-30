package com.example.damthoai.hardware.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Điều phối audio focus cho phiên đàm thoại.
 *
 * ĐIỂM NỐI TV2: gọi [start] khi bắt đầu phiên thoại và [stop] khi kết thúc. Mất focus sẽ ngắt PTT
 * và mute đầu ra; lấy lại focus chỉ phát sự cố, không tự unmute.
 */
object AudioFocusGuard {

    private const val TAG = "AudioFocusGuard"
    private val lock = Any()
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var focusWasLost = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                val regained = synchronized(lock) {
                    hasFocus = true
                    val hadLostFocus = focusWasLost
                    focusWasLost = false
                    hadLostFocus
                }
                if (regained) AudioIncidentBus.report(AudioIncident.AudioFocusRegained)
            }

            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                synchronized(lock) {
                    hasFocus = false
                    focusWasLost = true
                }
                AudioRouteGuard.onAudioFocusLost(
                    "Audio focus bị ứng dụng khác hoặc cuộc gọi hệ thống chiếm"
                )
            }
        }
    }

    /**
     * Xin focus kiểu giao tiếp giọng nói; có thể gọi lặp lại khi đã có focus.
     *
     * @return `true` nếu hệ thống cấp focus.
     */
    fun start(context: Context): Boolean {
        synchronized(lock) {
            if (hasFocus) return true
        }

        val manager: AudioManager? = runSafely(onError = { null }) {
            context.applicationContext
                .getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        }
        if (manager == null) {
            AudioRouteGuard.onAudioFocusLost("Không tìm thấy AudioManager")
            return false
        }

        val granted = runSafely(onError = { false }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(
                        focusChangeListener,
                        Handler(Looper.getMainLooper())
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                synchronized(lock) {
                    audioManager = manager
                    focusRequest = request
                }
                manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                synchronized(lock) { audioManager = manager }
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        }

        synchronized(lock) {
            hasFocus = granted
            focusWasLost = !granted
        }
        if (!granted) {
            Log.w(TAG, "Hệ thống từ chối yêu cầu audio focus")
            AudioRouteGuard.onAudioFocusLost("Hệ thống từ chối yêu cầu audio focus")
        }
        return granted
    }

    /** Từ bỏ audio focus khi phiên đàm thoại kết thúc. */
    fun stop() {
        val manager: AudioManager?
        val request: AudioFocusRequest?
        synchronized(lock) {
            manager = audioManager
            request = focusRequest
            audioManager = null
            focusRequest = null
            hasFocus = false
            focusWasLost = false
        }
        if (manager == null) return

        runSafely(onError = { false }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && request != null) {
                manager.abandonAudioFocusRequest(request)
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(focusChangeListener)
            }
            true
        }
    }
}
