package com.example.damthoai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.damthoai.MainActivity
import com.example.damthoai.R
import com.example.damthoai.hardware.PttController

/**
 * Giữ phiên đàm thoại chạy khi app ở nền / tắt màn hình.
 *
 * Không có service này, Android chặn micro khi app ở nền (Android 9+) và có thể tắt app hoặc cắt
 * mạng khi máy ngủ (Doze). Service:
 *  - chạy foreground loại "microphone" kèm thông báo cố định "Đang trong kênh…",
 *  - giữ CPU (PARTIAL_WAKE_LOCK) và Wi-Fi (WifiLock) thức để WebSocket + WebRTC không bị ngắt,
 *  - báo PttController là đang có phiên nền, để MainActivity.onPause() không cưỡng chế nhả mic.
 *
 * Được bật/tắt tự động theo DamThoaiCallManager.serverStatus (xem AndroidPlatform.install).
 */
class DamThoaiCallService : Service() {

    companion object {
        private const val TAG = "DamThoaiCallService"
        private const val CHANNEL_ID = "damthoai_call_session"
        private const val NOTIFICATION_ID = 4201
        private const val EXTRA_EVENT_NAME = "event_name"

        /** Bắt đầu (hoặc cập nhật tên sự kiện trên thông báo). Gọi khi app đang ở foreground. */
        fun start(context: Context, eventName: String) {
            val intent = Intent(context, DamThoaiCallService::class.java).putExtra(EXTRA_EVENT_NAME, eventName)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Android 12+: không được bật foreground service khi app đang ở nền
                Log.w(TAG, "Không bật được phiên chạy nền: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DamThoaiCallService::class.java))
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val eventName = intent?.getStringExtra(EXTRA_EVENT_NAME).orEmpty()
        val notification = buildNotification(eventName)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            )
        } catch (e: Exception) {
            // VD chưa cấp quyền micro (Android 14 bắt buộc khi khai báo loại microphone)
            Log.e(TAG, "startForeground thất bại: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        acquireLocks()
        PttController.setBackgroundSessionActive(true)
        // Bị hệ thống tắt thì không tự khởi động lại: trạng thái sự kiện nằm trong bộ nhớ app,
        // người dùng mở lại app để vào kênh.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        PttController.setBackgroundSessionActive(false)
        releaseLocks()
        super.onDestroy()
    }

    private fun buildNotification(eventName: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Phiên đàm thoại", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Giữ bộ đàm hoạt động khi tắt màn hình"
                    setShowBadge(false)
                }
            )
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radio)
            .setContentTitle("Đàm Thoại đang hoạt động")
            .setContentText(if (eventName.isBlank()) "Đang trong kênh" else "Đang trong kênh: $eventName")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DamThoai:CallSession")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }
        if (wifiLock?.isHeld != true) {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(mode, "DamThoai:CallSession")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        wifiLock = null
    }
}
