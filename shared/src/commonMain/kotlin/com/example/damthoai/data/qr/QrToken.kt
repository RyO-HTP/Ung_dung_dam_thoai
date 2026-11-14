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

import com.example.damthoai.network.SignalingJson
import com.example.damthoai.platform.currentTimeMillis
import com.example.damthoai.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Cấu trúc dữ liệu payload mã QR theo đặc tả kỹ thuật của Thành viên 5.
 */
@Serializable
data class QrTokenPayload(
    @SerialName("eventId") val eventId: Long,
    @SerialName("eventName") val eventName: String,
    @SerialName("pinCode") val pinCode: String,
    @SerialName("deptCode") val deptCode: String,
    @SerialName("deptTitle") val deptTitle: String,
    @SerialName("roleTitle") val roleTitle: String,
    @SerialName("userLevel") val userLevel: Int, // 1 Tổng đạo diễn, 2 Trưởng ban, 3 Cộng tác viên
    @SerialName("priority") val priority: Int, // 3, 2, 1
    @SerialName("primaryCh") val primaryCh: Long,
    @SerialName("primaryChCode") val primaryChCode: String, // "ch_01" đến "ch_05"
    @SerialName("listenCh") val listenCh: List<Long>,
    @SerialName("uiMode") val uiMode: String, // "EASY_SINGLE_BUTTON" hoặc "PRO_MULTI_CHANNEL"
    @SerialName("exp") val exp: Long // Thời điểm hết hạn (giây Unix)
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

    /**
     * Lấy giá trị tham số [key] trong phần query của URL (không dùng android.net.Uri để chạy được trong unit test JVM).
     * Chỉ khớp đúng tên tham số đứng sau '?' hoặc '&' (tránh "pin" khớp nhầm "spin=").
     */
    private fun extractQueryParam(url: String, key: String): String? {
        val query = url.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            val name = pair.substringBefore('=')
            if (name == key && pair.contains('=')) {
                val value = pair.substringAfter('=')
                return percentDecode(value) ?: value
            }
        }
        return null
    }

    private fun isSixDigitPin(value: String): Boolean = value.length == 6 && value.all { it.isDigit() }

    @OptIn(ExperimentalEncodingApi::class)
    fun parse(raw: String, nowEpochSeconds: Long = currentTimeMillis() / 1000): QrParseResult {
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

        // 1.5. Kiểm tra định dạng tần số PIN 6 số thực tế
        val cleanPin = trimmed.replace(" ", "")
        if (isSixDigitPin(cleanPin)) {
            Log.d(TAG, "Giải mã tần số PIN 6 số thực tế: $cleanPin")
            val pinPayload = QrTokenPayload(
                eventId = cleanPin.toLongOrNull() ?: 100000L,
                eventName = "Kênh tần số $cleanPin",
                pinCode = cleanPin,
                deptCode = "FREQ",
                deptTitle = "Ban Điều Phối Tần Số",
                roleTitle = "Thành viên kênh",
                userLevel = 2,
                priority = 1,
                primaryCh = cleanPin.toLongOrNull() ?: 101L,
                primaryChCode = "ch_$cleanPin",
                listenCh = listOf(cleanPin.toLongOrNull() ?: 101L),
                uiMode = "PRO_MULTI_CHANNEL",
                exp = nowEpochSeconds + 86400L
            )
            return QrParseResult.Valid(pinPayload, cleanPin)
        }

        // 2. Kiểm tra định dạng deep link
        if (trimmed.contains("damthoai://join")) {
            val extractedPin = extractQueryParam(trimmed, "pin")?.replace(" ", "")
            if (!extractedPin.isNullOrBlank()) {
                if (!isSixDigitPin(extractedPin)) {
                    Log.w(TAG, "Deep link chứa PIN không hợp lệ: $extractedPin")
                    return QrParseResult.Malformed
                }
                val pinPayload = QrTokenPayload(
                    eventId = extractedPin.toLongOrNull() ?: 100000L,
                    // Tên sự kiện do ban tổ chức đặt được nhúng trong QR (tham số "name")
                    eventName = extractQueryParam(trimmed, "name")?.trim()?.takeIf { it.isNotEmpty() }
                        ?: "Sự kiện Đàm Thoại",
                    pinCode = extractedPin,
                    deptCode = "JOIN",
                    deptTitle = "Ban Điều Phối",
                    roleTitle = "Thành viên kênh",
                    userLevel = 2,
                    priority = 1,
                    primaryCh = 101L,
                    primaryChCode = extractQueryParam(trimmed, "channel") ?: "ch_01",
                    listenCh = listOf(101L),
                    uiMode = "PRO_MULTI_CHANNEL",
                    exp = nowEpochSeconds + 86400L
                )
                return QrParseResult.Valid(pinPayload, extractedPin)
            }
        }

        val tokenPart = if (trimmed.startsWith(DEEP_LINK_PREFIX, ignoreCase = true)) {
            trimmed.removePrefix(DEEP_LINK_PREFIX)
        } else if (trimmed.contains("damthoai://join")) {
            extractQueryParam(trimmed, "token") ?: return QrParseResult.NotDamThoai
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
            val decodedBytes = QrBase64.decode(encodedPayload)
            val jsonString = decodedBytes.decodeToString()

            // 5. Đọc JSON thành QrTokenPayload
            val payload = SignalingJson.decodeFromString(QrTokenPayload.serializer(), jsonString)

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

/** Base64URL không đệm (base64.RawURLEncoding của backend Go); khi giải mã chấp nhận cả có/không đệm. */
@OptIn(ExperimentalEncodingApi::class)
internal val QrBase64: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

/** Giải mã %XX và '+' trong query URL theo UTF-8 (thay cho java.net.URLDecoder). null nếu sai định dạng. */
internal fun percentDecode(value: String): String? {
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            c == '+' -> { bytes.add(' '.code.toByte()); i++ }
            c == '%' -> {
                val hex = value.substring(i + 1, minOf(i + 3, value.length))
                if (hex.length != 2) return null
                bytes.add((hex.toIntOrNull(16) ?: return null).toByte())
                i += 3
            }
            else -> {
                c.toString().encodeToByteArray().forEach { bytes.add(it) }
                i++
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}
