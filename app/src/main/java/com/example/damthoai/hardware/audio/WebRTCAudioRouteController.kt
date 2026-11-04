package com.example.damthoai.hardware.audio

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.damthoai.webrtc.WebRTCClient

/**
 * =================================================================================
 * BỘ ĐIỀU KHIỂN & BẢO VỆ TUYẾN ÂM THANH THỰC TẾ (WebRTCAudioRouteController)
 * VAI TRÒ: Thành viên 2 (Audio & WebRTC Integration) - Tuần 4
 * =================================================================================
 *
 * Triển khai interface [AudioRouteController] của TV4, kết nối các thao tác chuyển tuyến
 * âm thanh (Loa ngoài, Tai nghe có dây, Bluetooth SCO, Loa trong Earpiece) và khóa âm thanh
 * bảo mật khi ngắt kết nối với AudioManager hệ thống Android và WebRTC Audio Track.
 */
class WebRTCAudioRouteController(
    private val context: Context,
    private val webRTCClient: WebRTCClient? = null
) : AudioRouteController {

    companion object {
        private const val TAG = "WebRTCAudioRouteCtrl"
    }

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var activeRoute: AudioRoute = AudioRoute.SPEAKER

    override fun currentRoute(): AudioRoute {
        return activeRoute
    }

    /**
     * Khóa âm thanh đầu ra khi tai nghe bị rút ra đột ngột để chống lọt tiếng bộ đàm bí mật ra ngoài.
     */
    override fun muteOutput() {
        Log.d(TAG, "[AudioRouteGuard] muteOutput: Khóa âm thanh đầu ra để bảo mật")
        webRTCClient?.setOutputMuted(true)
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi mute audio output: ${e.message}", e)
        }
    }

    /**
     * Mở lại âm thanh đầu ra sau khi tuyến âm thanh an toàn được thiết lập.
     */
    override fun unmuteOutput() {
        Log.d(TAG, "[AudioRouteGuard] unmuteOutput: Mở lại âm thanh đầu ra")
        webRTCClient?.setOutputMuted(false)
    }

    /**
     * Chuyển đổi tuyến phát âm thanh vật lý theo yêu cầu người dùng hoặc hệ thống.
     */
    override fun routeTo(route: AudioRoute) {
        Log.d(TAG, "[AudioRouteGuard] routeTo: Chuyển đổi sang tuyến $route")
        activeRoute = route
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            when (route) {
                AudioRoute.SPEAKER -> {
                    audioManager.isSpeakerphoneOn = true
                    if (audioManager.isBluetoothScoOn) {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                    }
                }
                AudioRoute.EARPIECE -> {
                    audioManager.isSpeakerphoneOn = false
                    if (audioManager.isBluetoothScoOn) {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                    }
                }
                AudioRoute.HEADSET -> {
                    audioManager.isSpeakerphoneOn = false
                    if (audioManager.isBluetoothScoOn) {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                    }
                }
                AudioRoute.BLUETOOTH -> {
                    audioManager.isSpeakerphoneOn = false
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi chuyển đổi tuyến âm thanh sang $route: ${e.message}", e)
        }
    }
}
