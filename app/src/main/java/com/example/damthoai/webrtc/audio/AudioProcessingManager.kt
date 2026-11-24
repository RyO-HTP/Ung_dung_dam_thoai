package com.example.damthoai.webrtc.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.MediaConstraints
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Cấu hình xử lý tín hiệu âm thanh (DSP):
 * - Tuần 4: Tự động khuếch đại mic (AGC / AGC2) khi nói nhỏ.
 * - Tuần 6: Lọc tiếng ồn (Noise Suppression), khử vọng (AEC), lọc tạp âm tần số thấp (High-pass).
 */
data class AudioDspConfig(
    val agcEnabled: Boolean = true,
    val noiseSuppressionEnabled: Boolean = true,
    val echoCancellationEnabled: Boolean = true,
    val highPassFilterEnabled: Boolean = true,
    val typingNoiseDetectionEnabled: Boolean = true,
    val targetRmsDbfs: Double = -18.0,
    val maxSoftwareGainDb: Double = 18.0,
    val noiseGateThresholdDbfs: Double = -52.0
)

/**
 * Trạng thái thời gian thực của bộ xử lý âm thanh TV2.
 */
data class AudioProcessingState(
    val config: AudioDspConfig = AudioDspConfig(),
    val hardwareAgcAvailable: Boolean = false,
    val hardwareNsAvailable: Boolean = false,
    val hardwareAecAvailable: Boolean = false,
    val currentSoftwareGainDb: Double = 0.0,
    val lastInputLevelDbfs: Double = -96.0
)

/**
 * Giữ các Android AudioEffect đã gắn vào một `audioSessionId` (dùng cho ghi âm Offline Queue)
 * để giải phóng an toàn khi ngừng ghi.
 */
class AttachedAudioEffects(
    private val agc: AutomaticGainControl? = null,
    private val ns: NoiseSuppressor? = null,
    private val aec: AcousticEchoCanceler? = null
) {
    val hasAgc: Boolean get() = agc?.enabled == true
    val hasNs: Boolean get() = ns?.enabled == true
    val hasAec: Boolean get() = aec?.enabled == true

    fun release() {
        runCatching { agc?.release() }
        runCatching { ns?.release() }
        runCatching { aec?.release() }
    }
}

/**
 * Bộ khuếch đại mic thích ứng bằng phần mềm (Software AGC + Noise Gate + Soft Limiter)
 * cho luồng PCM 16-bit (TV2 - Tuần 4).
 *
 * - Khi người dùng nói nhỏ (mức RMS > ngưỡng ồn nền và nhỏ hơn mục tiêu -18 dBFS),
 *   tự động nâng gain mượt mà tối đa +[maxGainDb] dB để giọng rõ nét.
 * - Khi tín hiệu quá lớn, giảm gain nhanh và áp dụng soft-clipping (`tanh`) chống vỡ tiếng.
 * - Khi chỉ có tạp âm nền dưới ngưỡng [noiseGateDbfs], giữ nguyên (hoặc giảm nhẹ khi NS bật)
 *   để tránh khuếch đại tiếng xì môi trường.
 */
