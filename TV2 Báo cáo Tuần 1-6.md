# BÁO CÁO HOÀN THIỆN NHIỆM VỤ THÀNH VIÊN 2 — TUẦN 1 ĐẾN TUẦN 6

**Người phụ trách:** Thành viên 2 (Client Architecture, Audio DSP, Foreground Service & Offline Queue)  
**Nhánh làm việc:** `Tuan-4` (đồng bộ mới nhất trên commit TV1 `6f26266`)

---

## 1. TỔNG QUAN TIẾN ĐỘ (TUẦN 1 – TUẦN 6)

| Tuần | Nhiệm vụ Thành viên 2 | Trạng thái | File triển khai chính |
| :--- | :--- | :--- | :--- |
| **Tuần 1** | Dự án Android Kotlin; kiến trúc đa luồng với Coroutines & `StateFlow`/`SharedFlow` | Hoàn thành | `app/build.gradle.kts`, `DamThoaiCallManager.kt`, toàn bộ module TV2 dùng Coroutines + `StateFlow` |
| **Tuần 2** | Tích hợp chuẩn mã hóa nén âm thanh **Opus**; tắt/mở micro; chuyển luồng loa ngoài / tai nghe / Bluetooth | Hoàn thành | `webrtc/audio/OpusAudioConfigurer.kt`, `hardware/audio/WebRTCAudioRouteController.kt`, `webrtc/WebRTCClient.kt` |
| **Tuần 3** | **Foreground Service** kèm **Smart WakeLock** giữ ứng dụng chạy ngầm khi tắt màn hình bỏ túi | Hoàn thành | `service/DamThoaiAudioService.kt`, `AndroidManifest.xml`, `PocketModeScreen.kt`, `ActiveCallScreen.kt` |
| **Tuần 4** | **Audio Ducking** (tự hạ âm kênh phụ khi kênh chính có người nói) & **AGC** (tự khuếch đại mic khi nói nhỏ) | Hoàn thành | `webrtc/audio/AudioDuckingController.kt`, `webrtc/audio/AudioProcessingManager.kt`, `webrtc/WebRTCClient.kt`, `ActiveCallScreen.kt` |
| **Tuần 5** | Tính năng **"Cứu mạng" — Hàng đợi Ngoại tuyến (Store and Forward)**: ghi âm lưu đệm khi rớt sóng, gửi vọt khi có mạng lại | Hoàn thành | `audio/offline/OfflineVoiceQueueManager.kt`, `SignalingModels.kt`, `SignalingChannel.kt`, `SignalingClient.kt`, `LocalLanSignaling.kt`, `signaling_server.py` |
| **Tuần 6** | Bật **Noise Suppression (NS/AEC)**, tối ưu **Jitter Buffer** chống méo tiếng, rà soát **tiêu thụ pin chạy nền 4 tiếng** | Hoàn thành | `webrtc/audio/AudioProcessingManager.kt`, `webrtc/audio/JitterBufferOptimizer.kt`, `service/BatteryOptimizationMonitor.kt`, `DamThoaiAudioService.kt` |

---

## 2. CHI TIẾT TRIỂN KHAI TỪNG TUẦN

### Tuần 1 — Kiến trúc Android Kotlin, Coroutines & StateFlow
- Toàn bộ pipeline TV2 được xây dựng bằng Kotlin với `CoroutineScope(SupervisorJob() + Dispatchers.IO / Default / Main)` và đồng bộ trạng thái qua `StateFlow` bất biến:
  - `AudioDuckingController.channelStates` & `isDuckingActive`
  - `AudioProcessingManager.state`
  - `OfflineVoiceQueueManager.state`
  - `JitterBufferOptimizer.recommendation`
  - `BatteryOptimizationMonitor.report`
  - `DamThoaiAudioService.serviceState`

### Tuần 2 — Chuẩn mã hóa nén âm thanh Opus & Điều khiển thiết bị âm thanh
- **`OpusAudioConfigurer.kt`**:
  - Chỉnh SDP Offer/Answer đưa `opus/48000` lên vị trí ưu tiên số 1 trong dòng `m=audio`.
  - Gắn tham số tối ưu cho đàm thoại bộ đàm: `minptime=10`, `useinbandfec=1` (phục hồi mất gói), `usedtx=1` (ngắt truyền khi im lặng tiết kiệm 4G/pin), `maxaveragebitrate=24000`, `stereo=0`, `a=ptime:20`, `a=maxptime:60`.
  - Cấu hình `maxBitrateBps` trực tiếp trên audio `RtpSender`.
- **`WebRTCAudioRouteController.kt`**:
  - Triển khai đầy đủ interface `AudioRouteController` của TV4, cài đặt vào `AudioRouteGuard.installController(...)` tại `MainActivity.onCreate()`.
  - Hỗ trợ song song **Android 12+ (`setCommunicationDevice`)** và **API 24–30 (`isSpeakerphoneOn` / `startBluetoothSco`)** cho 4 tuyến: `SPEAKER`, `EARPIECE`, `HEADSET`, `BLUETOOTH`.
  - `muteOutput()` / `unmuteOutput()` khóa/mở thật các remote `AudioTrack` trong `WebRTCClient` chống lọt tiếng ra loa ngoài khi rút tai nghe.
