package com.example.damthoai.data

import android.content.Context

/**
 * Lưu các cờ phục vụ xác định luồng khởi động ứng dụng.
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

    // Lưu trong bộ nhớ (volatile), khi tắt app hoàn toàn (kill process) sẽ mất,
    // yêu cầu người dùng phải vào kênh lại.
    private var memoryJoinedEvent = false

    /** Đọc trạng thái người dùng đã hoàn tất giới thiệu hay chưa. */
    fun isOnboardingDone(context: Context): Boolean =
        preferences(context).getBoolean(KEY_ONBOARDING_DONE, false)

    /** Ghi trạng thái hoàn tất giới thiệu. */
    fun setOnboardingDone(context: Context, done: Boolean) {
        preferences(context).edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
    }

    /** Đọc trạng thái người dùng hiện đang tham gia sự kiện hay không. */
    fun hasJoinedEvent(context: Context): Boolean = memoryJoinedEvent

    /** Ghi trạng thái tham gia sự kiện. */
    fun setHasJoinedEvent(context: Context, joined: Boolean) {
        memoryJoinedEvent = joined
    }

    /** Đọc mã PIN sự kiện hiện tại. */
    fun getEventPin(context: Context): String =
        preferences(context).getString(KEY_EVENT_PIN, "") ?: ""

    /** Lưu mã PIN sự kiện mới. */
    fun setEventPin(context: Context, pin: String) {
        preferences(context).edit().putString(KEY_EVENT_PIN, pin).apply()
    }

    // ---- Địa chỉ máy chủ Signaling ----
    // Hai giá trị tách riêng:
    //  - Địa chỉ TỰ NHẬP trong cài đặt: ưu tiên cao nhất (VD thử server riêng). Trống = tự động.
    //  - Địa chỉ TÌM ĐƯỢC lần trước (máy chủ đám mây hoặc LAN): giúp lần sau kết nối nhanh hơn.
    // Không có IP mặc định cố định, vì IP máy chủ khác nhau ở mỗi mạng.

    /** Địa chỉ người dùng tự nhập ("" = để app tự chọn máy chủ). */
    fun getManualServerUrl(context: Context): String =
        preferences(context).getString(KEY_MANUAL_SERVER_URL, "") ?: ""

    fun setManualServerUrl(context: Context, url: String) {
        preferences(context).edit().putString(KEY_MANUAL_SERVER_URL, normalizeSignalingUrl(url)).apply()
    }

    /** Địa chỉ máy chủ app tìm được và kết nối thành công lần gần nhất. */
    fun getLastServerUrl(context: Context): String =
        preferences(context).getString(KEY_LAST_SERVER_URL, "") ?: ""

    fun setLastServerUrl(context: Context, url: String) {
        preferences(context).edit().putString(KEY_LAST_SERVER_URL, normalizeSignalingUrl(url)).apply()
    }

    /** Tự thêm "ws://" nếu người dùng chỉ nhập IP:cổng (OkHttp chỉ chấp nhận URL có scheme). */
    fun normalizeSignalingUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return trimmed
        val hasScheme = listOf("ws://", "wss://", "http://", "https://").any { trimmed.startsWith(it, ignoreCase = true) }
        return if (hasScheme) trimmed else "ws://$trimmed"
    }

    /** Tên hiển thị mặc định khi chưa có tên sự kiện thật. */
    const val DEFAULT_EVENT_NAME = "Sự kiện Đàm Thoại"

    /** Đọc tên sự kiện hiện tại (luôn trả về tên hiển thị được, không bao giờ rỗng). */
    fun getEventName(context: Context): String {
        val name = preferences(context).getString(KEY_EVENT_NAME, "") ?: ""
        return if (name == "Sự kiện quét QR" || name.isBlank()) DEFAULT_EVENT_NAME else name
    }

    /** true nếu đã lưu tên sự kiện thật (không phải tên mặc định). */
    fun hasCustomEventName(context: Context): Boolean = getEventName(context) != DEFAULT_EVENT_NAME

    /** Lưu tên sự kiện mới. */
    fun setEventName(context: Context, name: String) {
        preferences(context).edit().putString(KEY_EVENT_NAME, name).apply()
    }

    /** Đọc hoặc tạo ID định danh duy nhất cho thiết bị. */
    fun getUserId(context: Context): String {
        var uid = preferences(context).getString(KEY_USER_ID, null)
        if (uid.isNullOrBlank()) {
            // Đủ dài để 2 máy gần như không thể trùng ID (trùng ID làm 2 máy không gọi được nhau)
            val randomSuffix = java.util.UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
            uid = "User_$randomSuffix"
            preferences(context).edit().putString(KEY_USER_ID, uid).apply()
        }
        return uid
    }

    /** Tên hiển thị của người dùng trên thiết bị này (hiện trong danh sách thành viên). */
    fun getDisplayName(context: Context): String =
        preferences(context).getString(KEY_DISPLAY_NAME, "") ?: ""

    /** Vai trò / chức vụ của người dùng trên thiết bị này. */
    fun getRoleTitle(context: Context): String =
        preferences(context).getString(KEY_ROLE_TITLE, "") ?: ""

    /** Lưu tên hiển thị và vai trò. */
    fun setProfile(context: Context, displayName: String, roleTitle: String) {
        preferences(context).edit()
            .putString(KEY_DISPLAY_NAME, displayName.trim())
            .putString(KEY_ROLE_TITLE, roleTitle.trim())
            .apply()
    }

    /** true nếu thiết bị này là ban tổ chức (đã tạo sự kiện hiện tại). */
    fun isOrganizer(context: Context): Boolean =
        preferences(context).getBoolean(KEY_IS_ORGANIZER, false)

    fun setOrganizer(context: Context, organizer: Boolean) {
        preferences(context).edit().putBoolean(KEY_IS_ORGANIZER, organizer).apply()
    }

    /** Xóa toàn bộ cờ luồng khởi động để kiểm thử lại trải nghiệm người mới. */
    fun clearAll(context: Context) {
        preferences(context).edit().clear().apply()
        memoryJoinedEvent = false
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
