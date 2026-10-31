# BÁO CÁO TIẾN ĐỘ TUẦN 3 — THÀNH VIÊN 2
## FOREGROUND SERVICE ĐÀM THOẠI CHẠY NỀN, SEAMLESS HANDOVER & TÍCH HỢP CHẾ ĐỘ BỎ TÚI

**Người phụ trách:** Thành viên 2 (Client Network & Audio Background)  
**Nhánh:** `Tuan-3`  

---

## 1. TIẾN ĐỘ THỰC HIỆN TUẦN 3

Đã hoàn thành 100% nhiệm vụ Tuần 3 về tối ưu hóa độ trễ, đàm thoại chạy ngầm và chuyển mạng mượt mà (Seamless Handover):

- [x] **Xây dựng Foreground Service Đàm thoại Chạy nền (`DamThoaiAudioService.kt`)**:
  - Chạy ngầm liên tục khi người dùng khóa màn hình, tắt ứng dụng hoặc sử dụng Chế độ Bỏ túi ([`PocketModeScreen.kt`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/src/main/java/com/example/damthoai/ui/screens/PocketModeScreen.kt)).
  - Khai báo Notification Channel liên tục với quyền `FOREGROUND_SERVICE_MICROPHONE` chuẩn Android 14.
  - Quản lý `WakeLock` (`PARTIAL_WAKE_LOCK`) chống tình trạng HĐH đưa CPU vào chế độ ngủ sâu (Deep Sleep) làm trễ âm thanh.

- [x] **Tích hợp Điểm nối với Thành viên 4 (Hardware / Hands-Free)**:
  - Khi bắt đầu phiên chạy nền: tự động kích hoạt `HandsFreeSession.start(context)` và `PttController.setBackgroundSessionActive(true)`.
  - Giúp phím cứng Volume và nút bấm trên tai nghe hoạt động xuyên suốt khi tắt màn hình mà không bị `MainActivity.onPause()` cưỡng chế ngắt micro.
  - Tích hợp vòng đời bắt phím PTT: `acquireCapture("ActiveCallScreen")` và `acquireCapture("PocketModeScreen")`.

- [x] **Seamless Network Handover & ICE Gathering (Tích hợp Thành viên 1)**:
  - **`NetworkChangeMonitor.kt`**: Giám sát thay đổi đường truyền qua `ConnectivityManager.NetworkCallback` (Wi-Fi <-> 4G/Cellular).
  - **`HandoverSignalingClient.kt`**: Kết nối WebSocket tới máy chủ Tuần 3 của TV1 ([`server.js`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/server.js) - cổng 8080).
  - Tự động gửi gói tin `network_change` `{ "type": "network_change", "user_id": ..., "network_type": "WIFI" | "CELLULAR" }` giúp giữ phiên đàm thoại không bị ngắt quãng khi người dùng di chuyển giữa các vùng sóng mạng.
  - Hỗ trợ chuyển tiếp `ice_candidate` tức thì khi có đường truyền mới.

- [x] **Cấu hình Hạ tầng & Dependencies**:
  - Bổ sung `com.squareup.okhttp3:okhttp:4.12.0`, `com.google.code.gson:gson:2.10.1`, `kotlinx-coroutines-android:1.8.0` trong `app/build.gradle.kts`.
  - Khai báo `<service android:name=".service.DamThoaiAudioService" android:foregroundServiceType="microphone" />` trong `AndroidManifest.xml`.
  - Cấu hình `android:usesCleartextTraffic="true"`.

---

## 2. DANH SÁCH FILE ĐÃ TRIỂN KHAI TRONG TUẦN 3

| File | Phân hệ | Chức năng chi tiết |
| :--- | :--- | :--- |
| [`app/src/main/java/com/example/damthoai/service/DamThoaiAudioService.kt`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/src/main/java/com/example/damthoai/service/DamThoaiAudioService.kt) | Service / Background | Foreground Service giữ Micro, WakeLock và duy trì phiên PTT khi tắt màn hình hoặc bỏ túi. |
| [`app/src/main/java/com/example/damthoai/network/NetworkChangeMonitor.kt`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/src/main/java/com/example/damthoai/network/NetworkChangeMonitor.kt) | Mạng / Handover | Giám sát trạng thái kết nối Wi-Fi / 4G phục vụ chuyển mạng không đứt cuộc gọi. |
| [`app/src/main/java/com/example/damthoai/network/HandoverSignalingClient.kt`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/src/main/java/com/example/damthoai/network/HandoverSignalingClient.kt) | Mạng / WebSocket | Client kết nối server.js (cổng 8080) báo cáo chuyển mạng và định tuyến ICE Candidate. |
| [`app/src/main/java/com/example/damthoai/ui/screens/ActiveCallScreen.kt`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/src/main/java/com/example/damthoai/ui/screens/ActiveCallScreen.kt) | Giao diện Cuộc gọi | Tích hợp vòng đời Foreground Service và PttController capture. |
| [`app/src/main/java/com/example/damthoai/ui/screens/PocketModeScreen.kt`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/src/main/java/com/example/damthoai/ui/screens/PocketModeScreen.kt) | Chế độ Bỏ túi | Tích hợp giữ phiên đàm thoại ngầm và chặn chạm nhầm. |
| [`app/build.gradle.kts`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/build.gradle.kts) | Cấu hình Build | Thêm dependencies mạng và coroutines. |
| [`app/src/main/AndroidManifest.xml`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/src/main/AndroidManifest.xml) | Cấu hình Android | Đăng ký Foreground Service Microphone và bật Cleartext HTTP. |

---

## 3. HƯỚNG DẪN KIỂM THỬ TÍNH NĂNG TUẦN 3

### A. Chạy Server Chuyển Mạng & ICE Gathering (TV1)
```bash
# Cài đặt thư viện ws (nếu chưa cài)
npm install

# Bật máy chủ Signaling Tuần 3 (port 8080)
node server.js
```

### B. Kiểm thử Chạy ngầm & Chế độ Bỏ túi trên Thiết bị
1. Mở màn hình **Đàm Thoại** (`ActiveCallScreen`) hoặc bấm vào **Bỏ túi** (`PocketModeScreen`).
2. Quan sát thanh thông báo Android: xuất hiện thông báo *"Bộ Đàm Đang Hoạt Động · Nhấn giữ phím Âm lượng để Nói"*.
3. Bấm nút nguồn để tắt/khóa màn hình điện thoại:
   - Bấm giữ phím **Volume Down** hoặc **Nút tai nghe**: Micro vẫn mở và bắt đầu thu tiếng.
   - Nhả phím: Micro tự động tắt.
4. Bật/tắt Wi-Fi để chuyển sang 4G:
   - Logcat sẽ ghi nhận: `[SEAMLESS HANDOVER] Chuyển mạng: WIFI -> CELLULAR`.
   - Máy chủ `server.js` ghi nhận: `[HANDOVER] User ... vừa chuyển mạng sang CELLULAR. Đang giữ phiên...`.
