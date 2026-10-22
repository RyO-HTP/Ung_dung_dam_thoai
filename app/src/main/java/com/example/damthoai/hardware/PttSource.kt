package com.example.damthoai.hardware

/**
 * =================================================================================
 * PHẦN THUỘC TÍNH: Nguồn kích hoạt nút PTT (Push-to-Talk Sources)
 * VAI TRÒ: Thành viên 4 (Hardware / Event Integration) - Tuần 2
 * =================================================================================
 *
 * Enum này phân loại các nguồn có thể gửi tín hiệu Bật/Tắt Micro bộ đàm.
 * Mọi nguồn kích hoạt từ phần cứng (phím volume, nút tai nghe, nút BLE)
 * hoặc phần mềm (nút PTT trên màn hình) đều sử dụng chung Enum này.
 */
enum class PttSource(val displayName: String) {
    /**
     * Nút Tăng âm lượng (Volume Up) trên cạnh điện thoại.
     */
    VOLUME_UP("Phím Tăng âm lượng (Vol+)"),

    /**
     * Nút Giảm âm lượng (Volume Down) trên cạnh điện thoại.
     */
    VOLUME_DOWN("Phím Giảm âm lượng (Vol-)"),

    /**
     * Nút Play/Pause/Call trên tai nghe có dây hoặc tai nghe Bluetooth.
     */
    HEADSET("Nút bấm Tai nghe"),

    /**
     * Nút bấm Bluetooth PTT ngoại vi (Bluetooth Shutter / Remote Control).
     */
    BLE_BUTTON("Nút PTT Bluetooth rời"),

    /**
     * Nút PTT lớn hiển thị trực tiếp trên màn hình cảm ứng (do TV3 dựng UI).
     */
    SCREEN_BUTTON("Nút PTT Màn hình")
}
