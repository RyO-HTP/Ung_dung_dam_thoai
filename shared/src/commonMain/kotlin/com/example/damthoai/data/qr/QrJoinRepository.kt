package com.example.damthoai.data.qr

import com.example.damthoai.util.Log
import com.example.damthoai.network.SignalingClient

class QrJoinRepository(private val signalingClient: SignalingClient) {
    companion object {
        private const val TAG = "QrJoinRepository"
    }

    /**
     * Xử lý quét mã QR tham gia sự kiện thực tế
     */
    fun joinEventByQr(qrCodeContent: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        Log.d(TAG, "Đang xử lý mã QR thực tế: $qrCodeContent")
        try {
            // TODO: Thêm logic phân tích mã QR (ví dụ: lấy URL WebSocket hoặc mã phòng từ chuỗi QR)
            // Ở môi trường test thực tế, bạn có thể gọi kết nối trực tiếp tới Signaling Server tại đây:
            // signalingClient.connect(qrCodeContent, "User_Test")

            onSuccess()
        } catch (e: Exception) {
            onError("Lỗi phân tích mã QR: ${e.message}")
        }
    }
}