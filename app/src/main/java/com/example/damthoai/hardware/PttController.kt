package com.example.damthoai.hardware

import com.example.damthoai.hardware.diagnostics.FieldTestLogger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cấu hình chọn phím Volume nào làm phím PTT.
 */
enum class VolumeKeySetting {
    /** Cả 2 phím Tăng & Giảm âm lượng đều làm nút PTT */
    BOTH,
    /** Chỉ dùng phím Giảm âm lượng (Vol-) làm nút PTT */
    VOLUME_DOWN_ONLY,
    /** Chỉ dùng phím Tăng âm lượng (Vol+) làm nút PTT */
    VOLUME_UP_ONLY
}

/**
 * =================================================================================
 * PHẦN QUẢN LÝ TẬP TRUNG: Lớp điều khiển PTT duy nhất (Central PTT Controller)
 * VAI TRÒ: Thành viên 4 (Hardware / Event Integration) - Tuần 2
 * =================================================================================
 *
 * [PttController] là TRẠM TRUNG CHUYỂN TẬP TRUNG cho toàn bộ hành động Bật/Tắt Micro bộ đàm.
 * Mọi nguồn kích hoạt (Phím Volume cứng, Tai nghe, Bluetooth BLE, hay Nút Compose trên UI)
 * ĐỀU PHẢI gửi tín hiệu qua [PttController].
 *
 * ĐIỂM NỐI VỚI CÁC THÀNH VIÊN KHÁC:
 * - TV2 (Audio): Đăng ký (collect) `events` để bật/tắt thu âm Micro và phát tiếng bíp.
 * - TV3 (UI): Đăng ký (collect) `isPttActive` và `activeSource` để cập nhật trạng thái nút PTT trên màn hình.
 */
object PttController {

    private val captureOwners = mutableSetOf<String>()
    private val _isCaptureActive = MutableStateFlow(false)
    val isCaptureActive: StateFlow<Boolean> = _isCaptureActive.asStateFlow()

    private val _backgroundSessionActive = MutableStateFlow(false)
    val backgroundSessionActive: StateFlow<Boolean> = _backgroundSessionActive.asStateFlow()

    // Trạng thái Micro đàm thoại hiện tại: true = Đang nói (Mic mở), false = Đang nghe (Mic tắt)
    private val _isPttActive = MutableStateFlow(false)
    val isPttActive: StateFlow<Boolean> = _isPttActive.asStateFlow()

    // Nguồn phím hiện tại đang kích hoạt PTT (null nếu đang rảnh)
    private val _activeSource = MutableStateFlow<PttSource?>(null)
    val activeSource: StateFlow<PttSource?> = _activeSource.asStateFlow()

    // Cờ bật/tắt chế độ bắt phím PTT (Nếu false, phím volume hoạt động chỉnh âm lượng bình thường)
    private val _isPttModeEnabled = MutableStateFlow(true)
    val isPttModeEnabled: StateFlow<Boolean> = _isPttModeEnabled.asStateFlow()

    // Cấu hình phím Volume được phép làm PTT
    private val _volumeKeySetting = MutableStateFlow(VolumeKeySetting.BOTH)
    val volumeKeySetting: StateFlow<VolumeKeySetting> = _volumeKeySetting.asStateFlow()

    // Luồng phát sự kiện [PttEvent] tới TV2 (Audio)
    private val _events = MutableSharedFlow<PttEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<PttEvent> = _events.asSharedFlow()

    @Synchronized
    fun acquireCapture(owner: String) {
        if (captureOwners.add(owner)) {
            _isCaptureActive.value = true
            HandsFreeSession.onCaptureActiveChanged(true)
        }
    }

    @Synchronized
    fun releaseCapture(owner: String) {
        if (captureOwners.remove(owner) && captureOwners.isEmpty()) {
            _isCaptureActive.value = false
            HandsFreeSession.onCaptureActiveChanged(false)
            forceReleasePtt()
        }
    }

    /**
     * ĐIỂM NỐI: TV2 (Foreground Service) và chế độ Bỏ túi sẽ bật cờ này khi có phiên chạy nền
     * trong Tuần 3. Khi cờ đang bật, MainActivity không cưỡng chế thả PTT trong onPause().
     */
    fun setBackgroundSessionActive(active: Boolean) {
        _backgroundSessionActive.value = active
    }

    /**
     * Được gọi khi người dùng BẤM VÀ GIỮ phím PTT.
     *
     * @param source Nguồn kích hoạt phím.
     */
    fun onKeyPressed(source: PttSource) {
        if (!_isPttModeEnabled.value) return

        if (_isPttActive.value) {
            // Không phát lại khi cùng nguồn đang giữ; cũng không chiếm PTT từ nguồn khác.
            return
        }

        _isPttActive.value = true
        _activeSource.value = source
        emitEvent(PttEvent.PttPressed(source))
    }

    /**
     * Được gọi khi người dùng THẢ phím PTT.
     *
     * @param source Nguồn giải phóng phím.
     */
    fun onKeyReleased(source: PttSource) {
        // Chỉ ngắt PTT nếu nguồn thả phím trùng với nguồn đang kích hoạt PTT
        if (_isPttActive.value && _activeSource.value == source) {
            _isPttActive.value = false
            _activeSource.value = null
            emitEvent(PttEvent.PttReleased(source))
        }
    }

    /**
     * LỆNH AN TOÀN HỆ THỐNG: Cưỡng chế ngắt Micro đàm thoại.
     * Gọi khi Activity tạm dừng hoặc app bị mất focus để tránh mic bị kẹt mở,
     * trừ khi có phiên chạy nền đang hoạt động.
     */
    fun forceReleasePtt() {
        if (_isPttActive.value) {
            val lastSource = _activeSource.value ?: PttSource.VOLUME_DOWN
            _isPttActive.value = false
            _activeSource.value = null
            emitEvent(PttEvent.PttReleased(lastSource))
        }
    }

    private fun emitEvent(event: PttEvent) {
        FieldTestLogger.recordPttEvent(event)
        _events.tryEmit(event)
    }

    /**
     * Thay đổi cấu hình phím Volume được dùng làm PTT (Cả 2 phím / Chỉ Vol- / Chỉ Vol+).
     */
    fun setVolumeKeySetting(setting: VolumeKeySetting) {
        _volumeKeySetting.value = setting
    }

    /**
     * Bật hoặc tắt chế độ bắt phím cứng PTT.
     */
    fun setPttModeEnabled(enabled: Boolean) {
        _isPttModeEnabled.value = enabled
        if (!enabled) {
            forceReleasePtt()
        }
    }

    /**
     * Kiểm tra xem nguồn phím Volume [source] có phù hợp với cài đặt [volumeKeySetting] hay không.
     *
     * @param source Phím Volume đang bấm ([PttSource.VOLUME_UP] hoặc [PttSource.VOLUME_DOWN]).
     * @return `true` nếu phím này được phép làm PTT, `false` nếu bỏ qua.
     */
    fun isVolumeKeyAllowed(source: PttSource): Boolean {
        return when (_volumeKeySetting.value) {
            VolumeKeySetting.BOTH -> source == PttSource.VOLUME_UP || source == PttSource.VOLUME_DOWN
            VolumeKeySetting.VOLUME_DOWN_ONLY -> source == PttSource.VOLUME_DOWN
            VolumeKeySetting.VOLUME_UP_ONLY -> source == PttSource.VOLUME_UP
        }
    }
}
