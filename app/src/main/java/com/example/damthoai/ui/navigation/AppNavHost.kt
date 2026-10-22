/**
 * Path: app/src/main/java/com/example/damthoai/ui/navigation/AppNavHost.kt
 *
 * Khối KDoc tiếng Việt:
 * Quản lý Sơ đồ Điều hướng (Navigation Host) chính cho ứng dụng Đàm Thoại.
 * Khởi tạo toàn bộ các điểm đến (Destinations) từ AppRoute và kết nối các luồng chuyển màn hình.
 */
package com.example.damthoai.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.components.SecondaryButton
import com.example.damthoai.ui.components.SectionLabel
import com.example.damthoai.ui.components.StatusPill
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = AppRoute.Onboarding.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // === NHÓM A: ONBOARDING & QUYỀN ===
        composable(AppRoute.Onboarding.route) {
            PlaceholderScreen(
                title = "ONBOARDING",
                description = "Màn hình 3 slide giới thiệu ứng dụng Đàm Thoại",
                onNext = { navController.navigate(AppRoute.GrantPermission.route) },
                nextButtonText = "Bắt đầu -> Cấp quyền"
            )
        }
        composable(AppRoute.GrantPermission.route) {
            PlaceholderScreen(
                title = "BƯỚC CUỐI CÙNG: CẤP QUYỀN",
                description = "Yêu cầu quyền Micro, Thông báo, Vị trí",
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(AppRoute.ScanQr.route) },
                nextButtonText = "Tiếp tục -> Vào Kênh"
            )
        }
        composable(AppRoute.MicrophonePermission.route) {
            PlaceholderScreen(
                title = "CẦN QUYỀN MICRO",
                description = "Cảnh báo khi chưa được cấp quyền micro để đàm thoại PTT",
                onBack = { navController.popBackStack() }
            )
        }

        // === NHÓM B: VÀO KÊNH ===
        composable(AppRoute.ScanQr.route) {
            PlaceholderScreen(
                title = "QUÉT MÃ QR",
                description = "Quét mã QR để vào kênh nhanh chóng",
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(AppRoute.UserInfo.route) },
                nextButtonText = "Đã quét QR -> Nhập Thông tin",
                secondaryAction = { navController.navigate(AppRoute.EnterPin.route) },
                secondaryButtonText = "Chuyển sang Nhập PIN"
            )
        }
        composable(AppRoute.EnterPin.route) {
            PlaceholderScreen(
                title = "NHẬP TẦN SỐ (PIN 6 SỐ)",
                description = "Màn hình nhập mã PIN 6 số với bàn phím số riêng",
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(AppRoute.UserInfo.route) },
                nextButtonText = "Đã nhập PIN -> Nhập Thông tin"
            )
        }
        composable(AppRoute.JoinError.route) {
            PlaceholderScreen(
                title = "KHÔNG VÀO ĐƯỢC KÊNH",
                description = "Thông báo sai mã PIN hoặc sự kiện đã đóng",
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.UserInfo.route) {
            PlaceholderScreen(
                title = "THÔNG TIN CỦA BẠN",
                description = "Nhập Tên và Chức vụ trong ban tổ chức",
                onBack = { navController.popBackStack() },
                onNext = {
                    navController.navigate(AppRoute.ChannelList.route) {
                        popUpTo(AppRoute.Onboarding.route) { inclusive = true }
                    }
                },
                nextButtonText = "Hoàn tất -> Danh sách Kênh"
            )
        }

        // === NHÓM C: DANH SÁCH KÊNH ===
        composable(AppRoute.ChannelList.route) {
            PlaceholderScreen(
                title = "DANH SÁCH KÊNH",
                description = "Hiển thị Live Cue, các thẻ Kênh: Sân khấu, Hậu cần, An ninh...",
                onNext = { navController.navigate(AppRoute.ActiveCall.route) },
                nextButtonText = "Mở màn Đàm Thoại",
                secondaryAction = { navController.navigate(AppRoute.CreateEvent.route) },
                secondaryButtonText = "Tạo Sự Kiện Mới (Cấp 1)"
            )
        }
        composable(AppRoute.ChannelMenuSheet.route) {
            PlaceholderScreen(
                title = "MENU KÊNH (BOTTOM SHEET)",
                description = "Tùy chỉnh âm lượng kênh, rời kênh, đổi chức vụ",
                onBack = { navController.popBackStack() }
            )
        }

        // === NHÓM D: MÀN ĐÀM THOẠI & BỎ TÚI ===
        composable(AppRoute.ActiveCall.route) {
            PlaceholderScreen(
                title = "ĐÀM THOẠI ACTIVE CALL",
                description = "Hiển thị trạng thái PTT, danh sách người nói, sóng âm Cyan",
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(AppRoute.PocketMode.route) },
                nextButtonText = "Bật Chế độ Bỏ túi (Pocket)"
            )
        }
        composable(AppRoute.PocketMode.route) {
            DamThoaiBackground(isPocketMode = true) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    StatusPill(text = "ĐÃ KHÓA MÀN HÌNH CHỐNG CHẠM", statusColor = AccentCyan)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "CHẾ ĐỘ BỎ TÚI",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Vuốt hoặc giữ nút để mở khóa",
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    PrimaryButton(
                        text = "Mở khóa màn hình",
                        onClick = { navController.popBackStack() }
                    )
                }
            }
        }

        // === NHÓM E: KHẨN CẤP ===
        composable(AppRoute.EmergencyCallSelect.route) {
            PlaceholderScreen(
                title = "GỌI KHẨN CẤP",
                description = "Màn hình chọn người nhận cuộc gọi khẩn cấp",
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(AppRoute.EmergencyActiveCall.route) },
                nextButtonText = "Phát Cuộc gọi Khẩn cấp"
            )
        }
        composable(AppRoute.EmergencyAlertIncoming.route) {
            PlaceholderScreen(
                title = "CẢNH BÁO ĐẾN KHẨN CẤP",
                description = "Cảnh báo khẩn cấp đỏ nổi bật đè lên màn hình",
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.EmergencyActiveCall.route) {
            PlaceholderScreen(
                title = "CUỘC GỌI KHẨN CẤP DANG DIỄN RA",
                description = "Chiếm quyền ưu tiên toàn bộ các kênh",
                onBack = { navController.popBackStack() }
            )
        }

        // === NHÓM F: CÀI ĐẶT ===
        composable(AppRoute.Settings.route) {
            PlaceholderScreen(
                title = "CÀI ĐẶT",
                description = "Hồ sơ, Kết nối đa tầng, Rảnh tay và âm thanh",
                onNext = { navController.navigate(AppRoute.UserProfile.route) },
                nextButtonText = "Xem Hồ sơ của bạn",
                secondaryAction = { navController.navigate(AppRoute.MultiLevelConnection.route) },
                secondaryButtonText = "Kết nối đa tầng"
            )
        }
        composable(AppRoute.MultiLevelConnection.route) {
            PlaceholderScreen(
                title = "KẾT NỐI ĐA TẦNG",
                description = "Mạng ngang hàng P2P / Server dự phòng",
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.HandsFreeAudio.route) {
            PlaceholderScreen(
                title = "RẢNH TAY VÀ ÂM THANH",
                description = "Cấu hình Tai nghe Bluetooth & Vox Rảnh tay",
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.UserProfile.route) {
            PlaceholderScreen(
                title = "HỒ SƠ CỦA BẠN",
                description = "Chỉnh sửa tên hiển thị và vai trò trong ekip",
                onBack = { navController.popBackStack() }
            )
        }

        // === NHÓM G: QUẢN TRỊ SỰ KIỆN (CẤP 1) ===
        composable(AppRoute.CreateEvent.route) {
            PlaceholderScreen(
                title = "TẠO SỰ KIỆN MỚI (CẤP 1)",
                description = "Nhập tên sự kiện, khởi tạo mã PIN và mã Quản trị",
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(AppRoute.ManageEvent.route) },
                nextButtonText = "Tạo xong -> Quản lý Sự kiện"
            )
        }
        composable(AppRoute.ManageEvent.route) {
            PlaceholderScreen(
                title = "QUẢN LÝ SỰ KIỆN",
                description = "Mã QR của ban, Danh sách Thành viên, Live Cue",
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(AppRoute.EventMembers.route) },
                nextButtonText = "Quản lý Thành viên",
                secondaryAction = { navController.navigate(AppRoute.LiveCueDraft.route) },
                secondaryButtonText = "Soạn Live Cue"
            )
        }
        composable(AppRoute.RestoreEvent.route) {
            PlaceholderScreen(
                title = "KHÔI PHỦC SỰ KIỆN",
                description = "Nhập mã Quản trị để lấy lại quyền Cấp 1",
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.MyQrCode.route) {
            PlaceholderScreen(
                title = "MÃ QR CỦA BAN",
                description = "Mã QR chia sẻ cho các ban ngành vào kênh nhanh",
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.EventMembers.route) {
            PlaceholderScreen(
                title = "THÀNH VIÊN SỰ KIỆN",
                description = "Danh sách thành viên và phân quyền Cấp 1/2/3",
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.LiveCueDraft.route) {
            PlaceholderScreen(
                title = "SOẠN LIVE CUE",
                description = "Soạn thông báo khẩn/Lệnh đạo diễn ghim lên đầu kênh",
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.EventEnded.route) {
            PlaceholderScreen(
                title = "SỰ KIỆN ĐÃ KẾT THÚC",
                description = "Màn hình thông báo sự kiện đã đóng bởi Cấp 1",
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.RemovedFromEvent.route) {
            PlaceholderScreen(
                title = "BẠN ĐÃ BỊ XÓA KHỎI SỰ KIỆN",
                description = "Thông báo khi bị Cấp 1 xóa khỏi ban tổ chức",
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Composable giao diện Màn hình Mẫu (Placeholder) dùng cho giai đoạn dựng Navigation
 */
@Composable
private fun PlaceholderScreen(
    title: String,
    description: String,
    onBack: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    nextButtonText: String = "Tiếp tục",
    secondaryAction: (() -> Unit)? = null,
    secondaryButtonText: String = "Hành động phụ"
) {
    DamThoaiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    BackButton(onClick = onBack)
                } else {
                    StatusPill(text = "ĐÀM THOẠI", statusColor = AccentMint)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SectionLabel(text = "MÀN HÌNH FRAMEWORK")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (onNext != null) {
                PrimaryButton(
                    text = nextButtonText,
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (secondaryAction != null) {
                Spacer(modifier = Modifier.height(10.dp))
                SecondaryButton(
                    text = secondaryButtonText,
                    onClick = secondaryAction,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
