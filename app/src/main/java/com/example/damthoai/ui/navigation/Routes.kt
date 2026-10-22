/**
 * Path: app/src/main/java/com/example/damthoai/ui/navigation/Routes.kt
 *
 * Khối KDoc tiếng Việt:
 * Quản lý tập trung toàn bộ Định tuyến (Routes) cho toàn app Đàm Thoại.
 * Phân chia cấu trúc theo các Nhóm Màn Hình A -> G:
 * A. Onboarding & Quyền
 * B. Vào Kênh (QR / PIN / Lỗi / Thông tin)
 * C. Danh sách Kênh
 * D. Màn Đàm Thoại & Bỏ túi
 * E. Khẩn cấp
 * F. Cài đặt
 * G. Quản trị Sự kiện (Cấp 1)
 */
package com.example.damthoai.ui.navigation

sealed class AppRoute(val route: String, val title: String) {

    // === NHÓM A: ONBOARDING & QUYỀN ===
    object Onboarding : AppRoute("onboarding", "Giới thiệu")
    object GrantPermission : AppRoute("grant_permission", "Cấp quyền ứng dụng")
    object MicrophonePermission : AppRoute("microphone_permission", "Cần quyền Micro")

    // === NHÓM B: VÀO KÊNH ===
    object ScanQr : AppRoute("scan_qr", "Quét mã QR")
    object EnterPin : AppRoute("enter_pin", "Nhập tần số (PIN)")
    object JoinError : AppRoute("join_error", "Không vào được kênh")
    object UserInfo : AppRoute("user_info", "Thông tin của bạn")

    // === NHÓM C: DANH SÁCH KÊNH ===
    object ChannelList : AppRoute("channel_list", "Danh sách Kênh")
    object ChannelMenuSheet : AppRoute("channel_menu_sheet", "Menu Kênh")

    // === NHÓM D: MÀN ĐÀM THOẠI & BỎ TÚI ===
    object ActiveCall : AppRoute("active_call", "Đàm thoại Active")
    object PocketMode : AppRoute("pocket_mode", "Màn hình Bỏ túi")

    // === NHÓM E: KHẨN CẤP ===
    object EmergencyCallSelect : AppRoute("emergency_call_select", "Gọi khẩn cấp")
    object EmergencyAlertIncoming : AppRoute("emergency_alert_incoming", "Cảnh báo đến")
    object EmergencyActiveCall : AppRoute("emergency_active_call", "Cuộc gọi khẩn cấp")

    // === NHÓM F: CÀI ĐẶT ===
    object Settings : AppRoute("settings", "Cài đặt")
    object MultiLevelConnection : AppRoute("multi_level_connection", "Kết nối đa tầng")
    object HandsFreeAudio : AppRoute("hands_free_audio", "Rảnh tay và âm thanh")
    object UserProfile : AppRoute("user_profile", "Hồ sơ của bạn")

    // === NHÓM G: QUẢN TRỊ SỰ KIỆN (CẤP 1) ===
    object CreateEvent : AppRoute("create_event", "Tạo sự kiện")
    object RestoreEvent : AppRoute("restore_event", "Khôi phục sự kiện")
    object ManageEvent : AppRoute("manage_event", "Quản lý sự kiện")
    object MyQrCode : AppRoute("my_qr_code", "Mã QR của ban")
    object EventMembers : AppRoute("event_members", "Thành viên sự kiện")
    object LiveCueDraft : AppRoute("live_cue_draft", "Soạn Live Cue")
    object EventEnded : AppRoute("event_ended", "Sự kiện đã kết thúc")
    object RemovedFromEvent : AppRoute("removed_from_event", "Bạn đã bị xóa khỏi sự kiện")
}
