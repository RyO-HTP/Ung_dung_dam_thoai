package com.example.damthoai.ios

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.example.damthoai.permission.AppPermission
import com.example.damthoai.permission.PermissionState
import com.example.damthoai.platform.PlatformUi
import com.example.damthoai.ui.components.AppToast
import com.example.damthoai.ui.permission.GrantPermissionScreen
import com.example.damthoai.ui.permission.MicrophonePermissionScreen
import com.example.damthoai.ui.permission.PermissionUiState
import com.example.damthoai.ui.theme.BgGradientTop
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIDevice
import platform.UIKit.UIView
import platform.UIKit.popoverPresentationController
import platform.UserNotifications.*
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS: giao diện / thao tác hệ thống cho phần UI dùng chung.
 */
@OptIn(ExperimentalForeignApi::class)
class IosPlatformUi(private val bridge: IosNativeBridge) : PlatformUi {

    // iOS chỉ trả trạng thái thông báo qua callback: giữ bản sao gần nhất
    private var notificationState = PermissionState.DENIED

    init {
        refreshNotificationState()
    }

    private fun refreshNotificationState() {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            notificationState = when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional,
                UNAuthorizationStatusEphemeral -> PermissionState.GRANTED
                UNAuthorizationStatusDenied -> PermissionState.PERMANENTLY_DENIED
                else -> PermissionState.DENIED
            }
        }
    }

    // ---------------- Quyền ----------------

    override fun checkPermission(permission: AppPermission): PermissionState = when (permission) {
        AppPermission.AUDIO -> when (AVAudioSession.sharedInstance().recordPermission) {
            AVAudioSessionRecordPermissionGranted -> PermissionState.GRANTED
            // iOS không hỏi lại sau khi người dùng từ chối: phải mở Cài đặt
            AVAudioSessionRecordPermissionDenied -> PermissionState.PERMANENTLY_DENIED
            else -> PermissionState.DENIED
        }
        AppPermission.CAMERA -> when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> PermissionState.GRANTED
            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> PermissionState.PERMANENTLY_DENIED
            else -> PermissionState.DENIED
        }
        AppPermission.NOTIFICATION -> notificationState
        // Bluetooth, chạy nền, mạng: iOS không có quyền runtime riêng cho các nhóm này
        AppPermission.BLUETOOTH, AppPermission.BACKGROUND, AppPermission.NETWORK -> PermissionState.GRANTED
    }

    private fun request(permission: AppPermission, onDone: () -> Unit) {
        val done: () -> Unit = { dispatch_async(dispatch_get_main_queue()) { onDone() } }
        when (permission) {
            AppPermission.AUDIO ->
                AVAudioSession.sharedInstance().requestRecordPermission { _ -> done() }
            AppPermission.CAMERA ->
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ -> done() }
            AppPermission.NOTIFICATION ->
                UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                    UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
                ) { _, _ ->
                    refreshNotificationState()
                    done()
                }
            else -> done()
        }
    }

    @Composable
    override fun rememberPermissionRequester(
        permission: AppPermission,
        onResult: (PermissionState) -> Unit
    ): () -> Unit {
        val latestOnResult = rememberUpdatedState(onResult)
        return remember(permission) {
            { request(permission) { latestOnResult.value(checkPermission(permission)) } }
        }
    }

    override fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    @Composable
    override fun PermissionScreen(onAllPermissionsGranted: () -> Unit, onMicDenied: () -> Unit) {
        GrantPermissionScreen(
            uiState = PermissionUiState(
                isMicrophoneGranted = checkPermission(AppPermission.AUDIO) == PermissionState.GRANTED,
                isNotificationGranted = checkPermission(AppPermission.NOTIFICATION) == PermissionState.GRANTED,
                isBluetoothGranted = true,
                isBackgroundGranted = true,
                isCameraGranted = checkPermission(AppPermission.CAMERA) == PermissionState.GRANTED
            ),
            onBack = {},
            onContinue = {
                // Micro là bắt buộc; thông báo (cảnh báo khẩn cấp) xin luôn nhưng không bắt buộc
                request(AppPermission.AUDIO) {
                    if (checkPermission(AppPermission.AUDIO) == PermissionState.GRANTED) {
                        request(AppPermission.NOTIFICATION) { onAllPermissionsGranted() }
                    } else {
                        onMicDenied()
                    }
                }
            }
        )
    }

    @Composable
    override fun MicDeniedScreen(onRetry: () -> Unit) {
        MicrophonePermissionScreen(
            onOpenSettings = { openAppSettings() },
            onRetry = {
                if (checkPermission(AppPermission.AUDIO) == PermissionState.GRANTED) {
                    onRetry()
                } else {
                    AppToast.show("Vui lòng bật Micro cho Đàm Thoại trong Cài đặt")
                }
            }
        )
    }

    // ---------------- QR ----------------

    @Composable
    override fun QrCameraScanner(
        onQrDetected: (String) -> Unit,
        onError: (String) -> Unit,
        torchEnabled: Boolean,
        onTorchAvailable: (Boolean) -> Unit,
        modifier: Modifier
    ) {
        val latestOnQr = rememberUpdatedState(onQrDetected)
        val latestOnError = rememberUpdatedState(onError)
        val listener = remember {
            object : QrCodeListener {
                override fun onQrDetected(code: String) = latestOnQr.value(code)
                override fun onError(message: String) = latestOnError.value(message)
            }
        }
        DisposableEffect(Unit) {
            onTorchAvailable(false)
            onDispose { }
        }
        UIKitView<UIView>(
            factory = { bridge.createQrScannerView(listener) },
            modifier = modifier,
            onRelease = { view -> bridge.releaseQrScannerView(view) }
        )
    }

    @Composable
    override fun QrCodeImage(content: String, size: Dp, modifier: Modifier, contentDescription: String?) {
        Image(
            painter = rememberQrCodePainter(content),
            contentDescription = contentDescription,
            modifier = modifier.size(size).background(Color.White).padding(8.dp)
        )
    }

    // ---------------- Hệ thống ----------------

    override fun shareText(text: String, title: String) {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        val controller = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        // iPad: bảng chia sẻ phải gắn vào một view nguồn
        controller.popoverPresentationController?.sourceView = root.view
        var presenter = root
        while (presenter.presentedViewController != null) presenter = presenter.presentedViewController!!
        presenter.presentViewController(controller, animated = true, completion = null)
    }

    @Composable
    override fun KeepScreenOn() {
        DisposableEffect(Unit) {
            UIApplication.sharedApplication.idleTimerDisabled = true
            // Chế độ bỏ túi: tắt màn hình khi áp vào người / cho vào túi (cảm biến tiệm cận)
            UIDevice.currentDevice.proximityMonitoringEnabled = true
            onDispose {
                UIApplication.sharedApplication.idleTimerDisabled = false
                UIDevice.currentDevice.proximityMonitoringEnabled = false
            }
        }
    }

    // ---------------- Phím cứng PTT ----------------

    override val supportsVolumeKeyPtt: Boolean = false
    override val volumeKeyServiceEnabled: StateFlow<Boolean> = MutableStateFlow(false)
    override fun refreshVolumeKeyService() = Unit
    override fun openVolumeKeyServiceSettings() = Unit

    @Composable
    override fun PttKeyDemoScreen(onBack: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize().background(BgGradientTop).statusBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            TextButton(onClick = onBack) { Text("‹ Quay lại", color = TextPrimary) }
            Text("Kiểm tra phím PTT", color = TextPrimary)
            Text(
                "iOS không cho ứng dụng dùng phím âm lượng làm nút PTT. " +
                    "Hãy dùng nút PTT trên màn hình, nút Play/Pause của tai nghe, " +
                    "hoặc nút nói của hệ thống (PushToTalk) trên màn hình khoá.",
                color = TextSecondary
            )
        }
    }
}
