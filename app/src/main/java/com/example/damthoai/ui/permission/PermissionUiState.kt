/**
 * Path: app/src/main/java/com/example/damthoai/ui/permission/PermissionUiState.kt
 *
 * Khối KDoc tiếng Việt:
 * Định nghĩa Trạng thái Quyền ứng dụng (Permission UI State) cho giao diện Đàm Thoại.
 * // TODO(TV4): Người 4 sẽ kết nối PermissionManager/ViewModel thật vào interface này.
 */
package com.example.damthoai.ui.permission

data class PermissionUiState(
    val isMicrophoneGranted: Boolean = true,
    val isNotificationGranted: Boolean = true,
    val isBluetoothGranted: Boolean = false,
    val isBackgroundGranted: Boolean = true,
    val isCameraGranted: Boolean = false
)
