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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.flow.collect
import com.example.damthoai.data.AppStartPrefs
import com.example.damthoai.hardware.PttKeyDemoScreen
import com.example.damthoai.hardware.emergency.EmergencyAlertManager
import com.example.damthoai.hardware.emergency.EmergencyAlert
import com.example.damthoai.data.fake.FakeRemainingUiData
import com.example.damthoai.data.fake.FakeEventRepository
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
    var channelMenuMuted by rememberSaveable { mutableStateOf(false) }
    var selectedEmergencyMemberIds by remember {
        mutableStateOf<Set<String>?>(null)
    }
    var activeEmergencyAlert by remember {
        mutableStateOf<EmergencyAlert?>(null)
    }
    var selectedQrChannelId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(navController) {
        var previousAlert: EmergencyAlert? = null
        EmergencyAlertManager.currentAlert.collect { alert ->
            if (
                alert != null &&
                previousAlert == null &&
                navController.currentDestination?.route != AppRoute.EmergencyAlertIncoming.route
            ) {
                navController.navigate(AppRoute.EmergencyAlertIncoming.route) {
                    launchSingleTop = true
                }
            }
            previousAlert = alert
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
                onFinishOnboarding = {
                    AppStartPrefs.setOnboardingDone(context, true)
                    navController.navigate(AppRoute.GrantPermission.route)
                }
            )
        }
        composable(AppRoute.GrantPermission.route) {
            PermissionScreen(
                onAllPermissionsGranted = {
                    val destination = if (AppStartPrefs.hasJoinedEvent(context)) {
                        AppRoute.ChannelList.route
                    } else {
                        AppRoute.ScanQr.route
                    }
                    navController.navigate(destination) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onMicDenied = { navController.navigate(AppRoute.MicrophonePermission.route) }
            )
        }
        composable(AppRoute.MicrophonePermission.route) {
            MicDeniedScreen(
                onRetry = {
                    if (!navController.popBackStack()) {
                        val destination = if (AppStartPrefs.hasJoinedEvent(context)) {
                            AppRoute.ChannelList.route
                        } else {
                            AppRoute.ScanQr.route
                        }
                        navController.navigate(destination) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                }
            )
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
                    AppStartPrefs.setHasJoinedEvent(context, true)
                    navController.navigate(AppRoute.ChannelList.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
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
                isMuted = channelMenuMuted,
                onDismiss = { navController.popBackStack() },
                onToggleMute = { channelMenuMuted = it },
                onLeaveChannel = {
                    navController.navigate(AppRoute.ScanQr.route) {
                        popUpTo(AppRoute.ChannelMenuSheet.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
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
                onCall = { selectedIds ->
                    selectedEmergencyMemberIds = selectedIds
                    activeEmergencyAlert = null
                    navController.navigate(AppRoute.EmergencyActiveCall.route)
                }
            )
        }
        composable(AppRoute.EmergencyAlertIncoming.route) {
            EmergencyAlertIncomingScreen(
                onAccept = {
                    selectedEmergencyMemberIds = null
                    activeEmergencyAlert = EmergencyAlertManager.currentAlert.value
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
            EmergencyActiveCallScreen(
                onEndCall = {
                    selectedEmergencyMemberIds = null
                    activeEmergencyAlert = null
                    navController.popBackStack()
                },
                selectedMemberIds = selectedEmergencyMemberIds,
                caller = activeEmergencyAlert
            )
        }

        // === NHÓM F: CÀI ĐẶT ===
        composable(AppRoute.Settings.route) {
            SettingsScreen(
                onProfile = { navController.navigate(AppRoute.UserProfile.route) },
                onManageEvent = { navController.navigate(AppRoute.ManageEvent.route) },
                onLeaveEvent = {
                    AppStartPrefs.setHasJoinedEvent(context, false)
                    navController.navigate(AppRoute.ScanQr.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                onMultiLevel = { navController.navigate(AppRoute.MultiLevelConnection.route) },
                onHandsFree = { navController.navigate(AppRoute.HandsFreeAudio.route) },
                onPttDebug = { navController.navigate(AppRoute.PttDebug.route) },
                onReviewOnboarding = {
                    AppStartPrefs.setOnboardingDone(context, false)
                    navController.navigate(AppRoute.Onboarding.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
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
                    navController.navigate(AppRoute.ManageEvent.route) {
                        popUpTo(AppRoute.CreateEvent.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToRestore = {
                    navController.navigate(AppRoute.RestoreEvent.route)
                }
            )
        }
        composable(AppRoute.ManageEvent.route) {
            ManageEventScreen(
                onBack = { navController.popBackStack() },
                onQrCode = {
                    selectedQrChannelId = FakeEventRepository.currentEvent.value
                        ?.channels
                        ?.firstOrNull { it.isDefault }
                        ?.id
                    navController.navigate(AppRoute.MyQrCode.route)
                },
                onChannelQrCode = { channelId ->
                    selectedQrChannelId = channelId
                    navController.navigate(AppRoute.MyQrCode.route)
                },
                onMembers = { navController.navigate(AppRoute.EventMembers.route) },
                onLiveCue = { navController.navigate(AppRoute.LiveCueDraft.route) },
                onEndEvent = {
                    AppStartPrefs.setHasJoinedEvent(context, false)
                    navController.navigate(AppRoute.EventEnded.route)
                }
            )
        }
        composable(AppRoute.RestoreEvent.route) {
            RestoreEventScreen(
                onBack = { navController.popBackStack() },
                onRestore = {
                    navController.navigate(AppRoute.ManageEvent.route) {
                        popUpTo(AppRoute.RestoreEvent.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(AppRoute.MyQrCode.route) {
            val event = FakeEventRepository.currentEvent.value ?: FakeRemainingUiData.event
            val channel = FakeEventRepository.findChannel(selectedQrChannelId)
                ?: event.channels.firstOrNull { it.isDefault }
            MyQrCodeScreen(
                onBack = { navController.popBackStack() },
                event = event,
                channel = channel,
                onShare = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "${event.name}\n${channel?.name ?: "Sự kiện"}\n" +
                                "Mã PIN: ${channel?.pin ?: event.pinCode}\n" +
                                "QR: ${channel?.qrPayload.orEmpty()}"
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
            LaunchedEffect(Unit) {
                AppStartPrefs.setHasJoinedEvent(context, false)
            }
            EventEndedScreen(
                onReturn = {
                    AppStartPrefs.setHasJoinedEvent(context, false)
                    navController.navigate(AppRoute.ScanQr.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(AppRoute.RemovedFromEvent.route) {
            LaunchedEffect(Unit) {
                AppStartPrefs.setHasJoinedEvent(context, false)
            }
            RemovedFromEventScreen(
                onReturn = {
                    AppStartPrefs.setHasJoinedEvent(context, false)
                    navController.navigate(AppRoute.ScanQr.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
