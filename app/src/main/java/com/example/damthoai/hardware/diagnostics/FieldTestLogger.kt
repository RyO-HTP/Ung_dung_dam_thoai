package com.example.damthoai.hardware.diagnostics

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.damthoai.hardware.PttEvent
import com.example.damthoai.hardware.PttController
import com.example.damthoai.hardware.PttSource
import com.example.damthoai.hardware.audio.AudioIncident
import com.example.damthoai.hardware.audio.AudioIncidentBus
import com.example.damthoai.hardware.audio.HardwareCoroutineExceptionHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Ghi log đo phần cứng khi chạy bản debug.
 *
 * ĐIỂM NỐI:
 * - [HardwareKeyHandler] ghi thời điểm nhận KeyEvent; [PttController] ghi lúc phát PttEvent.
 * - TV2 gọi [mark] với `mic_open`/`mic_closed` đúng lúc microphone thật được bật/tắt.
 * - Log thiết bị và [AudioIncidentBus] được thu khi bắt đầu phiên ghi.
 *
 * Tick Bluetooth và AudioTimestamp chỉ cho ước lượng phần mềm; không thay thế phép đo bằng quay
 * video chậm để thấy thời điểm âm thanh thực sự phát ra từ tai nghe.
 */
object FieldTestLogger {

    private const val TAG = "FieldTestLogger"
    private const val FLUSH_INTERVAL_MS = 2_000L
    private const val CSV_BUFFER_CAPACITY = 512
    private const val SAMPLE_RATE_HZ = 48_000
    private const val TICK_DURATION_MS = 35
    private const val TICK_FREQUENCY_HZ = 1_000.0
    private const val MAX_TIMESTAMP_WAIT_MS = 1_000L
    private const val TIMESTAMP_POLL_INTERVAL_MS = 5L
    private const val HARDWARE_KEY_DEBOUNCE_MS = 50L

    private val lock = Any()
    private val pendingKeyTimes = mutableMapOf<String, Long>()
    private val lastHardwareKeyDownTimeMs = mutableMapOf<Int, Long>()
    private val _loggingActive = MutableStateFlow(false)
    val loggingActive: StateFlow<Boolean> = _loggingActive.asStateFlow()

    private val _outputDirectoryPath = MutableStateFlow<String?>(null)
    val outputDirectoryPath: StateFlow<String?> = _outputDirectoryPath.asStateFlow()

    private val _currentCsvPath = MutableStateFlow<String?>(null)
    val currentCsvPath: StateFlow<String?> = _currentCsvPath.asStateFlow()

    private val _pressCounts = MutableStateFlow(
        mapOf(
            PttSource.VOLUME_UP.name to 0,
            PttSource.VOLUME_DOWN.name to 0,
            PttSource.HEADSET.name to 0,
            PttSource.SCREEN_BUTTON.name to 0
        )
    )
    val pressCounts: StateFlow<Map<String, Int>> = _pressCounts.asStateFlow()

    private val _duplicateKeyCount = MutableStateFlow(0)
    val duplicateKeyCount: StateFlow<Int> = _duplicateKeyCount.asStateFlow()

    private val _ignoredKeyCount = MutableStateFlow(0)
    val ignoredKeyCount: StateFlow<Int> = _ignoredKeyCount.asStateFlow()

    private val _bluetoothLatencyEstimateMs = MutableStateFlow<Long?>(null)
    val bluetoothLatencyEstimateMs: StateFlow<Long?> = _bluetoothLatencyEstimateMs.asStateFlow()

