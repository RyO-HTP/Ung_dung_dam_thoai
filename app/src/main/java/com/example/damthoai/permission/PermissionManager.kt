package com.example.damthoai.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * =================================================================================
 * PHẦN THUỘC TÍNH: Lớp quản lý cấp quyền tập trung (Centralized Permission Manager)
 * VAI TRÒ: Thành viên 4 (Hardware / Event Integration) - Tuần 1
 * =================================================================================
 *
 * File này chịu trách nhiệm:
 * 1. Kiểm tra trạng thái cấp quyền của từng nhóm [AppPermission] theo phiên bản Android SDK.
 * 2. Cung cấp [permissionStates] dạng [StateFlow] để các màn hình UI (TV3) hoặc Audio Service (TV2)
 *    theo dõi trạng thái quyền thời gian thực.
 * 3. Cung cấp hàm [allRequiredGranted] giúp ứng dụng biết khi nào đã đủ quyền cơ bản để hoạt động.
 * 4. Xử lý mở màn hình Tắt tối ưu hóa pin (Battery Optimization Exemption) & Cài đặt ứng dụng.
 */
object PermissionManager {
    private const val REQUESTED_PERMISSIONS_PREFERENCES = "app_permission_requests"

    /**
     * Luồng trạng thái phát ra Map chứa trạng thái hiện tại của từng nhóm [AppPermission].
     * Mặc định khởi tạo các quyền ở trạng thái [PermissionState.DENIED].
     */
    private val _permissionStates = MutableStateFlow<Map<AppPermission, PermissionState>>(
        AppPermission.entries.associateWith { PermissionState.DENIED }
    )
    val permissionStates: StateFlow<Map<AppPermission, PermissionState>> = _permissionStates.asStateFlow()

    /**
     * Camera is optional; this returns true only when both audio and camera permissions are granted.
     */
    fun hasAudioAndCameraPermissions(context: Context): Boolean =
        checkPermissionState(context, AppPermission.AUDIO) == PermissionState.GRANTED &&
            checkPermissionState(context, AppPermission.CAMERA) == PermissionState.GRANTED

    fun hasNetworkAndBluetoothPermissions(context: Context): Boolean =
        checkPermissionState(context, AppPermission.NETWORK) == PermissionState.GRANTED &&
            checkPermissionState(context, AppPermission.BLUETOOTH) == PermissionState.GRANTED

    fun hasBackgroundExecutionPermissions(context: Context): Boolean =
        checkPermissionState(context, AppPermission.BACKGROUND) == PermissionState.GRANTED

    /**
     * Requests all declared runtime permissions for the Settings screen.
     * The dedicated Permission screen requests permissions sequentially instead.
     */
    fun requestPermissions(activity: Activity, requestCode: Int) {
        val permissions = AppPermission.entries
            .flatMap { it.getManifestPermissions().toList() }
            .distinct()
            .toTypedArray()
        if (permissions.isNotEmpty()) {
            AppPermission.entries
                .filter { permission ->
                    permission.getManifestPermissions().any(permissions::contains)
                }
                .forEach { markRequested(activity, it) }
            ActivityCompat.requestPermissions(activity, permissions, requestCode)
        }
    }

    /**
     * Records that a request for this permission group is about to be shown.
     * Call immediately before launching the Android permission request.
     */
    fun markRequested(context: Context, permission: AppPermission) {
        context.getSharedPreferences(
            REQUESTED_PERMISSIONS_PREFERENCES,
            Context.MODE_PRIVATE
        ).edit().putBoolean(permission.name, true).apply()
    }

    private fun wasRequested(context: Context, permission: AppPermission): Boolean =
        context.getSharedPreferences(
            REQUESTED_PERMISSIONS_PREFERENCES,
            Context.MODE_PRIVATE
        ).getBoolean(permission.name, false)

    /**
     * Cập nhật lại trạng thái tất cả các nhóm quyền trong ứng dụng.
     * NÊN gọi hàm này trong `onResume()` của Activity hoặc khi nhận kết quả từ Activity Result API.
     *
     * @param context Context ứng dụng Android.
     * @param activity Activity hiện tại (nếu có) để kiểm tra Rationale và phân biệt từ chối vĩnh viễn.
     * Nếu không có Activity, cờ đã hỏi chỉ cho biết quyền từng được yêu cầu; trạng thái vĩnh viễn
     * được suy ra từ cờ này nhưng không thể phân biệt chắc chắn với một lần từ chối thông thường.
     */
    fun refreshPermissionStates(context: Context, activity: Activity? = null) {
        val updatedMap = AppPermission.entries.associateWith { permission ->
            checkPermissionState(context, permission, activity)
        }
        _permissionStates.value = updatedMap
    }

