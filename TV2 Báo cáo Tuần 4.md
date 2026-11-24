# BÁO CÁO TIẾN ĐỘ TUẦN 4 — THÀNH VIÊN 2
## TÍCH HỢP BẢO VỆ TUYẾN ÂM THANH (AUDIO ROUTE GUARD), LOG THỰC ĐỊA & CẢNH BÁO KHẨN CẤP

**Người phụ trách:** Thành viên 2 (Client Network & Audio/Hardware Integration)  
**Nhánh:** `Tuan-4`  

---

## 1. TIẾN ĐỘ THỰC HIỆN TUẦN 4

Đã hoàn thành 100% nhiệm vụ Tuần 4 theo hợp đồng điểm nối với Thành viên 4 và thiết kế hệ thống chung:

- [x] **Cài đặt Bộ điều khiển Tuyến âm thanh WebRTC thực tế (`WebRTCAudioRouteController.kt`)**:
  - Triển khai toàn bộ interface `AudioRouteController` của TV4, thay thế hoàn toàn bản giả `LoggingAudioRouteController`.
  - Kết nối trực tiếp với Android `AudioManager` (ở chế độ `MODE_IN_COMMUNICATION`) và luồng âm thanh `WebRTCClient`:
    - **`muteOutput()`**: Khóa âm thanh đầu ra khi người dùng rút tai nghe có dây hoặc ngắt kết nối Bluetooth, ngăn tuyệt đối âm thanh nhạy cảm của bộ đàm tự động phát ra loa ngoài.
    - **`unmuteOutput()`**: Mở lại âm thanh đầu ra an toàn sau khi tuyến nghe (Earpiece hoặc Loa ngoài được xác nhận) sẵn sàng.
    - **`routeTo(route: AudioRoute)`**: Chuyển đổi linh hoạt giữa các tuyến:
      - `SPEAKER`: Bật loa ngoài (`isSpeakerphoneOn = true`).
      - `EARPIECE`: Phát qua loa trong (áp tai nghe bảo mật).
      - `HEADSET`: Ưu tiên xuất âm thanh qua cổng tai nghe có dây 3.5mm / Type-C.
      - `BLUETOOTH`: Bật Bluetooth SCO (`startBluetoothSco()`, `isBluetoothScoOn = true`) phục vụ tai nghe không dây.
  - Cài đặt instance vào hệ thống tại `MainActivity.onCreate()`:
    `AudioRouteGuard.installController(WebRTCAudioRouteController(applicationContext, callManager.webRTCClient))`.

- [x] **Ghi Log Chẩn đoán Đo đạc Thực địa (`FieldTestLogger`)**:
  - Tích hợp lời gọi `FieldTestLogger.recordMicrophoneState(open = Boolean, source = "TV2_PTT")` trực tiếp tại thời điểm Micro WebRTC thực sự mở (`setEnabled(true)`) hoặc đóng (`setEnabled(false)`).
  - Cung cấp các mốc thời gian chính xác trong file CSV chẩn đoán, phục vụ kiểm thử độ trễ đàm thoại và đo đối chiếu âm học theo [`docs/TEST_THUC_DIA_TV4.md`](docs/TEST_THUC_DIA_TV4.md).

- [x] **Tích hợp Phát Cảnh Báo Khẩn Cấp (Emergency Alert)**:
  - Bổ sung hàm `triggerEmergency(fromName, fromRole, isDirectCall)` trong `DamThoaiCallManager`.
  - Tích hợp trực tiếp với `EmergencyAlertManager.trigger(context, alert)` của TV4 để kích hoạt rung nhịp điệu (`EmergencyVibrator`), chớp đèn flash cứu hộ (`EmergencyTorch`) và hiển thị màn hình cảnh báo khẩn cấp toàn màn hình (`RemainingScreens.kt`).

---

## 2. DANH SÁCH FILE ĐÃ TRIỂN KHAI TRONG TUẦN 4

