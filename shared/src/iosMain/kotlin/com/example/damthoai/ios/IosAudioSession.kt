package com.example.damthoai.ios

import com.example.damthoai.hardware.audio.AudioRoute
import com.example.damthoai.hardware.audio.AudioRouteBackend
import com.example.damthoai.platform.CallAudioSession
import com.example.damthoai.util.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.*
import platform.Foundation.*

/**
 * iOS: phiên âm thanh đàm thoại (AVAudioSession .playAndRecord + .voiceChat) và chọn tuyến
 * loa ngoài / loa trong / tai nghe / Bluetooth. Cài đặt cả [CallAudioSession] và [AudioRouteBackend].
 */
@OptIn(ExperimentalForeignApi::class)
class IosAudioSession : CallAudioSession, AudioRouteBackend {
    private val session: AVAudioSession get() = AVAudioSession.sharedInstance()

    private val _currentRoute = MutableStateFlow(AudioRoute.SPEAKER)
    override val currentRoute: StateFlow<AudioRoute> = _currentRoute.asStateFlow()

    private val _outputMuted = MutableStateFlow(false)
    override val outputMuted: StateFlow<Boolean> = _outputMuted.asStateFlow()

    private val _availableRoutes = MutableStateFlow(setOf(AudioRoute.SPEAKER, AudioRoute.EARPIECE))
    override val availableRoutes: StateFlow<Set<AudioRoute>> = _availableRoutes.asStateFlow()

    // Người dùng chọn loa ngoài: iOS bỏ override khi đổi tuyến, nên phải đặt lại sau mỗi lần đổi
    private var speakerSelected = true

    init {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ -> refreshRoutes() }
        refreshRoutes()
    }

    override fun start() {
        try {
            session.setCategory(
                AVAudioSessionCategoryPlayAndRecord,
                mode = AVAudioSessionModeVoiceChat,
                options = AVAudioSessionCategoryOptionAllowBluetooth or AVAudioSessionCategoryOptionDefaultToSpeaker,
                error = null
            )
            session.setActive(true, error = null)
        } catch (e: Throwable) {
            Log.e(TAG, "Không mở được phiên âm thanh: ${e.message}")
        }
        refreshRoutes()
    }

    override fun release() {
        try {
            session.overrideOutputAudioPort(AVAudioSessionPortOverrideNone, error = null)
            session.setActive(false, error = null)
        } catch (e: Throwable) {
            Log.e(TAG, "Không đóng được phiên âm thanh: ${e.message}")
        }
    }

    override fun userSelectRoute(route: AudioRoute) {
        if (route !in _availableRoutes.value) return
        speakerSelected = route == AudioRoute.SPEAKER
        when (route) {
            AudioRoute.SPEAKER ->
                session.overrideOutputAudioPort(AVAudioSessionPortOverrideSpeaker, error = null)
            AudioRoute.EARPIECE, AudioRoute.HEADSET -> {
                session.setPreferredInput(null, error = null)
                session.overrideOutputAudioPort(AVAudioSessionPortOverrideNone, error = null)
            }
            AudioRoute.BLUETOOTH -> {
                val bluetoothInput = inputs().firstOrNull { it.portType == AVAudioSessionPortBluetoothHFP }
                session.setPreferredInput(bluetoothInput, error = null)
                session.overrideOutputAudioPort(AVAudioSessionPortOverrideNone, error = null)
            }
        }
        _outputMuted.value = false
        refreshRoutes()
    }

    private fun inputs(): List<AVAudioSessionPortDescription> =
        session.availableInputs?.filterIsInstance<AVAudioSessionPortDescription>().orEmpty()

    private fun refreshRoutes() {
        val outputs = session.currentRoute.outputs.filterIsInstance<AVAudioSessionPortDescription>()
        val inputTypes = inputs().map { it.portType }
        val outputTypes = outputs.map { it.portType }
        val bluetoothTypes = listOf(AVAudioSessionPortBluetoothHFP, AVAudioSessionPortBluetoothA2DP, AVAudioSessionPortBluetoothLE)

        val available = mutableSetOf(AudioRoute.SPEAKER, AudioRoute.EARPIECE)
        if ((inputTypes + outputTypes).any { it in bluetoothTypes }) available += AudioRoute.BLUETOOTH
        if (AVAudioSessionPortHeadphones in outputTypes || AVAudioSessionPortHeadsetMic in inputTypes) {
            available += AudioRoute.HEADSET
        }
        _availableRoutes.value = available

        _currentRoute.value = when {
            outputTypes.any { it in bluetoothTypes } -> AudioRoute.BLUETOOTH
            AVAudioSessionPortHeadphones in outputTypes -> AudioRoute.HEADSET
            AVAudioSessionPortBuiltInReceiver in outputTypes -> AudioRoute.EARPIECE
            else -> AudioRoute.SPEAKER
        }
    }

    private companion object {
        const val TAG = "IosAudioSession"
    }
}
