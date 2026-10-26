# BÀN GIAO TÍCH HỢP MẠNG & WEBRTC CLIENT - TUẦN 1

**Người phụ trách:** Thành viên 2 (Client Network & WebRTC Integration)  
**Nhánh:** `Tuan-1`  

---

## 1. TIẾN ĐỘ THỰC HIỆN
Đã hoàn thành 100% nhiệm vụ Tuần 1 theo yêu cầu bàn giao từ Thành viên 1:

- [x] **Cấu hình Hạ tầng & Dependencies**:
  - Thêm `OkHttp 4.12.0` (WebSocket thời gian thực & Ping heartbeat).
  - Thêm `Gson 2.10.1` (Serialization/Deserialization gói tin signaling).
  - Thêm `io.getstream:stream-webrtc-android:1.3.10` (WebRTC Android Engine).
  - Cấu hình `android:usesCleartextTraffic="true"` trong AndroidManifest cho phép kết nối `ws://` nội bộ.
- [x] **Signaling Client (`SignalingClient.kt`)**:
  - Kết nối WebSocket tới máy chủ trung tâm (`ws://[IP]:8765`).
  - Tự động thực hiện Handshake (`type: register`) ngay khi mở kết nối.
  - Lắng nghe và nạp cấu hình STUN/TURN (`type: webrtc_config`).
  - Hỗ trợ định tuyến P2P: gửi/nhận SDP (`offer`, `answer`) và `ice_candidate`.
  - Cơ chế tự động kết nối lại (Auto Reconnect) khi rớt mạng với độ trễ an toàn.
- [x] **WebRTC Audio Engine (`WebRTCClient.kt`)**:
  - Khởi tạo Singleton `PeerConnectionFactory` an toàn.
  - Tự động nạp động danh sách `iceServers` nhận từ Signaling Server.
  - Thiết lập luồng âm thanh đàm thoại (AudioTrack / AudioSource) tích hợp bộ lọc chống vang (AEC), chống nhiễu (NS) và tự động tăng âm lượng (AGC).
  - Hỗ trợ chế độ bấm để nói Push-to-Talk (PTT): tắt mic khi nghe, mở mic khi nói.
- [x] **Tự động dò tìm Server LAN qua mDNS (`MdnsDiscoveryManager.kt`)**:
  - Tích hợp Android `NsdManager` quét dịch vụ `_bodam._tcp`.
  - Tự động nhận diện IP máy chủ từ `mdns_broadcaster.py` mà không cần nhập tay.
- [x] **Bộ điều phối trung tâm (`DamThoaiCallManager.kt`)**:
  - Cung cấp API một điểm duy nhất cho các màn hình Jetpack Compose (`ActiveCallScreen`, `QuickJoinScreen`):
    - `autoDiscoverAndConnect(userId)`
    - `startCall(targetUserId)`
    - `setPushToTalkPressed(isPressed)`
    - `endCall()`

---

## 2. DANH SÁCH FILE ĐÃ TRIỂN KHAI

| File | Chức năng |
| :--- | :--- |
| [`app/src/main/java/com/example/damthoai/network/model/SignalingModels.kt`] | Data models định nghĩa gói tin Register, WebRtcConfig, Offer, Answer, IceCandidate. |
| [`app/src/main/java/com/example/damthoai/network/SignalingClient.kt`] | Quản lý kết nối WebSocket, gửi nhận tin nhắn thời gian thực và tự động Reconnect. |
| [`app/src/main/java/com/example/damthoai/webrtc/WebRTCClient.kt`] | Điều khiển WebRTC PeerConnection, STUN/TURN động và quản lý microphone PTT. |
| [`app/src/main/java/com/example/damthoai/network/MdnsDiscoveryManager.kt`] | Quét dịch vụ mDNS `_bodam._tcp` trên mạng LAN để lấy IP Server tự động. |
| [`app/src/main/java/com/example/damthoai/service/DamThoaiCallManager.kt`] | Facade Singleton kết nối toàn bộ luồng mạng với giao diện UI Compose. |

---

## 3. HƯỚNG DẪN KẾT NỐI VÀ KIỂM THỬ TRÊN MÁY THẬT / EMULATOR

### A. Chạy Server máy chủ (trên máy tính)
```bash
# Terminal 1: Bật máy chủ trung tâm
python signaling_server.py

# Terminal 2: Bật phát sóng mDNS LAN
python mdns_broadcaster.py
```

### B. Sử dụng trong code Android / Compose UI
```kotlin
val callManager = DamThoaiCallManager.getInstance(context)

// 1. Tự động tìm IP qua mDNS và kết nối:
callManager.autoDiscoverAndConnect(userId = "Tong_Dao_Dien")

// Hoặc kết nối trực tiếp nếu biết IP:
// callManager.connect("ws://192.168.1.100:8765", userId = "Tong_Dao_Dien")

// 2. Gọi cho thiết bị khác:
callManager.startCall(targetUserId = "Pho_Dao_Dien")

// 3. Bấm giữ nút PTT để nói:
callManager.setPushToTalkPressed(true)  // Mở mic
callManager.setPushToTalkPressed(false) // Tắt mic (chỉ nghe)

// 4. Kết thúc cuộc gọi:
callManager.endCall()
```