    private var appContext: Context? = null
    private var loggerScope: CoroutineScope? = null
    private var writerChannel: Channel<WriterMessage>? = null
    private var writerJob: Job? = null
    private var tickJob: Job? = null
    private var audioManager: AudioManager? = null
    private var audioDeviceCallbackRegistered = false

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (!isDebuggable()) return
            addedDevices.orEmpty().forEach { device ->
                mark(
                    eventType = "audio_device_added",
                    source = deviceTypeName(device.type),
                    extra = "type=${device.type}",
                    deviceName = device.productName?.toString().orEmpty()
                )
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (!isDebuggable()) return
            removedDevices.orEmpty().forEach { device ->
                mark(
                    eventType = "audio_device_removed",
                    source = deviceTypeName(device.type),
                    extra = "type=${device.type}",
                    deviceName = device.productName?.toString().orEmpty()
                )
            }
        }
    }

    /**
     * Bắt đầu ghi log vào thư mục app-specific `fieldtest`.
     *
     * @return `true` nếu đã khởi chạy ghi log hoặc logger đang chạy; bản release luôn trả `false`.
     */
    fun start(context: Context): Boolean {
        if (!isDebuggable(context)) return false
        val appContext = context.applicationContext
        val directory = try {
            appContext.getExternalFilesDir("fieldtest")
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể truy cập thư mục log fieldtest", exception)
            null
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Thư mục log fieldtest không khả dụng", exception)
            null
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Lỗi runtime khi lấy thư mục fieldtest", exception)
            null
        }
        if (directory == null) {
            Log.e(TAG, "getExternalFilesDir(fieldtest) trả về null; không bắt đầu ghi")
            return false
        }

        synchronized(lock) {
            if (_loggingActive.value) return true

            val timestamp = SimpleDateFormat(FILE_TIMESTAMP_PATTERN, Locale.US).format(Date())
            val outputFile = File(directory, "fieldtest_$timestamp.csv")
            val channel = Channel<WriterMessage>(
                capacity = CSV_BUFFER_CAPACITY,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.IO + HardwareCoroutineExceptionHandler.handler
            )
            this.appContext = appContext
            loggerScope = scope
            writerChannel = channel
            audioManager = try {
                appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            } catch (exception: SecurityException) {
                Log.e(TAG, "Không thể lấy AudioManager", exception)
                null
            } catch (exception: IllegalStateException) {
                Log.e(TAG, "AudioManager chưa sẵn sàng", exception)
                null
            } catch (exception: RuntimeException) {
                Log.e(TAG, "Lỗi runtime khi lấy AudioManager", exception)
                null
            }
            _outputDirectoryPath.value = directory.absolutePath
            _currentCsvPath.value = outputFile.absolutePath
            _pressCounts.value = _pressCounts.value.mapValues { 0 }
            _duplicateKeyCount.value = 0
            _ignoredKeyCount.value = 0
            _bluetoothLatencyEstimateMs.value = null
            pendingKeyTimes.clear()
            lastHardwareKeyDownTimeMs.clear()
            _loggingActive.value = true

            val writer = scope.launch(start = CoroutineStart.LAZY) {
                writeCsv(channel, outputFile)
            }
            writerJob = writer
            writer.invokeOnCompletion {
                synchronized(lock) {
                    if (writerJob === writer) {
                        unregisterAudioDeviceCallback()
                        _loggingActive.value = false
                        writerChannel = null
                        writerJob = null
                        loggerScope = null
                        audioManager = null
                    }
                }
                scope.cancel()
            }

            registerAudioDeviceCallback()
            scope.launch {
                AudioIncidentBus.incidents.collect { incident ->
                    mark(
                        eventType = "audio_incident",
                        source = incidentSource(incident),
                        extra = incident.toLogText()
                    )
                }
            }
            writer.start()
            mark("logger_started", source = "FieldTestLogger", extra = outputFile.absolutePath)
        }
        return true
    }

    /**
     * Dừng ghi bất đồng bộ; hàng đợi được xả và file flush trước khi writer kết thúc.
     */
    fun stop() {
        if (!isDebuggable()) return
        synchronized(lock) {
            if (!_loggingActive.value) return
            unregisterAudioDeviceCallback()
            tickJob?.cancel()
            tickJob = null
            mark("logger_stopped", source = "FieldTestLogger")
            writerChannel?.trySend(WriterMessage.Stop)
            writerChannel?.close()
            pendingKeyTimes.clear()
        }
    }

    /**
     * Ghi một dòng sự kiện tổng quát. Mọi chuỗi được CSV-escape trước khi ghi.
     */
    fun mark(
        eventType: String,
        source: String = "",
        extra: String = "",
        deviceName: String = ""
    ) {
        if (!isDebuggable() || !_loggingActive.value) return
        val channel = synchronized(lock) { writerChannel } ?: return
        val record = CsvRecord(
            timestampWallMs = System.currentTimeMillis(),
            elapsedRealtimeMs = SystemClock.elapsedRealtimeNanos() / NANOS_PER_MILLISECOND,
            eventType = eventType,
            source = source,
            deviceName = deviceName,
            extra = extra
        )
        channel.trySend(WriterMessage.Record(record))
    }

    /**
     * ĐIỂM NỐI TV4: gọi khi nhận KeyEvent volume/media, trước khi chuyển sự kiện tới PttController.
     * [event] dùng thời gian Android của sự kiện; [receivedElapsedRealtimeNanos] là thời điểm vào
     * handler để đo `key_input_to_app`.
     */
    fun recordKeyInput(
        event: android.view.KeyEvent?,
        source: PttSource,
        receivedElapsedRealtimeNanos: Long,
        deviceName: String = "",
        isKeyDown: Boolean? = null
    ) {
        if (!isDebuggable() || !_loggingActive.value) return
        val now = receivedElapsedRealtimeNanos
        val eventTimeMs = event?.eventTime
        val latencyMs = eventTimeMs?.let { eventTime ->
            val uptimeNowMs = SystemClock.uptimeMillis()
            val elapsedOffsetNs = now - uptimeNowMs * NANOS_PER_MILLISECOND
            max(0L, (now - (eventTime * NANOS_PER_MILLISECOND + elapsedOffsetNs)) / NANOS_PER_MILLISECOND)
        }
        val actionName = when {
            isKeyDown == true -> "down"
            isKeyDown == false -> "up"
            event?.action == android.view.KeyEvent.ACTION_UP -> "up"
            event?.action == android.view.KeyEvent.ACTION_DOWN -> "down"
            else -> "unknown"
        }
        val pendingKey = pendingKey(source, actionName)
        synchronized(lock) {
            pendingKeyTimes[pendingKey] = now
        }
        mark(
            eventType = "key_input_to_app",
            source = source.name,
            extra = buildString {
                append("action=").append(actionName)
                append(";keyCode=").append(event?.keyCode ?: "unknown")
                append(";eventTime=").append(eventTimeMs ?: "unknown")
                append(";latencyMs=").append(latencyMs ?: "unknown")
                if (event != null && event.repeatCount > 0) {
                    append(";repeatCount=").append(event.repeatCount)
                }
            },
            deviceName = deviceName
        )
    }

    /**
     * Ghi một lần nhận phím Volume và phân loại lần nhấn trùng/bị bỏ theo cùng điều kiện hiện hành.
     * ĐIỂM NỐI: HardwareKeyHandler gọi đúng một lần ở đầu mỗi nhánh down/up.
     */
    fun recordHardwareKeyEvent(
        keyCode: Int,
        event: android.view.KeyEvent?,
        source: PttSource,
        isKeyDown: Boolean
    ) {
        if (!isDebuggable() || !_loggingActive.value) return

        val receivedNs = SystemClock.elapsedRealtimeNanos()
        val pttEnabled = PttController.isPttModeEnabled.value &&
            PttController.isCaptureActive.value
        val sourceAllowed = pttEnabled && PttController.isVolumeKeyAllowed(source)
        var duplicate = false
        var ignoredReason: String? = null

        if (!sourceAllowed) {
            ignoredReason = if (!pttEnabled) "ptt_inactive" else "source_not_allowed"
        } else if (isKeyDown) {
            val repeat = event != null && event.repeatCount > 0
            val nowMs = System.currentTimeMillis()
            val previousTimeMs = synchronized(lock) { lastHardwareKeyDownTimeMs[keyCode] }
            val debounced = previousTimeMs != null &&
                nowMs - previousTimeMs < HARDWARE_KEY_DEBOUNCE_MS
            when {
                repeat -> {
                    duplicate = true
                    ignoredReason = "key_repeat"
                }

                debounced -> {
                    duplicate = true
                    ignoredReason = "debounced"
                }

                else -> synchronized(lock) {
                    lastHardwareKeyDownTimeMs[keyCode] = nowMs
                }
            }
            if (ignoredReason == null && PttController.isPttActive.value) {
                duplicate = true
                ignoredReason = "ptt_already_active"
            }
        } else if (
            !PttController.isPttActive.value ||
            PttController.activeSource.value != source
        ) {
            ignoredReason = "release_without_matching_press"
        }

        recordKeyInput(
            event = event,
            source = source,
            receivedElapsedRealtimeNanos = receivedNs,
            isKeyDown = isKeyDown
        )
        if (ignoredReason != null) {
            recordIgnoredKey(source, duplicate, ignoredReason)
        }
    }

    /**
     * Điểm nối cho callback nút media/tai nghe có dây hoặc Bluetooth.
     * Gọi tại nơi nhận [event], trước khi gửi PTT, để ghi đúng mốc eventTime.
     */
    fun recordMediaKeyInput(
        event: android.view.KeyEvent,
        source: PttSource = PttSource.HEADSET,
        deviceName: String = ""
    ) {
        if (!isDebuggable() || !_loggingActive.value) return
        recordKeyInput(
            event = event,
            source = source,
            receivedElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            deviceName = deviceName
        )
    }

    /**
     * Ghi một phím bị lặp hoặc bị bỏ do trạng thái PTT/cấu hình.
     */
    fun recordIgnoredKey(source: PttSource, duplicate: Boolean, reason: String) {
        if (!isDebuggable() || !_loggingActive.value) return
        synchronized(lock) {
            pendingKeyTimes.remove(pendingKey(source, "down"))
            pendingKeyTimes.remove(pendingKey(source, "up"))
            if (duplicate) {
                _duplicateKeyCount.value += 1
            } else {
                _ignoredKeyCount.value += 1
            }
        }
        mark(
            eventType = if (duplicate) "duplicate_key_ignored" else "key_ignored",
            source = source.name,
            extra = reason
        )
    }

    /**
     * Ghi thời điểm PttEvent được phát và tính app-to-PTT khi có KeyEvent đầu vào tương ứng.
     */
    fun recordPttEvent(event: PttEvent) {
        if (!isDebuggable() || !_loggingActive.value) return
        val source = event.source
        val eventType = when (event) {
            is PttEvent.PttPressed -> {
                synchronized(lock) {
                    if (source.name in _pressCounts.value) {
                        _pressCounts.value = _pressCounts.value.toMutableMap().apply {
                            this[source.name] = (this[source.name] ?: 0) + 1
                        }
                    }
                }
                "PttPressed"
            }

            is PttEvent.PttReleased -> "PttReleased"
        }
        val actionName = if (event is PttEvent.PttPressed) "down" else "up"
        val receivedNs = synchronized(lock) {
            pendingKeyTimes.remove(pendingKey(source, actionName))
        }
        val latencyMs = receivedNs?.let {
            max(0L, (SystemClock.elapsedRealtimeNanos() - it) / NANOS_PER_MILLISECOND)
        }
        mark(
            eventType = "app_to_ptt_event",
            source = source.name,
            extra = "event=$eventType;latencyMs=${latencyMs ?: "not_key_event"}"
        )
    }

    /**
     * ĐIỂM NỐI TV2: gọi `mark("mic_open", source, extra)` hoặc `mark("mic_closed", source, extra)`
     * đúng lúc microphone thật sự chuyển trạng thái. Hiện tại logger chỉ đo tới PttEvent, chưa nối
     * vào thao tác mở/đóng mic bên trong WebRTC.
     */
    fun recordMicrophoneState(open: Boolean, source: String = "TV2", extra: String = "") {
        if (!isDebuggable() || !_loggingActive.value) return
        mark(if (open) "mic_open" else "mic_closed", source, extra)
    }

    /**
     * Phát tick PCM ngắn rồi lấy timestamp phần cứng để ước lượng thời gian đầu ra.
     */
    fun playBluetoothLatencyTick() {
        if (!isDebuggable() || !_loggingActive.value) return
        synchronized(lock) {
            tickJob?.cancel()
            val scope = loggerScope ?: return
            tickJob = scope.launch {
                measureBluetoothOutputLatency()
            }
        }
    }

    private suspend fun measureBluetoothOutputLatency() {
        if (synchronized(lock) { appContext } == null) return
        val manager = synchronized(lock) { audioManager }
        val bluetoothDevice = manager?.let(::findBluetoothOutput)
        if (manager == null || bluetoothDevice == null) {
            mark(
                eventType = "bt_output_latency_unavailable",
                source = "BLUETOOTH",
                extra = "Không phát hiện đầu ra Bluetooth"
            )
            return
        }

        var track: AudioTrack? = null
        try {
            val minBufferBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufferBytes <= 0) {
                mark(
                    eventType = "bt_output_latency_failed",
                    source = "BLUETOOTH",
                    extra = "AudioTrack.getMinBufferSize=$minBufferBytes"
                )
                return
            }
            val samples = createTickSamples()
            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE_HZ)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val builder = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(max(minBufferBytes, samples.size * 2))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            track = builder.build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                mark(
                    eventType = "bt_output_latency_failed",
                    source = "BLUETOOTH",
                    extra = "AudioTrack chưa khởi tạo"
                )
                return
            }
            if (!track.setPreferredDevice(bluetoothDevice)) {
                mark(
                    eventType = "bt_output_latency_failed",
                    source = "BLUETOOTH",
                    extra = "Không thể chọn thiết bị Bluetooth cho AudioTrack"
                )
                return
            }

            val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) {
                mark(
                    eventType = "bt_output_latency_failed",
                    source = "BLUETOOTH",
                    extra = "AudioTrack.write=$written"
                )
                return
            }
            val playStartMonotonicNs = System.nanoTime()
            track.play()
            val routedType = track.routedDevice?.type
            if (
                routedType != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                routedType != AudioDeviceInfo.TYPE_BLUETOOTH_SCO &&
                !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    routedType == AudioDeviceInfo.TYPE_BLE_HEADSET)
            ) {
                mark(
                    eventType = "bt_output_latency_failed",
                    source = "BLUETOOTH",
                    extra = "AudioTrack chưa route tới Bluetooth (type=$routedType)"
                )
                return
            }

            val timestamp = AudioTimestamp()
            val deadlineMs = SystemClock.elapsedRealtime() + MAX_TIMESTAMP_WAIT_MS
            var estimateMs: Long? = null
            while (SystemClock.elapsedRealtime() < deadlineMs) {
                if (track.getTimestamp(timestamp) && timestamp.framePosition > 0L) {
                    val frameOriginNs =
                        timestamp.nanoTime -
                            timestamp.framePosition * NANOS_PER_SECOND / SAMPLE_RATE_HZ
                    estimateMs = max(0L, (frameOriginNs - playStartMonotonicNs) / NANOS_PER_MILLISECOND)
                    break
                }
                delay(TIMESTAMP_POLL_INTERVAL_MS)
            }

            if (estimateMs != null) {
                _bluetoothLatencyEstimateMs.value = estimateMs
                mark(
                    eventType = "bt_output_latency_est_ms",
                    source = "BLUETOOTH",
                    extra = "estimateMs=$estimateMs;framesWritten=$written"
                )
            } else {
                mark(
                    eventType = "bt_output_latency_failed",
                    source = "BLUETOOTH",
                    extra = "AudioTimestamp không hợp lệ sau ${MAX_TIMESTAMP_WAIT_MS}ms"
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể phát tick đo Bluetooth", exception)
            mark("bt_output_latency_failed", "BLUETOOTH", exception.message.orEmpty())
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "AudioTrack không ở trạng thái hợp lệ", exception)
            mark("bt_output_latency_failed", "BLUETOOTH", exception.message.orEmpty())
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Lỗi runtime khi đo đầu ra Bluetooth", exception)
            mark("bt_output_latency_failed", "BLUETOOTH", exception.message.orEmpty())
        } finally {
            try {
                track?.stop()
            } catch (exception: IllegalStateException) {
                Log.w(TAG, "AudioTrack đã dừng hoặc chưa phát", exception)
            }
            try {
                track?.release()
            } catch (exception: IllegalStateException) {
                Log.w(TAG, "Không thể giải phóng AudioTrack", exception)
            } catch (exception: RuntimeException) {
                Log.e(TAG, "Lỗi runtime khi giải phóng AudioTrack", exception)
            }
        }
    }

    private fun createTickSamples(): ShortArray {
        val sampleCount = SAMPLE_RATE_HZ * TICK_DURATION_MS / 1_000
        return ShortArray(sampleCount) { index ->
            val envelope = minOf(1.0, index / 200.0, (sampleCount - index) / 200.0)
            (sin(2.0 * PI * TICK_FREQUENCY_HZ * index / SAMPLE_RATE_HZ) *
                envelope * Short.MAX_VALUE * 0.45).toInt().toShort()
        }
    }

    private fun findBluetoothOutput(manager: AudioManager): AudioDeviceInfo? {
        val devices = try {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể kiểm tra đầu ra Bluetooth", exception)
            return null
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Không thể liệt kê thiết bị audio", exception)
            return null
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Lỗi runtime khi liệt kê thiết bị audio", exception)
            return null
        }
        return devices.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }

    private fun registerAudioDeviceCallback() {
        val manager = audioManager ?: return
        try {
            manager.registerAudioDeviceCallback(
                audioDeviceCallback,
                Handler(Looper.getMainLooper())
            )
            audioDeviceCallbackRegistered = true
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể theo dõi thay đổi thiết bị audio", exception)
            mark("audio_device_callback_failed", extra = exception.message.orEmpty())
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "AudioManager không thể đăng ký callback", exception)
            mark("audio_device_callback_failed", extra = exception.message.orEmpty())
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Lỗi runtime khi đăng ký audio callback", exception)
            mark("audio_device_callback_failed", extra = exception.message.orEmpty())
        }
    }

    private fun unregisterAudioDeviceCallback() {
        if (!audioDeviceCallbackRegistered) return
        audioDeviceCallbackRegistered = false
        try {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể hủy theo dõi thiết bị audio", exception)
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Không thể hủy callback thiết bị audio", exception)
        } catch (exception: RuntimeException) {
            Log.e(TAG, "Lỗi runtime khi hủy audio callback", exception)
        }
    }

    private suspend fun writeCsv(channel: Channel<WriterMessage>, outputFile: File) {
        var writer: BufferedWriter? = null
        try {
            outputFile.parentFile?.let { directory ->
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IllegalStateException("Không tạo được ${directory.absolutePath}")
                }
            }
            writer = BufferedWriter(
                OutputStreamWriter(FileOutputStream(outputFile, false), Charsets.UTF_8)
            )
            writer.append(CSV_HEADER).append('\n')
            writer.flush()

            var lastFlushElapsedMs = SystemClock.elapsedRealtime()
            var finished = false
            while (!finished) {
                val flushTimeoutMs = max(
                    0L,
                    FLUSH_INTERVAL_MS - (SystemClock.elapsedRealtime() - lastFlushElapsedMs)
                )
                when (val message = withTimeoutOrNull(flushTimeoutMs) { channel.receive() }) {
                    is WriterMessage.Record -> {
                        writer.append(message.record.toCsvLine()).append('\n')
                    }

                    WriterMessage.Stop -> {
                        while (true) {
                            val queued = channel.tryReceive().getOrNull() ?: break
                            if (queued is WriterMessage.Record) {
                                writer.append(queued.record.toCsvLine()).append('\n')
                            }
                        }
                        writer.flush()
                        lastFlushElapsedMs = SystemClock.elapsedRealtime()
                        finished = true
                    }

                    null -> {
                        writer.flush()
                        lastFlushElapsedMs = SystemClock.elapsedRealtime()
                    }
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể ghi CSV thực địa", exception)
            AudioIncidentBus.report(AudioIncident.Unknown(exception))
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "CSV thực địa không thể được ghi", exception)
            AudioIncidentBus.report(AudioIncident.Unknown(exception))
        } catch (exception: Exception) {
            Log.e(TAG, "Lỗi khi ghi CSV thực địa", exception)
            AudioIncidentBus.report(AudioIncident.Unknown(exception))
        } finally {
            try {
                writer?.flush()
                writer?.close()
            } catch (exception: Exception) {
                Log.e(TAG, "Không thể flush/đóng CSV thực địa", exception)
            }
        }
    }

    private fun isDebuggable(): Boolean {
        val context = appContext ?: return false
        return isDebuggable(context)
    }

    private fun isDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun pendingKey(source: PttSource, action: String) = "${source.name}:$action"

    private fun deviceTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "BUILTIN_EARPIECE"
        else -> if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            type == AudioDeviceInfo.TYPE_BLE_HEADSET
        ) {
            "BLE_HEADSET"
        } else {
            "AUDIO_DEVICE"
        }
    }

    private fun incidentSource(incident: AudioIncident): String = when (incident) {
        is AudioIncident.HeadsetUnplugged -> "HeadsetUnplugged"
        AudioIncident.HeadsetReconnected -> "HeadsetReconnected"
        AudioIncident.BluetoothDisconnected -> "BluetoothDisconnected"
        is AudioIncident.AudioFocusLost -> "AudioFocusLost"
        AudioIncident.AudioFocusRegained -> "AudioFocusRegained"
        is AudioIncident.MicUnavailable -> "MicUnavailable"
        is AudioIncident.AudioPermissionRevoked -> "AudioPermissionRevoked"
        is AudioIncident.AudioRouteFailed -> "AudioRouteFailed"
        is AudioIncident.Unknown -> "Unknown"
    }

    private fun AudioIncident.toLogText(): String = when (this) {
        is AudioIncident.HeadsetUnplugged -> "deviceType=$deviceType"
        AudioIncident.HeadsetReconnected -> "headset reconnected"
        AudioIncident.BluetoothDisconnected -> "Bluetooth disconnected"
        is AudioIncident.AudioFocusLost -> reason
        AudioIncident.AudioFocusRegained -> "focus regained"
        is AudioIncident.MicUnavailable -> reason
        is AudioIncident.AudioPermissionRevoked -> permission
        is AudioIncident.AudioRouteFailed -> reason
        is AudioIncident.Unknown -> throwable.message ?: throwable.javaClass.simpleName
    }

    private data class CsvRecord(
        val timestampWallMs: Long,
        val elapsedRealtimeMs: Long,
        val eventType: String,
        val source: String,
        val deviceName: String,
        val extra: String
    ) {
        fun toCsvLine(): String = listOf(
            timestampWallMs.toString(),
            elapsedRealtimeMs.toString(),
            eventType,
            source,
            deviceName,
            extra
        ).joinToString(",") { it.toCsvField() }

        private fun String.toCsvField(): String =
            "\"${replace("\"", "\"\"").replace("\r", " ").replace("\n", " ")}\""
    }

    private sealed interface WriterMessage {
        data class Record(val record: CsvRecord) : WriterMessage
        object Stop : WriterMessage
    }

    private const val FILE_TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss_SSS"
    private const val CSV_HEADER =
        "timestampWallMs,elapsedRealtimeMs,eventType,source,deviceName,extra"
    private const val NANOS_PER_MILLISECOND = 1_000_000L
    private const val NANOS_PER_SECOND = 1_000_000_000L
}
