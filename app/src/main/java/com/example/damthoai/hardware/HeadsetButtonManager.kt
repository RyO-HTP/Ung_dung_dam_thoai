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
import kotlinx.coroutines.flow.StateFlow

/**
 * Cách bấm nút tai nghe: bật/tắt mic hoặc chỉ giữ mic trong lúc nhấn.
 */
enum class HeadsetPttMode {
    TOGGLE,
    HOLD
}

/**
 * Định tuyến nút tai nghe có dây/Bluetooth về [PttController].
 *
 * MediaSession nền tảng quảng bá trạng thái đang phát với ACTION_PLAY_PAUSE để Android
 * định tuyến phím media về ứng dụng khi phiên rảnh tay đang hoạt động, kể cả lúc tắt màn hình.
 * Đây không phải trình phát âm thanh và không tạo Service; phiên MediaSession chỉ sống cùng
 * [HandsFreeSession].
 */
object HeadsetButtonManager {

    private const val TAG = "HeadsetButtonManager"
    private const val DUPLICATE_HEADSET_EVENT_WINDOW_MS = 300L
    private const val HEADSET_PTT_AUTO_RELEASE_TIMEOUT_MS = 60_000L

    val headsetButtonEnabled: StateFlow<Boolean> = HandsFreeSettings.headsetButtonEnabled
    val headsetPttMode: StateFlow<HeadsetPttMode> = HandsFreeSettings.headsetPttMode

    private var mediaSession: MediaSession? = null
    private var timeoutScope: CoroutineScope? = null
    private var autoReleaseJob: Job? = null
    private var autoReleaseGeneration = 0L
    private var lastAcceptedPressAtMs: Long? = null
    private var holdPressActive = false

    val isRunning: Boolean
        get() = synchronized(this) { mediaSession != null }

    @Synchronized
    fun start(context: Context) {
        if (mediaSession != null) return

        val session = try {
            MediaSession(context.applicationContext, "DamThoaiHeadsetPtt")
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể tạo MediaSession tai nghe do thiếu quyền hệ thống", exception)
            return
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Không thể tạo MediaSession tai nghe ở trạng thái hiện tại", exception)
            return
        }

        try {
            session.apply {
                setFlags(
                    MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
                setCallback(object : MediaSession.Callback() {
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
                setPlaybackState(
                    PlaybackState.Builder()
                        .setActions(PlaybackState.ACTION_PLAY_PAUSE)
                        .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                        .build()
                )
                setActive(true)
            }
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể khởi động MediaSession tai nghe do thiếu quyền hệ thống", exception)
            releaseAfterStartFailure(session)
            return
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "MediaSession tai nghe không thể khởi động ở trạng thái hiện tại", exception)
            releaseAfterStartFailure(session)
            return
        }

        mediaSession = session
        timeoutScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    private fun releaseAfterStartFailure(session: MediaSession) {
        try {
            session.release()
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không có quyền giải phóng MediaSession tai nghe sau lỗi khởi động", exception)
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Không thể giải phóng MediaSession tai nghe sau lỗi khởi động", exception)
        }
    }

    @Synchronized
    fun stop() {
        cancelAutoRelease()
        timeoutScope?.cancel()
        timeoutScope = null
        holdPressActive = false
        lastAcceptedPressAtMs = null

        if (PttController.activeSource.value == PttSource.HEADSET) {
            PttController.onKeyReleased(PttSource.HEADSET)
        }

        mediaSession?.let { session ->
            try {
                session.setActive(false)
            } catch (exception: SecurityException) {
                Log.e(TAG, "Không thể tắt MediaSession tai nghe", exception)
            } catch (exception: IllegalStateException) {
                Log.e(TAG, "MediaSession tai nghe đã ở trạng thái không hợp lệ khi tắt", exception)
            }
            try {
                session.release()
            } catch (exception: SecurityException) {
                Log.e(TAG, "Không có quyền giải phóng MediaSession tai nghe", exception)
            } catch (exception: IllegalStateException) {
                Log.e(TAG, "Không thể giải phóng MediaSession tai nghe", exception)
            }
        }
        mediaSession = null
    }

    fun setHeadsetButtonEnabled(enabled: Boolean) {
        HandsFreeSettings.setHeadsetButtonEnabled(enabled)
        if (!enabled) {
            synchronized(this) {
                releaseHeadsetPress()
            }
        }
    }

    fun setHeadsetPttMode(mode: HeadsetPttMode) {
        synchronized(this) {
            if (headsetPttMode.value == mode) return
            HandsFreeSettings.setHeadsetPttMode(mode)
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
                if (previous != null &&
                    now - previous < DUPLICATE_HEADSET_EVENT_WINDOW_MS
                ) {
                    return true
                }
                lastAcceptedPressAtMs = now
                if (!headsetButtonEnabled.value) return true

                when (headsetPttMode.value) {
                    HeadsetPttMode.TOGGLE -> toggleHeadsetPtt()
                    HeadsetPttMode.HOLD -> pressAndHoldHeadsetPtt()
                }
            }

            KeyEvent.ACTION_UP -> {
                if (headsetPttMode.value == HeadsetPttMode.HOLD && holdPressActive) {
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
        PttController.onKeyPressed(PttSource.HEADSET)
        holdPressActive = PttController.activeSource.value == PttSource.HEADSET
    }

    private fun scheduleAutoRelease() {
        cancelAutoRelease()
        val scope = timeoutScope ?: return
        val generation = ++autoReleaseGeneration
        autoReleaseJob = scope.launch {
            delay(HEADSET_PTT_AUTO_RELEASE_TIMEOUT_MS)
            synchronized(this@HeadsetButtonManager) {
                if (generation == autoReleaseGeneration &&
                    headsetPttMode.value == HeadsetPttMode.TOGGLE &&
                    PttController.activeSource.value == PttSource.HEADSET
                ) {
                    PttController.onKeyReleased(PttSource.HEADSET)
                    Log.w(TAG, "Headset PTT tự thả sau 60 giây")
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
