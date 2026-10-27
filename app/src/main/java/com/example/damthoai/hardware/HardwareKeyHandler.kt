package com.example.damthoai.hardware

import android.view.KeyEvent

/**
 * =================================================================================
 * PHẦN XỬ LÝ PHÍM CỨNG: Bộ xử lý sự kiện phím vật lý (Hardware Key Handler)
 * VAI TRÒ: Thành viên 4 (Hardware / Event Integration) - Tuần 2
 * =================================================================================
 *
 * File này chịu trách nhiệm:
 * 1. Lắng nghe các phím bấm vật lý ([KeyEvent.KEYCODE_VOLUME_UP], [KeyEvent.KEYCODE_VOLUME_DOWN]) từ Activity.
 * 2. Lọc chống lặp phím khi người dùng giữ phím (`event.repeatCount == 0`) và chống dội phím (debounce).
 * 3. Chuyển đổi phím Volume thành sự kiện Bật/Tắt PTT gửi sang [PttController].
 * 4. NUỐT SỰ KIỆN (Consume KeyEvent): Trả về `true` trong `onKeyDown`/`onKeyUp` để HĐH Android
 *    KHÔNG THAY ĐỔI ÂM LƯỢNG THỰC TẾ của điện thoại khi đang đàm thoại.
 */
object HardwareKeyHandler {

    // Thời gian bấm gần nhất theo từng phím để debounce độc lập.
    private val lastKeyDownTimeMs = mutableMapOf<Int, Long>()
    private const val DEBOUNCE_THRESHOLD_MS = 50L

    /**
     * Bắt sự kiện người dùng NHẤN phím cứng down (`onKeyDown`).
     *
     * @param keyCode Mã phím cứng Android.
     * @param event Đối tượng [KeyEvent] từ hệ thống.
     * @return `true` nếu phím được xử lý làm PTT (HĐH sẽ KHÔNG đổi âm lượng), `false` để HĐH xử lý mặc định.
     */
    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Chỉ bắt phím khi PTT được bật và có ít nhất một chủ thể đang giữ quyền bắt phím.
        if (!PttController.isPttModeEnabled.value || !PttController.isCaptureActive.value) {
            return false
        }

        val source = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> PttSource.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> PttSource.VOLUME_DOWN

            // Gom tất cả các nút bấm từ tai nghe có dây và Bluetooth về nguồn HEADSET
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_RECORD -> PttSource.HEADSET
            else -> return false
        }

        // Kiểm tra phím Volume này có được phép theo cài đặt không (Both / Vol- / Vol+)
        if (!PttController.isVolumeKeyAllowed(source)) {
            return false
        }

        // TẠI SAO CẦN REPEAT COUNT == 0:
        // Khi giữ phím cứng, HĐH Android sẽ liên tục bắn các sự kiện onKeyDown với repeatCount > 0.
        // Ta chỉ lấy duy nhất sự kiện đầu tiên (repeatCount == 0) để không bắn tín hiệu PTT trùng lặp.
        if (event != null && event.repeatCount > 0) {
            // Vẫn trả về true để tiếp tục "nuốt" phím âm lượng khi đang giữ!
            return true
        }

        // Chống dội phím (Debounce)
        val currentTimeMs = System.currentTimeMillis()
        val previousKeyDownTimeMs = lastKeyDownTimeMs[keyCode]
        if (previousKeyDownTimeMs != null &&
            currentTimeMs - previousKeyDownTimeMs < DEBOUNCE_THRESHOLD_MS
        ) {
            return true
        }
        lastKeyDownTimeMs[keyCode] = currentTimeMs

        // Gửi tín hiệu Bấm PTT sang PttController
        PttController.onKeyPressed(source)

        // TẠI SAO TRẢ VỀ TRUE:
        // Trong cơ chế xử lý sự kiện của Android Framework:
        // - Trả về `true` báo cho HĐH biết "Sự kiện phím này đã được ứng dụng xử lý hoàn toàn (consumed)".
        // - HĐH sẽ DỪNG lan truyền KeyEvent đến AudioManager, nhờ đó ÂM LƯỢNG MÁY KHÔNG BỊ THAY ĐỔI!
        return true
    }

    /**
     * Bắt sự kiện người dùng THẢ phím cứng up (`onKeyUp`).
     *
     * @param keyCode Mã phím cứng Android.
     * @param event Đối tượng [KeyEvent] từ hệ thống.
     * @return `true` nếu phím được xử lý làm PTT, `false` để HĐH xử lý mặc định.
     */
    fun handleKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (!PttController.isPttModeEnabled.value || !PttController.isCaptureActive.value) {
            return false
        }

        val source = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> PttSource.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> PttSource.VOLUME_DOWN
            else -> return false
        }

        if (!PttController.isVolumeKeyAllowed(source)) {
            return false
        }

        // Gửi tín hiệu Thả PTT sang PttController
        PttController.onKeyReleased(source)

        // Trả về true để "nuốt" sự kiện thả phím, ngắt âm thanh hệ thống mặc định
        return true
    }
}
