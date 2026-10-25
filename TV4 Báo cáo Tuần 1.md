# TUẦN 1 — NGƯỜI 4: QUẢN LÝ PHÂN QUYỀN HỆ THỐNG ANDROID

---

## 1. Đối chiếu Nhiệm vụ & Phạm vi Quản lý Quyền

### 1.1. Mục tiêu của module phân quyền trong Tuần 1

Theo mô tả trong mã nguồn, ứng dụng Đàm Thoại cần kiểm tra và hướng dẫn cấp các quyền để thu âm, dùng camera/đèn flash, kết nối tai nghe và nút PTT Bluetooth, nhận thông báo và duy trì hoạt động nền. Module tập trung việc phân loại, kiểm tra trạng thái và mở các màn hình hệ thống liên quan.

### 1.2. Luồng kiểm tra và yêu cầu quyền

1. `PermissionScreen` tải trạng thái hiện tại từ `PermissionManager`.
2. Với quyền runtime như Micro, Camera, Bluetooth và Thông báo, ứng dụng yêu cầu quyền qua Activity Result API.
3. Quyền Mạng được xem là đã cấp vì là quyền Normal khai báo trong Manifest. Quyền Chạy nền được kiểm tra riêng qua `PowerManager`; nếu chưa bỏ tối ưu pin, ứng dụng mở trang cài đặt hệ thống tương ứng.
4. Sau khi nhận kết quả hoặc quay lại ứng dụng, trạng thái được cập nhật. Nút tiếp tục kiểm tra các quyền bắt buộc trước khi gọi callback tiếp tục.

### 1.3. Nhóm quyền thể hiện trong mã nguồn

1. **Micro:** `RECORD_AUDIO`, được đánh dấu bắt buộc.
2. **Camera:** `CAMERA`, không bắt buộc cho chức năng nói cơ bản.
3. **Bluetooth:** Android 12 (API 31) trở lên trả về `BLUETOOTH_CONNECT` và `BLUETOOTH_SCAN`; phiên bản thấp hơn không có quyền runtime trong nhóm này.
4. **Mạng:** `INTERNET`, `ACCESS_NETWORK_STATE` và `ACCESS_WIFI_STATE` được khai báo trong Manifest; mã phân loại đây là quyền Normal, không hỏi runtime.
5. **Chạy nền:** kiểm tra trạng thái bỏ qua tối ưu hóa pin bằng `PowerManager`, và mở thiết lập hệ thống để người dùng thay đổi.
6. **Thông báo:** `POST_NOTIFICATIONS` được lấy vào danh sách quyền runtime từ Android 13 (API 33) trở lên.

## 2. Giải thích Chi tiết Từng File Mã nguồn của Người 4 trong Tuần 1

### File 1: [`AppPermission.kt`](app/src/main/java/com/example/damthoai/permission/AppPermission.kt) — Phân loại quyền

- Enum `AppPermission` gom các nhóm `AUDIO`, `CAMERA`, `BLUETOOTH`, `NOTIFICATION`, `BACKGROUND` và `NETWORK`, kèm tiêu đề, mô tả và cờ `isRequired`.
- Hàm `getManifestPermissions()` trả về quyền cần xin runtime theo nhóm và phiên bản Android. Nhóm mạng và chạy nền trả về mảng rỗng vì được xử lý theo cơ chế khác.

### File 2: [`PermissionState.kt`](app/src/main/java/com/example/damthoai/permission/PermissionState.kt) — Trạng thái quyền

- Khai báo ba trạng thái `GRANTED`, `DENIED` và `PERMANENTLY_DENIED`, liên kết với chuỗi hiển thị tương ứng.
- **Lưu ý theo mã hiện tại:** `PERMANENTLY_DENIED` đã được định nghĩa nhưng `checkPermissionState()` trong `PermissionManager.kt` trả về `DENIED` cho cả hai nhánh của phép kiểm tra rationale. Vì vậy, việc phân biệt quyền bị chặn vĩnh viễn chưa được xác nhận là hoạt động.

### File 3: [`PermissionManager.kt`](app/src/main/java/com/example/damthoai/permission/PermissionManager.kt) — Kiểm tra và điều phối cấp quyền

