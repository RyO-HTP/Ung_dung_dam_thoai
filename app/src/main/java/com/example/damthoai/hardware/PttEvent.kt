package com.example.damthoai.hardware

/**
 * =================================================================================
 * PHẦN THUỘC TÍNH: Sự kiện bấm/thả PTT (Push-to-Talk Event Model)
 * VAI TRÒ: Thành viên 4 (Hardware / Event Integration) - Tuần 2
 * =================================================================================
 *
 * Sealed Class đại diện cho 2 trạng thái hành động PTT duy nhất trong toàn hệ thống:
 * 1. [PttPressed]: Người dùng ĐANG NHẤN VÀ GIỮ nút PTT (Yêu cầu mở Micro để nói).
 * 2. [PttReleased]: Người dùng ĐÃ THẢ nút PTT (Yêu cầu ngắt Micro, quay về chế độ nghe).
 *
 * ĐIỂM NỐI:
 * TV2 (Audio) sẽ lắng nghe các [PttEvent] này qua [PttController.events] để bật/tắt Micro thu âm.
 */
sealed class PttEvent(open val source: PttSource) {

    /**
     * Tín hiệu BẤM VÀ GIỮ phím PTT -> Bật Micro thu âm giọng nói.
     * @param source Nguồn kích hoạt ([PttSource.VOLUME_UP], [PttSource.VOLUME_DOWN], etc.).
     */
    data class PttPressed(override val source: PttSource) : PttEvent(source)

    /**
     * Tín hiệu THẢ phím PTT -> Ngắt Micro thu âm.
     * @param source Nguồn giải phóng phím.
     */
    data class PttReleased(override val source: PttSource) : PttEvent(source)
}
