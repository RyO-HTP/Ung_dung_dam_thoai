package com.example.damthoai.webrtc.audio

import com.example.damthoai.webrtc.CallStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bộ Unit Test cho toàn bộ pipeline âm thanh của Thành viên 2:
 * - Tuần 2: Cấu hình codec Opus (SDP munging: ưu tiên Opus, FEC, DTX, bitrate, ptime).
 * - Tuần 4: Audio Ducking đa kênh (hạ âm 80% + release-hold + emergency override) & Software AGC.
 * - Tuần 6: Adaptive Jitter Buffer (tăng bảo vệ ngay lập tức + hysteresis chống dao động).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Tv2AudioPipelineTest {

    // ------------------------------------------------------------------ Tuần 2: Opus

    @Test
    fun preferOpusInSdp_reordersAudioLineAndInjectsFecDtxBitrate() {
        val sampleSdp = listOf(
            "v=0",
            "o=- 12345 2 IN IP4 127.0.0.1",
            "s=-",
            "m=audio 9 UDP/TLS/RTP/SAVPF 0 8 111 103",
            "a=rtpmap:0 PCMU/8000",
            "a=rtpmap:8 PCMA/8000",
            "a=rtpmap:111 opus/48000/2",
            "a=fmtp:111 minptime=10;useinbandfec=0",
            "a=rtpmap:103 ISAC/16000"
        ).joinToString("\r\n")

        val tuned = OpusAudioConfigurer.preferOpusInSdp(
            sampleSdp,
            OpusAudioProfile(
                maxAverageBitrateBps = 24_000,
                useInbandFec = true,
                useDtx = true,
                stereo = false,
                ptimeMs = 20,
                maxPtimeMs = 60
            )
        )

        val lines = tuned.split("\r\n")
        assertEquals("m=audio 9 UDP/TLS/RTP/SAVPF 111 0 8 103", lines.first { it.startsWith("m=audio ") })
        val fmtp = lines.first { it.startsWith("a=fmtp:111 ") }
        assertTrue(fmtp.contains("useinbandfec=1"))
        assertTrue(fmtp.contains("usedtx=1"))
        assertTrue(fmtp.contains("maxaveragebitrate=24000"))
        assertTrue(fmtp.contains("stereo=0"))
        assertTrue(lines.contains("a=ptime:20"))
        assertTrue(lines.contains("a=maxptime:60"))
    }

    @Test
    fun preferOpusInSdp_insertsFmtpWhenMissing() {
        val sampleSdp = listOf(
            "v=0",
            "m=audio 9 UDP/TLS/RTP/SAVPF 0 111",
            "a=rtpmap:0 PCMU/8000",
            "a=rtpmap:111 opus/48000/2"
        ).joinToString("\n")

        val tuned = OpusAudioConfigurer.preferOpusInSdp(sampleSdp)
        assertTrue(tuned.contains("a=fmtp:111 "))
        assertTrue(tuned.contains("useinbandfec=1"))
        assertTrue(tuned.contains("usedtx=1"))
    }

    // ------------------------------------------------------------------ Tuần 4: Audio Ducking

    @Test
    fun audioDucking_primarySpeakingDucksSecondaryChannelsBy80PercentAndRestoresAfterHold() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val appliedVolumes = mutableMapOf<String, Float>()
        val controller = AudioDuckingController(
            duckReductionRatio = 0.80f,
            releaseHoldMs = 450L,
            dispatcher = dispatcher,
            onVolumeApplied = { ch, vol -> appliedVolumes[ch] = vol }
        )

        controller.setUserVolume(AudioDuckingController.CHANNEL_STAGE, 0.70f)
        controller.setUserVolume(AudioDuckingController.CHANNEL_SUPPORT, 1.00f)

        // Kênh ưu tiên cao nhất ("Toàn sự kiện") phát lệnh -> hạ âm kênh phụ ngay lập tức
        controller.updateSpeakingChannels(setOf(AudioDuckingController.CHANNEL_ALL_EVENT))

        assertTrue(controller.isDuckingActive.value)
        val supportState = controller.channelStates.value[AudioDuckingController.CHANNEL_SUPPORT]!!
        assertTrue(supportState.isDucked)
        assertEquals(80, supportState.duckPercent)
        assertEquals(0.20f, supportState.effectiveVolume, 0.001f)
        assertEquals(0.20f, appliedVolumes[AudioDuckingController.CHANNEL_SUPPORT]!!, 0.001f)

        val stageState = controller.channelStates.value[AudioDuckingController.CHANNEL_STAGE]!!
        assertTrue(stageState.isDucked)
        assertEquals(0.14f, stageState.effectiveVolume, 0.001f)

        // Kênh chính ngừng nói -> vẫn giữ ducking trong 450ms hold
        controller.updateSpeakingChannels(emptySet())
        advanceTimeBy(300L)
        runCurrent()
        assertTrue(controller.isDuckingActive.value)

        // Sau 450ms -> phục hồi nguyên vẹn về userVolume
        advanceTimeBy(200L)
        runCurrent()
        assertFalse(controller.isDuckingActive.value)
        assertEquals(1.00f, controller.getEffectiveVolume(AudioDuckingController.CHANNEL_SUPPORT), 0.001f)
        assertEquals(0.70f, controller.getEffectiveVolume(AudioDuckingController.CHANNEL_STAGE), 0.001f)
    }

    @Test
    fun audioDucking_disabledSettingDoesNotDuckSecondaryChannels() = runTest {
        val controller = AudioDuckingController(releaseHoldMs = 0L)
        controller.setDuckingEnabled(false)
        controller.updateSpeakingChannels(setOf(AudioDuckingController.CHANNEL_ALL_EVENT))

        assertFalse(controller.isDuckingActive.value)
        assertEquals(1.0f, controller.getEffectiveVolume(AudioDuckingController.CHANNEL_SUPPORT), 0.001f)
    }

    @Test
    fun audioDucking_emergencyOverrideDucksNonPrimaryChannels() = runTest {
        val controller = AudioDuckingController(releaseHoldMs = 0L)
        controller.setEmergencyOverrideActive(true)

        assertTrue(controller.isDuckingActive.value)
        assertEquals(0.20f, controller.getEffectiveVolume(AudioDuckingController.CHANNEL_SUPPORT), 0.001f)
        assertEquals(1.00f, controller.getEffectiveVolume(AudioDuckingController.CHANNEL_ALL_EVENT), 0.001f)
    }

    // ------------------------------------------------------------------ Tuần 4 & 6: Software AGC + NS

    @Test
    fun softwareAgc_boostsQuietSpeechWithoutAmplifyingSilentNoiseFloor() {
        val agc = SoftwareAgcProcessor(targetRmsDbfs = -18.0, maxGainDb = 18.0, noiseGateDbfs = -52.0)

        // 1. Giọng nói nhỏ (~ -36 dBFS: biên độ 500 trên thang 32767)
        val quietBefore = generateSineWave(amplitude = 500, samples = 320)
        val rmsBefore = computeRms(quietBefore)

        var processed = quietBefore.copyOf()
        repeat(8) {
            processed = generateSineWave(amplitude = 500, samples = 320)
            agc.processFrame(processed, agcEnabled = true, noiseSuppressionEnabled = true)
        }
        val rmsAfter = computeRms(processed)

        assertTrue("Giọng nhỏ phải được khuếch đại rõ rệt", rmsAfter > rmsBefore * 2.5)
        assertTrue("Gain dB phải dương khi nói nhỏ", agc.currentGainDb > 6.0)

        // 2. Tạp âm nền rất nhỏ (< -52 dBFS: biên độ 20) -> không được khuếch đại mà phải suy giảm
        agc.reset()
        val noise = generateSineWave(amplitude = 20, samples = 320)
        val noiseRmsBefore = computeRms(noise)
        repeat(6) {
            val frame = generateSineWave(amplitude = 20, samples = 320)
            agc.processFrame(frame, agcEnabled = true, noiseSuppressionEnabled = true)
            if (it == 5) {
                val noiseRmsAfter = computeRms(frame)
                assertTrue("Tạp âm nền phải giảm khi bật lọc ồn", noiseRmsAfter < noiseRmsBefore)
            }
        }
    }

    @Test
    fun softwareAgc_limitsVeryLoudSpeechAgainstClipping() {
        val agc = SoftwareAgcProcessor()
        val loud = generateSineWave(amplitude = 28_000, samples = 320)
        agc.processFrame(loud, agcEnabled = true, noiseSuppressionEnabled = true)
        val peak = loud.maxOf { abs(it.toInt()) }
        assertTrue("Soft limiter không được vượt quá biên độ Short.MAX_VALUE", peak <= Short.MAX_VALUE.toInt())
        assertTrue("Tín hiệu quá lớn phải được hạ gain âm", agc.currentGainDb < 0.0)
    }

    // ------------------------------------------------------------------ Tuần 6: Jitter Buffer Optimizer

    @Test
    fun jitterBufferOptimizer_escalatesImmediatelyAndDowngradesWithHysteresis() {
        val optimizer = JitterBufferOptimizer(stableWindowsForDowngrade = 3)

        // Mạng ổn định -> 20ms LOW_LATENCY
        val good = CallStats(rttMs = 35, jitterMs = 4.0, intervalLossRate = 0.0)
        val r1 = optimizer.evaluate(good)
        assertEquals(JitterProfile.LOW_LATENCY, r1.profile)
        assertEquals(0.02, r1.minPlayoutDelaySec, 0.001)

        // Mạng rớt gói 15% & jitter 90ms -> tăng ngay lên HIGH_RESILIENCE 200ms + hạ bitrate 18kbps
        val bad = CallStats(rttMs = 320, jitterMs = 90.0, intervalLossRate = 0.15, concealedSamples = 200, totalSamplesReceived = 1000)
        val r2 = optimizer.evaluate(bad)
        assertEquals(JitterProfile.HIGH_RESILIENCE, r2.profile)
        assertEquals(0.20, r2.minPlayoutDelaySec, 0.001)
        assertEquals(18_000, r2.recommendedOpusBitrateBps)
        assertTrue(r2.enableInbandFec)

        // Mạng vừa tốt lại 2 cửa sổ -> CHƯA hạ delay ngay (hysteresis chống méo tiếng)
        assertEquals(JitterProfile.HIGH_RESILIENCE, optimizer.evaluate(good).profile)
        assertEquals(JitterProfile.HIGH_RESILIENCE, optimizer.evaluate(good).profile)

        // Đủ 3 cửa sổ tốt liên tiếp -> hạ về LOW_LATENCY 20ms
        val rFinal = optimizer.evaluate(good)
        assertEquals(JitterProfile.LOW_LATENCY, rFinal.profile)
        assertEquals(0.02, rFinal.minPlayoutDelaySec, 0.001)
    }

    private fun generateSineWave(amplitude: Int, samples: Int, freqHz: Double = 440.0): ShortArray {
        return ShortArray(samples) { i ->
            (sin(2.0 * PI * freqHz * i / 16_000.0) * amplitude).toInt().toShort()
        }
    }

    private fun computeRms(samples: ShortArray): Double {
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s.toDouble()
        return sqrt(sum / samples.size)
    }
}