    /**
     * Kiểm tra trạng thái chi tiết của một nhóm quyền [AppPermission].
     *
     * @param context Context Android.
     * @param appPermission Nhóm quyền cần kiểm tra.
     * @param activity Activity (tùy chọn) để kiểm tra rationale. Khi không có Activity, cờ đã hỏi
     * được dùng để suy ra PERMANENTLY_DENIED nhưng không thể phân biệt chắc chắn kiểu từ chối.
     * @return Trạng thái [PermissionState] (GRANTED / DENIED / PERMANENTLY_DENIED).
     */
    fun checkPermissionState(
        context: Context,
        appPermission: AppPermission,
        activity: Activity? = null
    ): PermissionState {
        // 1. Nhóm Bắt buộc chạy nền / Tắt tối ưu pin
        if (appPermission == AppPermission.BACKGROUND) {
            return if (isIgnoringBatteryOptimizations(context)) {
                PermissionState.GRANTED
            } else {
                PermissionState.DENIED
            }
        }

        // 2. Nhóm Mạng (Normal Permissions - Luôn được cấp tự động)
        if (appPermission == AppPermission.NETWORK) {
            return PermissionState.GRANTED
        }

        // 3. Các nhóm quyền Runtime (AUDIO, CAMERA, BLUETOOTH, NOTIFICATION)
        val manifestPermissions = appPermission.getManifestPermissions()

        // Nếu không có quyền runtime nào cần xin ở phiên bản SDK hiện tại
        if (manifestPermissions.isEmpty()) {
            return PermissionState.GRANTED
        }

        // Kiểm tra xem tất cả quyền trong nhóm đã được granted chưa
        val allGranted = manifestPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            return PermissionState.GRANTED
        }

        val wasRequested = wasRequested(context, appPermission)

        // ActivityCompat cần Activity để phân biệt lần từ chối thông thường với từ chối vĩnh viễn.
        if (activity != null) {
            val showRationale = manifestPermissions.any { perm ->
                ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
            }
            return if (!showRationale && wasRequested) {
                PermissionState.PERMANENTLY_DENIED
            } else {
                PermissionState.DENIED
            }
        }

        return if (wasRequested) PermissionState.PERMANENTLY_DENIED else PermissionState.DENIED
    }

    /**
     * Kiểm tra xem tất cả các quyền BẮT BUỘC ([AppPermission.isRequired] == true) đã được cấp chưa.
     * Hiện tại Micro ([AppPermission.AUDIO]) là quyền bắt buộc duy nhất; các quyền khác không chặn vào ứng dụng.
     *
     * ĐIỂM NỐI CHO TV2 & TV3:
     * - TV3 (UI): Gọi hàm này trước khi cho phép người dùng bấm "Vào Kênh Đàm Thoại".
     * - TV2 (Audio): Kiểm tra hàm này trước khi khởi chạy Foreground Service thu âm.
     *
     * @param context Context Android.
     * @return `true` nếu tất cả quyền bắt buộc đã được cấp, `false` nếu còn thiếu.
     */
    fun allRequiredGranted(context: Context): Boolean {
        return AppPermission.entries
            .filter { it.isRequired }
            .all { appPerm ->
                checkPermissionState(context, appPerm) == PermissionState.GRANTED
            }
    }

    /**
     * Returns optional permissions that are useful to recommend but do not block app entry.
     * Currently includes background execution and notifications when they are not granted.
     */
    fun recommendedMissing(context: Context): List<AppPermission> =
        listOf(AppPermission.BACKGROUND, AppPermission.NOTIFICATION)
            .filter { checkPermissionState(context, it) != PermissionState.GRANTED }

    /**
     * Kiểm tra xem ứng dụng đã được đưa vào danh sách bỏ qua tối ưu hóa pin hay chưa.
     *
     * TRÁNH BỊ TẮT ỨNG DỤNG NỀN:
     * Do đặc thù bộ đàm cần giữ kết nối Socket/WebRTC liên tục ngay cả khi tắt màn hình,
     * ứng dụng cần được chạy không bị hạn chế pin.
     *
     * @param context Context Android.
     * @return `true` nếu ứng dụng đã được bỏ qua tối ưu pin (Unrestricted).
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    /**
     * Yêu cầu mở Dialog / Màn hình cài đặt để người dùng tắt Tối ưu hóa pin cho ứng dụng.
     *
     * GHI CHÚ ĐẶC BIỆT DÀNH CHO CÁC HÃNG ĐIỆN THOẠI TRUNG QUỐC (Xiaomi, OPPO, Vivo, Realme, Samsung...):
     * Các HĐH tùy biến (MIUI, ColorOS, FunTouch, OneUI...) thường có trình quản lý pin riêng biệt (Security/Autostart).
     * Ngoài việc gọi Intent chuẩn này, người dùng cần được hướng dẫn bật thủ công "Tự khởi chạy" (Autostart).
     *
     * @param context Context Android.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = context.packageName
            try {
                // Intent trực tiếp yêu cầu bỏ tối ưu pin cho package của app
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Nếu HĐH không hỗ trợ Intent trực tiếp, mở danh sách cài đặt pin tổng quát
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        if (context !is Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    context.startActivity(fallbackIntent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
    }

    /**
     * Mở trang Cài đặt ứng dụng trong Cài đặt hệ thống (Application Details Settings).
     * Dùng khi quyền bị từ chối vĩnh viễn ([PermissionState.PERMANENTLY_DENIED]),
     * bắt buộc người dùng phải bật bằng tay.
     *
     * @param context Context Android.
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
