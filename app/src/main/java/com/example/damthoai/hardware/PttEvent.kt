package com.example.damthoai.hardware

/**
 * =================================================================================
 * PHẦN THUỘC TÍNH: Sự kiện bấm/thả PTT (Push-to-Talk Event Model)
 * VAI TRÒ: Thành viên 4 (Hardware / Event Integration) - Tuần 2
 * =================================================================================
 *
 * Sealed Class đại diện cho 2 trạng thái hành động PTT duy nhất trong toàn hệ thống:
 * 1. [PttPressed]: Người dùng ĐANG NHẤN VÀ GIỮ nút PTT (Yêu cầu mở Micro để nói).
 * 2. [PttReleased]: Người dùng ĐÃ THẢ nút PTT hoặc bị ngắt PTT cưỡng chế (Yêu cầu ngắt Micro, quay về chế độ nghe).
 *    Thuộc tính [PttReleased.forced] = true khi ngắt do sự cố (mất audio focus, rút tai nghe, hết timeout, ngắt capture, v.v.).
 *
 * ĐIỂM NỐI:
 * TV2 (Audio) sẽ lắng nghe các [PttEvent] này qua [PttController.events] để bật/tắt Micro thu âm.
 * TV3 (UI) sẽ đọc [PttReleased.forced] để không phát tiếng bíp (Roger Beep) khi thả cưỡng bức.
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
     * @param forced Cờ báo thả cưỡng bức do lỗi/hệ thống ngắt (true) hay do người dùng nhả phím bình thường (false).
     */
    data class PttReleased(override val source: PttSource, val forced: Boolean = false) : PttEvent(source)
}