class SoftwareAgcProcessor(
    private val targetRmsDbfs: Double = -18.0,
    private val maxGainDb: Double = 18.0,
    private val minGainDb: Double = -6.0,
    private val noiseGateDbfs: Double = -52.0
) {
    private var smoothedGainLinear: Double = 1.0
    var currentGainDb: Double = 0.0
        private set
    var lastInputDbfs: Double = -96.0
        private set

    fun reset() {
        smoothedGainLinear = 1.0
        currentGainDb = 0.0
        lastInputDbfs = -96.0
    }

    /**
     * Xử lý tại chỗ một khung mẫu PCM 16-bit.
     *
     * @param samples Mảng PCM 16-bit (mono)
     * @param count Số lượng mẫu hợp lệ trong mảng
     * @param agcEnabled Bật/tắt tự động khuếch đại giọng nhỏ
     * @param noiseSuppressionEnabled Bật/tắt suy giảm nền ồn dưới ngưỡng gate
     * @return Mức gain thực tế đã áp dụng (dB)
     */
    fun processFrame(
        samples: ShortArray,
        count: Int = samples.size,
        agcEnabled: Boolean = true,
        noiseSuppressionEnabled: Boolean = true
    ): Double {
        if (count <= 0) return currentGainDb

        var sumSquares = 0.0
        for (i in 0 until count) {
            val normalized = samples[i] / 32768.0
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / count)
        val inputDbfs = if (rms > 1e-9) 20.0 * log10(rms) else -96.0
        lastInputDbfs = inputDbfs

        val targetGainDb = when {
            inputDbfs < noiseGateDbfs -> {
                // Tạp âm nền rất nhỏ -> không khuếch đại; giảm nhẹ nếu bật lọc ồn
                if (noiseSuppressionEnabled) -6.0 else 0.0
            }
            !agcEnabled -> 0.0
            else -> (targetRmsDbfs - inputDbfs).coerceIn(minGainDb, maxGainDb)
        }

        val targetGainLinear = 10.0.pow(targetGainDb / 20.0)
        // Attack nhanh khi cần hạ tiếng lớn, release mượt khi nâng giọng nói nhỏ
        val smoothing = if (targetGainLinear < smoothedGainLinear) 0.45 else 0.25
        smoothedGainLinear += (targetGainLinear - smoothedGainLinear) * smoothing
        currentGainDb = 20.0 * log10(max(smoothedGainLinear, 1e-6))

        if (abs(smoothedGainLinear - 1.0) < 0.01) {
            return currentGainDb
        }

        for (i in 0 until count) {
            val scaled = (samples[i] / 32768.0) * smoothedGainLinear
            // Soft limiter chống méo/clipping cứng khi khuếch đại
            val limited = if (abs(scaled) > 0.92) {
                tanh(scaled)
            } else {
                scaled
            }
            val pcm = (limited * 32767.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            samples[i] = pcm.toShort()
        }
        return currentGainDb
    }
}

/**
 * Quản lý cấu hình xử lý âm thanh WebRTC + Android AudioEffect (TV2 - Tuần 4 & Tuần 6).
 */
class AudioProcessingManager {

    companion object {
        private const val TAG = "AudioProcessingManager"

        // Hằng số MediaConstraints chuẩn WebRTC Android
        const val CONSTRAINT_AGC = "googAutoGainControl"
        const val CONSTRAINT_AGC2 = "googAutoGainControl2"
        const val CONSTRAINT_NS = "googNoiseSuppression"
        const val CONSTRAINT_NS2 = "googNoiseSuppression2"
        const val CONSTRAINT_AEC = "googEchoCancellation"
        const val CONSTRAINT_HIGHPASS = "googHighpassFilter"
        const val CONSTRAINT_TYPING_NOISE = "googTypingNoiseDetection"
        const val CONSTRAINT_AUDIO_MIRRORING = "googAudioMirroring"
    }

    private val softwareAgc = SoftwareAgcProcessor()

    private val _state = MutableStateFlow(
        AudioProcessingState(
            config = AudioDspConfig(),
            hardwareAgcAvailable = runCatching { AutomaticGainControl.isAvailable() }.getOrDefault(false),
            hardwareNsAvailable = runCatching { NoiseSuppressor.isAvailable() }.getOrDefault(false),
            hardwareAecAvailable = runCatching { AcousticEchoCanceler.isAvailable() }.getOrDefault(false)
        )
    )
    val state: StateFlow<AudioProcessingState> = _state.asStateFlow()

    val currentConfig: AudioDspConfig get() = _state.value.config

    fun setAgcEnabled(enabled: Boolean) {
        val current = _state.value.config
        if (current.agcEnabled == enabled) return
        _state.value = _state.value.copy(config = current.copy(agcEnabled = enabled))
        Log.d(TAG, "Cập nhật AGC (Tự khuếch đại mic): $enabled")
    }

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        val current = _state.value.config
        if (current.noiseSuppressionEnabled == enabled && current.echoCancellationEnabled == enabled) return
        _state.value = _state.value.copy(
            config = current.copy(
                noiseSuppressionEnabled = enabled,
                echoCancellationEnabled = enabled
            )
        )
        Log.d(TAG, "Cập nhật Lọc ồn & Khử vọng (NS/AEC): $enabled")
    }

    /**
     * Sinh [MediaConstraints] để truyền vào `PeerConnectionFactory.createAudioSource(...)`.
     */
    fun buildWebRtcAudioConstraints(config: AudioDspConfig = currentConfig): MediaConstraints {
        val agc = config.agcEnabled.toString()
        val ns = config.noiseSuppressionEnabled.toString()
        val aec = config.echoCancellationEnabled.toString()
        val hp = config.highPassFilterEnabled.toString()
        val typing = config.typingNoiseDetectionEnabled.toString()

        return MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair(CONSTRAINT_AGC, agc))
            mandatory.add(MediaConstraints.KeyValuePair(CONSTRAINT_AGC2, agc))
            mandatory.add(MediaConstraints.KeyValuePair(CONSTRAINT_NS, ns))
            mandatory.add(MediaConstraints.KeyValuePair(CONSTRAINT_NS2, ns))
            mandatory.add(MediaConstraints.KeyValuePair(CONSTRAINT_AEC, aec))
            mandatory.add(MediaConstraints.KeyValuePair(CONSTRAINT_HIGHPASS, hp))
            optional.add(MediaConstraints.KeyValuePair(CONSTRAINT_TYPING_NOISE, typing))
            optional.add(MediaConstraints.KeyValuePair(CONSTRAINT_AUDIO_MIRRORING, "false"))
        }
    }

    /**
     * Gắn hiệu ứng phần cứng Android (AGC, NoiseSuppressor, AEC) vào `AudioRecord.audioSessionId`.
     */
    fun attachHardwareEffects(audioSessionId: Int, config: AudioDspConfig = currentConfig): AttachedAudioEffects {
        if (audioSessionId <= 0) return AttachedAudioEffects()

        val agc = if (config.agcEnabled && runCatching { AutomaticGainControl.isAvailable() }.getOrDefault(false)) {
            runCatching {
                AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
            }.getOrNull()
        } else null

        val ns = if (config.noiseSuppressionEnabled && runCatching { NoiseSuppressor.isAvailable() }.getOrDefault(false)) {
            runCatching {
                NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
            }.getOrNull()
        } else null

        val aec = if (config.echoCancellationEnabled && runCatching { AcousticEchoCanceler.isAvailable() }.getOrDefault(false)) {
            runCatching {
                AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
            }.getOrNull()
        } else null

        Log.d(TAG, "Hardware AudioEffects session=$audioSessionId: agc=${agc != null}, ns=${ns != null}, aec=${aec != null}")
        return AttachedAudioEffects(agc = agc, ns = ns, aec = aec)
    }

    /**
     * Xử lý khung PCM 16-bit bằng Software AGC/NS (dùng trong ghi âm Offline Queue).
     */
    fun processPcmFrame(samples: ShortArray, count: Int = samples.size): Double {
        val cfg = currentConfig
        val gainDb = softwareAgc.processFrame(
            samples = samples,
            count = count,
            agcEnabled = cfg.agcEnabled,
            noiseSuppressionEnabled = cfg.noiseSuppressionEnabled
        )
        _state.value = _state.value.copy(
            currentSoftwareGainDb = gainDb,
            lastInputLevelDbfs = softwareAgc.lastInputDbfs
        )
        return gainDb
    }

    fun resetSoftwareAgc() {
        softwareAgc.reset()
        _state.value = _state.value.copy(currentSoftwareGainDb = 0.0, lastInputLevelDbfs = -96.0)
    }
}
