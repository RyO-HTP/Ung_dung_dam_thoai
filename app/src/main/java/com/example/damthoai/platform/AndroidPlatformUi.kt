package com.example.damthoai.platform

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.example.damthoai.hardware.AccessibilityStatus
import com.example.damthoai.permission.AppPermission
import com.example.damthoai.permission.PermissionManager
import com.example.damthoai.permission.PermissionState
import com.example.damthoai.permission.getManifestPermissions
import com.example.damthoai.ui.components.AndroidQrCodeImage
import com.example.damthoai.ui.join.AndroidQrCameraScanner
import kotlinx.coroutines.flow.StateFlow
import com.example.damthoai.hardware.PttKeyDemoScreen as AndroidPttKeyDemoScreen
import com.example.damthoai.permission.MicDeniedScreen as AndroidMicDeniedScreen
import com.example.damthoai.permission.PermissionScreen as AndroidPermissionScreen

/**
 * Android: giao diện / thao tác hệ thống cho phần UI dùng chung.
 * Dùng lại các thành phần Android có sẵn (PermissionManager, CameraX + ML Kit, ZXing, Accessibility...).
 */
class AndroidPlatformUi(private val activity: Activity) : PlatformUi {

    override fun checkPermission(permission: AppPermission): PermissionState =
        PermissionManager.checkPermissionState(activity, permission, activity)

    @Composable
    override fun rememberPermissionRequester(
        permission: AppPermission,
        onResult: (PermissionState) -> Unit
    ): () -> Unit {
        val latestOnResult = rememberUpdatedState(onResult)
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            latestOnResult.value(checkPermission(permission))
        }
        return remember(permission, launcher) {
            {
                PermissionManager.markRequested(activity, permission)
                launcher.launch(manifestPermissionsFor(permission))
            }
        }
    }

    /** Quét QR còn cần đọc ảnh trong thư viện (chọn ảnh QR có sẵn), giống luồng cũ. */
    private fun manifestPermissionsFor(permission: AppPermission): Array<String> =
        if (permission == AppPermission.CAMERA) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            permission.getManifestPermissions()
        }

    override fun openAppSettings() = PermissionManager.openAppSettings(activity)

    @Composable
    override fun PermissionScreen(onAllPermissionsGranted: () -> Unit, onMicDenied: () -> Unit) =
        AndroidPermissionScreen(onAllPermissionsGranted = onAllPermissionsGranted, onMicDenied = onMicDenied)

    @Composable
    override fun MicDeniedScreen(onRetry: () -> Unit) = AndroidMicDeniedScreen(onRetry = onRetry)

    @Composable
    override fun QrCameraScanner(
        onQrDetected: (String) -> Unit,
        onError: (String) -> Unit,
        torchEnabled: Boolean,
        onTorchAvailable: (Boolean) -> Unit,
        modifier: Modifier
    ) = AndroidQrCameraScanner(onQrDetected, onError, torchEnabled, onTorchAvailable, modifier)

    @Composable
    override fun QrCodeImage(content: String, size: Dp, modifier: Modifier, contentDescription: String?) =
        AndroidQrCodeImage(content, size, modifier, contentDescription)

    override fun shareText(text: String, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(shareIntent, title))
    }

    @Composable
    override fun KeepScreenOn() {
        val window = (LocalContext.current as? Activity)?.window
        DisposableEffect(window) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    override val supportsVolumeKeyPtt: Boolean = true
    override val volumeKeyServiceEnabled: StateFlow<Boolean> = AccessibilityStatus.isServiceEnabled
    override fun refreshVolumeKeyService() = AccessibilityStatus.refresh(activity)
    override fun openVolumeKeyServiceSettings() = AccessibilityStatus.openSettings(activity)

    @Composable
    override fun PttKeyDemoScreen(onBack: () -> Unit) = AndroidPttKeyDemoScreen(onBack = onBack)
}
