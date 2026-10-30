package com.example.damthoai.hardware.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.damthoai.hardware.PttController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Theo dõi tuyến đầu ra và ngăn tự chuyển âm thanh ra loa ngoài khi tai nghe bị ngắt.
 *
 * ĐIỂM NỐI:
 * - MainActivity gọi [start] / [stop].
 * - TV3 chỉ yêu cầu đổi tuyến qua [userSelectRoute] và bind các StateFlow bên dưới.
 * - TV2 cài [AudioRouteController] qua [installController] để mute/unmute pipeline thật.
 *
 * Khi tai nghe rời, guard mute trước rồi chọn earpiece làm tuyến an toàn nếu có; nếu không,
 * đầu ra phải tiếp tục im lặng. Android có thể tự đổi tuyến media khi phát bằng STREAM_MUSIC,
 * còn MODE_IN_COMMUNICATION có thể ưu tiên earpiece. Vì vậy muteOutput của TV2 là hàng rào chính;
 * stub hiện tại chỉ log, chưa thể tự bảo đảm âm thanh vật lý im lặng.
 */
object AudioRouteGuard {

    private const val TAG = "AudioRouteGuard"
    private const val DISCONNECT_DEBOUNCE_MS = 700L
    private val lock = Any()

    private val _currentRoute = MutableStateFlow(AudioRoute.EARPIECE)
    val currentRoute: StateFlow<AudioRoute> = _currentRoute.asStateFlow()

    private val _outputMuted = MutableStateFlow(false)
    val outputMuted: StateFlow<Boolean> = _outputMuted.asStateFlow()

    private val _availableRoutes = MutableStateFlow(setOf(AudioRoute.SPEAKER, AudioRoute.EARPIECE))
    val availableRoutes: StateFlow<Set<AudioRoute>> = _availableRoutes.asStateFlow()

