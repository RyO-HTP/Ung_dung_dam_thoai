# TUẦN 4 — NGƯỜI 4: TÍCH HỢP HARDWARE, CẢNH BÁO KHẨN CẤP VÀ CHẨN ĐOÁN

---

## 1. Mục tiêu và Phạm vi

Tuần 4 tập trung hoàn thiện các điểm nối phần cứng/hệ thống của TV4: quản lý cảnh báo khẩn cấp, bảo vệ tuyến âm thanh khi thiết bị bị ngắt, ghi log đo thực địa và kết nối các API hardware sẵn có với giao diện. Không thay đổi logic WebRTC của TV2 hoặc tạo đường điều khiển microphone ngoài `PttController`.

Các API hardware hiện được giao diện sử dụng gồm `EmergencyAlertManager`, `AudioRouteGuard` và một phần `HandsFreeSettings`. Không thay đổi chữ ký API công khai của các thành phần này.

## 2. Kết quả triển khai

### 2.1. Cảnh báo khẩn cấp

- `EmergencyAlertManager` quản lý `currentAlert`, trạng thái rung/flash, lý do thiết bị không khả dụng, thời hạn tự dừng và các thao tác `trigger`, `acknowledge`, `snooze`, `stop`.
- `EmergencyVibrator`, `EmergencyTorch` và `EmergencyNotifier` cung cấp các đầu ra phần cứng/thông báo. MainActivity khởi tạo settings/session, theo dõi cảnh báo để bật/tắt màn hình khi khóa và chuyển hành động từ notification về manager.
- `AppNavHost` theo dõi `EmergencyAlertManager.currentAlert` và mở route cảnh báo khi có cảnh báo. Màn cảnh báo đọc tên/vai trò từ alert; các callback hiện tại gọi acknowledge/snooze và điều hướng theo luồng sẵn có.
- Đã sửa lỗi Kotlin smart-cast ở `RemainingScreens.kt` bằng cách đọc delegated state vào biến cục bộ `currentAlert`.

**Giới hạn giao diện:** màn cảnh báo hiện có trong source chỉ chứa chip “ĐANG RUNG” cùng các nút “Nhận cuộc gọi” và “Từ chối”. Chưa có chip trạng thái flash hoặc các nút mang đúng nhãn “Trả lời”/“Để sau”; không tự dựng lại bố cục trong phạm vi tích hợp này.

### 2.2. Bảo vệ và chọn tuyến âm thanh

- `AudioRouteGuard` theo dõi thiết bị đầu ra, cập nhật `currentRoute`, `availableRoutes` và `outputMuted`, đồng thời báo sự cố qua `AudioIncidentBus`.
- Hai nút “Loa ngoài” và “Tai nghe” hiện có trong hàng thao tác cuộc gọi được nối với `AudioRouteGuard.userSelectRoute(...)`.
- Trạng thái chọn dựa trên `currentRoute`; nút tai nghe bị làm mờ và không nhận thao tác nếu không có route HEADSET hoặc BLUETOOTH trong `availableRoutes`.
- Lựa chọn an toàn khi rút tai nghe phụ thuộc vào mute/route của adapter audio TV2. `AudioRouteGuard` hiện có logging adapter; chưa có bằng chứng adapter đó điều khiển âm thanh WebRTC thật.

### 2.3. Logger và checklist thực địa

- `FieldTestLogger` chỉ ghi ở bản debug, sử dụng `ApplicationInfo.FLAG_DEBUGGABLE`, lưu CSV trong thư mục app-specific `fieldtest` và ghi bất đồng bộ qua coroutine I/O.
- CSV có mốc thời gian, loại sự kiện, nguồn, tên thiết bị và thông tin bổ sung. Logger ghi sự kiện PTT, phím Volume, thay đổi thiết bị audio và sự cố audio; có API để TV2 đánh dấu `mic_open`/`mic_closed`.
- Công cụ phát tick và đọc `AudioTimestamp` cung cấp ước lượng phần mềm cho độ trễ đầu ra Bluetooth. Checklist test nằm trong [`docs/TEST_THUC_DIA_TV4.md`](docs/TEST_THUC_DIA_TV4.md).
- Logger có API `recordMediaKeyInput()`, nhưng callback phím media hiện chưa được nối tại `HeadsetButtonManager`.

### 2.4. Cài đặt hands-free và tích hợp giao diện

- `HandsFreeSettings` đã có StateFlow và setter cho bảy lựa chọn: nút tai nghe, giữ Volume Down, khử ồn, tự tăng gain microphone, hạ âm kênh phụ, beep/haptic và cảnh báo khẩn cấp.
- Các switch hiện có trong `SettingsScreen` cho nút tai nghe và audio ducking được nối với StateFlow/setter tương ứng.
- Đã xóa các comment `TODO(TV4)` được tìm thấy trong `ui/permission/PermissionUiState.kt` và `ui/permission/PermissionScreen.kt`.
- Không tìm thấy `HapticController` hoặc `RogerBeepPlayer` trong source đã kiểm tra để nối với `HandsFreeSettings.beepHapticEnabled` và `PttController.events`.

## 3. File và Điểm nối liên quan

