package com.example.damthoai.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.example.damthoai.hardware.diagnostics.FieldTestLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * Chế độ tiết kiệm năng lượng khi ứng dụng chạy nền liên tục 4 giờ (TV2 - Tuần 6).
 */
enum class PowerProfile {
    /** Đang trong cuộc gọi foreground hoặc đang bấm PTT */
    ACTIVE_CALL,
    /** Chạy nền nghe chờ bỏ túi -> giảm chu kỳ đánh thức CPU/getStats */
    BACKGROUND_STANDBY,
    /** Pin yếu (< 20%) -> tối đa hóa tiết kiệm pin & Radio */
    LOW_BATTERY_SAVER
}

/**
 * Báo cáo rà soát tiêu thụ pin của phiên chạy nền (mục tiêu: chạy liên tục >= 4 giờ).
 */
data class BatteryAuditReport(
    val sessionDurationMs: Long = 0L,
    val startBatteryPct: Int = 100,
    val currentBatteryPct: Int = 100,
    val totalDrainPct: Int = 0,
    val drainRatePctPerHour: Double = 0.0,
    val estimatedHoursRemaining: Double = 0.0,
    val temperatureC: Float = 0f,
    val isCharging: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val activeWakeLockMs: Long = 0L,
    val powerProfile: PowerProfile = PowerProfile.ACTIVE_CALL,
    val withinFourHourBudget: Boolean = true
)

/**
 * =================================================================================
 * GIÁM SÁT & TỐI ƯU TIÊU THỤ PIN CHẠY NỀN 4 GIỜ (TV2 - Tuần 6)
 * =================================================================================
 *
 * - Theo dõi mức pin, nhiệt độ pin và tốc độ tiêu hao (%/giờ) trong suốt phiên Foreground Service.
 * - Tự động đề xuất chu kỳ thu thập thống kê ([recommendedStatsIntervalMs]) để giảm CPU wakeup
 *   khi chạy nền (2s khi đang đàm thoại tích cực -> 10s khi nghe chờ bỏ túi -> 15s khi pin yếu).
 * - Kiểm tra định mức ngân sách pin cho ca sự kiện 4 tiếng (<= 6%/giờ, tổng < 24% trong 4 giờ).
 * - Ghi định kỳ `battery_audit` vào [FieldTestLogger] phục vụ đánh giá thực địa.
 */
