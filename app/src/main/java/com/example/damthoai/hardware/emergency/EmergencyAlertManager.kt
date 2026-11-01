package com.example.damthoai.hardware.emergency

import android.content.Context
import android.util.Log
import com.example.damthoai.hardware.HandsFreeSettings
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

/**
 * Quản lý tập trung trạng thái và đầu ra phần cứng cho cảnh báo khẩn cấp.
 *
 * ĐIỂM NỐI:
 * - TV5/TV1 gọi [trigger] khi nhận tín hiệu khẩn cấp qua WebSocket.
 * - TV3 quan sát [currentAlert], [isVibrating], [isFlashing] và [flashUnavailableReason].
 * - Mọi thay đổi đầu ra phần cứng chỉ đi qua manager này.
 */
object EmergencyAlertManager {

    private const val TAG = "EmergencyAlertManager"
    const val ALERT_TIMEOUT_MS = 90_000L

    private val lock = Any()
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

    /**
     * Bắt đầu cảnh báo mới; cảnh báo đang chạy được thay thế mà không chồng vòng lặp phần cứng.
     * Cài đặt tắt cảnh báo vẫn cập nhật [currentAlert] để TV3 hiển thị màn cảnh báo.
     */
    fun trigger(context: Context, alert: EmergencyAlert) {
        val appContext = context.applicationContext
        val enabled = HandsFreeSettings.emergencyAlertEnabled.value
        val scheduledJob: Job

        synchronized(lock) {
            generation += 1
            val token = generation
            val previousJob = alertJob
            previousJob?.cancel()

            _currentAlert.value = alert
            _isVibrating.value = false
            _isFlashing.value = false
            _flashUnavailableReason.value = null
            _vibrationUnavailableReason.value = null
            _applicationContext = appContext

            EmergencyVibrator.stop()
            EmergencyTorch.stop()
            EmergencyNotifier.cancel(appContext)
            if (enabled) EmergencyNotifier.showIfAllowed(appContext, alert)

            scheduledJob = scope.launch(start = CoroutineStart.LAZY) {
                previousJob?.join()
                runAlert(token, appContext, enabled)
            }
            alertJob = scheduledJob
        }

        scheduledJob.start()
    }

    /** Dừng cảnh báo khi người dùng chọn "Trả lời". */
    fun acknowledge() = stop()

    fun acknowledge(context: Context) = stop(context)

    /** Dừng cảnh báo khi người dùng chọn "Để sau". */
    fun snooze() = stop()

    fun snooze(context: Context) = stop(context)

    /** Dừng mọi đầu ra và xóa trạng thái cảnh báo, kể cả khi một thiết bị phần cứng gặp lỗi. */
    fun stop() = stopInternal(null)

    fun stop(context: Context) = stopInternal(context)

    private fun stopInternal(context: Context?) {
        val appContext: Context?
        synchronized(lock) {
            generation += 1
            alertJob?.cancel()
            alertJob = null
            _currentAlert.value = null
            _isVibrating.value = false
            _isFlashing.value = false
            _flashUnavailableReason.value = null
            _vibrationUnavailableReason.value = null
            appContext = context?.applicationContext ?: _applicationContext
            if (context != null) _applicationContext = context.applicationContext
            EmergencyVibrator.stop()
            EmergencyTorch.stop()
            if (appContext != null) EmergencyNotifier.cancel(appContext)
        }
    }

    private var _applicationContext: Context? = null

    private suspend fun runAlert(token: Long, context: Context, enabled: Boolean) {
        synchronized(lock) {
            if (generation == token) _applicationContext = context
        }

        try {
            if (enabled && isCurrent(token)) {
                synchronized(lock) {
                    if (generation == token) {
                        val vibration = EmergencyVibrator.start(context)
                        _isVibrating.value = vibration.started
                        _vibrationUnavailableReason.value = vibration.unavailableReason
                    }
                }
            }

            coroutineScope {
                val torchJob = if (enabled && isCurrent(token)) {
                    launch {
                        val reason = EmergencyTorch.flash(
                            context = context,
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
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể khởi động đầu ra cảnh báo", exception)
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Đầu ra cảnh báo không thể chạy ở trạng thái hiện tại", exception)
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
                    EmergencyVibrator.stop()
                    EmergencyTorch.stop()
                    EmergencyNotifier.cancel(context)
                    true
                } else {
                    false
                }
            }
            if (!wasCurrent) {
                EmergencyVibrator.stop()
                EmergencyTorch.stop()
            }
        }
    }

    private fun isCurrent(token: Long): Boolean =
        synchronized(lock) { generation == token && _currentAlert.value != null }
}