- **`WebRTCClient.setMicrophoneEnabled()`**:
  - Bật/tắt micro thật sự theo phím PTT và ghi mốc `mic_open` / `mic_closed` vào `FieldTestLogger` của TV4.

### Tuần 3 — Foreground Service kèm Smart WakeLock (Chạy ngầm bỏ túi)
- **`DamThoaiAudioService.kt`** (đã khai báo `foregroundServiceType="microphone"` trong `AndroidManifest.xml`):
  - Giữ ứng dụng không bị hệ điều hành Android đóng khi tắt màn hình hoặc bật Chế độ Bỏ túi (`PocketModeScreen`).
  - **Smart WakeLock phân đoạn**: giữ `PARTIAL_WAKE_LOCK` theo từng khung 15 phút và tự động gia hạn mỗi 12 phút (an toàn chống rò rỉ pin) kết hợp `WifiLock` chế độ độ trễ thấp (`WIFI_MODE_FULL_LOW_LATENCY`).
  - **Nối đầy đủ hợp đồng TV4**:
    - `PttController.setBackgroundSessionActive(true/false)`
    - `HandsFreeSession.start(context)` / `HandsFreeSession.stop()`
    - `AudioFocusGuard.start(context)` / `AudioFocusGuard.stop()`
  - Thông báo thường trực hiển thị kênh đang bật, số tin nhắn đang chờ trong Hàng đợi Ngoại tuyến và nút **Dừng chạy nền**.
  - Đã nối `PttController.acquireCapture()` / `releaseCapture()` và khởi chạy dịch vụ tại `ActiveCallScreen.kt` & `PocketModeScreen.kt` (có bảo vệ `LocalInspectionMode` để không lỗi Compose Preview).

### Tuần 4 — Audio Ducking đa kênh & Tự động khuếch đại mic (AGC)
- **`AudioDuckingController.kt`**:
  - Phân cấp ưu tiên kênh (`PRIMARY`: Toàn sự kiện > `STAGE`: Sân khấu > `SECONDARY`: Hậu cần – An ninh).
  - Đồng bộ tự động với công tắc `HandsFreeSettings.duckSecondaryEnabled`, trạng thái người đang nói `SpeakingStateRepository.speakingChannelIds` và cảnh báo khẩn cấp `EmergencyAlertManager.currentAlert`.
  - Khi kênh ưu tiên cao hơn có người nói (hoặc có cảnh báo khẩn cấp), tự động hạ âm lượng kênh phụ xuống **20%** (hạ 80%, khớp nhãn `"HẠ ÂM 80%"` trên `ActiveCallScreen`), áp dụng thật lên `WebRTCClient.setRemoteVolume(...)`, và có độ trễ hồi âm `450ms` (release-hold) tránh giật âm thanh.
- **`AudioProcessingManager.kt` + `WebRTCClient.kt`**:
  - Bật `googAutoGainControl` và `googAutoGainControl2` trong `MediaConstraints` của WebRTC theo công tắc `HandsFreeSettings.autoMicGainEnabled`.
  - Gắn thêm `android.media.audiofx.AutomaticGainControl` phần cứng và bộ **`SoftwareAgcProcessor`** (tính RMS dBFS, tự động khuếch đại tối đa `+18 dB` khi nói nhỏ, có ngưỡng Noise Gate `-52 dBFS` và Soft Limiter `tanh` chống vỡ tiếng) cho luồng ghi âm ngoại tuyến.

### Tuần 5 — Tính năng "Cứu mạng" (Hàng đợi Ngoại tuyến — Store and Forward)
- **`OfflineVoiceQueueManager.kt`**:
  - Khi mất mạng (`NetworkStatus.Offline` hoặc đang `Reconnecting` mất kênh signaling), nhấn giữ phím PTT sẽ tự động ghi âm bằng `AudioRecord` (16kHz mono 16-bit, có chạy qua Hardware AudioEffects + Software AGC/NS), đóng gói chuẩn WAV và lưu bền vững vào `filesDir/offline_voice_queue/` kèm chỉ mục JSON (`queue_index.json`).
  - Giới hạn bộ nhớ đệm an toàn (tối đa 30 tin / 10MB, TTL 15 phút, bỏ bản ghi chạm nhầm < 300ms).
  - Ngay khi mạng phục hồi (`SignalingConnectionState.Registered`, `NetworkQualityMonitor` thoát `Offline`, hoặc có peer trong `LAN_P2P`), tự động **gửi vọt FIFO** toàn bộ hàng đợi qua gói tin `voice_note` (hỗ trợ cả Signaling Server `signaling_server.py` lẫn `LocalLanSignaling`), giữ lại & retry nếu mạng chập chờn giữa chừng, xóa file đệm khi gửi thành công.
  - Phía máy nhận lắng nghe `SignalingEvent.VoiceNoteReceived`, giải mã WAV/PCM và phát lại qua `AudioTrack` tôn trọng khóa bảo mật `AudioRouteGuard.outputMuted`.
  - Nối số tin chờ gửi thật (`queuedCount`) lên banner của `ActiveCallScreen`.

