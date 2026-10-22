/**
 * Path: app/src/main/java/com/example/damthoai/ui/navigation/AppNavHost.kt
 *
 * Khối KDoc tiếng Việt:
 * Quản lý Sơ đồ Điều hướng (Navigation Host) chính cho ứng dụng Đàm Thoại.
 * Tích hợp các Màn hình thật của Tuần 2: Onboarding, GrantPermission, MicrophonePermission,
 * JoinChannelScreen (Quét QR / Nhập PIN), UserInfoScreen, và ChannelListScreen.
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
import com.example.damthoai.ui.channels.ChannelListScreen
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.components.SecondaryButton
import com.example.damthoai.ui.components.SectionLabel
import com.example.damthoai.ui.components.StatusPill
import com.example.damthoai.ui.join.JoinChannelScreen
import com.example.damthoai.ui.join.UserInfoScreen
import com.example.damthoai.ui.onboarding.OnboardingScreen
import com.example.damthoai.ui.permission.GrantPermissionScreen
import com.example.damthoai.ui.permission.MicrophonePermissionScreen
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
        // === NHÓM A: ONBOARDING & QUYỀN (TUẦN 2 MÀN THẬT) ===
        composable(AppRoute.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = { navController.navigate(AppRoute.GrantPermission.route) }
            )
        }
        composable(AppRoute.GrantPermission.route) {
            GrantPermissionScreen(
                onContinue = { navController.navigate(AppRoute.ScanQr.route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.MicrophonePermission.route) {
            MicrophonePermissionScreen(
                onOpenSettings = { /* mở cài đặt hệ thống */ },
                onRetry = { navController.popBackStack() }
            )
        }

        // === NHÓM B: VÀO KÊNH (TUẦN 2 MÀN THẬT) ===
        composable(AppRoute.ScanQr.route) {
            JoinChannelScreen(
                onBack = { navController.popBackStack() },
                onSuccessJoin = { navController.navigate(AppRoute.UserInfo.route) },
                onNavigateToCreateEvent = { navController.navigate(AppRoute.CreateEvent.route) }
            )
        }
        composable(AppRoute.EnterPin.route) {
            JoinChannelScreen(
                onBack = { navController.popBackStack() },
                onSuccessJoin = { navController.navigate(AppRoute.UserInfo.route) },
                onNavigateToCreateEvent = { navController.navigate(AppRoute.CreateEvent.route) }
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
            UserInfoScreen(
                onBack = { navController.popBackStack() },
                onEnterChannel = {
                    navController.navigate(AppRoute.ChannelList.route) {
                        popUpTo(AppRoute.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // === NHÓM C: DANH SÁCH KÊNH (TUẦN 2 MÀN THẬT) ===
        composable(AppRoute.ChannelList.route) {
            ChannelListScreen(
                onNavigateToActiveCall = { navController.navigate(AppRoute.ActiveCall.route) },
                onNavigateToAddChannel = { navController.navigate(AppRoute.ScanQr.route) }
            )
        }
        composable(AppRoute.ChannelMenuSheet.route) {
            PlaceholderScreen(
                title = "MENU KÊNH (BOTTOM SHEET)",
                description = "Tùy chỉnh âm lượng kênh, rời kênh, đổi chức vụ",
                onBack = { navController.popBackStack() }
            )
        }

        // === NHÓM D: MÀN ĐÀM THOẠI & BỎ TÚI (PLACEHOLDERS CHO TUẦN SAU) ===
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
 * Composable giao diện Màn hình Mẫu (Placeholder) dùng cho các nhóm chưa code
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
