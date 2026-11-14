package com.example.damthoai.ios

import platform.UIKit.UIView

/** Swift báo máy chủ Signaling tìm thấy qua Bonjour (dịch vụ _bodam._tcp). */
interface ServerFoundListener {
    fun onServerFound(name: String, host: String, port: Int)
}

/** Swift báo kết quả quét QR từ camera. */
interface QrCodeListener {
    fun onQrDetected(code: String)
    fun onError(message: String)
}

/**
 * Những phần phải viết bằng Swift vì dùng thư viện/framework không gọi trực tiếp được từ Kotlin
 * (WebRTC qua Swift Package, PushToTalk, AVCaptureSession quét QR, Bonjour NetServiceBrowser).
 * Swift cài đặt giao diện này (xem iosApp/iosApp/NativeBridge.swift) rồi truyền vào [MainViewController].
 */
interface IosNativeBridge {
    /** Âm thanh P2P WebRTC (iosApp/iosApp/WebRTCEngine.swift). */
    val rtcBackend: IosRtcBackend

    fun startBonjour(listener: ServerFoundListener)
    fun stopBonjour()

    /** Tạo view camera quét QR; Kotlin nhúng bằng UIKitView. */
    fun createQrScannerView(listener: QrCodeListener): UIView
    fun releaseQrScannerView(view: UIView)

    /**
     * Framework PushToTalk (iOS 16+): tham gia kênh hệ thống để nói/nghe khi app chạy nền
     * hoặc màn hình khoá. Không hỗ trợ (iOS < 16, máy ảo) thì bỏ qua.
     */
    fun pushToTalkJoin(channelName: String)
    fun pushToTalkLeave()

    /** Bật/tắt nhận nút Play/Pause tai nghe (MPRemoteCommandCenter) trong phiên đàm thoại. */
    fun setHeadsetRemoteEnabled(enabled: Boolean)
}
