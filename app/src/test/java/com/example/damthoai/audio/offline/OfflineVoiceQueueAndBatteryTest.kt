package com.example.damthoai.audio.offline

import com.example.damthoai.service.BatteryOptimizationMonitor
import com.example.damthoai.service.PowerProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bộ Unit Test cho:
 * - Tuần 5 (TV2): Hàng đợi Ngoại tuyến Store-and-Forward (lưu đệm khi offline, gửi vọt FIFO khi có mạng).
 * - Tuần 6 (TV2): Giám sát & tối ưu tiêu thụ pin chạy nền liên tục 4 giờ.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineVoiceQueueAndBatteryTest {

    private class InMemoryOfflineVoiceStorage : OfflineVoiceStorage {
        private var queue: List<QueuedVoiceMessage> = emptyList()
        val files = LinkedHashMap<String, ByteArray>()

        override fun loadQueue(): List<QueuedVoiceMessage> = queue
        override fun saveQueue(messages: List<QueuedVoiceMessage>) {
            queue = messages.toList()
        }

        override fun writeAudioBytes(fileName: String, bytes: ByteArray): Boolean {
            files[fileName] = bytes.copyOf()
            return true
        }

        override fun readAudioBytes(fileName: String): ByteArray? = files[fileName]

        override fun deleteAudioFile(fileName: String) {
            files.remove(fileName)
        }
    }

    // ------------------------------------------------------------------ Tuần 5: Store & Forward

    @Test
    fun offlineQueue_storesWhileOfflineAndFlushesFifoWhenNetworkRestores() = runTest {
        var fakeNow = 1_000_000L
        val storage = InMemoryOfflineVoiceStorage()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val manager = OfflineVoiceQueueManager(
            context = null,
            storage = storage,
            ioDispatcher = dispatcher,
            clockMs = { fakeNow }
        )

        var online = false
        val deliveredIds = mutableListOf<String>()
        manager.attachTransport(object : VoiceQueueTransport {
            override fun isTransportReady(): Boolean = online
            override fun sendVoiceNote(message: QueuedVoiceMessage, audioBase64: String): Boolean {
                if (!online) return false
                deliveredIds.add(message.id)
                return true
            }
        })

        // Bản ghi quá ngắn (< 300ms) phải bị bỏ qua
        val tooShort = manager.enqueueAudioBytes(
            channelId = "Hậu cần – An ninh",
            targetId = "peer_1",
            senderId = "me",
            audioBytes = ByteArray(120),
            durationMs = 150L
        )
        assertNull(tooShort)

        // Khi mất mạng -> lưu 2 câu lệnh thoại vào bộ nhớ đệm
        val wav1 = OfflineVoiceQueueManager.encodePcmToWav(ByteArray(1600) { 1 })
        val wav2 = OfflineVoiceQueueManager.encodePcmToWav(ByteArray(1600) { 2 })
        manager.enqueueAudioBytes("Hậu cần – An ninh", "peer_1", "me", wav1, durationMs = 1200L, id = "msg_1")
        fakeNow += 2000L
        manager.enqueueAudioBytes("Hậu cần – An ninh", "peer_1", "me", wav2, durationMs = 1500L, id = "msg_2")

        assertEquals(2, manager.state.value.queuedCount)
        assertEquals(0, manager.flushQueueNow()) // Vẫn offline -> không gửi

        // Có mạng trở lại -> gửi vọt toàn bộ đúng thứ tự FIFO và giải phóng file đệm
        online = true
        val sent = manager.flushQueueNow()
        assertEquals(2, sent)
        assertEquals(listOf("msg_1", "msg_2"), deliveredIds)
        assertEquals(0, manager.state.value.queuedCount)
        assertEquals(2, manager.state.value.totalSentCount)
        assertTrue(storage.files.isEmpty())
    }

    @Test
    fun offlineQueue_preservesUnsentMessagesWhenTransportFailsMidFlush() = runTest {
        val storage = InMemoryOfflineVoiceStorage()
        val manager = OfflineVoiceQueueManager(
            context = null,
            storage = storage,
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        var failSecond = true
        manager.attachTransport(object : VoiceQueueTransport {
            override fun isTransportReady(): Boolean = true
            override fun sendVoiceNote(message: QueuedVoiceMessage, audioBase64: String): Boolean {
                return !(failSecond && message.id == "msg_2")
            }
        })

        manager.enqueueAudioBytes("ch", "peer", "me", ByteArray(640) { 3 }, 900L, id = "msg_1")
        manager.enqueueAudioBytes("ch", "peer", "me", ByteArray(640) { 4 }, 900L, id = "msg_2")

        val firstAttempt = manager.flushQueueNow()
        assertEquals(1, firstAttempt)
        assertEquals(1, manager.state.value.queuedCount)
        assertEquals("msg_2", manager.state.value.queuedMessages.first().id)
        assertEquals(1, manager.state.value.queuedMessages.first().retryCount)

        // Lần thử sau mạng ổn -> gửi nốt msg_2
        failSecond = false
        assertEquals(1, manager.flushQueueNow())
        assertEquals(0, manager.state.value.queuedCount)
    }

    @Test
    fun wavCodec_roundTripPreservesExactPcmBytes() {
        val pcm = ByteArray(256) { (it - 128).toByte() }
        val wav = OfflineVoiceQueueManager.encodePcmToWav(pcm, sampleRateHz = 16_000)
        assertEquals(44 + pcm.size, wav.size)
        val extracted = OfflineVoiceQueueManager.extractPcmFromWav(wav)
        assertArrayEquals(pcm, extracted)
    }

    // ------------------------------------------------------------------ Tuần 6: Battery Audit 4h

    @Test
    fun batteryMonitor_auditsFourHourBackgroundBudgetAndAdaptiveIntervals() {
        var nowMs = 0L
        val monitor = BatteryOptimizationMonitor(clockMs = { nowMs })
        monitor.startSessionWithSample(batteryPct = 85, temperatureC = 31.0f, isCharging = false)

        monitor.onWakeLockAcquired()
        // Giả lập chạy nền 4 giờ liên tục, tụt 16% pin (4.0%/giờ <= ngưỡng 6.0%/giờ)
        nowMs += 4L * 3_600_000L
        monitor.onWakeLockReleased()

        val report = monitor.recordSample(
            currentBatteryPct = 69,
            temperatureC = 33.5f,
            isCharging = false,
            isInBackground = true,
            isPttActive = false
        )

        assertEquals(16, report.totalDrainPct)
        assertEquals(4.0, report.drainRatePctPerHour, 0.01)
        assertTrue(report.withinFourHourBudget)
        assertEquals(PowerProfile.BACKGROUND_STANDBY, report.powerProfile)
        assertEquals(4L * 3_600_000L, report.activeWakeLockMs)

        // Kiểm tra chu kỳ thích ứng tiết kiệm pin
        assertEquals(2_000L, monitor.recommendedStatsIntervalMs(isInBackground = false, isPttActive = false, batteryPct = 70))
        assertEquals(10_000L, monitor.recommendedStatsIntervalMs(isInBackground = true, isPttActive = false, batteryPct = 70))
        assertEquals(15_000L, monitor.recommendedStatsIntervalMs(isInBackground = true, isPttActive = false, batteryPct = 15))
    }

    @Test
    fun batteryMonitor_flagsExcessiveDrainAboveFourHourBudget() {
        var nowMs = 0L
        val monitor = BatteryOptimizationMonitor(clockMs = { nowMs })
        monitor.startSessionWithSample(batteryPct = 80, isCharging = false)

        // 1 giờ tụt 12% -> vượt ngưỡng 6%/giờ
        nowMs += 3_600_000L
        val report = monitor.recordSample(currentBatteryPct = 68, isCharging = false)
        assertFalse(report.withinFourHourBudget)
        assertEquals(12.0, report.drainRatePctPerHour, 0.01)
    }
}
