package com.example.damthoai.permission

/**
 * Các nhóm quyền ứng dụng cần. Ánh xạ sang quyền hệ điều hành nằm ở từng nền tảng
 * (Android: AppPermission.getManifestPermissions() trong :app; iOS: AVFoundation/UserNotifications).
 */
enum class AppPermission(
    val title: String,
    val description: String,
    val isRequired: Boolean
) {
    AUDIO(
        title = "Micro & Âm thanh",
        description = "Bắt buộc để thu âm truyền giọng nói qua bộ đàm và chỉnh âm lượng.",
        isRequired = true
    ),
    CAMERA(
        title = "Camera & Đèn Flash",
        description = "Dùng để bật đèn Flash nháy báo động khẩn cấp (Emergency Ping).",
        isRequired = false
    ),
    BLUETOOTH(
        title = "Tai nghe & Phím Bluetooth",
        description = "Dùng kết nối tai nghe không dây và phím bấm PTT Bluetooth rời.",
        isRequired = false
    ),
    NOTIFICATION(
        title = "Thông báo hệ thống",
        description = "Hiển thị trạng thái kênh đàm thoại đang chạy và tín hiệu khẩn cấp.",
        isRequired = false
    ),
    BACKGROUND(
        title = "Chạy nền & Bỏ tối ưu pin",
        description = "Giữ kết nối bộ đàm liên tục ngay cả khi tắt màn hình hoặc khóa máy.",
        isRequired = false
    ),
    NETWORK(
        title = "Kết nối Mạng & Wi-Fi",
        description = "Cho phép truyền nhận dữ liệu bộ đàm qua Wi-Fi hoặc 4G/5G.",
        isRequired = false
    );
}
