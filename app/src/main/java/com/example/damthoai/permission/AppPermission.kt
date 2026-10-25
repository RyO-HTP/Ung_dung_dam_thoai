package com.example.damthoai.permission

import android.Manifest
import android.os.Build
import com.example.damthoai.R

/**
 * =================================================================================
 * PHẦN THUỘC TÍNH: Phân loại quyền hệ thống Android (Android System Permissions)
 * VAI TRÒ: Thành viên 4 (Hardware / Event Integration) - Tuần 1
 * =================================================================================
 *
 * File này định nghĩa Enum [AppPermission] để nhóm các quyền Android theo tính năng sử dụng
 * trong ứng dụng Bộ Đàm "Đàm Thoại".
 *
 * GIẢI THÍCH VỀ QUYỀN TRÊN ANDROID:
 * 1. Quyền Normal (Thông thường): Ví dụ INTERNET, ACCESS_NETWORK_STATE, VIBRATE, MODIFY_AUDIO_SETTINGS.
 *    - HĐH tự động cấp ngay khi cài đặt ứng dụng.
 *    - Chỉ cần khai báo trong [AndroidManifest.xml], KHÔNG cần xin lúc runtime.
 * 2. Quyền Runtime (Nguy hiểm): Ví dụ RECORD_AUDIO, CAMERA, BLUETOOTH_CONNECT, POST_NOTIFICATIONS.
 *    - Có nguy cơ ảnh hưởng quyền riêng tư của người dùng.
 *    - Phải khai báo trong Manifest VÀ chủ động gọi API hỏi xin người dùng lúc ứng dụng đang chạy.
 * 3. Quyền Đặc biệt (Special Permission / Intent System Settings):
 *    - Ví dụ: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (Bỏ tối ưu pin để chạy nền).
 *    - Cần chuyển người dùng sang trang Cài đặt hệ thống để bật thủ công.
 */
enum class AppPermission(
    val titleRes: Int,
    val descriptionRes: Int,
    val isRequired: Boolean
) {
    /**
     * Nhóm Micro & Âm thanh: Dùng để thu âm giọng nói truyền qua bộ đàm.
     * Bắt buộc phải có để TV2 (Audio) và TV3 (UI) hoạt động.
     */
    AUDIO(
        titleRes = R.string.perm_title_audio,
        descriptionRes = R.string.perm_desc_audio,
        isRequired = true
    ) {
        override fun getManifestPermissions(): Array<String> {
            return arrayOf(Manifest.permission.RECORD_AUDIO)
        }
    },

    /**
     * Nhóm Camera & Đèn Flash: Dùng để chớp đèn Flash báo động khẩn cấp (Emergency Ping).
     * Không bắt buộc đối với chức năng nói cơ bản.
     */
    CAMERA(
        titleRes = R.string.perm_title_camera,
        descriptionRes = R.string.perm_desc_camera,
        isRequired = false
    ) {
        override fun getManifestPermissions(): Array<String> {
            return arrayOf(Manifest.permission.CAMERA)
        }
    },

    /**
     * Nhóm Bluetooth: Kết nối tai nghe không dây và nút PTT Bluetooth ngoại vi.
     * Từ Android 12 (API 31) trở lên, cần xin quyền Runtime [BLUETOOTH_CONNECT] và [BLUETOOTH_SCAN].
     * Android 11 trở xuống dùng quyền Normal trong Manifest.
     */
    BLUETOOTH(
        titleRes = R.string.perm_title_bluetooth,
        descriptionRes = R.string.perm_desc_bluetooth,
        isRequired = false
    ) {
        override fun getManifestPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            } else {
                // Trên API < 31, BLUETOOTH và BLUETOOTH_ADMIN là Normal permissions
                emptyArray()
            }
        }
    },

    /**
     * Nhóm Thông báo: Hiển thị trạng thái bộ đàm đàng chạy nền và cuộc gọi khẩn cấp.
     * Từ Android 13 (API 33) trở lên, [POST_NOTIFICATIONS] là quyền Runtime.
     */
    NOTIFICATION(
        titleRes = R.string.perm_title_notification,
        descriptionRes = R.string.perm_desc_notification,
        isRequired = false
    ) {
        override fun getManifestPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyArray()
            }
        }
    },

    /**
     * Nhóm Chạy nền & Tối ưu pin: Đảm bảo bộ đàm không bị HĐH ngắt kết nối khi tắt màn hình.
     * Xử lý thông qua kiểm tra [PowerManager.isIgnoringBatteryOptimizations].
     */
    BACKGROUND(
        titleRes = R.string.perm_title_background,
        descriptionRes = R.string.perm_desc_background,
        isRequired = true
    ) {
        override fun getManifestPermissions(): Array<String> {
            // Không có quyền runtime chuẩn dạng mảng String, xử lý qua PowerManager API
            return emptyArray()
        }
    },

    /**
     * Nhóm Mạng & Internet: Truyền dữ liệu bộ đàm qua Wi-Fi/4G/5G.
     * Tất cả quyền (INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE) đều là Normal Permissions,
     * chỉ cần khai báo trong AndroidManifest.xml.
     */
    NETWORK(
        titleRes = R.string.perm_title_network,
        descriptionRes = R.string.perm_desc_network,
        isRequired = true
    ) {
        override fun getManifestPermissions(): Array<String> {
            // Normal permissions - HĐH tự động cấp
            return emptyArray()
        }
    };

    /**
     * Trả về danh sách tên quyền Android ([Manifest.permission]) cần hỏi xin lúc Runtime.
     *
     * @return Mảng danh sách chuỗi các quyền tương ứng với phiên bản Android hiện tại.
     */
    abstract fun getManifestPermissions(): Array<String>
}
