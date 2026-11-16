package com.example.damthoai.service

import com.example.damthoai.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Trạng thái lượt nói của máy này. */
sealed class FloorStatus {
    /** Không giữ PTT. */
    object Idle : FloorStatus()
    /** Đã bấm PTT, đang chờ server cho lượt (mic còn tắt). */
    data class Requesting(val channelId: String) : FloorStatus()
    /** Được lượt: mic đang mở. */
    data class Talking(val channelId: String) : FloorStatus()
    /** Bấm PTT nhưng kênh đang có người khác nói (ngang/cao cấp hơn): mic tắt. */
    data class Busy(val channelId: String, val holderId: String?) : FloorStatus()
    /** Đang nói thì bị người cấp cao hơn chen ngang, hoặc hết thời gian nói: mic tắt. */
    data class Cut(val channelId: String, val byId: String?) : FloorStatus()
}

/** Sự kiện cần báo cho người dùng (UI đổi thành câu chữ kèm tên người). */
sealed class FloorNotice {
    data class Busy(val holderId: String?) : FloorNotice()
    data class Preempted(val byId: String?) : FloorNotice()
    object Timeout : FloorNotice()
}

/**
 * Máy trạng thái GIÀNH LƯỢT NÓI phía app. Server là nơi quyết định (ai giữ lượt, ai được chen ngang);
 * lớp này chỉ: xin lượt khi bấm PTT, mở mic khi được cho, tắt mic khi bị từ chối / chen ngang / hết giờ.
 *
 * Không có kết nối máy chủ, hoặc máy chủ bản cũ không trả lời trong [replyTimeoutMs]: vẫn mở mic
 * (bộ đàm "nói được" quan trọng hơn "đúng lượt"), ghi log để biết.
 *
 * Mọi hàm gọi trên cùng một luồng (Main) — không tự đồng bộ hoá.
 */
class FloorController(
    private val scope: CoroutineScope,
    private val myUserId: () -> String,
    private val isOnline: () -> Boolean,
    private val sendRequest: (channelId: String) -> Unit,
    private val sendRelease: (channelId: String) -> Unit,
    private val setMicOpen: (Boolean) -> Unit,
    /** Báo server "vẫn đang nói" (server thu hồi lượt nếu im quá 5 giây — máy mất sóng giữa chừng). */
    private val sendKeepalive: (channelId: String) -> Unit = {},
    private val replyTimeoutMs: Long = REPLY_TIMEOUT_MS
) {
    companion object {
        private const val TAG = "FloorController"
        const val REPLY_TIMEOUT_MS = 1500L
        const val KEEPALIVE_INTERVAL_MS = 2000L
    }

    private var keepaliveJob: Job? = null

    private val _status = MutableStateFlow<FloorStatus>(FloorStatus.Idle)
    val status: StateFlow<FloorStatus> = _status.asStateFlow()

    private val _notices = MutableSharedFlow<FloorNotice>(extraBufferCapacity = 8)
    val notices: SharedFlow<FloorNotice> = _notices.asSharedFlow()

    private var replyTimeout: Job? = null

    /** Bấm PTT trên [channelId]. */
    fun press(channelId: String) {
        if (!isOnline()) {
            Log.w(TAG, "Không có máy chủ: mở mic không cần xin lượt")
            openMic(channelId)
            return
        }
        _status.value = FloorStatus.Requesting(channelId)
        sendRequest(channelId)
        replyTimeout?.cancel()
        replyTimeout = scope.launch {
            delay(replyTimeoutMs)
            if (_status.value == FloorStatus.Requesting(channelId)) {
                Log.w(TAG, "Máy chủ không trả lời xin lượt (bản cũ?): mở mic luôn")
                openMic(channelId)
            }
        }
    }

    /** Nhả PTT: tắt mic, trả lượt (nếu đang xin hoặc đang giữ). */
    fun release() {
        replyTimeout?.cancel()
        keepaliveJob?.cancel()
        setMicOpen(false)
        val channel = when (val s = _status.value) {
            is FloorStatus.Requesting -> s.channelId
            is FloorStatus.Talking -> s.channelId
            else -> null
        }
        if (channel != null && isOnline()) sendRelease(channel)
        _status.value = FloorStatus.Idle
    }

    /** Đổi kênh phát khi đang giữ PTT: trả lượt kênh cũ, xin lượt kênh mới. */
    fun switchChannel(channelId: String) {
        if (_status.value == FloorStatus.Idle) return
        release()
        press(channelId)
    }

    fun onGranted(channelId: String) {
        if (_status.value == FloorStatus.Requesting(channelId)) {
            openMic(channelId)
        }
        // Không còn chờ (đã nhả PTT): gói release đã gửi sau request nên server tự nhả, không cần làm gì
    }

    fun onDenied(channelId: String, holderId: String?) {
        if (_status.value != FloorStatus.Requesting(channelId)) return
        replyTimeout?.cancel()
        setMicOpen(false)
        _status.value = FloorStatus.Busy(channelId, holderId)
        _notices.tryEmit(FloorNotice.Busy(holderId))
    }

    /** Ai đang giữ lượt trên [channelId] đổi. Mình đang nói mà lượt sang người khác → tắt mic. */
    fun onState(channelId: String, holderId: String?, preemptedId: String?, reason: String) {
        val me = myUserId()
        val current = _status.value
        if (current !is FloorStatus.Talking || current.channelId != channelId) return
        if (holderId == me) return
        // Được mở mic theo kiểu "không chờ" (server không trả lời kịp) mà server báo kênh trống: bỏ qua
        if (holderId == null && reason != "timeout" && preemptedId != me) return

        setMicOpen(false)
        _status.value = FloorStatus.Cut(channelId, holderId)
        _notices.tryEmit(if (reason == "timeout") FloorNotice.Timeout else FloorNotice.Preempted(holderId))
    }

    private fun openMic(channelId: String) {
        replyTimeout?.cancel()
        _status.value = FloorStatus.Talking(channelId)
        setMicOpen(true)
        // Đang giữ lượt: cứ 2 giây báo "vẫn đang nói"; dừng ngay khi không còn nói trên kênh này
        keepaliveJob?.cancel()
        keepaliveJob = scope.launch {
            while (true) {
                delay(KEEPALIVE_INTERVAL_MS)
                if (_status.value != FloorStatus.Talking(channelId)) break
                if (isOnline()) sendKeepalive(channelId)
            }
        }
    }
}
