package com.example.damthoai.hardware

import android.content.ComponentName
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Trạng thái bật Trợ năng của dịch vụ lọc phím Volume.
 *
 * ĐIỂM NỐI TV3: dùng [isServiceEnabled] cho thẻ nhắc bật Trợ năng và nút mở cài đặt.
 */
object AccessibilityStatus {

    private const val TAG = "AccessibilityStatus"

    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    fun refresh(context: Context) {
        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val expected = ComponentName(
                context,
                VolumeKeyAccessibilityService::class.java
            )
            _isServiceEnabled.value = enabledServices
                .split(':')
                .mapNotNull { ComponentName.unflattenFromString(it) }
                .any { it == expected }
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể đọc trạng thái AccessibilityService", exception)
            _isServiceEnabled.value = false
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Không thể làm mới trạng thái AccessibilityService", exception)
            _isServiceEnabled.value = false
        }
    }

    fun openSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            if (context !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể mở cài đặt Trợ năng", exception)
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Không thể mở cài đặt Trợ năng ở trạng thái hiện tại", exception)
        } catch (exception: ActivityNotFoundException) {
            Log.e(TAG, "Thiết bị không có trang cài đặt Trợ năng khả dụng", exception)
        }
    }
}
