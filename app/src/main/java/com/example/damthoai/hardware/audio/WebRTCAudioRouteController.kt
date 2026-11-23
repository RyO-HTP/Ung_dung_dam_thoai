package com.example.damthoai.hardware.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.damthoai.webrtc.WebRTCClient

/**
 * =================================================================================
 * BỘ ĐIỀU KHIỂN & BẢO VỆ TUYẾN ÂM THANH THỰC TẾ (WebRTCAudioRouteController)
 * VAI TRÒ: Thành viên 2 (Tuần 2 & Tuần 4)
 * =================================================================================
 *
 * Triển khai interface [AudioRouteController] của TV4, kết nối thao tác chuyển tuyến
 * âm thanh (Loa ngoài, Loa trong Earpiece, Tai nghe có dây, Bluetooth SCO/BLE) và khóa
 * âm thanh bảo mật khi ngắt tai nghe với Android [AudioManager] (API 24-34+) và [WebRTCClient].
 */
class WebRTCAudioRouteController(
    context: Context,
    private val webRTCClient: WebRTCClient? = null
) : AudioRouteController {

    companion object {
        private const val TAG = "WebRTCAudioRouteCtrl"
    }

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var activeRoute: AudioRoute = AudioRoute.EARPIECE

    override fun currentRoute(): AudioRoute = activeRoute

    /**
     * Khóa âm thanh đầu ra khi tai nghe bị rút đột ngột để chống lọt tiếng ra loa ngoài.
     */
    override fun muteOutput() {
        Log.d(TAG, "[AudioRouteGuard] muteOutput: Khóa âm thanh đầu ra để bảo mật")
        webRTCClient?.setOutputMuted(true)
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        }.onFailure { e ->
            Log.e(TAG, "Lỗi khi đặt mode communication lúc mute: ${e.message}", e)
        }
    }

    /**
     * Mở lại âm thanh đầu ra sau khi tuyến âm thanh an toàn được xác nhận.
     */
    override fun unmuteOutput() {
        Log.d(TAG, "[AudioRouteGuard] unmuteOutput: Mở lại âm thanh đầu ra")
        webRTCClient?.setOutputMuted(false)
    }

    /**
     * Chuyển đổi tuyến phát âm thanh vật lý (hỗ trợ cả Android 12+ `setCommunicationDevice`
     * lẫn API 24-30 `isSpeakerphoneOn` / `startBluetoothSco`).
     */
    override fun routeTo(route: AudioRoute) {
        Log.d(TAG, "[AudioRouteGuard] routeTo: Chuyển đổi sang tuyến $route")
        activeRoute = route
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applyRouteApi31Plus(route)
            } else {
                applyRouteLegacy(route)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi chuyển đổi tuyến âm thanh sang $route: ${e.message}", e)
        }
    }

    private fun applyRouteApi31Plus(route: AudioRoute) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val devices = audioManager.availableCommunicationDevices
        val targetDevice = devices.firstOrNull { device ->
            when (route) {
                AudioRoute.SPEAKER -> device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                AudioRoute.EARPIECE -> device.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                AudioRoute.HEADSET -> device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET
                AudioRoute.BLUETOOTH -> device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        }

        if (targetDevice != null) {
            val applied = audioManager.setCommunicationDevice(targetDevice)
            if (!applied) {
                Log.w(TAG, "setCommunicationDevice($route) trả về false, fallback clearCommunicationDevice")
                audioManager.clearCommunicationDevice()
            }
        } else if (route == AudioRoute.EARPIECE) {
            audioManager.clearCommunicationDevice()
        }
    }

    @Suppress("DEPRECATION")
    private fun applyRouteLegacy(route: AudioRoute) {
        when (route) {
            AudioRoute.SPEAKER -> {
                stopBluetoothScoIfNeeded()
                audioManager.isSpeakerphoneOn = true
            }
            AudioRoute.EARPIECE,
            AudioRoute.HEADSET -> {
                stopBluetoothScoIfNeeded()
                audioManager.isSpeakerphoneOn = false
            }
            AudioRoute.BLUETOOTH -> {
                audioManager.isSpeakerphoneOn = false
                if (audioManager.isBluetoothScoAvailableOffCall) {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun stopBluetoothScoIfNeeded() {
        if (audioManager.isBluetoothScoOn) {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        }
    }
}
