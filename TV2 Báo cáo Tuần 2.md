# BÁO CÁO TIẾN ĐỘ TUẦN 2 — THÀNH VIÊN 2
## TÍCH HỢP LIVEKIT SFU & ĐÀM THOẠI ÂM THANH THEO KÊNH TRÊN ANDROID

**Người phụ trách:** Thành viên 2 (Client Network & Audio Streaming)  
**Nhánh:** `feat/tuan2-tv2-livekit` (hoặc `Tuan-2`)  

---

## 1. TIẾN ĐỘ THỰC HIỆN TUẦN 2

Đã hoàn thành 100% nhiệm vụ Tuần 2 theo yêu cầu bàn giao từ Thành viên 1 và thiết kế hệ thống:

- [x] **Cấu hình Thư viện & Quyền mạng**:
  - Thêm `io.livekit:livekit-android:2.11.1` (LiveKit SFU Android Client).
  - Thêm `com.squareup.okhttp3:okhttp:4.12.0` và `com.google.code.gson:gson:2.10.1`.
  - Cập nhật `settings.gradle.kts` bổ sung repository `maven("https://jitpack.io")`.
  - Bật `android:usesCleartextTraffic="true"` trong `AndroidManifest.xml` cho phép gọi API Token HTTP cục bộ.

- [x] **REST API Client Lấy Token SFU (`LiveKitTokenService.kt`)**:
  - Gửi request `GET http://[IP_HOST]:3000/get-token?room=[Kênh]&identity=[User]` tới máy chủ Node.js (`api.js`).
  - Tự động bóc tách chuỗi JSON trả về chứa `serverUrl` (`wss://ung-dung-dam-thoai-pze2v7u9.livekit.cloud`) và `token` JWT an toàn.
  - Hỗ trợ IP linh hoạt (`10.0.2.2` cho Android Emulator hoặc IP LAN cho máy thật).

- [x] **Quản lý Đàm thoại SFU Kênh (`LiveKitCallManager.kt`)**:
  - Khởi tạo và quản lý vòng đời `Room` của LiveKit.
  - Kết nối và tham gia vào kênh chỉ định (mặc định: `Kenh_Hau_Can` hoặc phòng người dùng chọn).
  - Quản lý luồng âm thanh micro (LocalAudioTrack) với bộ lọc chống vọng, chống nhiễu Opus tích hợp.
  - Lắng nghe danh sách thành viên trong phòng và hiển thị thời gian thực người đang phát biểu (`RoomEvent.ActiveSpeakersChanged`).

- [x] **Tích hợp Chặt chẽ với Phím Cứng Volume PTT (TV4)**:
  - Lắng nghe trực tiếp `PttController.events` từ module của Thành viên 4:
    - **Khi bấm giữ phím Volume** (`PttEvent.PttPressed`): Tự động bật micro (`setMicrophoneEnabled(true)`), chuyển sang trạng thái "ĐANG NÓI".
    - **Khi thả phím Volume** (`PttEvent.PttReleased`): Tự động ngắt micro (`setMicrophoneEnabled(false)`), chuyển về chế độ "ĐANG NGHE".
  - Hỗ trợ song song cả nút bấm PTT trên màn hình Compose UI lẫn phím cứng vật lý.

---

## 2. DANH SÁCH FILE ĐÃ TRIỂN KHAI TRONG TUẦN 2

| File | Phân hệ | Chức năng chi tiết |
| :--- | :--- | :--- |
| [`app/src/main/java/com/example/damthoai/livekit/LiveKitTokenService.kt`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/src/main/java/com/example/damthoai/livekit/LiveKitTokenService.kt) | Mạng / API | Gọi REST API Node.js lấy JWT Token kết nối LiveKit Cloud SFU. |
| [`app/src/main/java/com/example/damthoai/livekit/LiveKitCallManager.kt`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/src/main/java/com/example/damthoai/livekit/LiveKitCallManager.kt) | Audio / SFU | Quản lý kết nối phòng thoại, mute/unmute mic, lắng nghe sự kiện phím Volume PTT từ TV4 và danh sách người đang nói. |
| [`api.js`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/api.js) | Server Backend | Hỗ trợ tham số query `room` và `identity`, cấp phát token JWT theo từng kênh. |
| [`app/build.gradle.kts`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/build.gradle.kts) | Cấu hình Build | Bổ sung dependencies `livekit-android`, `okhttp`, `gson`. |
| [`settings.gradle.kts`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/settings.gradle.kts) | Cấu hình Gradle | Thêm JitPack repository. |
| [`app/src/main/AndroidManifest.xml`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/src/main/AndroidManifest.xml) | Cấu hình Android | Cho phép kết nối Cleartext HTTP nội bộ. |

---

## 3. HƯỚNG DẪN KIỂM THỬ TRẢI NGHIỆM ĐÀM THOẠI SFU

### Bước 1: Khởi động Server Cấp Token (Máy tính)
```bash
# Cài đặt thư viện Node.js (nếu chưa cài)
npm install

# Bật máy chủ cấp Token LiveKit (port 3000)
node api.js
```

### Bước 2: Sử dụng trong Mã nguồn Android / Compose UI
```kotlin
val liveKitManager = LiveKitCallManager.getInstance(context)

// 1. Tham gia phòng thoại theo Kênh (vd: Kênh Hậu Cần):
liveKitManager.joinChannel(
    apiBaseUrl = "http://10.0.2.2:3000", // Hoặc IP LAN máy tính nếu test máy thật
    roomName = "Kenh_Hau_Can",
    identity = "Tong_Dao_Dien"
)

// 2. Nói chuyện (PTT):
// Không cần gọi thủ công nếu dùng phím Volume cứng vì LiveKitCallManager đã tự động lắng nghe PttController!
// Nếu bấm trên màn hình UI:
liveKitManager.setPushToTalkPressed(true)  // Mở mic nói
liveKitManager.setPushToTalkPressed(false) // Tắt mic nghe

// 3. Rời phòng:
liveKitManager.leaveChannel()
```

---

## 4. TÀI LIỆU BÀN GIAO GỐC TỪ THÀNH VIÊN 1 (LƯU TRỮ ĐỐI CHIẾU)

> - **Cấu hình SFU**: Khởi tạo thành công cụm máy chủ LiveKit Cloud phục vụ định tuyến âm thanh nhiều kênh cùng lúc (`wss://ung-dung-dam-thoai-pze2v7u9.livekit.cloud`).
> - **API Cấp Token**: Chạy tại `http://[IP_MÁY_CHỦ]:3000/get-token`.
> - **Thông số**: Cấp phép người dùng tham gia phòng thoại chỉ định (`roomJoin`).