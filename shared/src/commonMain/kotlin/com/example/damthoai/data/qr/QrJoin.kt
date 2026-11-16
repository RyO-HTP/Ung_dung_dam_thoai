package com.example.damthoai.data.qr

import com.example.damthoai.data.AppStartPrefs
import com.example.damthoai.service.DamThoaiCallManager
import com.example.damthoai.ui.components.AppToast
import com.example.damthoai.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Vào sự kiện từ nội dung mã QR — dùng chung cho camera trong app và link `damthoai://join?...`
 * mở từ ứng dụng Camera của máy, để cả hai đường đều vào ĐÚNG kênh và ĐÚNG vai trò.
 */
object QrJoin {
    private const val TAG = "QrJoin"

    // Link mở app từ bên ngoài đã vào sự kiện: giao diện cần chuyển sang màn "Thông tin của bạn"
    private val _pendingUserInfo = MutableStateFlow(false)
    val pendingUserInfo: StateFlow<Boolean> = _pendingUserInfo.asStateFlow()

    /** Đọc mã QR và vào sự kiện. Trả về false (kèm thông báo) nếu mã không hợp lệ. */
    fun join(raw: String): Boolean {
        val result = QrTokenParser.parse(raw)
        if (result !is QrParseResult.Valid) {
            Log.w(TAG, "Mã QR không hợp lệ: $result")
            AppToast.show("Mã QR không hợp lệ hoặc đã hết hạn. Vui lòng quét lại.")
            return false
        }
        val payload = result.payload
        val pin = payload.pinCode.ifBlank { payload.eventId.toString() }
        // Chỉ dùng tên trong QR nếu đó là tên thật do ban tổ chức đặt (không phải tên tạm)
        val nameHint = payload.eventName.takeIf {
            it.isNotBlank() && it != AppStartPrefs.DEFAULT_EVENT_NAME && !it.startsWith("Kênh tần số")
        }
        val seat = payload.seat()
        DamThoaiCallManager.getInstance()
            .joinEvent(pin = pin, eventNameHint = nameHint, asOrganizer = false, seat = seat)
        seat?.let { AppToast.show("Đã vào ${it.groupName.ifBlank { "kênh" }} · ${it.role.title}") }
        return true
    }

    /** Link `damthoai://join?...` mở app từ bên ngoài (VD quét bằng Camera của máy). */
    fun handleExternalLink(url: String) {
        if (join(url)) _pendingUserInfo.value = true
    }

    fun consumePendingUserInfo() {
        _pendingUserInfo.value = false
    }
}
