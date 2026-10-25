package com.example.damthoai.hardware

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HeadsetPttMode {
    TOGGLE,
    HOLD
}

/**
 * Routes wired and Bluetooth headset media buttons through the central PTT controller.
 *
 * The active platform MediaSession advertises playback while the capture session is running.
 * Android uses that active session to route headset media-button events to this app, including
 * when the screen is off. The session is deliberately not an audio player and does not create a
 * service; its owner must start and stop it with the capture session.
 */
object HeadsetButtonManager {

    private const val TAG = "HeadsetButtonManager"
    private const val DUPLICATE_EVENT_WINDOW_MS = 300L
    private const val HEADSET_PTT_TIMEOUT_MS = 60_000L

    private val _headsetButtonEnabled = MutableStateFlow(true)
    val headsetButtonEnabled: StateFlow<Boolean> = _headsetButtonEnabled.asStateFlow()

    private val _headsetPttMode = MutableStateFlow(HeadsetPttMode.TOGGLE)
    val headsetPttMode: StateFlow<HeadsetPttMode> = _headsetPttMode.asStateFlow()

    private var mediaSession: MediaSession? = null
    private var timeoutScope: CoroutineScope? = null
    private var autoReleaseJob: Job? = null
    private var autoReleaseGeneration = 0L
    private var lastAcceptedPressAtMs: Long? = null
    private var holdPressActive = false

    @Synchronized
    fun start(context: Context) {
        if (mediaSession != null) return

        timeoutScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val session = MediaSession(context.applicationContext, "DamThoaiHeadsetPtt")
        session.setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        session.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mediaButtonIntent.getParcelableExtra(
                        Intent.EXTRA_KEY_EVENT,
                        KeyEvent::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                }
                return event?.let(::handleMediaKeyEvent)
                    ?: super.onMediaButtonEvent(mediaButtonIntent)
            }
        })
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE)
                .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                .build()
        )
        mediaSession = session
        session.isActive = true
    }

    @Synchronized
    fun stop() {
        cancelAutoRelease()
        timeoutScope?.cancel()
        timeoutScope = null
        holdPressActive = false

        if (PttController.activeSource.value == PttSource.HEADSET) {
            PttController.onKeyReleased(PttSource.HEADSET)
        }

        mediaSession?.let { session ->
            session.isActive = false
            session.release()
        }
        mediaSession = null
        lastAcceptedPressAtMs = null
    }

    fun setHeadsetButtonEnabled(enabled: Boolean) {
        _headsetButtonEnabled.value = enabled
        if (!enabled) {
            synchronized(this) {
                releaseHeadsetPress()
            }
        }
    }

    fun setHeadsetPttMode(mode: HeadsetPttMode) {
        synchronized(this) {
            if (_headsetPttMode.value == mode) return
            _headsetPttMode.value = mode
            releaseHeadsetPress()
        }
    }

    @Synchronized
    private fun handleMediaKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> Unit
            else -> return false
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount != 0) return true
                val now = SystemClock.elapsedRealtime()
                val previous = lastAcceptedPressAtMs
                if (previous != null && now - previous < DUPLICATE_EVENT_WINDOW_MS) {
                    return true
                }
                lastAcceptedPressAtMs = now
                if (!_headsetButtonEnabled.value) return true

                when (_headsetPttMode.value) {
                    HeadsetPttMode.TOGGLE -> toggleHeadsetPtt()
                    HeadsetPttMode.HOLD -> pressAndHoldHeadsetPtt()
                }
            }

            KeyEvent.ACTION_UP -> {
                if (_headsetPttMode.value == HeadsetPttMode.HOLD && holdPressActive) {
                    holdPressActive = false
                    PttController.onKeyReleased(PttSource.HEADSET)
                }
            }
        }
        return true
    }

    private fun toggleHeadsetPtt() {
        if (PttController.isPttActive.value) {
            if (PttController.activeSource.value == PttSource.HEADSET) {
                PttController.onKeyReleased(PttSource.HEADSET)
                cancelAutoRelease()
            }
            return
        }

        PttController.onKeyPressed(PttSource.HEADSET)
        if (PttController.activeSource.value == PttSource.HEADSET) {
            scheduleAutoRelease()
        }
    }

    private fun pressAndHoldHeadsetPtt() {
        if (PttController.isPttActive.value &&
            PttController.activeSource.value != PttSource.HEADSET
        ) {
            return
        }

        PttController.onKeyPressed(PttSource.HEADSET)
        holdPressActive = PttController.activeSource.value == PttSource.HEADSET
    }

    private fun scheduleAutoRelease() {
        cancelAutoRelease()
        val scope = timeoutScope ?: return
        val generation = ++autoReleaseGeneration
        autoReleaseJob = scope.launch {
            delay(HEADSET_PTT_TIMEOUT_MS)
            synchronized(this@HeadsetButtonManager) {
                if (generation == autoReleaseGeneration &&
                    _headsetPttMode.value == HeadsetPttMode.TOGGLE &&
                    PttController.activeSource.value == PttSource.HEADSET
                ) {
                    PttController.onKeyReleased(PttSource.HEADSET)
                    Log.w(TAG, "Headset PTT auto-released after 60 seconds")
                }
                autoReleaseJob = null
            }
        }
    }

    private fun cancelAutoRelease() {
        autoReleaseGeneration++
        autoReleaseJob?.cancel()
        autoReleaseJob = null
    }

    private fun releaseHeadsetPress() {
        cancelAutoRelease()
        holdPressActive = false
        if (PttController.activeSource.value == PttSource.HEADSET) {
            PttController.onKeyReleased(PttSource.HEADSET)
        }
    }
}
