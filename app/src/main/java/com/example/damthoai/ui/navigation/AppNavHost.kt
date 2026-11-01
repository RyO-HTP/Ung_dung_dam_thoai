/**
 * Path: app/src/main/java/com/example/damthoai/ui/navigation/AppNavHost.kt
 *
 * Khối KDoc tiếng Việt:
 * Quản lý Sơ đồ Điều hướng (Navigation Host) chính cho ứng dụng Đàm Thoại.
 * Tích hợp các Màn hình thật: Onboarding, GrantPermission (PermissionScreen),
 * JoinChannelScreen (Quét QR / Nhập PIN), UserInfoScreen, và ChannelListScreen.
 */
package com.example.damthoai.ui.navigation

import android.content.Intent
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.flow.collect
import com.example.damthoai.hardware.PttKeyDemoScreen
import com.example.damthoai.hardware.emergency.EmergencyAlertManager
import com.example.damthoai.data.fake.FakeRemainingUiData
import com.example.damthoai.permission.PermissionScreen
import com.example.damthoai.ui.channels.ChannelListScreen
import com.example.damthoai.permission.MicDeniedScreen
import com.example.damthoai.ui.join.JoinChannelScreen
import com.example.damthoai.ui.join.JoinErrorScreen
import com.example.damthoai.ui.join.UserInfoScreen
import com.example.damthoai.ui.event.CreateEventScreen
import com.example.damthoai.ui.event.RestoreEventScreen
import com.example.damthoai.ui.onboarding.OnboardingScreen
import com.example.damthoai.ui.screens.ActiveCallScreen
import com.example.damthoai.ui.screens.EmergencyActiveCallScreen
import com.example.damthoai.ui.screens.EmergencyCallSelectScreen
import com.example.damthoai.ui.screens.EmergencyAlertIncomingScreen
import com.example.damthoai.ui.screens.PocketModeScreen
import com.example.damthoai.ui.screens.EventMembersScreen
import com.example.damthoai.ui.screens.ChannelMenuRouteScreen
import com.example.damthoai.ui.screens.EventEndedScreen
import com.example.damthoai.ui.screens.HandsFreeAudioScreen
import com.example.damthoai.ui.screens.LiveCueDraftScreen
import com.example.damthoai.ui.screens.ManageEventScreen
import com.example.damthoai.ui.screens.MultiLevelConnectionScreen
import com.example.damthoai.ui.screens.MyQrCodeScreen
import com.example.damthoai.ui.screens.RemovedFromEventScreen
import com.example.damthoai.ui.screens.SettingsScreen
import com.example.damthoai.ui.screens.UserProfileScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = AppRoute.Onboarding.route
) {
    val context = LocalContext.current

    LaunchedEffect(navController) {
        EmergencyAlertManager.currentAlert.collect { alert ->
            if (
                alert != null &&
                navController.currentDestination?.route != AppRoute.EmergencyAlertIncoming.route
            ) {
                navController.navigate(AppRoute.EmergencyAlertIncoming.route) {
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // === NHÓM A: ONBOARDING & QUYỀN ===
        composable(AppRoute.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = { navController.navigate(AppRoute.GrantPermission.route) }
            )
        }
        composable(AppRoute.GrantPermission.route) {
            PermissionScreen(
                onAllPermissionsGranted = {
                    navController.navigate(AppRoute.ScanQr.route)
                },
                onMicDenied = { navController.navigate(AppRoute.MicrophonePermission.route) }
            )
        }
        composable(AppRoute.MicrophonePermission.route) {
            MicDeniedScreen(onRetry = { navController.popBackStack() })
        }

        // === NHÓM B: VÀO KÊNH ===
        composable(AppRoute.ScanQr.route) {
            JoinChannelScreen(
                onBack = { navController.popBackStack() },
                onSuccessJoin = { navController.navigate(AppRoute.UserInfo.route) },
                onNavigateToCreateEvent = { navController.navigate(AppRoute.CreateEvent.route) },
                onJoinError = { navController.navigate(AppRoute.JoinError.route) }
            )
        }
        composable(AppRoute.EnterPin.route) {
            JoinChannelScreen(
                onBack = { navController.popBackStack() },
                onSuccessJoin = { navController.navigate(AppRoute.UserInfo.route) },
                onNavigateToCreateEvent = { navController.navigate(AppRoute.CreateEvent.route) },
                onJoinError = { navController.navigate(AppRoute.JoinError.route) },
                initialTab = 1
            )
        }
        composable(AppRoute.JoinError.route) {
            JoinErrorScreen(
                onRetryQr = {
                    navController.navigate(AppRoute.ScanQr.route) {
                        popUpTo(AppRoute.JoinError.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSwitchPin = {
                    navController.navigate(AppRoute.EnterPin.route) {
                        popUpTo(AppRoute.JoinError.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToCreateEvent = {
                    navController.navigate(AppRoute.CreateEvent.route)
                }
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

        // === NHÓM C: DANH SÁCH KÊNH ===
        composable(AppRoute.ChannelList.route) {
            ChannelListScreen(
                onNavigateToActiveCall = { navController.navigate(AppRoute.ActiveCall.route) },
                onNavigateToAddChannel = { navController.navigate(AppRoute.ScanQr.route) }
            )
        }
        composable(AppRoute.ChannelMenuSheet.route) {
            ChannelMenuRouteScreen(
                onDismiss = { navController.popBackStack() },
                onToggleMute = {},
                onLeaveChannel = { navController.popBackStack() }
            )
        }

        // === NHÓM D: MÀN ĐÀM THOẠI & BỎ TÚI ===
        composable(AppRoute.ActiveCall.route) {
            ActiveCallScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPocketMode = {
                    navController.navigate(AppRoute.PocketMode.route)
                },
                onNavigateToEmergency = {
                    navController.navigate(AppRoute.EmergencyCallSelect.route)
                }
            )
        }
        composable(AppRoute.PocketMode.route) {
            PocketModeScreen(
                onUnlock = { navController.popBackStack() }
            )
        }

        // === NHÓM E: KHẨN CẤP ===
        composable(AppRoute.EmergencyCallSelect.route) {
            EmergencyCallSelectScreen(
                onBack = { navController.popBackStack() },
                onCall = {
                    navController.navigate(AppRoute.EmergencyActiveCall.route)
                }
            )
        }
        composable(AppRoute.EmergencyAlertIncoming.route) {
            EmergencyAlertIncomingScreen(
                onAccept = {
                    EmergencyAlertManager.acknowledge()
                    navController.navigate(AppRoute.EmergencyActiveCall.route)
                },
                onDecline = {
                    EmergencyAlertManager.snooze()
                    navController.popBackStack()
                },
                alertFlow = EmergencyAlertManager.currentAlert,
                isVibratingFlow = EmergencyAlertManager.isVibrating,
                isFlashingFlow = EmergencyAlertManager.isFlashing
            )
        }
        composable(AppRoute.EmergencyActiveCall.route) {
            EmergencyActiveCallScreen(onEndCall = { navController.popBackStack() })
        }

        // === NHÓM F: CÀI ĐẶT ===
        composable(AppRoute.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onProfile = { navController.navigate(AppRoute.UserProfile.route) },
                onManageEvent = { navController.navigate(AppRoute.ManageEvent.route) },
                onLeaveEvent = { navController.navigate(AppRoute.RemovedFromEvent.route) },
                onMultiLevel = { navController.navigate(AppRoute.MultiLevelConnection.route) },
                onHandsFree = { navController.navigate(AppRoute.HandsFreeAudio.route) },
                onPttDebug = { navController.navigate(AppRoute.PttDebug.route) }
            )
        }
        composable(AppRoute.MultiLevelConnection.route) {
            MultiLevelConnectionScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoute.HandsFreeAudio.route) {
            HandsFreeAudioScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.PttDebug.route) {
            PttKeyDemoScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.UserProfile.route) {
            UserProfileScreen(
                onBack = { navController.popBackStack() },
                onSave = { navController.popBackStack() }
            )
        }

        // === NHÓM G: QUẢN TRỊ SỰ KIỆN (CẤP 1) ===
        composable(AppRoute.CreateEvent.route) {
            CreateEventScreen(
                onBack = { navController.popBackStack() },
                onEventCreated = {
                    navController.navigate(AppRoute.ManageEvent.route)
                },
                onNavigateToRestore = {
                    navController.navigate(AppRoute.RestoreEvent.route)
                }
            )
        }
        composable(AppRoute.ManageEvent.route) {
            ManageEventScreen(
                onBack = { navController.popBackStack() },
                onQrCode = { navController.navigate(AppRoute.MyQrCode.route) },
                onMembers = { navController.navigate(AppRoute.EventMembers.route) },
                onLiveCue = { navController.navigate(AppRoute.LiveCueDraft.route) },
                onEndEvent = { navController.navigate(AppRoute.EventEnded.route) }
            )
        }
        composable(AppRoute.RestoreEvent.route) {
            RestoreEventScreen(
                onBack = { navController.popBackStack() },
                onRestore = {
                    navController.navigate(AppRoute.ManageEvent.route)
                }
            )
        }
        composable(AppRoute.MyQrCode.route) {
            MyQrCodeScreen(
                onBack = { navController.popBackStack() },
                onShare = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "${FakeRemainingUiData.event.name}\nMã PIN: ${FakeRemainingUiData.event.pinCode}"
                        )
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Chia sẻ mã sự kiện")
                    )
                }
            )
        }
        composable(AppRoute.EventMembers.route) {
            EventMembersScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoute.LiveCueDraft.route) {
            LiveCueDraftScreen(
                onBack = { navController.popBackStack() },
                onSendCue = { cue ->
                    FakeRemainingUiData.sendLiveCue(cue)
                    navController.popBackStack()
                }
            )
        }
        composable(AppRoute.EventEnded.route) {
            EventEndedScreen(
                onReturn = {
                    navController.navigate(AppRoute.ChannelList.route) {
                        popUpTo(AppRoute.EventEnded.route) { inclusive = true }
                    }
                }
            )
        }
        composable(AppRoute.RemovedFromEvent.route) {
            RemovedFromEventScreen(
                onReturn = {
                    navController.navigate(AppRoute.ScanQr.route) {
                        popUpTo(AppRoute.RemovedFromEvent.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
