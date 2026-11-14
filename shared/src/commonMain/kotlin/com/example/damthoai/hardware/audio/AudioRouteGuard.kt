package com.example.damthoai.hardware.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Phần điều khiển tuyến âm thanh của từng nền tảng (Android: AndroidAudioRouteGuard, iOS: AVAudioSession). */
interface AudioRouteBackend {
    val currentRoute: StateFlow<AudioRoute>
    val outputMuted: StateFlow<Boolean>
    val availableRoutes: StateFlow<Set<AudioRoute>>

    /** Người dùng chủ động chọn tuyến (loa ngoài / tai nghe / Bluetooth...). */
    fun userSelectRoute(route: AudioRoute)
}

/**
 * Cổng dùng chung cho UI và DamThoaiCallManager để đọc/chọn tuyến âm thanh.
 * Mỗi nền tảng gán [backend] khi khởi động app.
 */
object AudioRouteGuard {
    private val fallbackRoute = MutableStateFlow(AudioRoute.SPEAKER)
    private val fallbackMuted = MutableStateFlow(false)
    private val fallbackAvailable = MutableStateFlow(setOf(AudioRoute.SPEAKER))

    var backend: AudioRouteBackend? = null

    val currentRoute: StateFlow<AudioRoute> get() = backend?.currentRoute ?: fallbackRoute
    val outputMuted: StateFlow<Boolean> get() = backend?.outputMuted ?: fallbackMuted
    val availableRoutes: StateFlow<Set<AudioRoute>> get() = backend?.availableRoutes ?: fallbackAvailable

    fun userSelectRoute(route: AudioRoute) {
        backend?.userSelectRoute(route) ?: run { fallbackRoute.value = route }
    }
}
