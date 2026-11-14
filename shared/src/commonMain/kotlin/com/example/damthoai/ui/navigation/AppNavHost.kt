/**
 * Path: app/src/main/java/com/example/damthoai/ui/navigation/AppNavHost.kt
 * Quản lý Sơ đồ Điều hướng (Navigation Host) chính cho ứng dụng Đàm Thoại.
 */
package com.example.damthoai.ui.navigation

import com.example.damthoai.ui.components.AppToast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.damthoai.data.AppStartPrefs
import com.example.damthoai.data.LiveCue
import com.example.damthoai.hardware.emergency.EmergencyAlert
import com.example.damthoai.hardware.emergency.EmergencyAlertManager
import com.example.damthoai.platform.LocalPlatformUi
import com.example.damthoai.ui.channels.ChannelListScreen
import com.example.damthoai.ui.event.CreateEventScreen
import com.example.damthoai.ui.event.RestoreEventScreen
import com.example.damthoai.ui.join.JoinChannelScreen
import com.example.damthoai.ui.join.JoinErrorScreen
import com.example.damthoai.ui.join.UserInfoScreen
import com.example.damthoai.ui.livecue.LiveCueViewModel
import com.example.damthoai.ui.onboarding.OnboardingScreen
import com.example.damthoai.ui.screens.ActiveCallScreen
import com.example.damthoai.ui.screens.ChannelMenuRouteScreen
import com.example.damthoai.ui.screens.EmergencyActiveCallScreen
import com.example.damthoai.ui.screens.EmergencyCallParticipant
import com.example.damthoai.ui.screens.EmergencyCallSelectScreen
import com.example.damthoai.ui.event.toUiMember
import com.example.damthoai.service.DamThoaiCallManager
import com.example.damthoai.ui.screens.EmergencyAlertIncomingScreen
import com.example.damthoai.ui.screens.EventEndedScreen
import com.example.damthoai.ui.screens.EventMembersScreen
import com.example.damthoai.ui.screens.HandsFreeAudioScreen
import com.example.damthoai.ui.screens.LiveCueDraftScreen
import com.example.damthoai.ui.screens.ManageEventScreen
import com.example.damthoai.ui.screens.MultiLevelConnectionScreen
import com.example.damthoai.ui.screens.MyQrCodeScreen
import com.example.damthoai.ui.screens.PocketModeScreen
import com.example.damthoai.ui.screens.RemovedFromEventScreen
import com.example.damthoai.ui.screens.SettingsScreen
import com.example.damthoai.ui.screens.UserProfileScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = AppRoute.Onboarding.route
) {
    val platformUi = LocalPlatformUi.current
    // ViewModel kênh dùng chung (phạm vi Activity) giữa màn Quản lý sự kiện và Danh sách kênh,
    // để kênh thêm/đổi tên/xóa ở màn quản trị hiển thị ngay ở danh sách kênh.
    val sharedChannelViewModel: com.example.damthoai.ui.channels.ChannelViewModel = viewModel { com.example.damthoai.ui.channels.ChannelViewModel() }
    val callManager = remember { DamThoaiCallManager.getInstance() }
    val onlineMembers by callManager.onlineMembers.collectAsState()
    val currentEventName by callManager.eventName.collectAsState()
    val myUserId = remember { AppStartPrefs.getUserId() }
    // Sự kiện hiện tại kèm danh sách thành viên online thật từ server. Các màn dùng chung giá trị này
    // để số thành viên luôn khớp với trang Thành viên. Chỉ tính lại khi danh sách hoặc tên sự kiện đổi.
    val currentEvent = remember(onlineMembers, currentEventName) {
        val pin = AppStartPrefs.getEventPin()
        com.example.damthoai.data.Event(
            id = pin,
            name = currentEventName,
            pinCode = pin,
            members = onlineMembers.map { it.toUiMember(myUserId) },
            ownerId = onlineMembers.firstOrNull { it.isOrganizer }?.userId.orEmpty(),
            isOwner = AppStartPrefs.isOrganizer()
        )
    }
    var channelMenuMuted by rememberSaveable { mutableStateOf(false) }
    var selectedEmergencyMemberIds by remember { mutableStateOf<Set<String>?>(null) }
    var activeEmergencyAlert by remember { mutableStateOf<EmergencyAlert?>(null) }
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
                    AppStartPrefs.setOnboardingDone(true)
                    navController.navigate(AppRoute.GrantPermission.route)
                }
            )
        }
        composable(AppRoute.GrantPermission.route) {
            platformUi.PermissionScreen(
                onAllPermissionsGranted = {
                    val destination = if (AppStartPrefs.hasJoinedEvent()) {
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
            platformUi.MicDeniedScreen(
                onRetry = {
                    if (!navController.popBackStack()) {
                        val destination = if (AppStartPrefs.hasJoinedEvent()) {
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
                onEnterChannel = { displayName, roleTitle ->
                    // Lưu tên/vai trò và gửi lên server để danh sách thành viên hiển thị đúng
                    callManager.updateProfile(displayName, roleTitle)
                    AppStartPrefs.setHasJoinedEvent(true)
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
                onNavigateToAddChannel = { navController.navigate(AppRoute.ScanQr.route) },
                viewModel = sharedChannelViewModel
            )
        }
        composable(AppRoute.ChannelMenuSheet.route) {
            ChannelMenuRouteScreen(
                isMuted = channelMenuMuted,
                onDismiss = { navController.popBackStack() },
                onToggleMute = { muted: Boolean -> channelMenuMuted = muted },
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
            val channelUiState by sharedChannelViewModel.uiState.collectAsState()
            ActiveCallScreen(
                channels = channelUiState.channels,
                activeChannelId = channelUiState.activeMicChannelId,
                onChannelVolumeChange = { id, volume -> sharedChannelViewModel.updateChannelVolume(id, volume) },
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

        // === NHÓM E: KHẨN CẤP & LIVE CUE ===
        composable(AppRoute.LiveCueDraft.route) {
            val liveCueViewModel: LiveCueViewModel = viewModel { LiveCueViewModel() }
            LiveCueDraftScreen(
                onBack = { navController.popBackStack() },
                onSendCue = { cue: LiveCue ->
                    liveCueViewModel.sendLiveCue(cueContent = cue.message)
                    AppToast.show("Đã gửi LiveCue thành công")
                    navController.popBackStack()
                }
            )
        }
        composable(AppRoute.EmergencyCallSelect.route) {
            if (!AppStartPrefs.isOrganizer()) {
                LaunchedEffect(Unit) {
                    AppToast.show("Chỉ Ban tổ chức (Cấp 1) mới được phát cảnh báo khẩn cấp.")
                    navController.popBackStack()
                }
            } else {
                EmergencyCallSelectScreen(
                    onBack = { navController.popBackStack() },
                    onCall = { selectedIds ->
                        selectedEmergencyMemberIds = selectedIds
                        activeEmergencyAlert = null
                        navController.navigate(AppRoute.EmergencyActiveCall.route)
                    },
                    recipients = onlineMembers
                        .filter { it.userId != myUserId }
                        .map { it.toUiMember(myUserId) }
                )
            }
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
                activeParticipants = onlineMembers
                    .filter { member ->
                        member.userId != myUserId &&
                            (selectedEmergencyMemberIds?.contains(member.userId) ?: true)
                    }
                    .map { EmergencyCallParticipant(member = it.toUiMember(myUserId), status = "Đang nghe") },
                caller = activeEmergencyAlert
            )
        }

        // === NHÓM F: CÀI ĐẶT ===
        composable(AppRoute.Settings.route) {
            SettingsScreen(
                onProfile = { navController.navigate(AppRoute.UserProfile.route) },
                onManageEvent = { navController.navigate(AppRoute.ManageEvent.route) },
                onLeaveEvent = {
                    callManager.leaveEvent()
                    navController.navigate(AppRoute.ScanQr.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                },
                onMultiLevel = { navController.navigate(AppRoute.MultiLevelConnection.route) },
                onHandsFree = { navController.navigate(AppRoute.HandsFreeAudio.route) }
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
            platformUi.PttKeyDemoScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.UserProfile.route) {
            UserProfileScreen(
                onBack = { navController.popBackStack() },
                onSave = { navController.popBackStack() }
            )
        }

        // === NHÓM G: QUẢN TRỊ SỰ KIỆN ===
        composable(AppRoute.CreateEvent.route) {
            CreateEventScreen(
                onBack = { navController.popBackStack() },
                onEventCreated = {
                    navController.navigate("${AppRoute.ManageEvent.route}?fromCreate=true") {
                        popUpTo(AppRoute.CreateEvent.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToRestore = {
                    navController.navigate(AppRoute.RestoreEvent.route)
                }
            )
        }
        composable(
            route = "${AppRoute.ManageEvent.route}?fromCreate={fromCreate}",
            arguments = listOf(androidx.navigation.navArgument(name = "fromCreate") { defaultValue = false })
        ) { backStackEntry ->
            val fromCreate = backStackEntry.savedStateHandle.get<Boolean>("fromCreate") ?: false
            val channelViewModel = sharedChannelViewModel
            val channelUiState by channelViewModel.uiState.collectAsState()
            ManageEventScreen(
                onBack = {
                    if (fromCreate) {
                        navController.navigate(AppRoute.ChannelList.route) {
                            popUpTo(AppRoute.ManageEvent.route) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onQrCode = { navController.navigate(AppRoute.MyQrCode.route) },
                onChannelQrCode = { _ -> navController.navigate(AppRoute.MyQrCode.route) },
                onMembers = { navController.navigate(AppRoute.EventMembers.route) },
                onLiveCue = { navController.navigate(AppRoute.LiveCueDraft.route) },
                onEndEvent = {
                    callManager.leaveEvent()
                    navController.navigate(AppRoute.EventEnded.route)
                },
                onNavigateToActiveCall = {
                    navController.navigate(AppRoute.ActiveCall.route)
                },
                channels = channelUiState.channels.map { it.channel },
                event = currentEvent,
                onAddChannel = { channelViewModel.addChannel(it) },
                onRenameChannel = { id, name -> channelViewModel.renameChannel(id, name) },
                onDeleteChannel = { channelViewModel.deleteChannel(it) }
            )
        }
        composable(AppRoute.RestoreEvent.route) {
            RestoreEventScreen(
                onBack = { navController.popBackStack() },
                onRestore = {
                    AppStartPrefs.setHasJoinedEvent(true)
                    navController.navigate(AppRoute.ChannelList.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }
        composable(AppRoute.MyQrCode.route) {
            MyQrCodeScreen(
                onBack = { navController.popBackStack() },
                onShare = {},
                event = currentEvent.copy(channels = sharedChannelViewModel.uiState.value.channels.map { it.channel })
            )
        }
        composable(AppRoute.EventMembers.route) {
            EventMembersScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoute.EventEnded.route) {
            EventEndedScreen(
                onReturn = {
                    AppStartPrefs.setHasJoinedEvent(false)
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
            RemovedFromEventScreen(
                onReturn = {
                    AppStartPrefs.setHasJoinedEvent(false)
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