class BatteryOptimizationMonitor(
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        const val TARGET_CONTINUOUS_HOURS = 4.0
        /** Ngân sách tối đa cho phép: <= 6%/giờ (tối đa 24% pin cho 4 tiếng chạy nền liên tục) */
        const val MAX_ACCEPTABLE_DRAIN_PCT_PER_HOUR = 6.0
        const val LOW_BATTERY_THRESHOLD_PCT = 20

        const val STATS_INTERVAL_ACTIVE_MS = 2_000L
        const val STATS_INTERVAL_BACKGROUND_MS = 10_000L
        const val STATS_INTERVAL_LOW_BATTERY_MS = 15_000L
    }

    private val _report = MutableStateFlow(BatteryAuditReport())
    val report: StateFlow<BatteryAuditReport> = _report.asStateFlow()

    private var sessionStartMs: Long = 0L
    private var startBatteryPct: Int = 100
    private var accumulatedWakeLockMs: Long = 0L
    private var wakeLockAcquiredAtMs: Long? = null
    private var sessionActive: Boolean = false

    fun startSession(context: Context) {
        val sample = readSystemBattery(context)
        startSessionWithSample(
            batteryPct = sample.batteryPct,
            temperatureC = sample.temperatureC,
            isCharging = sample.isCharging,
            isIgnoringOptimizations = sample.isIgnoringOptimizations
        )
    }

    internal fun startSessionWithSample(
        batteryPct: Int,
        temperatureC: Float = 30f,
        isCharging: Boolean = false,
        isIgnoringOptimizations: Boolean = true
    ) {
        val now = clockMs()
        sessionStartMs = now
        startBatteryPct = batteryPct.coerceIn(0, 100)
        accumulatedWakeLockMs = 0L
        wakeLockAcquiredAtMs = null
        sessionActive = true

        _report.value = BatteryAuditReport(
            sessionDurationMs = 0L,
            startBatteryPct = startBatteryPct,
            currentBatteryPct = startBatteryPct,
            totalDrainPct = 0,
            drainRatePctPerHour = 0.0,
            estimatedHoursRemaining = TARGET_CONTINUOUS_HOURS,
            temperatureC = temperatureC,
            isCharging = isCharging,
            isIgnoringBatteryOptimizations = isIgnoringOptimizations,
            activeWakeLockMs = 0L,
            powerProfile = resolveProfile(isInBackground = false, isPttActive = false, batteryPct = startBatteryPct),
            withinFourHourBudget = true
        )
    }

    fun onWakeLockAcquired() {
        if (wakeLockAcquiredAtMs == null) {
            wakeLockAcquiredAtMs = clockMs()
        }
    }

    fun onWakeLockReleased() {
        val acquiredAt = wakeLockAcquiredAtMs ?: return
        accumulatedWakeLockMs += max(0L, clockMs() - acquiredAt)
        wakeLockAcquiredAtMs = null
    }

    fun sampleNow(
        context: Context,
        isInBackground: Boolean,
        isPttActive: Boolean
    ): BatteryAuditReport {
        val sample = readSystemBattery(context)
        val updated = recordSample(
            currentBatteryPct = sample.batteryPct,
            temperatureC = sample.temperatureC,
            isCharging = sample.isCharging,
            isIgnoringOptimizations = sample.isIgnoringOptimizations,
            isInBackground = isInBackground,
            isPttActive = isPttActive
        )
        runCatching {
            FieldTestLogger.mark(
                eventType = "battery_audit",
                source = "TV2_POWER",
                extra = "pct=${updated.currentBatteryPct};drain=${updated.totalDrainPct};" +
                    "ratePerHr=${"%.2f".format(updated.drainRatePctPerHour)};" +
                    "tempC=${"%.1f".format(updated.temperatureC)};" +
                    "profile=${updated.powerProfile};within4h=${updated.withinFourHourBudget};" +
                    "wakeLockMs=${updated.activeWakeLockMs}"
            )
        }
        return updated
    }

    internal fun recordSample(
        currentBatteryPct: Int,
        temperatureC: Float = 30f,
        isCharging: Boolean = false,
        isIgnoringOptimizations: Boolean = true,
        isInBackground: Boolean = true,
        isPttActive: Boolean = false
    ): BatteryAuditReport {
        val now = clockMs()
        if (!sessionActive) {
            startSessionWithSample(currentBatteryPct, temperatureC, isCharging, isIgnoringOptimizations)
        }

        val clampedPct = currentBatteryPct.coerceIn(0, 100)
        val durationMs = max(0L, now - sessionStartMs)
        val totalDrain = max(0, startBatteryPct - clampedPct)
        val elapsedHours = durationMs / 3_600_000.0
        val drainRatePerHour = if (elapsedHours >= 1.0 / 60.0 && !isCharging) {
            totalDrain / elapsedHours
        } else {
            0.0
        }

        val estimatedHoursRemaining = when {
            isCharging -> 24.0
            drainRatePerHour > 0.05 -> clampedPct / drainRatePerHour
            else -> max(TARGET_CONTINUOUS_HOURS, clampedPct / 4.0)
        }

        val liveWakeLockMs = accumulatedWakeLockMs + (wakeLockAcquiredAtMs?.let { max(0L, now - it) } ?: 0L)
        val profile = resolveProfile(isInBackground, isPttActive, clampedPct)
        val withinBudget = isCharging ||
            drainRatePerHour <= MAX_ACCEPTABLE_DRAIN_PCT_PER_HOUR &&
            estimatedHoursRemaining >= TARGET_CONTINUOUS_HOURS - elapsedHours

        val updated = BatteryAuditReport(
            sessionDurationMs = durationMs,
            startBatteryPct = startBatteryPct,
            currentBatteryPct = clampedPct,
            totalDrainPct = totalDrain,
            drainRatePctPerHour = drainRatePerHour,
            estimatedHoursRemaining = estimatedHoursRemaining,
            temperatureC = temperatureC,
            isCharging = isCharging,
            isIgnoringBatteryOptimizations = isIgnoringOptimizations,
            activeWakeLockMs = liveWakeLockMs,
            powerProfile = profile,
            withinFourHourBudget = withinBudget
        )
        _report.value = updated
        return updated
    }

    fun stopSession() {
        onWakeLockReleased()
        sessionActive = false
    }

    /**
     * Tính chu kỳ lấy thống kê WebRTC để tối ưu pin khi chạy nền 4 giờ liên tục.
     */
    fun recommendedStatsIntervalMs(
        isInBackground: Boolean,
        isPttActive: Boolean,
        batteryPct: Int = _report.value.currentBatteryPct
    ): Long {
        return when (resolveProfile(isInBackground, isPttActive, batteryPct)) {
            PowerProfile.LOW_BATTERY_SAVER -> STATS_INTERVAL_LOW_BATTERY_MS
            PowerProfile.BACKGROUND_STANDBY -> STATS_INTERVAL_BACKGROUND_MS
            PowerProfile.ACTIVE_CALL -> STATS_INTERVAL_ACTIVE_MS
        }
    }

    private fun resolveProfile(
        isInBackground: Boolean,
        isPttActive: Boolean,
        batteryPct: Int
    ): PowerProfile = when {
        batteryPct <= LOW_BATTERY_THRESHOLD_PCT && !isPttActive -> PowerProfile.LOW_BATTERY_SAVER
        isInBackground && !isPttActive -> PowerProfile.BACKGROUND_STANDBY
        else -> PowerProfile.ACTIVE_CALL
    }

    private data class SystemBatterySample(
        val batteryPct: Int,
        val temperatureC: Float,
        val isCharging: Boolean,
        val isIgnoringOptimizations: Boolean
    )

    private fun readSystemBattery(context: Context): SystemBatterySample {
        val appCtx = context.applicationContext
        val intent = runCatching {
            appCtx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300) ?: 300
        val tempC = tempTenths / 10f

        val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignoringOpt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pm != null) {
            runCatching { pm.isIgnoringBatteryOptimizations(appCtx.packageName) }.getOrDefault(false)
        } else {
            true
        }

        return SystemBatterySample(
            batteryPct = pct,
            temperatureC = tempC,
            isCharging = isCharging,
            isIgnoringOptimizations = ignoringOpt
        )
    }
}
