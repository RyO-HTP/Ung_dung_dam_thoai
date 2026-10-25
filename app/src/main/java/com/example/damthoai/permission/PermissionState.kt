package com.example.damthoai.permission

import com.example.damthoai.R

/**
 * =================================================================================
 * PHẦN THUỘC TÍNH: Trạng thái cấp quyền (Permission State Enum)
 * VAI TRÒ: Thành viên 4 (Hardware / Event Integration) - Tuần 1
 * =================================================================================
 *
 * File này chứa enum biểu diễn 3 trạng thái có thể xảy ra của một nhóm quyền [AppPermission]:
 * 1. [GRANTED]: Người dùng đã đồng ý cấp quyền.
 * 2. [DENIED]: Người dùng chưa cấp hoặc mới từ chối lần đầu (có thể hỏi lại bằng dialog chuẩn).
 * 3. [PERMANENTLY_DENIED]: Đã từng hỏi nhưng Android không còn đề xuất hiển thị rationale.
 *    Thường do người dùng chọn "Không hỏi lại" hoặc hệ thống không cho hỏi lại bằng dialog;
 *    cần hướng dẫn mở Cài đặt ứng dụng để bật thủ công.
 */
enum class PermissionState(val statusTextRes: Int) {
    /**
     * Quyền đã được chấp thuận.
     */
    GRANTED(R.string.perm_status_granted),

    /**
     * Quyền chưa được cấp hoặc bị từ chối 1 lần.
     */
    DENIED(R.string.perm_status_denied),

    /**
     * Quyền bị từ chối vĩnh viễn ("Don't ask again").
     */
    PERMANENTLY_DENIED(R.string.perm_status_permanently_denied)
}
