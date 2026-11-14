package com.example.damthoai.platform

import kotlinx.coroutines.flow.Flow

/** Máy chủ Signaling tìm thấy trong LAN qua mDNS/Bonjour. */
data class DiscoveredServer(val name: String, val host: String, val port: Int) {
    val wsUrl: String get() = "ws://$host:$port"
}

/** Tìm máy chủ trong mạng LAN bằng mDNS (Android NSD / iOS Bonjour NWBrowser). */
interface ServerDiscovery {
    val serverFound: Flow<DiscoveredServer>
    fun startDiscovery()
    fun stopDiscovery()
}

/** Thao tác mạng mức thấp phục vụ quét LAN (socket TCP, đọc IP của các card mạng). */
interface LanProbe {
    /** Thử mở kết nối TCP. Hàm chặn luồng: gọi trên Dispatchers.IO / Default. */
    fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean

    /** Các IPv4 cùng dải /24 với mạng LAN máy đang tham gia (trừ chính máy). */
    fun lanScanTargets(): List<String>
}

/** Báo khi đổi mạng (Wi-Fi <-> 4G) để chạy Seamless Handover. */
interface NetworkMonitor {
    fun start(onNetworkChanged: () -> Unit)
    fun stop()
}

/** Phiên âm thanh đàm thoại của hệ điều hành (Android: MODE_IN_COMMUNICATION, iOS: AVAudioSession .voiceChat). */
interface CallAudioSession {
    fun start()
    fun release()
}

/**
 * Tập hợp các dịch vụ riêng của từng nền tảng. Gọi [install] một lần khi khởi động app
 * (Android: MainActivity/Application, iOS: MainViewController) TRƯỚC khi dùng DamThoaiCallManager.
 */
class PlatformServices(
    val rtc: RtcEngine,
    val discovery: ServerDiscovery,
    val lanProbe: LanProbe,
    val networkMonitorFactory: () -> NetworkMonitor,
    val callAudio: CallAudioSession,
    /** Địa chỉ máy chủ Signaling trên đám mây (Render); "" để tắt. */
    val cloudSignalingUrl: String,
    /** Hệ điều hành cho phép dùng phím âm lượng làm PTT không (iOS: không). */
    val supportsVolumeKeyPtt: Boolean,
) {
    companion object {
        private var current: PlatformServices? = null

        fun install(services: PlatformServices) {
            current = services
        }

        val instance: PlatformServices
            get() = current ?: error("PlatformServices chưa được install() khi khởi động app")

        val isInstalled: Boolean get() = current != null
    }
}
