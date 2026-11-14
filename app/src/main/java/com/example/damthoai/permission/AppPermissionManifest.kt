package com.example.damthoai.permission

import android.Manifest
import android.os.Build

/** Quyền Android (Manifest) tương ứng với từng nhóm quyền dùng chung. */
fun AppPermission.getManifestPermissions(): Array<String> = when (this) {
    AppPermission.AUDIO -> arrayOf(Manifest.permission.RECORD_AUDIO)
    AppPermission.CAMERA -> arrayOf(Manifest.permission.CAMERA)
    // Trên API < 31, BLUETOOTH và BLUETOOTH_ADMIN là Normal permissions
    AppPermission.BLUETOOTH ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()
    AppPermission.NOTIFICATION ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
    // Không có quyền runtime chuẩn dạng mảng String, xử lý qua PowerManager API
    AppPermission.BACKGROUND -> emptyArray()
    // Normal permissions - HĐH tự động cấp
    AppPermission.NETWORK -> emptyArray()
}
