package com.example.damthoai.data

import com.example.damthoai.platform.createSettings
import com.russhwolf.settings.Settings
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Lưu các cờ phục vụ xác định luồng khởi động ứng dụng.
 * Android: SharedPreferences "app_start_preferences" (giữ nguyên tên cũ để không mất dữ liệu).
 * iOS: NSUserDefaults cùng tên.
 *
 * ĐIỂM NỐI TV5: thay hasJoinedEvent bằng phiên sự kiện thật.
 */
object AppStartPrefs {
    private const val PREFERENCES_NAME = "app_start_preferences"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_EVENT_PIN = "event_pin"
    private const val KEY_MANUAL_SERVER_URL = "manual_server_url"
    private const val KEY_LAST_SERVER_URL = "signaling_url" // Giữ tên khóa cũ để dùng lại địa chỉ đã lưu
    private const val KEY_EVENT_NAME = "event_name"
    private const val KEY_USER_ID = "device_user_id"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_ROLE_TITLE = "role_title"
    private const val KEY_IS_ORGANIZER = "is_organizer"

    private val prefs: Settings by lazy { createSettings(PREFERENCES_NAME) }

    // Lưu trong bộ nhớ (volatile), khi tắt app hoàn toàn (kill process) sẽ mất,
    // yêu cầu người dùng phải vào kênh lại.
    private var memoryJoinedEvent = false

    /** Đọc trạng thái người dùng đã hoàn tất giới thiệu hay chưa. */
    fun isOnboardingDone(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    /** Ghi trạng thái hoàn tất giới thiệu. */
    fun setOnboardingDone(done: Boolean) = prefs.putBoolean(KEY_ONBOARDING_DONE, done)

    /** Đọc trạng thái người dùng hiện đang tham gia sự kiện hay không. */
    fun hasJoinedEvent(): Boolean = memoryJoinedEvent

    /** Ghi trạng thái tham gia sự kiện. */
    fun setHasJoinedEvent(joined: Boolean) {
        memoryJoinedEvent = joined
    }

    /** Đọc mã PIN sự kiện hiện tại. */
    fun getEventPin(): String = prefs.getString(KEY_EVENT_PIN, "")

    /** Lưu mã PIN sự kiện mới. */
    fun setEventPin(pin: String) = prefs.putString(KEY_EVENT_PIN, pin)

    // ---- Địa chỉ máy chủ Signaling ----
    // Hai giá trị tách riêng:
    //  - Địa chỉ TỰ NHẬP trong cài đặt: ưu tiên cao nhất (VD thử server riêng). Trống = tự động.
    //  - Địa chỉ TÌM ĐƯỢC lần trước (máy chủ đám mây hoặc LAN): giúp lần sau kết nối nhanh hơn.
    // Không có IP mặc định cố định, vì IP máy chủ khác nhau ở mỗi mạng.

    /** Địa chỉ người dùng tự nhập ("" = để app tự chọn máy chủ). */
    fun getManualServerUrl(): String = prefs.getString(KEY_MANUAL_SERVER_URL, "")

    fun setManualServerUrl(url: String) = prefs.putString(KEY_MANUAL_SERVER_URL, normalizeSignalingUrl(url))

    /** Địa chỉ máy chủ app tìm được và kết nối thành công lần gần nhất. */
    fun getLastServerUrl(): String = prefs.getString(KEY_LAST_SERVER_URL, "")

    fun setLastServerUrl(url: String) = prefs.putString(KEY_LAST_SERVER_URL, normalizeSignalingUrl(url))

    /** Tự thêm "ws://" nếu người dùng chỉ nhập IP:cổng (WebSocket chỉ chấp nhận URL có scheme). */
    fun normalizeSignalingUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return trimmed
        val hasScheme = listOf("ws://", "wss://", "http://", "https://").any { trimmed.startsWith(it, ignoreCase = true) }
        return if (hasScheme) trimmed else "ws://$trimmed"
    }

    /** Tên hiển thị mặc định khi chưa có tên sự kiện thật. */
    const val DEFAULT_EVENT_NAME = "Sự kiện Đàm Thoại"

    /** Đọc tên sự kiện hiện tại (luôn trả về tên hiển thị được, không bao giờ rỗng). */
    fun getEventName(): String {
        val name = prefs.getString(KEY_EVENT_NAME, "")
        return if (name == "Sự kiện quét QR" || name.isBlank()) DEFAULT_EVENT_NAME else name
    }

    /** true nếu đã lưu tên sự kiện thật (không phải tên mặc định). */
    fun hasCustomEventName(): Boolean = getEventName() != DEFAULT_EVENT_NAME

    /** Lưu tên sự kiện mới. */
    fun setEventName(name: String) = prefs.putString(KEY_EVENT_NAME, name)

    /** Đọc hoặc tạo ID định danh duy nhất cho thiết bị. */
    @OptIn(ExperimentalUuidApi::class)
    fun getUserId(): String {
        var uid = prefs.getStringOrNull(KEY_USER_ID)
        if (uid.isNullOrBlank()) {
            // Đủ dài để 2 máy gần như không thể trùng ID (trùng ID làm 2 máy không gọi được nhau)
            val randomSuffix = Uuid.random().toHexString().take(8).uppercase()
            uid = "User_$randomSuffix"
            prefs.putString(KEY_USER_ID, uid)
        }
        return uid
    }

    /** Tên hiển thị của người dùng trên thiết bị này (hiện trong danh sách thành viên). */
    fun getDisplayName(): String = prefs.getString(KEY_DISPLAY_NAME, "")

    /** Vai trò / chức vụ của người dùng trên thiết bị này. */
    fun getRoleTitle(): String = prefs.getString(KEY_ROLE_TITLE, "")

    /** Lưu tên hiển thị và vai trò. */
    fun setProfile(displayName: String, roleTitle: String) {
        prefs.putString(KEY_DISPLAY_NAME, displayName.trim())
        prefs.putString(KEY_ROLE_TITLE, roleTitle.trim())
    }

    /** true nếu thiết bị này là ban tổ chức (đã tạo sự kiện hiện tại). */
    fun isOrganizer(): Boolean = prefs.getBoolean(KEY_IS_ORGANIZER, false)

    fun setOrganizer(organizer: Boolean) = prefs.putBoolean(KEY_IS_ORGANIZER, organizer)

    /** Xóa toàn bộ cờ luồng khởi động để kiểm thử lại trải nghiệm người mới. */
    fun clearAll() {
        prefs.clear()
        memoryJoinedEvent = false
    }
}
