package com.example.damthoai.webrtc.audio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Mức độ ưu tiên của kênh đàm thoại phục vụ tính năng Audio Ducking (TV2 - Tuần 4).
 * Số [rank] càng nhỏ thì độ ưu tiên càng cao (ví dụ kênh Toàn sự kiện áp đảo kênh phụ).
 */
enum class ChannelPriority(val rank: Int) {
    PRIMARY(1),
    STAGE(2),
    SECONDARY(3),
    AUXILIARY(4)
}

/**
 * Trạng thái âm lượng thời gian thực của một kênh.
 */
data class ChannelAudioState(
    val channelId: String,
    val displayName: String,
    val priority: ChannelPriority,
    val userVolume: Float,
    val effectiveVolume: Float,
    val isDucked: Boolean,
    val duckPercent: Int,
    val duckedByChannel: String? = null
)

/**
 * =================================================================================
 * BỘ ĐIỀU KHIỂN AUDIO DUCKING ĐA KÊNH (TV2 - Tuần 4)
 * =================================================================================
 *
 * Tự động hạ thấp âm lượng các kênh phụ (mặc định hạ 80%, còn 20% âm lượng gốc) khi có
 * người nói lệnh trên kênh có mức ưu tiên cao hơn hoặc khi phát cảnh báo khẩn cấp.
 * Khi kênh chính ngừng nói, giữ một khoảng trễ hồi âm ([releaseHoldMs]) rồi khôi phục
 * về đúng mức [ChannelAudioState.userVolume] của người dùng để tránh giật âm thanh.
 */
