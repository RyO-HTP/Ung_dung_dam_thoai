package com.example.damthoai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.damthoai.MainActivity
import com.example.damthoai.R
import com.example.damthoai.hardware.HandsFreeSession
import com.example.damthoai.hardware.PttController
import com.example.damthoai.network.HandoverSignalingClient
import com.example.damthoai.network.NetworkChangeMonitor
import com.example.damthoai.network.NetworkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * =================================================================================
 * DỊCH VỤ ÂM THANH NỀN (DamThoaiAudioService) — FOREGROUND SERVICE TUẦN 3
 * VAI TRÒ: Thành viên 2 (Client Network & Audio Background)
 * =================================================================================
 *
 * Chức năng:
 * 1. Chạy ngầm liên tục khi người dùng khóa màn hình hoặc dùng Chế độ Bỏ túi (Pocket Mode).
 * 2. ĐIỂM NỐI TV4:
 *    - Gọi [HandsFreeSession.start] để mở điều phối nút tai nghe khi chạy nền.
 *    - Gọi [PttController.setBackgroundSessionActive(true)] để MainActivity không cưỡng chế
 *      ngắt micro khi Activity tạm dừng (onPause).
 * 3. ĐIỂM NỐI TV1:
 *    - Tích hợp [NetworkChangeMonitor] và [HandoverSignalingClient] để thực hiện
 *      Seamless Handover (báo cáo chuyển mạng Wi-Fi <-> 4G cho server.js cổng 8080).
 */
class DamThoaiAudioService : Service() {

    companion object {
        private const val TAG = "DamThoaiAudioService"
        private const val NOTIFICATION_ID = 2026
        private const val CHANNEL_ID = "dam_thoai_call_session_channel"

        private const val EXTRA_CHANNEL_NAME = "extra_channel_name"
        private const val EXTRA_USER_ID = "extra_user_id"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        /**
         * Bật phiên đàm thoại chạy nền
         */
        fun start(context: Context, channelName: String = "Kênh Hậu Cần", userId: String = "Tong_Dao_Dien") {
            val intent = Intent(context, DamThoaiAudioService::class.java).apply {
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_USER_ID, userId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Dừng phiên đàm thoại chạy nền
         */
        fun stop(context: Context) {
            val intent = Intent(context, DamThoaiAudioService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var networkMonitor: NetworkChangeMonitor
    private val signalingClient = HandoverSignalingClient()

    private var currentChannel: String = "Kênh Đàm Thoại"
    private var currentUserId: String = "User"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Đang khởi tạo Foreground Service đàm thoại")
        createNotificationChannel()

        // 1. Giữ CPU không bị deep-sleep khi khóa màn hình
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DamThoai:AudioServiceWakeLock"
        ).apply {
            acquire(24 * 60 * 60 * 1000L) // Giữ tối đa 24h
        }

        // 2. ĐIỂM NỐI TV4: Bật cờ chạy nền cho PTT và kích hoạt phiên tai nghe rảnh tay
        PttController.setBackgroundSessionActive(true)
        HandsFreeSession.start(this)

        // 3. Khởi tạo giám sát chuyển mạng Seamless Handover (Tuần 3)
        networkMonitor = NetworkChangeMonitor(this)
        networkMonitor.startMonitoring { newNetworkType ->
            handleSeamlessHandover(newNetworkType)
        }

        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentChannel = intent?.getStringExtra(EXTRA_CHANNEL_NAME) ?: currentChannel
        currentUserId = intent?.getStringExtra(EXTRA_USER_ID) ?: currentUserId

        val notification = buildForegroundNotification(currentChannel)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Kết nối vào Signaling Handover Server (cổng 8080 của TV1)
        signalingClient.connect(userId = currentUserId)

        Log.d(TAG, "onStartCommand: Đang phục vụ kênh [$currentChannel] cho User [$currentUserId]")
        return START_STICKY
    }

    /**
     * Xử lý chuyển mạng tức thì (Seamless Handover) khi máy chuyển giữa Wi-Fi và 4G
     */
    private fun handleSeamlessHandover(newNetworkType: NetworkType) {
        serviceScope.launch {
            Log.d(TAG, "[SEAMLESS HANDOVER] Đang báo cáo sự kiện chuyển mạng [$newNetworkType] lên Server 8080...")
            val success = signalingClient.sendNetworkChange(newNetworkType)
            if (success) {
                Log.d(TAG, "[SEAMLESS HANDOVER] Máy chủ đã ghi nhận chuyển mạng, giữ phiên thành công!")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Phiên Đàm Thoại Bộ Đàm",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Duy trì kết nối âm thanh và nút PTT khi tắt màn hình hoặc bỏ túi"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(channelName: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bộ Đàm Đang Hoạt Động · $channelName")
            .setContentText("Nhấn giữ phím Âm lượng hoặc Nút tai nghe để Nói")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Giải phóng Foreground Service đàm thoại")

        // 1. ĐIỂM NỐI TV4: Ngắt cờ chạy nền và tắt phiên tai nghe
        HandsFreeSession.stop()
        PttController.setBackgroundSessionActive(false)
        PttController.forceReleasePtt()

        // 2. Dừng giám sát mạng và ngắt kết nối Signaling
        networkMonitor.stopMonitoring()
        signalingClient.disconnect()

        // 3. Giải phóng WakeLock
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (ignored: Exception) {}
        wakeLock = null

        serviceScope.cancel()
        _isRunning.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