### Tuần 6 — Noise Suppression, Adaptive Jitter Buffer & Tối ưu Pin chạy nền 4 giờ
- **Lọc tiếng ồn & Khử vọng (`AudioProcessingManager.kt` + `WebRTCClient.kt`)**:
  - Khởi tạo `JavaAudioDeviceModule` với Hardware AEC + Hardware Noise Suppressor (tự kiểm tra phần cứng thiết bị hỗ trợ).
  - Bật `googNoiseSuppression`, `googNoiseSuppression2`, `googEchoCancellation`, `googHighpassFilter`, `googTypingNoiseDetection` đồng bộ theo `HandsFreeSettings.noiseSuppressionEnabled`.
- **Tối ưu bộ đệm chống méo tiếng (`JitterBufferOptimizer.kt` + `WebRTCClient.applyJitterBufferConfig()`)**:
  - Phân tích liên tục `CallStats` từ TV1 (`jitterMs`, `intervalLossRate`, `concealmentRatio`, `rttMs`) và điều chỉnh động `RTCConfiguration.audioJitterBufferMaxPackets`, `audioJitterBufferFastAccelerate`, bật QoS `enableDscp = true` kết hợp bitrate Opus:
    - `LOW_LATENCY` (50 gói, fastAccelerate=true, 24 kbps): mạng ổn định.
    - `BALANCED` (100 gói, fastAccelerate=true, 24 kbps): jitter 15–45ms hoặc mất gói 1–5%.
    - `HIGH_RESILIENCE` (150–200 gói, fastAccelerate=false, 18 kbps + In-band FEC): mạng yếu / mất gói >= 5% / concealment >= 5%.
  - Tăng độ đệm ngay lập tức khi mạng xấu và chỉ hạ xuống sau 3 cửa sổ ổn định liên tiếp (hysteresis chống méo tiếng).
- **Rà soát & Tối ưu tiêu thụ pin chạy nền liên tục 4 giờ (`BatteryOptimizationMonitor.kt`)**:
  - Kết hợp Opus DTX (`usedtx=1`), Smart WakeLock phân đoạn 15 phút và chu kỳ `getStats` thích ứng (`2s` khi đàm thoại foreground → `10s` khi nghe chờ chạy nền → `15s` khi pin <= 20%).
  - Theo dõi tốc độ tụt pin (`%/giờ`), nhiệt độ, tổng thời gian giữ WakeLock, kiểm tra ngân sách pin cho ca 4 tiếng (`<= 6%/giờ`) và ghi `battery_audit` vào CSV `FieldTestLogger` mỗi 5 phút.

---

## 3. KIỂM CHỨNG & UNIT TEST

Đã biên dịch thành công toàn bộ ứng dụng Android (`:app:compileDebugKotlin`) và chạy toàn bộ **33/33 Unit Test PASSED (0 failures, 0 errors)** với Gradle `testDebugUnitTest`:

- **`Tv2AudioPipelineTest` (8/8 PASS)** — `app/src/test/java/com/example/damthoai/webrtc/audio/Tv2AudioPipelineTest.kt`:
  - Kiểm thử Opus SDP munging (ưu tiên payload, FEC, DTX, bitrate, ptime/maxptime).
  - Kiểm thử Audio Ducking 80%, release-hold 450ms, công tắc tắt ducking, emergency override.
  - Kiểm thử Software AGC khuếch đại giọng nhỏ, noise gate không khuếch đại tạp âm nền, soft limiter chống vỡ tiếng.
  - Kiểm thử Adaptive Jitter Buffer nâng cấp tức thì & hysteresis hạ cấp.
- **`OfflineVoiceQueueAndBatteryTest` (5/5 PASS)** — `app/src/test/java/com/example/damthoai/audio/offline/OfflineVoiceQueueAndBatteryTest.kt`:
  - Kiểm thử Store-and-Forward lưu đệm offline, bỏ bản ghi quá ngắn, gửi vọt FIFO khi có mạng lại, bảo toàn tin chưa gửi khi lỗi giữa chừng, round-trip WAV header.
  - Kiểm thử kiểm toán pin chạy nền 4 giờ (đạt/vượt ngân sách 6%/giờ, theo dõi WakeLock, chu kỳ thích ứng tiết kiệm pin).
- **20/20 test hiện có của TV3/TV4 tiếp tục PASS**: `PttControllerTest` (7), `AudioRouteDecisionTest` (4), `PermissionStateResolverTest` (4), `QrTokenParserTest` (3), `LocalQrCardsRepositoryTest` (1), `SpeakerStatusTest` (1).
- **`signaling_server.py`**: Kiểm tra cú pháp Python (`py_compile`) thành công sau khi bổ sung loại gói `voice_note`.
