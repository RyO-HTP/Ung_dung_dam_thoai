package com.example.damthoai.hardware.emergency

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.damthoai.MainActivity
import com.example.damthoai.R
import com.example.damthoai.permission.AppPermission
import com.example.damthoai.permission.PermissionManager
import com.example.damthoai.permission.PermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hiển thị cảnh báo khẩn cấp khi ứng dụng ở nền hoặc màn hình tắt.
 *
 * ĐIỂM NỐI:
 * - [EmergencyAlertManager] đăng và hủy thông báo; các nút chuyển hành động tới [MainActivity].
 * - [fullScreenIntentAllowed] cho TV3 kiểm tra xem quyền mở màn hình khẩn cấp có được phép không.
 * - [openFullScreenIntentSettings] cho TV3 dẫn người dùng bật quyền nếu bị Android 14 chặn.
 *
 * GHI CHÚ CHÍNH SÁCH GOOGLE PLAY:
 * Quyền `USE_FULL_SCREEN_INTENT` trên Android 14 (API 34+) bị Google Play kiểm soát gắt gao.
 * Chỉ ứng dụng Gọi điện (Calling) hoặc Báo thức (Alarm) mới được tự động bật. Nếu không có quyền,
 * ứng dụng sẽ hạ cấp xuống thông báo Heads-Up thường (IMPORTANCE_HIGH).
 */
internal object EmergencyNotifier {

    private const val TAG = "EmergencyNotifier"
    private const val CHANNEL_ID = "emergency"
    private const val NOTIFICATION_ID = 2301
    const val ACTION_ACKNOWLEDGE = "com.example.damthoai.action.EMERGENCY_ACKNOWLEDGE"
    const val ACTION_SNOOZE = "com.example.damthoai.action.EMERGENCY_SNOOZE"

    @Volatile
    private var channelCreated = false

    private val _fullScreenIntentAllowed = MutableStateFlow(true)
    val fullScreenIntentAllowed: StateFlow<Boolean> = _fullScreenIntentAllowed.asStateFlow()

    fun isFullScreenIntentAllowed(context: Context): Boolean {
        val allowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.canUseFullScreenIntent() ?: true
        } else {
            true
        }
        _fullScreenIntentAllowed.value = allowed
        return allowed
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                Log.w(TAG, "Không thể mở Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, mở App Details")
            }
        }
        PermissionManager.openAppSettings(context)
    }

    fun showIfAllowed(context: Context, alert: EmergencyAlert) {
        val appContext = context.applicationContext
        val notificationPermissionGranted = try {
            PermissionManager.checkPermissionState(
                appContext,
                AppPermission.NOTIFICATION
            ) == PermissionState.GRANTED
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể kiểm tra quyền thông báo", exception)
            false
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Không thể kiểm tra trạng thái quyền thông báo", exception)
            false
        }
        if (!notificationPermissionGranted) return

        val canUseFullScreen = isFullScreenIntentAllowed(appContext)

        try {
            if (!ensureChannel(appContext)) return
            val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentTitle(appContext.getString(R.string.emergency_notification_title))
                .setContentText(
                    appContext.getString(
                        R.string.emergency_notification_message,
                        alert.fromName,
                        alert.fromRole
                    )
                )
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(activityPendingIntent(appContext, null, REQUEST_CODE_OPEN))
                .addAction(
                    0,
                    appContext.getString(R.string.emergency_action_answer),
                    activityPendingIntent(
                        appContext,
                        ACTION_ACKNOWLEDGE,
                        REQUEST_CODE_ACKNOWLEDGE
                    )
                )
                .addAction(
                    0,
                    appContext.getString(R.string.emergency_action_snooze),
                    activityPendingIntent(appContext, ACTION_SNOOZE, REQUEST_CODE_SNOOZE)
                )

            if (canUseFullScreen) {
                builder.setFullScreenIntent(
                    activityPendingIntent(appContext, null, REQUEST_CODE_FULL_SCREEN),
                    true
                )
            }

            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, builder.build())
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể đăng thông báo khẩn cấp", exception)
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Không thể tạo thông báo khẩn cấp ở trạng thái hiện tại", exception)
        }
    }

    fun cancel(context: Context) {
        try {
            NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể hủy thông báo khẩn cấp", exception)
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Không thể hủy thông báo ở trạng thái hiện tại", exception)
        }
    }

    private fun ensureChannel(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || channelCreated) return true

        return synchronized(this) {
            if (channelCreated) return@synchronized true
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (manager == null) {
                Log.e(TAG, "Không tìm thấy NotificationManager")
                return@synchronized false
            }
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.emergency_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.emergency_notification_channel_description)
                }
            )
            channelCreated = true
            true
        }
    }

    private fun activityPendingIntent(
        context: Context,
        action: String?,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val REQUEST_CODE_OPEN = 2302
    private const val REQUEST_CODE_FULL_SCREEN = 2303
    private const val REQUEST_CODE_ACKNOWLEDGE = 2304
    private const val REQUEST_CODE_SNOOZE = 2305
}