| Khu vực | File chính | Trạng thái |
|---|---|---|
| Cảnh báo khẩn cấp | `hardware/emergency/EmergencyAlertManager.kt`, `EmergencyVibrator.kt`, `EmergencyTorch.kt`, `EmergencyNotifier.kt` | API hardware có sẵn; UI cảnh báo được nối một phần |
| Vòng đời hệ thống | `MainActivity.kt` | Khởi tạo hands-free/audio; theo dõi alert và xử lý notification action |
| Tuyến audio | `hardware/audio/AudioRouteGuard.kt`, `AudioRouteController.kt`, `AudioIncidentBus.kt` | Guard/StateFlow có sẵn; nút chọn route đã nối; adapter audio thật của TV2 chưa xác nhận |
| Log thực địa | `hardware/diagnostics/FieldTestLogger.kt`, `HardwareKeyHandler.kt`, `PttController.kt` | Đã có log CSV và các mốc Volume/PTT |
| Giao diện cuộc gọi | `ui/screens/ActiveCallScreen.kt`, `ui/components/CallComponents.kt` | Hai nút tuyến audio đã nối |
| Giao diện cảnh báo | `ui/screens/RemainingScreens.kt`, `ui/navigation/AppNavHost.kt` | Đọc alert, trạng thái rung và tự điều hướng |
| Cài đặt | `hardware/HandsFreeSettings.kt`, `ui/screens/SettingsScreen.kt` | Hai switch đang có đã nối; màn Rảnh tay chưa có đủ bảy switch |
| Tài liệu | `docs/TEST_THUC_DIA_TV4.md` | Checklist test ngoài trời/hội trường và hướng dẫn CSV |

## 4. Kiểm chứng, Giới hạn và Khó khăn

### 4.1. Kết quả kiểm tra

- Sau khi sửa lỗi smart-cast, Problems không báo lỗi cho `RemainingScreens.kt`.
- Các Kotlin file đã chỉnh trong lượt tích hợp được kiểm tra bằng Problems và không báo lỗi tại thời điểm kiểm tra.
- Đã rà soát mã nguồn để tìm `TODO(TV4)` và marker xung đột; không còn kết quả tìm thấy trong `app/src/main/java`.
- Chưa xác nhận bằng một lần Gradle build đầy đủ hoặc chạy ứng dụng trên máy thật.

### 4.2. Giới hạn cần ghi nhận

- Route `HandsFreeAudio` hiện vẫn mở `PttKeyDemoScreen`; `SettingsScreen` chưa xuất hiện trong luồng điều hướng. Vì vậy, hai switch đã nối trong `SettingsScreen` chưa được xác nhận là truy cập được từ luồng UI hiện hành.
- Các công tắc Trợ năng và năm công tắc hands-free còn lại chưa có control tương ứng trong màn hiện tại. Chưa thể nối đầy đủ mà không bổ sung giao diện hoặc thay đổi điều hướng.
- Màn cảnh báo hiện không có chip flash; chưa thể hiển thị trạng thái flash ngay trên màn đó nếu không thêm control.
- Chưa tìm thấy implementation của HapticController/RogerBeepPlayer; phản hồi beep/haptic trên mọi nguồn PTT chưa được xác minh.
- Chưa nối `mic_open`/`mic_closed` đến thời điểm microphone WebRTC thực sự mở/đóng.
- Chưa nối sự kiện media key vào logger, nên không có phép đo `KeyEvent.eventTime` thực tế cho nút tai nghe.
- `AudioTimestamp` chỉ cung cấp ước lượng phần mềm; buffer, codec và firmware Bluetooth có thể khiến kết quả khác độ trễ âm học. Cần đo đối chiếu bằng video chậm 240 fps.
- Tuyến âm thanh an toàn khi rút tai nghe phụ thuộc cách TV2 phát audio và adapter mute/route thực tế. Chưa xác nhận bằng thử nghiệm trên tai nghe và thiết bị thật.

## 5. Kịch bản Kiểm thử Máy thật

1. Cài bản debug, vào màn demo và bắt đầu ghi log; nhấn/thả các phím Volume và xác nhận CSV có `key_input_to_app`/`app_to_ptt_event`.
2. Kết nối tai nghe có dây và Bluetooth, đổi giữa “Loa ngoài”/“Tai nghe”; xác nhận route khả dụng và trạng thái nút.
3. Trong khi đang nghe qua tai nghe, rút dây hoặc ngắt Bluetooth; kiểm tra không tự phát ra loa ngoài, PTT được thả và sự cố được ghi nhận.
4. Gửi cảnh báo khẩn cấp khi app ở foreground, background và khi màn hình khóa; xác nhận route cảnh báo, trạng thái rung, thao tác nhận/trì hoãn và tự dừng.
5. Phát tick khi có tai nghe Bluetooth, ghi ít nhất 20 mẫu và đối chiếu với video 240 fps.
6. Dừng logger, lấy CSV bằng ADB từ đường dẫn app-specific `fieldtest`, rồi điền kết quả vào [`docs/TEST_THUC_DIA_TV4.md`](docs/TEST_THUC_DIA_TV4.md).

## 6. Phối hợp và Công việc Cần tiếp tục

- **TV2:** cài `AudioRouteController` thực tế để mute/unmute/đổi tuyến audio; gọi logger tại thời điểm mic thật sự mở/đóng; thống nhất chế độ phát và xử lý audio focus.
- **TV3:** thống nhất màn Rảnh tay và cảnh báo để bổ sung/nối các control còn thiếu; xác nhận nhãn và thao tác nút cảnh báo; nếu có HapticController/RogerBeepPlayer thì nối với `beepHapticEnabled` và collect `PttController.events`.
- **TV1/TV5:** gọi `EmergencyAlertManager.trigger(context, alert)` khi nhận tín hiệu khẩn cấp.
- **Kiểm thử:** chạy Gradle build đầy đủ và test trên các hãng Android khác nhau, đặc biệt khi màn hình khóa, tiết kiệm pin, tai nghe bị ngắt và quyền phần cứng bị từ chối.