    private var audioManager: AudioManager? = null
    private var appContext: Context? = null
    private var started = false
    private var audioCallbackRegistered = false
    private var receiverRegistered = false
    private var controller: AudioRouteController = LoggingAudioRouteController()
    private var selectedRoute: AudioRoute? = null
    private var connectedOutputDevices = emptyList<AudioDeviceInfo>()
    private var lastDisconnectKey: String? = null
    private var lastDisconnectAtMs = 0L

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (addedDevices.isNullOrEmpty()) return
            val knownIds = synchronized(lock) { connectedOutputDevices.mapTo(mutableSetOf()) { it.id } }
            refreshAvailableRoutes()
            val newlyConnected = addedDevices.filter {
                isHeadsetDevice(it.type) && it.id !in knownIds
            }
            if (newlyConnected.isNotEmpty()) {
                val route = newlyConnected.firstNotNullOfOrNull { routeForDevice(it.type) }
                synchronized(lock) {
                    if (route != null && (selectedRoute == null || selectedRoute == route)) {
                        _currentRoute.value = route
                    }
                }
                AudioIncidentBus.report(AudioIncident.HeadsetReconnected)
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (removedDevices.isNullOrEmpty()) return
            refreshAvailableRoutes()
            removedDevices
                .filter { isHeadsetDevice(it.type) }
                .forEach(::handleDeviceRemoved)
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            val route = synchronized(lock) { selectedRoute ?: _currentRoute.value }
            if (route == AudioRoute.SPEAKER) return
            val device = synchronized(lock) {
                connectedOutputDevices.firstOrNull { routeForDevice(it.type) == route }
            } ?: return
            handleDeviceRemoved(device)
        }
    }

    /**
     * Bắt đầu theo dõi thiết bị; gọi lặp lại an toàn.
     */
    fun start(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (started) return
            val manager: AudioManager? = runSafely(onError = { null }) {
                appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            }
            if (manager == null) {
                AudioIncidentBus.report(
                    AudioIncident.AudioRouteFailed("Không tìm thấy AudioManager")
                )
                return
            }
            this.appContext = appContext
            audioManager = manager
            started = true
        }

        refreshAvailableRoutes()
        val initialRoute = when {
            availableRoutes.value.contains(AudioRoute.BLUETOOTH) -> AudioRoute.BLUETOOTH
            availableRoutes.value.contains(AudioRoute.HEADSET) -> AudioRoute.HEADSET
            availableRoutes.value.contains(AudioRoute.EARPIECE) -> AudioRoute.EARPIECE
            else -> AudioRoute.SPEAKER
        }
        synchronized(lock) {
            selectedRoute = null
            _currentRoute.value = initialRoute
        }

        audioCallbackRegistered = runSafely(
            onError = { false },
            block = {
                audioManager?.registerAudioDeviceCallback(
                    deviceCallback,
                    Handler(Looper.getMainLooper())
                )
                true
            }
        )
        try {
            ContextCompat.registerReceiver(
                appContext,
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        } catch (exception: SecurityException) {
            AudioIncidentBus.report(
                AudioIncident.AudioRouteFailed(
                    exception.message ?: "Không đăng ký được bộ nhận audio becoming noisy"
                )
            )
            Log.e(TAG, "Không thể đăng ký receiver audio", exception)
        } catch (exception: IllegalStateException) {
            AudioIncidentBus.report(
                AudioIncident.AudioRouteFailed(
                    exception.message ?: "Receiver audio đang ở trạng thái không hợp lệ"
                )
            )
            Log.e(TAG, "Receiver audio không thể đăng ký", exception)
        } catch (exception: RuntimeException) {
            AudioIncidentBus.report(AudioIncident.Unknown(exception))
            Log.e(TAG, "Lỗi runtime khi đăng ký receiver audio", exception)
        }
    }

    /** Dừng callback và receiver đã đăng ký. */
    fun stop() {
        val context: Context?
        val manager: AudioManager?
        val unregisterCallback: Boolean
        val unregisterReceiver: Boolean
        synchronized(lock) {
            if (!started) return
            started = false
            context = appContext
            manager = audioManager
            unregisterCallback = audioCallbackRegistered
            unregisterReceiver = receiverRegistered
            audioCallbackRegistered = false
            receiverRegistered = false
            appContext = null
            audioManager = null
            connectedOutputDevices = emptyList()
            _availableRoutes.value = setOf(AudioRoute.SPEAKER, AudioRoute.EARPIECE)
        }

        if (unregisterCallback) {
            runSafely(onError = { false }) {
                manager?.unregisterAudioDeviceCallback(deviceCallback)
                true
            }
        }
        if (context != null && unregisterReceiver) {
            try {
                context.unregisterReceiver(becomingNoisyReceiver)
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Receiver audio đã được gỡ trước đó", exception)
            } catch (exception: SecurityException) {
                Log.e(TAG, "Không thể gỡ receiver audio", exception)
            } catch (exception: IllegalStateException) {
                Log.e(TAG, "Không thể gỡ receiver audio ở trạng thái hiện tại", exception)
            }
        }
    }

    /**
     * Cài đặt adapter do TV2 cung cấp; hàm này không yêu cầu quyền truy cập khác.
     */
    fun installController(audioRouteController: AudioRouteController) {
        synchronized(lock) {
            controller = audioRouteController
            _currentRoute.value = runSafely(
                onError = { _currentRoute.value },
                block = { audioRouteController.currentRoute() }
            )
        }
    }

    /**
     * ĐIỂM NỐI TV3: chỉ dùng hàm này khi người dùng chủ động chọn tuyến âm thanh.
     * Chọn tuyến thành công mới unmute đầu ra.
     */
    fun userSelectRoute(route: AudioRoute) {
        if (!availableRoutes.value.contains(route)) {
            AudioIncidentBus.report(
                AudioIncident.AudioRouteFailed("Tuyến $route hiện không khả dụng")
            )
            return
        }

        val routeController = synchronized(lock) { controller }
        val routed = runSafely(onError = { false }) {
            routeController.routeTo(route)
            applyPlatformRoute(route)
        }
        if (!routed) {
            runSafely(onError = { false }) {
                routeController.muteOutput()
                true
            }
            synchronized(lock) { _outputMuted.value = true }
            AudioIncidentBus.report(
                AudioIncident.AudioRouteFailed("Không thể chuyển sang tuyến $route")
            )
            return
        }

        val unmuted = runSafely(onError = { false }) {
            routeController.unmuteOutput()
            true
        }
        if (!unmuted) return

        synchronized(lock) {
            selectedRoute = route
            _currentRoute.value = route
            _outputMuted.value = false
        }
    }

    internal fun onAudioFocusLost(reason: String) {
        val routeController = synchronized(lock) { controller }
        runSafely(onError = { false }) {
            routeController.muteOutput()
            true
        }
        synchronized(lock) { _outputMuted.value = true }
        if (PttController.isPttActive.value) {
            PttController.forceReleasePtt()
        }
        AudioIncidentBus.report(AudioIncident.AudioFocusLost(reason))
    }

    private fun refreshAvailableRoutes() {
        val manager = synchronized(lock) { audioManager } ?: return
        val devices = runSafely(onError = { emptyList() }) {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }
        synchronized(lock) {
            connectedOutputDevices = devices
            val routes = mutableSetOf(AudioRoute.SPEAKER)
            if (devices.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }) {
                routes.add(AudioRoute.EARPIECE)
            }
            if (devices.any { routeForDevice(it.type) == AudioRoute.HEADSET }) {
                routes.add(AudioRoute.HEADSET)
            }
            if (devices.any { routeForDevice(it.type) == AudioRoute.BLUETOOTH }) {
                routes.add(AudioRoute.BLUETOOTH)
            }
            _availableRoutes.value = routes
        }
    }

    private fun handleDeviceRemoved(device: AudioDeviceInfo) {
        val route = routeForDevice(device.type) ?: return
        val shouldProtectOutput = synchronized(lock) {
            selectedRoute != AudioRoute.SPEAKER &&
                (selectedRoute == null ||
                    route == selectedRoute ||
                    route == _currentRoute.value)
        }

        if (shouldProtectOutput) protectOutputAfterDisconnect()

        val key = "${device.type}:${device.id}"
        val now = System.currentTimeMillis()
        val shouldReport = synchronized(lock) {
            val duplicate = lastDisconnectKey == key &&
                now - lastDisconnectAtMs < DISCONNECT_DEBOUNCE_MS
            if (!duplicate) {
                lastDisconnectKey = key
                lastDisconnectAtMs = now
            }
            !duplicate
        }
        if (!shouldReport) return

        when (route) {
            AudioRoute.BLUETOOTH -> AudioIncidentBus.report(AudioIncident.BluetoothDisconnected)
            AudioRoute.HEADSET -> AudioIncidentBus.report(
                AudioIncident.HeadsetUnplugged(device.type)
            )
            else -> Unit
        }
    }

    private fun protectOutputAfterDisconnect() {
        val routeController = synchronized(lock) { controller }
        runSafely(onError = { false }) {
            routeController.muteOutput()
            true
        }
        synchronized(lock) { _outputMuted.value = true }

        if (PttController.isPttActive.value) {
            PttController.forceReleasePtt()
        }

        val manager = synchronized(lock) { audioManager }
        applySafeNonSpeakerRoute(manager)

        if (availableRoutes.value.contains(AudioRoute.EARPIECE)) {
            runSafely(onError = { false }) {
                routeController.routeTo(AudioRoute.EARPIECE)
                true
            }
            synchronized(lock) { _currentRoute.value = AudioRoute.EARPIECE }
        }
    }

    private fun applySafeNonSpeakerRoute(manager: AudioManager?) {
        if (manager == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val earpiece = runSafely(onError = { null }) {
                manager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
            }
            if (earpiece != null) {
                val applied = runSafely(onError = { false }) {
                    manager.setCommunicationDevice(earpiece)
                }
                if (!applied) {
                    AudioIncidentBus.report(
                        AudioIncident.AudioRouteFailed("Không đặt được tuyến an toàn earpiece")
                    )
                    runSafely(onError = { false }) {
                        manager.clearCommunicationDevice()
                        true
                    }
                }
            } else {
                runSafely(onError = { false }) {
                    manager.clearCommunicationDevice()
                    true
                }
            }
        } else {
            runSafely(onError = { false }) {
                @Suppress("DEPRECATION")
                manager.isSpeakerphoneOn = false
                true
            }
        }
    }

    private fun applyPlatformRoute(route: AudioRoute): Boolean {
        val manager = synchronized(lock) { audioManager } ?: return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = manager.availableCommunicationDevices.firstOrNull {
                routeForDevice(it.type) == route ||
                    route == AudioRoute.SPEAKER && it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            if (device != null) return manager.setCommunicationDevice(device)

            if (route == AudioRoute.EARPIECE || route == AudioRoute.SPEAKER) {
                manager.clearCommunicationDevice()
            }
            // Wired/A2DP and some built-in routes are managed by the TV2 audio pipeline.
            return true
        }

        @Suppress("DEPRECATION")
        manager.isSpeakerphoneOn = route == AudioRoute.SPEAKER
        return true
    }

    private fun isHeadsetDevice(type: Int): Boolean = routeForDevice(type) in
        setOf(AudioRoute.HEADSET, AudioRoute.BLUETOOTH)

    private fun routeForDevice(type: Int): AudioRoute? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRoute.SPEAKER
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioRoute.EARPIECE
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET -> AudioRoute.HEADSET

        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioRoute.BLUETOOTH

        else -> if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            type == AudioDeviceInfo.TYPE_BLE_HEADSET
        ) {
            AudioRoute.BLUETOOTH
        } else {
            null
        }
    }
}
