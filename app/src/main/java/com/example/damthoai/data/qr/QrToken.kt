/**
 * Path: app/src/main/java/com/example/damthoai/data/qr/QrToken.kt
 *
 * Khối KDoc tiếng Việt:
 * Định nghĩa cấu trúc dữ liệu payload mã QR và bộ phân tích cú pháp [QrTokenParser].
 * 
 * LƯU Ý BẢO MẬT: Chữ ký HMAC-SHA256 do máy chủ giữ khóa. Ứng dụng client (app) KHÔNG tự
 * kiểm tra chữ ký HMAC mà chỉ giải mã Base64URL để xem trước, kiểm tra hạn (exp), 
 * và gửi token lên máy chủ xác thực thật.
 */
package com.example.damthoai.data.qr

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Cấu trúc dữ liệu payload mã QR theo đặc tả kỹ thuật của Thành viên 5.
 */
data class QrTokenPayload(
    @SerializedName("eventId") val eventId: Long,
    @SerializedName("eventName") val eventName: String,
    @SerializedName("pinCode") val pinCode: String,
    @SerializedName("deptCode") val deptCode: String,
    @SerializedName("deptTitle") val deptTitle: String,
    @SerializedName("roleTitle") val roleTitle: String,
    @SerializedName("userLevel") val userLevel: Int, // 1 Tổng đạo diễn, 2 Trưởng ban, 3 Cộng tác viên
    @SerializedName("priority") val priority: Int, // 3, 2, 1
    @SerializedName("primaryCh") val primaryCh: Long,
    @SerializedName("primaryChCode") val primaryChCode: String, // "ch_01" đến "ch_05"
    @SerializedName("listenCh") val listenCh: List<Long>,
    @SerializedName("uiMode") val uiMode: String, // "EASY_SINGLE_BUTTON" hoặc "PRO_MULTI_CHANNEL"
    @SerializedName("exp") val exp: Long // Thời điểm hết hạn (giây Unix)
)

/**
 * Kết quả phân tích cú pháp mã QR.
 */
sealed interface QrParseResult {
    data class Valid(val payload: QrTokenPayload, val rawToken: String) : QrParseResult
    data class Expired(val payload: QrTokenPayload?) : QrParseResult
    object Malformed : QrParseResult
    object NotDamThoai : QrParseResult
}

/**
 * Bộ phân tích cú pháp token mã QR chuyên sâu.
 */
object QrTokenParser {
    private const val TAG = "QrTokenParser"
    private const val MOCK_QR_STRING = "MOCK_QR_EVENT_889900"
    private const val DEEP_LINK_PREFIX = "damthoai://join?token="
    private const val TOLERANCE_SECONDS = 60L
    private val gson = Gson()

    fun parse(raw: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): QrParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return QrParseResult.Malformed

        // 1. Kiểm tra chuỗi mẫu demo
        if (trimmed == MOCK_QR_STRING) {
            Log.d(TAG, "Phát hiện mã QR chuỗi mẫu demo: $MOCK_QR_STRING")
            val demoPayload = QrTokenPayload(
                eventId = 889900L,
                eventName = "Sự kiện Demo Đàm Thoại",
                pinCode = "123456",
                deptCode = "DEMO",
                deptTitle = "Ban Tổ Chức Demo",
                roleTitle = "Cộng tác viên Kỹ thuật",
                userLevel = 3,
                priority = 1,
                primaryCh = 101L,
                primaryChCode = "ch_01",
                listenCh = listOf(101L, 102L),
                uiMode = "PRO_MULTI_CHANNEL",
                exp = nowEpochSeconds + 86400L
            )
            return QrParseResult.Valid(demoPayload, MOCK_QR_STRING)
        }

        // 2. Kiểm tra định dạng deep link
        val tokenPart = if (trimmed.startsWith(DEEP_LINK_PREFIX, ignoreCase = true)) {
            trimmed.removePrefix(DEEP_LINK_PREFIX)
        } else if (trimmed.contains("damthoai://join")) {
            // Trường hợp URI tổng quát hơn
            val uri = android.net.Uri.parse(trimmed)
            uri.getQueryParameter("token") ?: return QrParseResult.NotDamThoai
        } else {
            return QrParseResult.NotDamThoai
        }

        try {
            // 3. Tách theo dấu chấm (.) thành 2 phần: base64Payload.hmacSignatureHex
            val parts = tokenPart.split(".")
            if (parts.size != 2) {
                Log.w(TAG, "Token QR không đúng cấu trúc 2 phần payload.signature")
                return QrParseResult.Malformed
            }

            val encodedPayload = parts[0]
            // Phần 2 là hmacSignatureHex, app không kiểm tra, chỉ cần tồn tại

            // 4. Giải mã Base64URL KHÔNG đệm (base64.RawURLEncoding)
            val decodedBytes = Base64.decode(
                encodedPayload,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val jsonString = String(decodedBytes, Charsets.UTF_8)

            // 5. Đọc JSON thành QrTokenPayload
            val payload = gson.fromJson(jsonString, QrTokenPayload::class.java) ?: return QrParseResult.Malformed

            // Kiểm tra các trường bắt buộc
            if (payload.eventName.isBlank() || payload.roleTitle.isBlank() || payload.userLevel !in 1..3 || payload.exp <= 0) {
                Log.w(TAG, "Payload thiếu hoặc sai trường bắt buộc")
                return QrParseResult.Malformed
            }

            // 6. Kiểm tra hạn (exp) với giờ hiện tại (cho phép lệch tối đa TOLERANCE_SECONDS)
            if (payload.exp < (nowEpochSeconds - TOLERANCE_SECONDS)) {
                Log.w(TAG, "Mã QR đã hết hạn (exp: ${payload.exp}, now: $nowEpochSeconds)")
                return QrParseResult.Expired(payload)
            }

            return QrParseResult.Valid(payload, tokenPart)
        } catch (exception: Exception) {
            Log.e(TAG, "Lỗi giải mã token QR: ${exception.message}", exception)
            return QrParseResult.Malformed
        }
    }
}
