package com.example.damthoai.hardware.emergency

import com.example.damthoai.data.MemberLevel
import com.example.damthoai.hardware.HandsFreeSettings
import com.example.damthoai.util.Log
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Kết quả bật bộ rung. */
data class VibrationStartResult(
    val started: Boolean,
    val unavailableReason: String? = null
)

/**
 * Đầu ra phần cứng của cảnh báo khẩn cấp trên từng nền tảng.
 * Android: Vibrator + CameraManager torch + Notification full-screen.
 * iOS: CoreHaptics/AudioServices + AVCaptureDevice torch + UNUserNotification.
 */
interface EmergencyOutputs {
    fun startVibration(): VibrationStartResult
    fun stopVibration()

    /** Nháy đèn tới khi bị huỷ. Trả về lý do không dùng được đèn (null nếu chạy bình thường). */
    suspend fun flash(onFlashingChanged: (Boolean) -> Unit, onUnavailable: (String) -> Unit): String?
    fun stopTorch()

    fun showNotification(alert: EmergencyAlert)
    fun cancelNotification()
}

/**
 * Quản lý tập trung trạng thái và đầu ra phần cứng cho cảnh báo khẩn cấp.
 *
 * ĐIỂM NỐI:
 * - TV5/TV1 gọi [trigger] khi nhận tín hiệu khẩn cấp qua WebSocket.
 * - TV3 quan sát [currentAlert], [isVibrating], [isFlashing] và [flashUnavailableReason].
 * - Mọi thay đổi đầu ra phần cứng chỉ đi qua manager này.
 * - Mỗi nền tảng gán [outputs] khi khởi động app.
 */
object EmergencyAlertManager {

    private const val TAG = "EmergencyAlertManager"
    const val ALERT_TIMEOUT_MS = 90_000L

    /** Đầu ra phần cứng của nền tảng hiện tại (null = chỉ cập nhật trạng thái, không rung/nháy). */
    var outputs: EmergencyOutputs? = null

    private val lock = SynchronizedObject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var generation = 0L
    private var alertJob: Job? = null

    private val _currentAlert = MutableStateFlow<EmergencyAlert?>(null)
    val currentAlert: StateFlow<EmergencyAlert?> = _currentAlert.asStateFlow()

    private val _isVibrating = MutableStateFlow(false)
    val isVibrating: StateFlow<Boolean> = _isVibrating.asStateFlow()

    private val _isFlashing = MutableStateFlow(false)
    val isFlashing: StateFlow<Boolean> = _isFlashing.asStateFlow()

    private val _flashUnavailableReason = MutableStateFlow<String?>(null)
    val flashUnavailableReason: StateFlow<String?> = _flashUnavailableReason.asStateFlow()

    private val _vibrationUnavailableReason = MutableStateFlow<String?>(null)
    val vibrationUnavailableReason: StateFlow<String?> = _vibrationUnavailableReason.asStateFlow()

    fun canInitiateAlert(level: MemberLevel): Boolean =
        level == MemberLevel.LEVEL_1

    /**
     * Bắt đầu cảnh báo mới; cảnh báo đang chạy được thay thế mà không chồng vòng lặp phần cứng.
     * Chỉ Cấp 1 (Tổng đạo diễn) mới có quyền phát cảnh báo khẩn cấp (Mục 3.4.8).
     */
    fun trigger(
        alert: EmergencyAlert,
        callerLevel: MemberLevel = MemberLevel.LEVEL_1
    ) {
        if (callerLevel != MemberLevel.LEVEL_1) {
            Log.w(TAG, "Từ chối phát khẩn cấp: Thành viên cấp $callerLevel không phải Cấp 1")
            return
        }
        val enabled = HandsFreeSettings.emergencyAlertEnabled.value
        val out = outputs
        val scheduledJob: Job = synchronized(lock) {
            if (_currentAlert.value?.id == alert.id) {
                // Idempotent: Cùng alert.id đang chạy thì không kích hoạt lại chồng vòng lặp
                return
            }
            generation += 1
            val token = generation
            val previousJob = alertJob
            previousJob?.cancel()

            _currentAlert.value = alert
            _isVibrating.value = false
            _isFlashing.value = false
            _flashUnavailableReason.value = null
            _vibrationUnavailableReason.value = null

            out?.stopVibration()
            out?.stopTorch()
            out?.cancelNotification()
            if (enabled) out?.showNotification(alert)

            val job = scope.launch(start = CoroutineStart.LAZY) {
                previousJob?.join()
                runAlert(token, out, enabled)
            }
            alertJob = job
            job
        }

        scheduledJob.start()
    }

    /** Dừng cảnh báo khi người dùng chọn "Trả lời". */
    fun acknowledge() = stop()

    /** Dừng cảnh báo khi người dùng chọn "Để sau". */
    fun snooze() = stop()

    /** Dừng mọi đầu ra và xóa trạng thái cảnh báo, kể cả khi một thiết bị phần cứng gặp lỗi. */
    fun stop() {
        synchronized(lock) {
            generation += 1
            alertJob?.cancel()
            alertJob = null
            _currentAlert.value = null
            _isVibrating.value = false
            _isFlashing.value = false
            _flashUnavailableReason.value = null
            _vibrationUnavailableReason.value = null
            outputs?.stopVibration()
            outputs?.stopTorch()
            outputs?.cancelNotification()
        }
    }

    private suspend fun runAlert(token: Long, out: EmergencyOutputs?, enabled: Boolean) {
        try {
            if (enabled && out != null && isCurrent(token)) {
                synchronized(lock) {
                    if (generation == token) {
                        val vibration = out.startVibration()
                        _isVibrating.value = vibration.started
                        _vibrationUnavailableReason.value = vibration.unavailableReason
                    }
                }
            }

            coroutineScope {
                val torchJob = if (enabled && out != null && isCurrent(token)) {
                    launch {
                        val reason = out.flash(
                            onFlashingChanged = { flashing ->
                                synchronized(lock) {
                                    if (generation == token) _isFlashing.value = flashing
                                }
                            },
                            onUnavailable = { reason ->
                                synchronized(lock) {
                                    if (generation == token) {
                                        _isFlashing.value = false
                                        _flashUnavailableReason.value = reason
                                    }
                                }
                            }
                        )
                        if (reason != null) {
                            synchronized(lock) {
                                if (generation == token) {
                                    _isFlashing.value = false
                                    _flashUnavailableReason.value = reason
                                }
                            }
                        }
                    }
                } else {
                    null
                }

                delay(ALERT_TIMEOUT_MS)
                torchJob?.cancelAndJoin()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // Android: SecurityException / IllegalStateException khi phần cứng không sẵn sàng
            Log.e(TAG, "Không thể chạy đầu ra cảnh báo", exception)
        } finally {
            val wasCurrent = synchronized(lock) {
                if (generation == token) {
                    generation += 1
                    alertJob = null
                    _currentAlert.value = null
                    _isVibrating.value = false
                    _isFlashing.value = false
                    _flashUnavailableReason.value = null
                    _vibrationUnavailableReason.value = null
                    out?.stopVibration()
                    out?.stopTorch()
                    out?.cancelNotification()
                    true
                } else {
                    false
                }
            }
            if (!wasCurrent) {
                out?.stopVibration()
                out?.stopTorch()
            }
        }
    }

    private fun isCurrent(token: Long): Boolean =
        synchronized(lock) { generation == token && _currentAlert.value != null }
}
