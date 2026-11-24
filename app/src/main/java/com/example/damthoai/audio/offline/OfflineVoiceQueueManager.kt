package com.example.damthoai.audio.offline

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.damthoai.hardware.audio.AudioRouteGuard
import com.example.damthoai.hardware.diagnostics.FieldTestLogger
import com.example.damthoai.webrtc.audio.AttachedAudioEffects
import com.example.damthoai.webrtc.audio.AudioProcessingManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Bản ghi câu lệnh thoại lưu tạm trong bộ nhớ đệm ngoại tuyến (TV2 - Tuần 5: Store and Forward).
 */
data class QueuedVoiceMessage(
    val id: String,
    val channelId: String,
    val targetId: String,
    val senderId: String,
    val fileName: String,
    val recordedAtMs: Long,
    val durationMs: Long,
    val sampleRateHz: Int = OfflineVoiceQueueManager.SAMPLE_RATE_HZ,
    val sizeBytes: Int = 0,
    val retryCount: Int = 0
)

/**
 * Trạng thái thời gian thực của Hàng đợi Ngoại tuyến (expose cho UI `ActiveCallScreen` & Service).
 */
data class OfflineQueueState(
    val isRecording: Boolean = false,
    val isFlushing: Boolean = false,
    val queuedMessages: List<QueuedVoiceMessage> = emptyList(),
    val totalSentCount: Int = 0,
    val lastFlushedAtMs: Long? = null,
    val lastError: String? = null
) {
    val queuedCount: Int get() = queuedMessages.size
}

/**
 * Hợp đồng tầng vận chuyển để đẩy tin nhắn thoại đã lưu khi có mạng trở lại.
 */
interface VoiceQueueTransport {
    fun isTransportReady(): Boolean
    fun sendVoiceNote(
        message: QueuedVoiceMessage,
        audioBase64: String
    ): Boolean
}

/**
 * Hợp đồng lưu trữ cục bộ bền vững cho hàng đợi ngoại tuyến (tách riêng để dễ kiểm thử Unit Test).
 */
interface OfflineVoiceStorage {
    fun loadQueue(): List<QueuedVoiceMessage>
    fun saveQueue(messages: List<QueuedVoiceMessage>)
    fun writeAudioBytes(fileName: String, bytes: ByteArray): Boolean
    fun readAudioBytes(fileName: String): ByteArray?
    fun deleteAudioFile(fileName: String)
}

/**
 * Triển khai lưu trữ bền vững trên thư mục riêng của ứng dụng (`filesDir/offline_voice_queue`).
 */