class AudioDuckingController(
    private val duckReductionRatio: Float = DEFAULT_DUCK_REDUCTION,
    private val releaseHoldMs: Long = DEFAULT_RELEASE_HOLD_MS,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onVolumeApplied: (channelId: String, effectiveVolume: Float) -> Unit = { _, _ -> }
) {
    companion object {
        /** Hạ 80% âm lượng kênh phụ khi kênh ưu tiên có người nói (khớp nhãn "HẠ ÂM 80%") */
        const val DEFAULT_DUCK_REDUCTION = 0.80f
        const val DEFAULT_RELEASE_HOLD_MS = 450L

        const val CHANNEL_ALL_EVENT = "Toàn sự kiện"
        const val CHANNEL_STAGE = "Sân khấu"
        const val CHANNEL_SUPPORT = "Hậu cần – An ninh"
    }

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var releaseHoldJob: Job? = null

    private data class ChannelEntry(
        val channelId: String,
        val displayName: String,
        val priority: ChannelPriority,
        var userVolume: Float
    )

    private val channels = LinkedHashMap<String, ChannelEntry>()
    private var duckingEnabled: Boolean = true
    private var emergencyActive: Boolean = false
    private var activeSpeakingChannels: Set<String> = emptySet()
    private var effectiveSpeakingChannels: Set<String> = emptySet()

    private val _channelStates = MutableStateFlow<Map<String, ChannelAudioState>>(emptyMap())
    val channelStates: StateFlow<Map<String, ChannelAudioState>> = _channelStates.asStateFlow()

    private val _isDuckingActive = MutableStateFlow(false)
    val isDuckingActive: StateFlow<Boolean> = _isDuckingActive.asStateFlow()

    init {
        registerDefaultEventChannels()
    }

    /**
     * Đăng ký 3 kênh chuẩn của hệ thống đàm thoại sự kiện.
     */
    fun registerDefaultEventChannels() {
        registerChannel(CHANNEL_ALL_EVENT, CHANNEL_ALL_EVENT, ChannelPriority.PRIMARY, 1.0f)
        registerChannel(CHANNEL_STAGE, CHANNEL_STAGE, ChannelPriority.STAGE, 0.7f)
        registerChannel(CHANNEL_SUPPORT, CHANNEL_SUPPORT, ChannelPriority.SECONDARY, 1.0f)
    }

    fun registerChannel(
        channelId: String,
        displayName: String = channelId,
        priority: ChannelPriority,
        initialVolume: Float = 1.0f
    ) {
        val clamped = initialVolume.coerceIn(0f, 1f)
        synchronized(lock) {
            channels[channelId] = ChannelEntry(channelId, displayName, priority, clamped)
        }
        recalculateAndEmit()
    }

    fun setUserVolume(channelId: String, volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        synchronized(lock) {
            val existing = channels[channelId]
            if (existing != null) {
                existing.userVolume = clamped
            } else {
                channels[channelId] = ChannelEntry(channelId, channelId, ChannelPriority.SECONDARY, clamped)
            }
        }
        recalculateAndEmit()
    }

    /**
     * Đồng bộ từ `HandsFreeSettings.duckSecondaryEnabled`.
     */
    fun setDuckingEnabled(enabled: Boolean) {
        synchronized(lock) {
            if (duckingEnabled == enabled) return
            duckingEnabled = enabled
            if (!enabled) {
                releaseHoldJob?.cancel()
                effectiveSpeakingChannels = activeSpeakingChannels
            }
        }
        recalculateAndEmit()
    }

    /**
     * Khi có cảnh báo khẩn cấp, hạ âm tất cả kênh thường để ưu tiên âm cảnh báo / chỉ huy.
     */
    fun setEmergencyOverrideActive(active: Boolean) {
        synchronized(lock) {
            if (emergencyActive == active) return
            emergencyActive = active
        }
        recalculateAndEmit()
    }

    /**
     * Cập nhật tập hợp các kênh hiện đang có người nói (nối với `SpeakingStateRepository`).
     */
    fun updateSpeakingChannels(speakingIds: Set<String>) {
        val normalized = speakingIds.map(::resolveChannelKey).toSet()
        val shouldDelayRelease: Boolean
        synchronized(lock) {
            val previousTopRank = highestSpeakingRankLocked(effectiveSpeakingChannels)
            val newTopRank = highestSpeakingRankLocked(normalized)
            activeSpeakingChannels = normalized

            // Nếu vừa xuất hiện người nói ưu tiên cao hơn -> duck ngay lập tức (attack = 0ms)
            if (newTopRank <= previousTopRank || releaseHoldMs <= 0L) {
                releaseHoldJob?.cancel()
                effectiveSpeakingChannels = normalized
                shouldDelayRelease = false
            } else {
                // Kênh ưu tiên vừa dứt lời -> giữ nhẹ trong releaseHoldMs trước khi phục hồi
                shouldDelayRelease = true
            }
        }

        if (shouldDelayRelease) {
            releaseHoldJob?.cancel()
            releaseHoldJob = scope.launch {
                delay(releaseHoldMs)
                synchronized(lock) {
                    effectiveSpeakingChannels = activeSpeakingChannels
                }
                recalculateAndEmit()
            }
        } else {
            recalculateAndEmit()
        }
    }

    fun getEffectiveVolume(channelId: String): Float {
        val key = resolveChannelKey(channelId)
        return _channelStates.value[key]?.effectiveVolume ?: 1.0f
    }

    private fun resolveChannelKey(rawId: String): String {
        val trimmed = rawId.trim()
        return when (trimmed.lowercase()) {
            "ch_01", "all_event", "toàn sự kiện" -> CHANNEL_ALL_EVENT
            "ch_02", "stage", "sân khấu" -> CHANNEL_STAGE
            "ch_03", "support", "hậu cần – an ninh", "hậu cần - an ninh" -> CHANNEL_SUPPORT
            else -> trimmed
        }
    }

    private fun highestSpeakingRankLocked(speakingIds: Set<String>): Int {
        if (speakingIds.isEmpty()) return Int.MAX_VALUE
        var minRank = Int.MAX_VALUE
        for (id in speakingIds) {
            val rank = channels[id]?.priority?.rank ?: ChannelPriority.SECONDARY.rank
            if (rank < minRank) minRank = rank
        }
        return minRank
    }

    private fun recalculateAndEmit() {
        val snapshot: Map<String, ChannelAudioState>
        val anyDucked: Boolean
        synchronized(lock) {
            val topSpeakingChannel = effectiveSpeakingChannels
                .mapNotNull { channels[it] }
                .minByOrNull { it.priority.rank }

            val topRank = topSpeakingChannel?.priority?.rank ?: Int.MAX_VALUE
            val retainFactor = (1f - duckReductionRatio).coerceIn(0.05f, 1f)
            val duckPercentInt = (duckReductionRatio * 100f).roundToInt().coerceIn(0, 95)

            val map = LinkedHashMap<String, ChannelAudioState>(channels.size)
            var duckedCount = 0

            for (entry in channels.values) {
                val shouldDuckByPriority = duckingEnabled &&
                    topSpeakingChannel != null &&
                    entry.priority.rank > topRank &&
                    entry.channelId !in effectiveSpeakingChannels

                val shouldDuckByEmergency = emergencyActive && entry.priority != ChannelPriority.PRIMARY

                val isDucked = shouldDuckByPriority || shouldDuckByEmergency
                val effective = if (isDucked) {
                    duckedCount++
                    (entry.userVolume * retainFactor).coerceIn(0f, 1f)
                } else {
                    entry.userVolume
                }

                map[entry.channelId] = ChannelAudioState(
                    channelId = entry.channelId,
                    displayName = entry.displayName,
                    priority = entry.priority,
                    userVolume = entry.userVolume,
                    effectiveVolume = effective,
                    isDucked = isDucked,
                    duckPercent = if (isDucked) duckPercentInt else 0,
                    duckedByChannel = if (shouldDuckByEmergency) {
                        "Khẩn cấp"
                    } else if (shouldDuckByPriority) {
                        topSpeakingChannel?.displayName
                    } else {
                        null
                    }
                )
            }
            snapshot = map
            anyDucked = duckedCount > 0
        }

        _channelStates.value = snapshot
        _isDuckingActive.value = anyDucked
        snapshot.values.forEach { state ->
            onVolumeApplied(state.channelId, state.effectiveVolume)
        }
    }
}
