package com.example.damthoai

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.damthoai.data.AppStartPrefs
import com.example.damthoai.permission.AppPermission
import com.example.damthoai.permission.PermissionState
import com.example.damthoai.platform.LocalPlatformUi
import com.example.damthoai.platform.PlatformUi
import com.example.damthoai.ui.components.AppToastHost
import com.example.damthoai.ui.navigation.AdaptiveAppLayout
import com.example.damthoai.ui.navigation.AppRoute
import com.example.damthoai.ui.theme.BgGradientTop
import com.example.damthoai.ui.theme.DamThoaiTheme

/** Màn hình khởi đầu theo trạng thái giới thiệu, quyền micro và sự kiện đang tham gia. */
fun resolveStartDestination(platformUi: PlatformUi): String {
    val microphonePermissionState = platformUi.checkPermission(AppPermission.AUDIO)
    return when {
        !AppStartPrefs.isOnboardingDone() -> AppRoute.Onboarding.route
        microphonePermissionState == PermissionState.PERMANENTLY_DENIED -> AppRoute.MicrophonePermission.route
        microphonePermissionState != PermissionState.GRANTED -> AppRoute.GrantPermission.route
        AppStartPrefs.hasJoinedEvent() -> AppRoute.ChannelList.route
        else -> AppRoute.ScanQr.route
    }
}

/**
 * Gốc giao diện dùng chung cho Android (MainActivity) và iOS (MainViewController).
 */
@Composable
fun App(
    platformUi: PlatformUi,
    startDestination: String = resolveStartDestination(platformUi)
) {
    CompositionLocalProvider(LocalPlatformUi provides platformUi) {
        DamThoaiTheme {
            Surface(modifier = Modifier.fillMaxSize(), color = BgGradientTop) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    AdaptiveAppLayout(
                        isCompact = maxWidth < 600.dp,
                        startDestination = startDestination
                    )
                    AppToastHost()
                }
            }
        }
    }
}
