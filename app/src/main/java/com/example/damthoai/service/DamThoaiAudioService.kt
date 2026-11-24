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
import com.example.damthoai.MainActivity
import com.example.damthoai.R
import com.example.damthoai.hardware.HandsFreeSession
import com.example.damthoai.hardware.PttController
import com.example.damthoai.hardware.audio.AudioFocusGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Trạng thái dịch vụ chạy ngầm đàm thoại (TV2 - Tuần 3 & Tuần 6).
 */
data class BackgroundServiceState(
    val isRunning: Boolean = false,
    val channelName: String = "",
    val wakeLockHeld: Boolean = false,
    val wifiLockHeld: Boolean = false,
    val startedAtMs: Long = 0L
)

/**
 * =================================================================================
 * DỊCH VỤ ÂM THANH NỀN KÈM SMART WAKELOCK (DamThoaiAudioService - TV2 Tuần 3 & 6)
 * =================================================================================
 *
 * 1. Tuần 3: Foreground Service (`FOREGROUND_SERVICE_TYPE_MICROPHONE`) kèm `PARTIAL_WAKE_LOCK`
 *    giữ cho ứng dụng không bị hệ điều hành Android đóng khi tắt màn hình bỏ túi quần.
 * 2. Điểm nối TV4:
 *    - Bật `PttController.setBackgroundSessionActive(true)` để `MainActivity.onPause()`
 *      không cưỡng chế nhả mic khi tắt màn hình.
 *    - Gọi `HandsFreeSession.start(this)` / `stop()` cho nút Media tai nghe khi chạy nền.
 *    - Gọi `AudioFocusGuard.start(this)` / `stop()` quản lý Audio Focus chuẩn thoại.
 * 3. Tuần 6 (Tối ưu pin chạy nền 4 giờ liên tục):
 *    - Gia hạn WakeLock theo từng khung 15 phút (tránh rò rỉ WakeLock nếu app treo) và chỉ
 *      giữ WifiLock chế độ độ trễ thấp khi đang đàm thoại tích cực.
 *    - Chạy vòng kiểm toán pin (`BatteryOptimizationMonitor`) mỗi 5 phút ghi vào `FieldTestLogger`.
 */
class DamThoaiAudioService : Service() {

    companion object {
        private const val TAG = "DamThoaiAudioService"
        private const val NOTIFICATION_ID = 2026
        private const val CHANNEL_ID = "dam_thoai_call_session_channel"

        const val ACTION_START = "com.example.damthoai.action.START_BACKGROUND_AUDIO"
        const val ACTION_STOP = "com.example.damthoai.action.STOP_BACKGROUND_AUDIO"
        private const val EXTRA_CHANNEL_NAME = "extra_channel_name"

        /** Khung gia hạn WakeLock 15 phút để đảm bảo an toàn pin và tự giải phóng nếu lỗi */
        private const val WAKE_LOCK_SLICE_MS = 15 * 60 * 1000L
        private const val WAKE_LOCK_RENEW_INTERVAL_MS = 12 * 60 * 1000L
        private const val BATTERY_AUDIT_INTERVAL_MS = 5 * 60 * 1000L

        private val _serviceState = MutableStateFlow(BackgroundServiceState())
        val serviceState: StateFlow<BackgroundServiceState> = _serviceState.asStateFlow()

        val isRunning: Boolean get() = _serviceState.value.isRunning

        fun start(context: Context, channelName: String = "Hậu cần – An ninh") {
            val intent = Intent(context.applicationContext, DamThoaiAudioService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CHANNEL_NAME, channelName)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Không thể khởi động DamThoaiAudioService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, DamThoaiAudioService::class.java)
                )
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLockRenewJob: Job? = null
    private var batteryAuditJob: Job? = null
    private var queueObserverJob: Job? = null

    private var currentChannelName: String = "Hậu cần – An ninh"
    private var startedAtMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Khởi tạo Foreground Service đàm thoại chạy nền")
        startedAtMs = System.currentTimeMillis()
        createNotificationChannel()

        val callManager = DamThoaiCallManager.getInstance(applicationContext)
        callManager.batteryMonitor.startSession(applicationContext)

        acquireSmartLocks(callManager)

        // ĐIỂM NỐI TV4: đăng ký phiên chạy nền và bật điều phối tai nghe rảnh tay + audio focus
        PttController.setBackgroundSessionActive(true)
        HandsFreeSession.start(applicationContext)
        AudioFocusGuard.start(applicationContext)

