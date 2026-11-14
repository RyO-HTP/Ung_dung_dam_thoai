package com.example.damthoai.permission

/**
 * Trạng thái của một nhóm quyền.
 */
enum class PermissionState(val statusText: String) {
    /** Quyền đã được chấp thuận. */
    GRANTED("Đã cấp quyền"),

    /** Quyền chưa được cấp hoặc bị từ chối 1 lần. */
    DENIED("Chưa cấp quyền"),

    /** Quyền bị từ chối vĩnh viễn ("Don't ask again" / iOS: đã từ chối trong Cài đặt). */
    PERMANENTLY_DENIED("Bị chặn vĩnh viễn")
}
