package com.example.damthoai.hardware.emergency

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.damthoai.MainActivity
import com.example.damthoai.R
import com.example.damthoai.permission.AppPermission
import com.example.damthoai.permission.PermissionManager
import com.example.damthoai.permission.PermissionState

/**
 * Hiển thị cảnh báo khẩn cấp khi ứng dụng ở nền hoặc màn hình tắt.
 *
 * ĐIỂM NỐI: [EmergencyAlertManager] đăng và hủy thông báo; các nút chuyển hành động tới
 * [MainActivity].
 *
 * Android 14 giới hạn quyền full-screen intent cho ứng dụng gọi điện hoặc báo thức; cần cân nhắc
 * điều kiện Google Play trước khi phát hành.
 */
internal object EmergencyNotifier {

    private const val TAG = "EmergencyNotifier"
    private const val CHANNEL_ID = "emergency"
    private const val NOTIFICATION_ID = 2301
    const val ACTION_ACKNOWLEDGE = "com.example.damthoai.action.EMERGENCY_ACKNOWLEDGE"
    const val ACTION_SNOOZE = "com.example.damthoai.action.EMERGENCY_SNOOZE"

    @Volatile
    private var channelCreated = false

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

        try {
            if (!ensureChannel(appContext)) return
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
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
                .setFullScreenIntent(
                    activityPendingIntent(appContext, null, REQUEST_CODE_FULL_SCREEN),
                    true
                )
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
                .build()
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
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
