package com.example.damthoai.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.damthoai.permission.AppPermission
import com.example.damthoai.permission.PermissionState
import kotlinx.coroutines.flow.StateFlow

/**
 * Phần giao diện / thao tác hệ thống khác nhau giữa Android và iOS.
 * Android cài đặt trong :app (CameraX, ML Kit, ZXing, PermissionManager, Intent...),
 * iOS cài đặt trong iosMain (AVFoundation, CoreImage, UIActivityViewController...).
 * Cung cấp qua [LocalPlatformUi] ở gốc cây Compose (xem App()).
 */
interface PlatformUi {

    // ---------------- Quyền ----------------

    fun checkPermission(permission: AppPermission): PermissionState

    /**
     * Trả về hàm xin quyền. Gọi hàm đó sẽ hiện hộp thoại hệ thống, sau đó gọi [onResult]
     * với trạng thái mới.
     */
    @Composable
    fun rememberPermissionRequester(
        permission: AppPermission,
        onResult: (PermissionState) -> Unit
    ): () -> Unit

    /** Mở trang cài đặt quyền của ứng dụng. */
    fun openAppSettings()

    /** Màn xin toàn bộ quyền lúc khởi động (luồng onboarding). */
    @Composable
    fun PermissionScreen(onAllPermissionsGranted: () -> Unit, onMicDenied: () -> Unit)

    /** Màn hướng dẫn khi micro bị từ chối. */
    @Composable
    fun MicDeniedScreen(onRetry: () -> Unit)

    // ---------------- QR ----------------

    /** Khung camera quét QR. */
    @Composable
    fun QrCameraScanner(
        onQrDetected: (String) -> Unit,
        onError: (String) -> Unit,
        torchEnabled: Boolean,
        onTorchAvailable: (Boolean) -> Unit,
        modifier: Modifier
    )

    /** Ảnh mã QR của [content]. */
    @Composable
    fun QrCodeImage(content: String, size: Dp, modifier: Modifier, contentDescription: String?)

    // ---------------- Hệ thống ----------------

    /** Mở bảng chia sẻ của hệ thống với nội dung [text]. */
    fun shareText(text: String, title: String)

    /** Giữ màn hình sáng khi composable này đang hiển thị (chế độ bỏ túi). */
    @Composable
    fun KeepScreenOn()

    // ---------------- Phím cứng PTT ----------------

    /** Nền tảng cho dùng phím âm lượng làm PTT không (Android: có qua Accessibility; iOS: không). */
    val supportsVolumeKeyPtt: Boolean

    /** Dịch vụ trợ năng bắt phím âm lượng đang bật (luôn false nếu không hỗ trợ). */
    val volumeKeyServiceEnabled: StateFlow<Boolean>
    fun refreshVolumeKeyService()
    fun openVolumeKeyServiceSettings()

    /** Màn kiểm tra phím PTT / log thực địa (chỉ dùng khi chẩn đoán). */
    @Composable
    fun PttKeyDemoScreen(onBack: () -> Unit)
}

val LocalPlatformUi = staticCompositionLocalOf<PlatformUi> {
    error("Chưa cung cấp PlatformUi: bọc giao diện trong App(platformUi = ...)")
}
