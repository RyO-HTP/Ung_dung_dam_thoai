package com.example.damthoai.platform

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.damthoai.BuildConfig
import com.example.damthoai.hardware.HandsFreeSession
import com.example.damthoai.hardware.HandsFreeSettings
import com.example.damthoai.hardware.PttController
import com.example.damthoai.hardware.audio.AndroidAudioRouteGuard
import com.example.damthoai.hardware.audio.AudioRouteGuard
import com.example.damthoai.hardware.audio.WebRTCAudioRouteController
import com.example.damthoai.hardware.diagnostics.FieldTestLogger
import com.example.damthoai.hardware.emergency.AndroidEmergencyOutputs
import com.example.damthoai.hardware.emergency.EmergencyAlertManager
import com.example.damthoai.network.AndroidLanProbe
import com.example.damthoai.network.MdnsDiscoveryManager
import com.example.damthoai.network.NetworkChangeReceiver
import com.example.damthoai.service.DamThoaiCallManager
import com.example.damthoai.service.DamThoaiCallService
import com.example.damthoai.service.ServerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.example.damthoai.webrtc.WebRTCClient

/**
 * Nối các dịch vụ Android vào phần code dùng chung (:shared). Gọi một lần, đầu MainActivity.onCreate.
 */
object AndroidPlatform {
    private var installed = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        AndroidAppContext.init(appContext)

        HandsFreeSettings.initialize()
        HandsFreeSession.initialize(appContext)
        AndroidAudioRouteGuard.start(appContext)

        val webRTCClient = WebRTCClient(appContext)
        PlatformServices.install(
            PlatformServices(
                rtc = webRTCClient,
                discovery = MdnsDiscoveryManager(appContext),
                lanProbe = AndroidLanProbe,
                networkMonitorFactory = { NetworkChangeReceiver(appContext) },
                callAudio = AndroidCallAudioSession(appContext),
                cloudSignalingUrl = BuildConfig.CLOUD_SIGNALING_URL,
                supportsVolumeKeyPtt = true,
            )
        )

        // ĐIỂM NỐI TV2/TV4 (Tuần 4): bộ điều khiển tuyến âm thanh WebRTC thực tế
        AndroidAudioRouteGuard.installController(WebRTCAudioRouteController(appContext, webRTCClient))
        AudioRouteGuard.backend = AndroidAudioRouteGuard
        EmergencyAlertManager.outputs = AndroidEmergencyOutputs(appContext)

        PttController.onCaptureActiveChanged = { active -> HandsFreeSession.onCaptureActiveChanged(active) }
        PttController.onPttEventLogged = { event -> FieldTestLogger.recordPttEvent(event) }

        // Phiên chạy nền: bật khi đang trong sự kiện (bộ giám sát kết nối đang chạy), tắt khi rời sự kiện.
        // Đổi trạng thái Connected <-> Reconnecting không bật/tắt lại service, chỉ cập nhật tên trên thông báo.
        val callManager = DamThoaiCallManager.getInstance()
        scope.launch {
            combine(callManager.serverStatus, callManager.eventName) { status, name ->
                (status !is ServerStatus.Idle) to name
            }
                .distinctUntilChanged()
                .collect { (inEvent, name) ->
                    if (inEvent) DamThoaiCallService.start(appContext, name) else DamThoaiCallService.stop(appContext)
                }
        }
    }
}

/** Android: chế độ âm thanh đàm thoại (MODE_IN_COMMUNICATION) và trả về mặc định khi kết thúc. */
private class AndroidCallAudioSession(context: Context) : CallAudioSession {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun start() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    override fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        Log.d("AndroidCallAudioSession", "Đã trả âm thanh về chế độ thường")
    }
}