| File | Phân hệ | Chức năng chi tiết |
| :--- | :--- | :--- |
| [`app/src/main/java/com/example/damthoai/hardware/audio/WebRTCAudioRouteController.kt`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/src/main/java/com/example/damthoai/hardware/audio/WebRTCAudioRouteController.kt) | Audio / Tuyến âm thanh | Cài đặt `AudioRouteController`, điều khiển `AudioManager` chuyển loa ngoài, loa trong, tai nghe và khóa âm thanh bảo mật. |
| [`app/src/main/java/com/example/damthoai/webrtc/WebRTCClient.kt`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/src/main/java/com/example/damthoai/webrtc/WebRTCClient.kt) | Audio / WebRTC | Thêm hàm `setOutputMuted(Boolean)` và tích hợp `FieldTestLogger.recordMicrophoneState` tại thời điểm mic đóng/mở thật. |
| [`app/src/main/java/com/example/damthoai/MainActivity.kt`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/src/main/java/com/example/damthoai/MainActivity.kt) | Vòng đời ứng dụng | Cài đặt `WebRTCAudioRouteController` vào `AudioRouteGuard` khi khởi động ứng dụng. |
| [`app/src/main/java/com/example/damthoai/service/DamThoaiCallManager.kt`](file:///e:/learning/do%20an%20thuc%20te/Ung_dung_dam_thoai/app/src/main/java/com/example/damthoai/service/DamThoaiCallManager.kt) | Quản lý Đàm thoại | Bổ sung hàm `triggerEmergency` kích hoạt cảnh báo khẩn cấp kết nối với `EmergencyAlertManager`. |

---

## 3. HƯỚNG DẪN KIỂM THỬ THỰC ĐỊA TUẦN 4

### A. Kiểm thử Đổi Tuyến Âm thanh và Khóa Bảo vệ khi Rút Tai Nghe
1. Mở màn hình Đàm thoại (`ActiveCallScreen`):
   - Bấm nút **Loa ngoài**: Âm thanh phát lớn qua loa ngoài của điện thoại.
   - Bấm nút **Tai nghe** (khi cắm tai nghe có dây hoặc kết nối tai nghe Bluetooth): Âm thanh chuyển ngay sang tai nghe, nút hiển thị trạng thái được chọn.
2. Đang trong cuộc đàm thoại qua tai nghe, tiến hành **rút jack tai nghe ra**:
   - `AudioRouteGuard` ngay lập tức gọi `WebRTCAudioRouteController.muteOutput()`.
   - Âm thanh được khóa lại, không bị tự động phát rồ ra loa ngoài.
   - Hệ thống chuyển về loa trong (Earpiece) hoặc giữ im lặng an toàn.

### B. Kiểm thử Log Thực Địa (Field Test Logger)
1. Mở ứng dụng ở chế độ Debug, bấm giữ nút Volume để nói chuyện (PTT):
   - `WebRTCClient` tự động ghi nhận sự kiện mở mic vào CSV:
     `[Timestamp], mic_open, TV2_PTT, ARDAMSA0`.
   - Nhả nút: ghi nhận sự kiện đóng mic:
     `[Timestamp], mic_closed, TV2_PTT, ARDAMSA0`.
2. Dùng lệnh ADB kéo file log CSV ra đối chiếu:
   `adb pull /sdcard/Android/data/com.example.damthoai/files/fieldtest/ .`

### C. Kiểm thử Kích hoạt Cảnh Báo Khẩn Cấp
- Bấm nút **Khẩn cấp** trên màn hình đàm thoại:
  - `EmergencyAlertManager` được kích hoạt ngay lập tức.
  - Điện thoại rung theo chu kỳ SOS khẩn cấp, đèn Flash nháy báo hiệu (nếu phần cứng hỗ trợ).
  - Màn hình tự động bật sáng (ngay cả khi màn hình đang khóa) và hiển thị thông tin người phát cảnh báo khẩn cấp.
