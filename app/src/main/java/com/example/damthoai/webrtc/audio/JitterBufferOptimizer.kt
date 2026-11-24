package com.example.damthoai.webrtc.audio

import com.example.damthoai.webrtc.CallStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Chế độ bộ đệm chống rung giật (Jitter Buffer) thích ứng theo chất lượng mạng (TV2 - Tuần 6).
 */
enum class JitterProfile {
    /** Mạng ổn định: độ trễ tối thiểu 20ms cho phản hồi bộ đàm tức thì */
    LOW_LATENCY,
    /** Mạng dao động nhẹ: đệm 60ms để chống méo/ngắt âm */
    BALANCED,
    /** Mạng yếu/rớt gói cao: đệm 140-200ms kết hợp Opus FEC mạnh để chống giọng robot */
    HIGH_RESILIENCE
}

data class JitterBufferRecommendation(
    val profile: JitterProfile = JitterProfile.LOW_LATENCY,
    val minPlayoutDelaySec: Double = 0.02,
    val maxPackets: Int = 50,
    val fastAccelerate: Boolean = true,
    val recommendedOpusBitrateBps: Int = 24_000,
    val enableInbandFec: Boolean = true,
    val reason: String = "Mạng ổn định"
)

/**
 * =================================================================================
 * BỘ TỐI ƯU HÓA JITTER BUFFER CHỐNG MÉO TIẾNG (TV2 - Tuần 6)
 * =================================================================================
 *
 * Phân tích liên tục [CallStats] (`jitterMs`, `intervalLossRate`, `concealmentRatio`, `rttMs`)
 * để điều chỉnh động `RtpReceiver.setJitterBufferMinimumDelay(seconds)` và bitrate Opus:
 * - Tự động mở rộng vùng đệm (tăng ngay lập tức - fast attack) khi phát hiện jitter cao hoặc rớt gói.
 * - Chỉ thu hẹp lại độ trễ sau [stableWindowsForDowngrade] cửa sổ ổn định liên tiếp (hysteresis)
 *   để tránh làm méo tiếng do thay đổi playout delay quá thường xuyên.
 */
class JitterBufferOptimizer(
    private val stableWindowsForDowngrade: Int = 3
) {
    companion object {
        const val MIN_DELAY_LOW_LATENCY_SEC = 0.02
        const val MIN_DELAY_BALANCED_SEC = 0.06
        const val MIN_DELAY_HIGH_RESILIENCE_SEC = 0.14
        const val MIN_DELAY_SEVERE_SEC = 0.20

        const val BITRATE_NORMAL_BPS = 24_000
        const val BITRATE_CONGESTED_BPS = 18_000
    }

    private val _recommendation = MutableStateFlow(JitterBufferRecommendation())
    val recommendation: StateFlow<JitterBufferRecommendation> = _recommendation.asStateFlow()

    private var consecutiveBetterWindows = 0

    fun evaluate(stats: CallStats): JitterBufferRecommendation {
        val candidate = computeRawCandidate(stats)
        val current = _recommendation.value

        val result = when {
            // Cần tăng bảo vệ (chống méo tiếng ngay lập tức)
            candidate.profile.ordinal > current.profile.ordinal -> {
                consecutiveBetterWindows = 0
                candidate
            }
            // Cùng cấp độ nhưng cần tăng delay lên mức khắc nghiệt
            candidate.profile == current.profile && candidate.minPlayoutDelaySec > current.minPlayoutDelaySec -> {
                consecutiveBetterWindows = 0
                candidate
            }
            // Mạng tốt hơn -> chờ đủ số cửa sổ ổn định mới giảm đệm (hysteresis)
            candidate.profile.ordinal < current.profile.ordinal ||
                candidate.minPlayoutDelaySec < current.minPlayoutDelaySec -> {
                consecutiveBetterWindows++
                if (consecutiveBetterWindows >= stableWindowsForDowngrade) {
                    consecutiveBetterWindows = 0
                    candidate
                } else {
                    current
                }
            }
            else -> {
                consecutiveBetterWindows = 0
                current
            }
        }

        _recommendation.value = result
        return result
    }

    fun reset() {
        consecutiveBetterWindows = 0
        _recommendation.value = JitterBufferRecommendation()
    }

    private fun computeRawCandidate(stats: CallStats): JitterBufferRecommendation {
        val severe = stats.jitterMs >= 80.0 ||
            stats.intervalLossRate >= 0.12 ||
            stats.concealmentRatio >= 0.10

        if (severe) {
            return JitterBufferRecommendation(
                profile = JitterProfile.HIGH_RESILIENCE,
                minPlayoutDelaySec = MIN_DELAY_SEVERE_SEC,
                maxPackets = 200,
                fastAccelerate = false,
                recommendedOpusBitrateBps = BITRATE_CONGESTED_BPS,
                enableInbandFec = true,
                reason = "Mạng nghẽn nặng (jitter=${"%.1f".format(stats.jitterMs)}ms, loss=${"%.1f".format(stats.intervalLossRate * 100)}%)"
            )
        }

        val highResilience = stats.jitterMs >= 45.0 ||
            stats.intervalLossRate >= 0.05 ||
            stats.concealmentRatio >= 0.05

        if (highResilience) {
            return JitterBufferRecommendation(
                profile = JitterProfile.HIGH_RESILIENCE,
                minPlayoutDelaySec = MIN_DELAY_HIGH_RESILIENCE_SEC,
                maxPackets = 150,
                fastAccelerate = false,
                recommendedOpusBitrateBps = BITRATE_CONGESTED_BPS,
                enableInbandFec = true,
                reason = "Rớt gói/jitter cao (jitter=${"%.1f".format(stats.jitterMs)}ms, loss=${"%.1f".format(stats.intervalLossRate * 100)}%)"
            )
        }

        val balanced = stats.jitterMs >= 15.0 ||
            stats.intervalLossRate >= 0.01 ||
            stats.concealmentRatio >= 0.015 ||
            (stats.rttMs != null && stats.rttMs >= 180)

        if (balanced) {
            return JitterBufferRecommendation(
                profile = JitterProfile.BALANCED,
                minPlayoutDelaySec = MIN_DELAY_BALANCED_SEC,
                maxPackets = 100,
                fastAccelerate = true,
                recommendedOpusBitrateBps = BITRATE_NORMAL_BPS,
                enableInbandFec = true,
                reason = "Mạng dao động nhẹ (jitter=${"%.1f".format(stats.jitterMs)}ms)"
            )
        }

        return JitterBufferRecommendation(
            profile = JitterProfile.LOW_LATENCY,
            minPlayoutDelaySec = MIN_DELAY_LOW_LATENCY_SEC,
            maxPackets = 50,
            fastAccelerate = true,
            recommendedOpusBitrateBps = BITRATE_NORMAL_BPS,
            enableInbandFec = true,
            reason = "Mạng ổn định"
        )
    }
}