class FileOfflineVoiceStorage(
    private val queueDir: File,
    private val gson: Gson = Gson()
) : OfflineVoiceStorage {

    companion object {
        private const val TAG = "FileOfflineVoiceStorage"
        private const val INDEX_FILE_NAME = "queue_index.json"
    }

    private val indexFile: File get() = File(queueDir, INDEX_FILE_NAME)

    private fun ensureDir() {
        if (!queueDir.exists()) {
            queueDir.mkdirs()
        }
    }

    override fun loadQueue(): List<QueuedVoiceMessage> {
        return try {
            if (!indexFile.exists()) return emptyList()
            val json = indexFile.readText(Charsets.UTF_8)
            val listType = object : TypeToken<List<QueuedVoiceMessage>>() {}.type
            gson.fromJson<List<QueuedVoiceMessage>>(json, listType) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đọc queue_index.json: ${e.message}", e)
            emptyList()
        }
    }

    override fun saveQueue(messages: List<QueuedVoiceMessage>) {
        try {
            ensureDir()
            val tmp = File(queueDir, "$INDEX_FILE_NAME.tmp")
            tmp.writeText(gson.toJson(messages), Charsets.UTF_8)
            if (!tmp.renameTo(indexFile)) {
                indexFile.writeText(gson.toJson(messages), Charsets.UTF_8)
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi lưu queue_index.json: ${e.message}", e)
        }
    }

    override fun writeAudioBytes(fileName: String, bytes: ByteArray): Boolean {
        return try {
            ensureDir()
            File(queueDir, fileName).writeBytes(bytes)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi ghi file thoại ngoại tuyến $fileName: ${e.message}", e)
            false
        }
    }

    override fun readAudioBytes(fileName: String): ByteArray? {
        return try {
            val file = File(queueDir, fileName)
            if (file.exists()) file.readBytes() else null
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đọc file thoại ngoại tuyến $fileName: ${e.message}", e)
            null
        }
    }

    override fun deleteAudioFile(fileName: String) {
        runCatching { File(queueDir, fileName).delete() }
    }
}

/**
 * =================================================================================
 * HÀNG ĐỢI NGOẠI TUYẾN - STORE AND FORWARD ("CỨU MẠNG" - TV2 Tuần 5)
 * =================================================================================
 *
 * Chức năng:
 * 1. Khi điện thoại rớt sóng (Offline), giữ phím PTT sẽ tự động ghi âm câu lệnh thoại
 *    (áp dụng AGC & lọc ồn từ [AudioProcessingManager]) và lưu bền vững vào bộ nhớ đệm.
 * 2. Ngay khi có mạng trở lại (Server WebSocket hoặc LAN P2P sẵn sàng), tự động gửi vọt
 *    toàn bộ hàng đợi theo thứ tự FIFO với cơ chế retry và dọn bộ đệm sau khi gửi thành công.
 * 3. Phía máy nhận giải mã và phát lại câu lệnh thoại tôn trọng [AudioRouteGuard.outputMuted].
 */
class OfflineVoiceQueueManager(
    private val context: Context? = null,
    private val storage: OfflineVoiceStorage,
    private val audioProcessing: AudioProcessingManager = AudioProcessingManager(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val TAG = "OfflineVoiceQueueMgr"
        const val SAMPLE_RATE_HZ = 16_000
        const val MIN_RECORDING_MS = 300L
        const val MAX_RECORDING_MS = 30_000L
        const val MAX_QUEUE_ITEMS = 30
        const val MAX_TOTAL_BYTES = 10 * 1024 * 1024 // 10 MB bộ nhớ đệm tối đa
        const val MESSAGE_TTL_MS = 15 * 60 * 1000L   // 15 phút
        const val MAX_RETRY_COUNT = 5

        fun createDefault(
            context: Context,
            audioProcessing: AudioProcessingManager
        ): OfflineVoiceQueueManager {
            val dir = File(context.applicationContext.filesDir, "offline_voice_queue")
            return OfflineVoiceQueueManager(
                context = context.applicationContext,
                storage = FileOfflineVoiceStorage(dir),
                audioProcessing = audioProcessing
            )
        }

        /**
         * Đóng gói mảng PCM 16-bit mono thành byte WAV chuẩn 44-byte header.
         */
        fun encodePcmToWav(pcmBytes: ByteArray, sampleRateHz: Int = SAMPLE_RATE_HZ): ByteArray {
            val channels = 1
            val bitsPerSample = 16
            val byteRate = sampleRateHz * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            val dataSize = pcmBytes.size
            val chunkSize = 36 + dataSize

            val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
            buffer.putInt(chunkSize)
            buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
            buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
            buffer.putInt(16) // PCM subchunk size
            buffer.putShort(1) // AudioFormat = 1 (PCM)
            buffer.putShort(channels.toShort())
            buffer.putInt(sampleRateHz)
            buffer.putInt(byteRate)
            buffer.putShort(blockAlign.toShort())
            buffer.putShort(bitsPerSample.toShort())
            buffer.put("data".toByteArray(Charsets.US_ASCII))
            buffer.putInt(dataSize)
            buffer.put(pcmBytes)
            return buffer.array()
        }

        /**
         * Tách dữ liệu PCM thuần từ WAV (nếu có header RIFF/WAVE).
         */
        fun extractPcmFromWav(wavOrPcm: ByteArray): ByteArray {
            if (wavOrPcm.size > 44 &&
                wavOrPcm[0] == 'R'.code.toByte() &&
                wavOrPcm[1] == 'I'.code.toByte() &&
                wavOrPcm[2] == 'F'.code.toByte() &&
                wavOrPcm[3] == 'F'.code.toByte()
            ) {
                return wavOrPcm.copyOfRange(44, wavOrPcm.size)
            }
            return wavOrPcm
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val queueMutex = Mutex()

    @Volatile
    private var transport: VoiceQueueTransport? = null

    private val _state = MutableStateFlow(OfflineQueueState())
    val state: StateFlow<OfflineQueueState> = _state.asStateFlow()

    private var recordJob: Job? = null
    private var flushJob: Job? = null

    @Volatile
    private var activeRecordingMeta: ActiveRecordingSession? = null

    private data class ActiveRecordingSession(
        val id: String,
        val channelId: String,
        val targetId: String,
        val senderId: String,
        val startedAtMs: Long,
        val pcmBuffer: ByteArrayOutputStream = ByteArrayOutputStream(),
        @Volatile var stopped: Boolean = false
    )

    init {
        restorePersistedQueue()
    }

    fun attachTransport(voiceTransport: VoiceQueueTransport) {
        transport = voiceTransport
    }

    private fun restorePersistedQueue() {
        val loaded = pruneExpiredAndOversized(storage.loadQueue())
        storage.saveQueue(loaded)
        _state.value = _state.value.copy(queuedMessages = loaded)
    }

    /**
     * Bắt đầu ghi âm câu lệnh thoại khi đang rớt mạng (giữ phím PTT ở chế độ Offline).
     */
    fun startOfflineRecording(
        channelId: String,
        targetId: String,
        senderId: String
    ): Boolean {
        if (_state.value.isRecording) return true
        val ctx = context ?: run {
            _state.value = _state.value.copy(lastError = "Không có Context để ghi âm ngoại tuyến")
            return false
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            _state.value = _state.value.copy(lastError = "Chưa có quyền RECORD_AUDIO để ghi âm ngoại tuyến")
            return false
        }

        val session = ActiveRecordingSession(
            id = "vn_${UUID.randomUUID().toString().take(8)}",
            channelId = channelId,
            targetId = targetId,
            senderId = senderId,
            startedAtMs = clockMs()
        )
        activeRecordingMeta = session
        _state.value = _state.value.copy(isRecording = true, lastError = null)
        audioProcessing.resetSoftwareAgc()

        recordJob = scope.launch {
            recordAudioLoop(session)
        }
        return true
    }

    /**
     * Dừng ghi âm (nhả phím PTT) và lưu câu lệnh vào hàng đợi bộ nhớ đệm.
     */
    fun stopOfflineRecordingAndEnqueue() {
        val session = activeRecordingMeta ?: return
        session.stopped = true
    }

    private suspend fun recordAudioLoop(session: ActiveRecordingSession) {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(3200)

        var recorder: AudioRecord? = null
        var effects: AttachedAudioEffects? = null

        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                _state.value = _state.value.copy(
                    isRecording = false,
                    lastError = "Không khởi tạo được AudioRecord cho hàng đợi ngoại tuyến"
                )
                return
            }

            effects = audioProcessing.attachHardwareEffects(recorder.audioSessionId)
            recorder.startRecording()

            // Khung 20ms @ 16kHz = 320 samples
            val frame = ShortArray(320)
            val byteBuf = ByteArray(frame.size * 2)

            while (scope.isActive && !session.stopped &&
                (clockMs() - session.startedAtMs) < MAX_RECORDING_MS
            ) {
                val read = recorder.read(frame, 0, frame.size)
                if (read > 0) {
                    // Áp dụng Software AGC + Noise Gate (Tuần 4 & Tuần 6)
                    audioProcessing.processPcmFrame(frame, read)
                    for (i in 0 until read) {
                        val s = frame[i].toInt()
                        byteBuf[i * 2] = (s and 0xFF).toByte()
                        byteBuf[i * 2 + 1] = ((s ushr 8) and 0xFF).toByte()
                    }
                    session.pcmBuffer.write(byteBuf, 0, read * 2)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Thiếu quyền thu âm ngoại tuyến: ${e.message}", e)
            _state.value = _state.value.copy(lastError = e.message)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi trong quá trình ghi âm ngoại tuyến: ${e.message}", e)
            _state.value = _state.value.copy(lastError = e.message)
        } finally {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            effects?.release()
            activeRecordingMeta = null
            _state.value = _state.value.copy(isRecording = false)
        }

        val durationMs = clockMs() - session.startedAtMs
        val pcmBytes = session.pcmBuffer.toByteArray()
        if (durationMs < MIN_RECORDING_MS || pcmBytes.isEmpty()) {
            Log.d(TAG, "Bỏ qua bản ghi ngoại tuyến quá ngắn (${durationMs}ms)")
            return
        }

        val wavBytes = encodePcmToWav(pcmBytes, SAMPLE_RATE_HZ)
        enqueueAudioBytes(
            id = session.id,
            channelId = session.channelId,
            targetId = session.targetId,
            senderId = session.senderId,
            audioBytes = wavBytes,
            durationMs = durationMs,
            recordedAtMs = session.startedAtMs
        )
    }

    /**
     * Đưa trực tiếp một đoạn âm thanh (WAV/PCM) vào hàng đợi bộ nhớ đệm bền vững.
     * Nếu mạng đang sẵn sàng, có thể chủ động gọi [flushQueueNow] để gửi ngay.
     */
    suspend fun enqueueAudioBytes(
        channelId: String,
        targetId: String,
        senderId: String,
        audioBytes: ByteArray,
        durationMs: Long,
        id: String = "vn_${UUID.randomUUID().toString().take(8)}",
        recordedAtMs: Long = clockMs()
    ): QueuedVoiceMessage? {
        if (durationMs < MIN_RECORDING_MS || audioBytes.isEmpty()) return null

        val fileName = "$id.wav"
        if (!storage.writeAudioBytes(fileName, audioBytes)) return null

        val msg = QueuedVoiceMessage(
            id = id,
            channelId = channelId,
            targetId = targetId,
            senderId = senderId,
            fileName = fileName,
            recordedAtMs = recordedAtMs,
            durationMs = durationMs,
            sampleRateHz = SAMPLE_RATE_HZ,
            sizeBytes = audioBytes.size
        )

        queueMutex.withLock {
            val updated = pruneExpiredAndOversized(_state.value.queuedMessages + msg)
            storage.saveQueue(updated)
            _state.value = _state.value.copy(queuedMessages = updated, lastError = null)
        }

        Log.d(TAG, "Đã lưu tin nhắn thoại ngoại tuyến [$id] (${durationMs}ms, ${audioBytes.size} bytes)")
        runCatching {
            FieldTestLogger.mark(
                eventType = "offline_voice_queued",
                source = "TV2_OFFLINE",
                extra = "id=$id;durationMs=$durationMs;bytes=${audioBytes.size};queueSize=${_state.value.queuedCount}"
            )
        }
        return msg
    }

    /**
     * Kích hoạt gửi vọt toàn bộ hàng đợi ngay khi mạng hồi phục (không chặn luồng gọi).
     */
    fun onNetworkRestored() {
        if (_state.value.queuedMessages.isEmpty()) return
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            flushQueueNow()
        }
    }

    /**
     * Duyệt hàng đợi FIFO và gửi vọt tất cả tin nhắn thoại đang chờ.
     * @return Số lượng tin nhắn đã gửi thành công trong lượt này.
     */
    suspend fun flushQueueNow(): Int {
        val activeTransport = transport ?: return 0
        if (!activeTransport.isTransportReady()) return 0

        return queueMutex.withLock {
            val initial = pruneExpiredAndOversized(_state.value.queuedMessages)
            if (initial.isEmpty()) {
                storage.saveQueue(emptyList())
                _state.value = _state.value.copy(queuedMessages = emptyList(), isFlushing = false)
                return@withLock 0
            }

            _state.value = _state.value.copy(isFlushing = true, lastError = null)
            val remaining = mutableListOf<QueuedVoiceMessage>()
            var sentNow = 0

            for (index in initial.indices) {
                val msg = initial[index]
                if (!activeTransport.isTransportReady()) {
                    remaining.addAll(initial.subList(index, initial.size))
                    break
                }

                val bytes = storage.readAudioBytes(msg.fileName)
                if (bytes == null) {
                    Log.w(TAG, "File thoại ${msg.fileName} không còn tồn tại, bỏ khỏi hàng đợi")
                    continue
                }

                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val delivered = runCatching { activeTransport.sendVoiceNote(msg, base64) }
                    .getOrDefault(false)

                if (delivered) {
                    storage.deleteAudioFile(msg.fileName)
                    sentNow++
                    Log.d(TAG, "Gửi vọt thành công tin nhắn ngoại tuyến [${msg.id}] tới ${msg.targetId}")
                } else {
                    val nextRetry = msg.retryCount + 1
                    if (nextRetry < MAX_RETRY_COUNT) {
                        remaining.add(msg.copy(retryCount = nextRetry))
                    } else {
                        Log.w(TAG, "Tin nhắn [${msg.id}] vượt quá $MAX_RETRY_COUNT lần thử, loại bỏ")
                        storage.deleteAudioFile(msg.fileName)
                    }
                    // Mạng vừa chập chờn -> dừng lượt flush để thử lại sau
                    if (index + 1 < initial.size) {
                        remaining.addAll(initial.subList(index + 1, initial.size))
                    }
                    break
                }
            }

            storage.saveQueue(remaining)
            val now = clockMs()
            _state.value = _state.value.copy(
                isFlushing = false,
                queuedMessages = remaining,
                totalSentCount = _state.value.totalSentCount + sentNow,
                lastFlushedAtMs = if (sentNow > 0) now else _state.value.lastFlushedAtMs
            )

            if (sentNow > 0) {
                runCatching {
                    FieldTestLogger.mark(
                        eventType = "offline_voice_flushed",
                        source = "TV2_OFFLINE",
                        extra = "sent=$sentNow;remaining=${remaining.size}"
                    )
                }
            }
            sentNow
        }
    }

    /**
     * Phát lại tin nhắn thoại gửi bù nhận được từ đồng đội (tôn trọng khóa bảo mật [AudioRouteGuard.outputMuted]).
     */
    fun playReceivedVoiceNote(
        audioBase64: String,
        sampleRateHz: Int = SAMPLE_RATE_HZ,
        senderId: String? = null
    ) {
        if (AudioRouteGuard.outputMuted.value) {
            Log.w(TAG, "Đầu ra đang bị khóa bảo mật (AudioRouteGuard), tạm chặn phát voice note từ $senderId")
            return
        }
        scope.launch {
            val rawBytes = runCatching { Base64.decode(audioBase64, Base64.DEFAULT) }.getOrNull() ?: return@launch
            val pcm = extractPcmFromWav(rawBytes)
            if (pcm.isEmpty()) return@launch

            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRateHz,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(3200)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRateHz)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(minBuf)
                    .build()

                track.play()
                var offset = 0
                val chunkSize = 1280
                while (offset < pcm.size && isActive && !AudioRouteGuard.outputMuted.value) {
                    val toWrite = minOf(chunkSize, pcm.size - offset)
                    val written = track.write(pcm, offset, toWrite)
                    if (written <= 0) break
                    offset += written
                }
                delay(150)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi phát lại tin nhắn thoại ngoại tuyến: ${e.message}", e)
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
            }
        }
    }

    suspend fun clearQueue() {
        queueMutex.withLock {
            _state.value.queuedMessages.forEach { storage.deleteAudioFile(it.fileName) }
            storage.saveQueue(emptyList())
            _state.value = _state.value.copy(queuedMessages = emptyList())
        }
    }

    private fun pruneExpiredAndOversized(list: List<QueuedVoiceMessage>): List<QueuedVoiceMessage> {
        val now = clockMs()
        val kept = mutableListOf<QueuedVoiceMessage>()
        for (msg in list) {
            if (now - msg.recordedAtMs > MESSAGE_TTL_MS) {
                storage.deleteAudioFile(msg.fileName)
            } else {
                kept.add(msg)
            }
        }
        while (kept.size > MAX_QUEUE_ITEMS) {
            val dropped = kept.removeAt(0)
            storage.deleteAudioFile(dropped.fileName)
        }
        var totalBytes = kept.sumOf { it.sizeBytes }
        while (totalBytes > MAX_TOTAL_BYTES && kept.size > 1) {
            val dropped = kept.removeAt(0)
            totalBytes -= dropped.sizeBytes
            storage.deleteAudioFile(dropped.fileName)
        }
        return kept
    }
}