- Cung cấp `permissionStates` dưới dạng `StateFlow`; `refreshPermissionStates()` cập nhật trạng thái cho các nhóm quyền.
- `checkPermissionState()` kiểm tra quyền runtime, coi nhóm mạng là đã cấp và kiểm tra nhóm chạy nền qua `isIgnoringBatteryOptimizations()`.
- `allRequiredGranted()` kiểm tra các nhóm có `isRequired = true`; các hàm `hasAudioAndCameraPermissions()`, `hasNetworkAndBluetoothPermissions()` và `hasBackgroundExecutionPermissions()` cung cấp các phép kiểm tra theo nhóm.
- `requestPermissions()` tổng hợp danh sách quyền runtime theo phiên bản SDK. Riêng chạy nền, `requestIgnoreBatteryOptimizations()` mở thiết lập hệ thống; `openAppSettings()` mở trang cài đặt ứng dụng.

### File 4: [`PermissionScreen.kt`](app/src/main/java/com/example/damthoai/permission/PermissionScreen.kt) — Giao diện xin quyền và `MicDeniedScreen`

- `PermissionScreen` dùng Activity Result API để xin các quyền runtime, refresh trạng thái khi màn hình được tạo, khi nhận kết quả và khi Activity quay lại `ON_RESUME`.
- Nút tiếp tục kiểm tra các quyền bắt buộc; nếu chưa đủ, màn hình khởi chạy yêu cầu cho danh sách runtime và yêu cầu bỏ tối ưu pin nếu cần.
- Giao diện có hàng quyền Micro, Thông báo, Bluetooth và Chạy nền; phần chữ trên màn hình nêu Camera sẽ được hỏi khi quét QR. Trong mã màn hình này, danh sách runtime dùng cho nút tiếp tục cũng chứa các quyền mà `AppPermission` trả về, bao gồm Camera.
- `MicDeniedScreen` cung cấp nút mở cài đặt và nút thử lại; callback thử lại chỉ được gọi khi quyền Micro được kiểm tra là `GRANTED`.

### File 5: [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) — Khai báo quyền và tính năng thiết bị

- Khai báo các quyền mạng, âm thanh, camera, Bluetooth cũ/mới, thông báo, wake lock, foreground service và yêu cầu bỏ tối ưu hóa pin.
- Giới hạn quyền Bluetooth cũ ở `maxSdkVersion="30"`; khai báo camera và autofocus không bắt buộc, còn microphone được đánh dấu bắt buộc.
- Có khai báo `FOREGROUND_SERVICE_MICROPHONE`; báo cáo này chỉ xác nhận khai báo trong Manifest, chưa xác nhận hành vi chạy service nền.

### File 6: [`strings.xml`](app/src/main/res/values/strings.xml) — Nội dung hiển thị quyền

- Có tiêu đề, mô tả và trạng thái cấp quyền cho Micro, Camera, Bluetooth, Thông báo, Chạy nền và Mạng.
- Có chuỗi cho nút cấp quyền, mở cài đặt, thông báo rationale, hướng dẫn quyền bị từ chối và lưu ý tối ưu pin trên một số dòng máy.

### Bằng chứng Git và mức độ kiểm chứng

- `git log` trên nhánh `Tuan-1` ghi nhận các commit liên quan TV4: `11bf0b6` (“Hoan thanh nhiem vu quan ly phan quyen Android”), `171a73d` (“feat(permission): Hoàn thành module quản lý quyền hệ thống Android (Tuần 1 - TV4)”) và `7245f95` (“[Tuần 1][TV4] Thêm module quản lý phân quyền hệ thống (Micro, Camera, Mạng, Bluetooth, Chạy nền) - Closes #3”). Log hiển thị ngày `2026-10-22` cho các commit này.
- Tại thời điểm kiểm tra, nhánh hiện tại là `Tuan-1` và `git diff --stat` không hiển thị thay đổi chưa commit. Các nội dung trên được đối chiếu với mã nguồn đang có; **chưa kiểm chứng** bằng chạy ứng dụng, thử cấp quyền trên thiết bị hoặc kết quả test.
