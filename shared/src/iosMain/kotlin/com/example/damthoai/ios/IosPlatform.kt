package com.example.damthoai.ios

import com.example.damthoai.data.AppStartPrefs
import com.example.damthoai.hardware.HandsFreeSettings
import com.example.damthoai.hardware.PttController
import com.example.damthoai.hardware.audio.AudioRouteGuard
import com.example.damthoai.hardware.emergency.EmergencyAlertManager
import com.example.damthoai.platform.PlatformServices
import com.example.damthoai.service.DamThoaiCallManager
import com.example.damthoai.service.ServerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Nối dịch vụ iOS vào phần code dùng chung (tương đương AndroidPlatform bên :app).
 */
object IosPlatform {
    /** Máy chủ Signaling trên đám mây (Render), giống giá trị mặc định của bản Android. */
    const val CLOUD_SIGNALING_URL = "wss://ung-dung-dam-thoai.onrender.com"

    private var installed = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun install(bridge: IosNativeBridge) {
        if (installed) return
        installed = true

        HandsFreeSettings.initialize()
        val audio = IosAudioSession()
        PlatformServices.install(
            PlatformServices(
                rtc = IosRtcEngine(bridge.rtcBackend),
                discovery = IosServerDiscovery(bridge),
                lanProbe = IosLanProbe,
                networkMonitorFactory = { IosNetworkMonitor() },
                callAudio = audio,
                cloudSignalingUrl = CLOUD_SIGNALING_URL,
                supportsVolumeKeyPtt = false,
            )
        )
        AudioRouteGuard.backend = audio
        EmergencyAlertManager.outputs = IosEmergencyOutputs()

        // Nút Play/Pause tai nghe chỉ được bắt trong phiên đàm thoại (giống MediaSession bên Android)
        PttController.onCaptureActiveChanged = { active ->
            bridge.setHeadsetRemoteEnabled(active && HandsFreeSettings.headsetButtonEnabled.value)
        }

        // Kênh PushToTalk của hệ thống đi theo trạng thái kết nối sự kiện
        val callManager = DamThoaiCallManager.getInstance()
        scope.launch {
            combine(callManager.serverStatus, callManager.eventName) { status, name -> status to name }
                .distinctUntilChanged()
                .collect { (status, name) ->
                    when {
                        status is ServerStatus.Connected && AppStartPrefs.hasJoinedEvent() -> bridge.pushToTalkJoin(name)
                        status is ServerStatus.Idle -> bridge.pushToTalkLeave()
                        else -> Unit // Đang nối lại: giữ kênh để không làm phiền người dùng
                    }
                }
        }
    }
}