        startBatteryAuditLoop(callManager)
        observeOfflineQueueForNotification(callManager)

        _serviceState.value = BackgroundServiceState(
            isRunning = true,
            channelName = currentChannelName,
            wakeLockHeld = wakeLock?.isHeld == true,
            wifiLockHeld = wifiLock?.isHeld == true,
            startedAtMs = startedAtMs
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        currentChannelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME) ?: currentChannelName
        val queuedCount = DamThoaiCallManager.getInstance(applicationContext).offlineVoiceQueue.state.value.queuedCount
        val notification = buildForegroundNotification(currentChannelName, queuedCount)

        startForegroundSafely(notification)

        _serviceState.value = _serviceState.value.copy(
            isRunning = true,
            channelName = currentChannelName,
            wakeLockHeld = wakeLock?.isHeld == true,
            wifiLockHeld = wifiLock?.isHeld == true
        )
        return START_STICKY
    }

    private fun startForegroundSafely(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (se: SecurityException) {
            // Android 14+ ném SecurityException nếu chưa cấp RECORD_AUDIO khi gọi type microphone
            Log.w(TAG, "Chưa đủ quyền microphone cho Foreground Service, hạ về foreground thường: ${se.message}")
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi startForeground: ${e.message}", e)
        }
    }

    /**
     * Giữ CPU WakeLock phân đoạn (15 phút/chu kỳ) + WifiLock độ trễ thấp (tối ưu pin 4 giờ).
     */
    private fun acquireSmartLocks(callManager: DamThoaiCallManager) {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DamThoai:AudioForegroundWakeLock"
        )?.apply {
            setReferenceCounted(false)
            runCatching {
                acquire(WAKE_LOCK_SLICE_MS)
                callManager.batteryMonitor.onWakeLockAcquired()
            }
        }

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiLockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wm?.createWifiLock(wifiLockMode, "DamThoai:AudioWifiLock")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }

        // Tự động gia hạn khung WakeLock khi dịch vụ vẫn hoạt động
        wakeLockRenewJob?.cancel()
        wakeLockRenewJob = serviceScope.launch {
            while (isActive) {
                delay(WAKE_LOCK_RENEW_INTERVAL_MS)
                runCatching {
                    wakeLock?.acquire(WAKE_LOCK_SLICE_MS)
                }
            }
        }
    }

    private fun startBatteryAuditLoop(callManager: DamThoaiCallManager) {
        batteryAuditJob?.cancel()
        batteryAuditJob = serviceScope.launch {
            while (isActive) {
                callManager.batteryMonitor.sampleNow(
                    context = applicationContext,
                    isInBackground = true,
                    isPttActive = PttController.isPttActive.value
                )
                delay(BATTERY_AUDIT_INTERVAL_MS)
            }
        }
    }

    private fun observeOfflineQueueForNotification(callManager: DamThoaiCallManager) {
        queueObserverJob?.cancel()
        queueObserverJob = serviceScope.launch {
            callManager.offlineVoiceQueue.state.collect { queueState ->
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(
                    NOTIFICATION_ID,
                    buildForegroundNotification(currentChannelName, queueState.queuedCount)
                )
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Phiên Đàm Thoại Chạy Nền",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Giữ kết nối bộ đàm, nút PTT và hàng đợi ngoại tuyến khi tắt màn hình"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(channelName: String, queuedCount: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, DamThoaiAudioService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtitle = if (queuedCount > 0) {
            "Giữ phím Âm lượng/Tai nghe để nói · $queuedCount tin chờ gửi"
        } else {
            "Giữ phím Âm lượng hoặc nút Tai nghe để nói khi tắt màn hình"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bộ Đàm Đang Chạy Nền · $channelName")
            .setContentText(subtitle)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Dừng chạy nền", stopPendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Dừng Foreground Service và giải phóng WakeLock")
        wakeLockRenewJob?.cancel()
        batteryAuditJob?.cancel()
        queueObserverJob?.cancel()

        val callManager = DamThoaiCallManager.getInstance(applicationContext)
        callManager.batteryMonitor.stopSession()

        // ĐIỂM NỐI TV4: tắt cờ chạy nền & giải phóng phiên tai nghe / audio focus
        HandsFreeSession.stop()
        PttController.setBackgroundSessionActive(false)
        PttController.forceReleasePtt()
        AudioFocusGuard.stop()

        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        runCatching {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        }
        wakeLock = null
        wifiLock = null

        _serviceState.value = BackgroundServiceState(isRunning = false)
        serviceScope.cancel()
        super.onDestroy()
    }
}
