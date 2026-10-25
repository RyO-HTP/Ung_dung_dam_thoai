# TUẦN 3 — NGƯỜI 4: TÍCH HỢP NÚT TAI NGHE VÀ PHIÊN ĐÀM THOẠI RẢNH TAY

---

## 1. Đối chiếu Nhiệm vụ & Phạm vi Tích hợp Phần cứng

### 1.1. Mục tiêu của Thành viên 4 trong Tuần 3

Theo phạm vi đã ghi ở mục việc tuần sau trong báo cáo Tuần 2, công việc dự kiến gồm triển khai `HeadsetButtonManager`, `HandsFreeSession` cho nút tai nghe/đàm thoại rảnh tay và nối vòng đời bắt âm thanh với màn gọi, Bỏ túi. Báo cáo này phân biệt mã đang có trong working tree với commit đã ghi nhận trên nhánh `Tuan-3`: các thay đổi Tuần 3 được kiểm tra hiện chưa có commit tương ứng của TV4.

Issue #7 được nhắc trong kế hoạch Tuần 3 của báo cáo Tuần 2. Trong `git log` đã kiểm tra không thấy commit TV4 gắn với Issue #7; trạng thái xử lý Issue #7 chưa có bằng chứng.

### 1.2. Luồng xử lý thể hiện trong mã hiện tại

1. `MainActivity.onCreate()` gọi `HandsFreeSession.initialize(this)`. `HandsFreeSession` giữ application context và đồng bộ trạng thái phiên với cờ bắt âm thanh.
2. `PttController.acquireCapture(owner)` và `releaseCapture(owner)` cập nhật `isCaptureActive`, rồi thông báo thay đổi cho `HandsFreeSession.onCaptureActiveChanged()`.
3. `HandsFreeSession` chỉ khởi chạy `HeadsetButtonManager` khi có capture hoặc có lời gọi `start(context)`; `stop()` bỏ trạng thái khởi chạy tường minh. Trong mã đã tìm, chưa thấy nơi gọi `HandsFreeSession.start()`/`stop()` ngoài các định nghĩa.
4. `HeadsetButtonManager` tạo Android `MediaSession` để nhận các sự kiện nút tai nghe/Media, sau đó gửi nhấn/thả PTT qua `PttController`. Có chế độ `TOGGLE`/`HOLD` và tự nhả sau 60 giây ở chế độ `TOGGLE`.
5. `MainActivity.onPause()` trong working tree chỉ gọi `forceReleasePtt()` khi `backgroundSessionActive` đang tắt và `HandsFreeSession.isRunning` là false. Đây là điều kiện trong mã, chưa phải kết quả kiểm chứng thực tế khi tắt màn hình.

### 1.3. Kiểm tra các hạng mục và bằng chứng

| Hạng mục | Đối chiếu mã và Git |
|---|---|
| `hardware/HeadsetButtonManager.kt` (nút tai nghe) | File tồn tại dưới dạng **untracked** trong working tree; có mã tạo `MediaSession`, xử lý nút media và gửi PTT qua `PttController`. Chưa có commit tương ứng trên `Tuan-3`. |
| `hardware/HandsFreeSession.kt` (phiên nói rảnh tay) | File tồn tại dưới dạng **untracked** trong working tree; có `initialize`, `start`, `stop`, `onCaptureActiveChanged` và điều phối `HeadsetButtonManager`. Chưa có commit tương ứng trên `Tuan-3`. |
| `VolumeKeyAccessibilityService` và `HandsFreeSettings` | Chưa có bằng chứng: không tìm thấy file/lớp tương ứng trong workspace đã kiểm tra. |
| `MainActivity.kt` | Working tree có diff gọi `HandsFreeSession.initialize(this)` trong `onCreate()` và điều kiện giữ PTT trong `onPause()`. Chưa có commit Tuần 3 tương ứng; thay đổi hiện chưa commit. |
| `PttController.kt` | Working tree có diff gọi `HandsFreeSession.onCaptureActiveChanged(true/false)` trong `acquireCapture()`/`releaseCapture()`. Chưa có commit Tuần 3 tương ứng; thay đổi hiện chưa commit. |
| Nối `acquireCapture()`/`releaseCapture()` vào `ActiveCallScreen.kt` | Chưa có bằng chứng. Không thấy lời gọi trong màn hình này. |
| Nối `acquireCapture()`/`releaseCapture()` vào `PocketModeScreen.kt` | Chưa có bằng chứng. Không thấy lời gọi trong màn hình này. |
| Lời gọi `acquireCapture()`/`releaseCapture()` hiện có | Có lời gọi với owner `"ptt_demo"` trong `PttKeyDemoScreen.kt`; điều này không chứng minh hai màn hình Active Call và Bỏ túi đã được nối. |
| Lời gọi `PttController.setBackgroundSessionActive()` | Chưa có bằng chứng lời gọi nào trong workspace đã tìm. Có hàm định nghĩa trong `PttController.kt`, nhưng chưa thấy TV2/Chế độ Bỏ túi gọi hàm này. |
| Issue #7 | Chưa có bằng chứng commit TV4 xử lý/đóng Issue #7 trong `git log` đã kiểm tra. |

## 2. Giải thích Chi tiết Từng File Mã nguồn của Người 4 trong Tuần 3

### File 1: [`HeadsetButtonManager.kt`](app/src/main/java/com/example/damthoai/hardware/HeadsetButtonManager.kt) — Bắt nút tai nghe

- Khai báo `HeadsetPttMode.TOGGLE` và `HOLD`, cùng các trạng thái bật/tắt nút tai nghe.
- `start(context)` tạo `MediaSession`, đăng ký callback nhận `KEYCODE_HEADSETHOOK` và các phím Media Play/Pause; sự kiện phù hợp được chuyển thành thao tác PTT.
- `stop()` hủy tác vụ timeout, nhả PTT nếu nguồn đang là `HEADSET`, rồi giải phóng `MediaSession`.
- `scheduleAutoRelease()` đặt thời hạn tự nhả 60 giây khi ở chế độ `TOGGLE`. Đây là hằng số trong code, không phải kết quả đo kiểm.
- File đang untracked; chưa có bằng chứng commit chứa phần triển khai này. Mô tả trong comment về nhận nút khi tắt màn hình chưa được xác nhận bằng test trên thiết bị.

### File 2: [`HandsFreeSession.kt`](app/src/main/java/com/example/damthoai/hardware/HandsFreeSession.kt) — Điều phối phiên rảnh tay

- `initialize(context)` lưu application context, đọc trạng thái capture hiện tại rồi gọi `reconcile()`.
- `onCaptureActiveChanged(active)` cập nhật trạng thái capture; `reconcile()` mở/đóng `HeadsetButtonManager` theo capture hoặc trạng thái được yêu cầu bởi `start()`/`stop()`.
- Nếu cần khởi chạy trước khi context được khởi tạo, code ghi lỗi bằng `Log.e`; chưa có bằng chứng nhánh lỗi này đã xảy ra khi chạy ứng dụng.
- File đang untracked; chưa có bằng chứng commit chứa phần triển khai này. Trong workspace đã tìm, chưa thấy thành phần khác gọi `start()` hoặc `stop()`.

### File 3: [`PttController.kt`](app/src/main/java/com/example/damthoai/hardware/PttController.kt) — Nối trạng thái capture và PTT

- Working tree có bổ sung gọi `HandsFreeSession.onCaptureActiveChanged(true)` khi owner đầu tiên được thêm bằng `acquireCapture()`.
- Khi owner cuối cùng bị bỏ bằng `releaseCapture()`, code gửi trạng thái `false` và gọi `forceReleasePtt()`.
- Bản thân các hàm capture và `setBackgroundSessionActive(active)` có trong file đang xem; riêng phần nối callback hands-free là diff chưa commit.
- `setBackgroundSessionActive(active)` chỉ cập nhật `backgroundSessionActive`; chưa tìm thấy call site nào trong workspace.

### File 4: [`MainActivity.kt`](app/src/main/java/com/example/damthoai/MainActivity.kt) — Khởi tạo và xử lý khi Activity tạm dừng

- Working tree có bổ sung `HandsFreeSession.initialize(this)` trong `onCreate()`.
- `onPause()` trong working tree chỉ gọi `PttController.forceReleasePtt()` khi cả `backgroundSessionActive` và `HandsFreeSession.isRunning` đều false.
- Các thay đổi này hiện chưa commit. Việc giữ được âm thanh/phiên PTT khi màn hình tắt hoặc Activity mất focus chưa được kiểm tra trên máy thật/ADB.

### File 5: [`ActiveCallScreen.kt`](app/src/main/java/com/example/damthoai/ui/screens/ActiveCallScreen.kt) và [`PocketModeScreen.kt`](app/src/main/java/com/example/damthoai/ui/screens/PocketModeScreen.kt) — Điểm nối giao diện

- Cả hai file có mặt trong workspace. `ActiveCallScreen` hiện gọi `PttController.onKeyPressed()`/`onKeyReleased()` cho `SCREEN_BUTTON`; `PocketModeScreen` cũng xử lý nút PTT theo luồng giao diện.
- Không thấy `acquireCapture()` hoặc `releaseCapture()` trong hai file này. Vì vậy, chưa có bằng chứng vòng đời capture của hai màn hình đã được nối vào `HandsFreeSession`.
- Các file giao diện có thay đổi trên nhánh Tuần 3 nhưng thuộc phần UI của TV3; không tính các thay đổi đó là phần việc TV4.

### File 6: `VolumeKeyAccessibilityService` và `HandsFreeSettings` — Accessibility/cài đặt hands-free

- Chưa có bằng chứng về file hoặc lớp mang các tên này trong workspace đã kiểm tra; không ghi nhận triển khai hoặc commit tương ứng.

## 3. Kết quả, Khó khăn, Phối hợp và Công việc Tiếp theo

### 3.1. Kết quả và mức độ kiểm chứng

- Mã hiện có trong working tree thể hiện luồng `PttController` → `HandsFreeSession` → `HeadsetButtonManager`, cùng khởi tạo hands-free trong `MainActivity`.
- Hai file `HandsFreeSession.kt` và `HeadsetButtonManager.kt` hiện untracked; phần sửa `MainActivity.kt` và `PttController.kt` hiện là thay đổi chưa commit. `git log` không ghi nhận commit triển khai Tuần 3 của TV4 sau khi hợp nhất Tuan-2 vào Tuan-3.
- Chưa có bằng chứng nối `acquireCapture()`/`releaseCapture()` vào Active Call/Bỏ túi, hoặc có nơi gọi `setBackgroundSessionActive()`.
- Kết quả test tay trên máy thật hoặc qua ADB: **(mình điền)**.
- Kết quả test tự động: **chưa có bằng chứng**.
- Ảnh chụp/kết quả minh họa: **(mình điền)**.
- Ngày báo cáo: **(mình điền)**.
- Tỷ lệ hoàn thành (nếu cần): **(mình điền)**.

### 3.2. Khó khăn và cách xử lý / giới hạn bằng chứng

- Hành vi khi tắt màn hình chưa thể kết luận từ code: `MediaSession` được tạo khi `HandsFreeSession` chạy, nhưng chưa có kết quả xác nhận hệ điều hành tiếp tục chuyển nút tai nghe hoặc phiên thu âm có bị ngắt hay không.
- `MediaSession`/AccessibilityService có thể chịu ảnh hưởng bởi chính sách Android, nhà sản xuất thiết bị hoặc ứng dụng khác. Đây là rủi ro cần kiểm thử; chưa có bằng chứng bị chặn thực tế. Không thấy triển khai `VolumeKeyAccessibilityService` trong workspace.
- Để hoàn chỉnh luồng, cần thống nhất vòng đời capture với TV3 trước khi sửa các màn hình của TV3; hiện hai màn hình mục tiêu chưa gọi `acquireCapture()`/`releaseCapture()`.
- `git log` ghi nhận merge `Tuan-2` vào `Tuan-3` tại commit `10c2ad7` (2026-10-25). Đây là thông tin lịch sử nhánh; các commit Tuần 1/Tuần 2 được mang sang không tính là công việc Tuần 3.

### 3.3. Điểm nối với thành viên khác

- **TV2 (Audio/Foreground Service):** cần gọi `HandsFreeSession.start(context)`/`stop()` theo vòng đời phiên chạy nền nếu service sở hữu phiên; đồng thời gọi `PttController.setBackgroundSessionActive(true/false)` khi trạng thái nền thay đổi. Hiện chưa thấy các call site này trong workspace.
- **TV3 (màn gọi và Bỏ túi):** cần thống nhất owner và vòng đời để gọi `PttController.acquireCapture()` khi bắt đầu capture và `releaseCapture()` khi kết thúc tại `ActiveCallScreen`/`PocketModeScreen`. Hiện chưa có bằng chứng đã nối.
- Việc sửa trực tiếp file UI của TV3 cần báo trước và thống nhất điểm tích hợp với TV3; chưa ghi nhận việc phối hợp này đã diễn ra.

### 3.4. Việc tuần sau — Tuần 4: Hardware/Emergency

- Theo phạm vi được giao cho tuần tiếp theo: triển khai/tích hợp `EmergencyAlertManager`, `EmergencyVibrator`, `EmergencyTorch` và `EmergencyNotifier`.
- Phối hợp với TV3 về điểm kích hoạt giao diện/cảnh báo và TV2 nếu có yêu cầu phát âm thanh hoặc chạy nền. Chưa ghi nhận các thành phần Tuần 4 đã được triển khai trong bằng chứng đang kiểm tra.

### Bằng chứng Git và mức độ kiểm chứng

- `git log Tuan-2..Tuan-3` lọc tác giả `doanlehongyen` ghi nhận merge `10c2ad7` (“Merge branch 'Tuan-2' into Tuan-3”); không thấy commit tính năng TV4 mới sau merge. Các commit Tuần 1/Tuần 2 không được tính vào kết quả Tuần 3.
- Lịch sử các file `MainActivity.kt` và `PttController.kt` có commit TV4 trước đó thuộc Tuần 1/Tuần 2; các thay đổi hands-free đang thấy ở hai file là diff chưa commit.
- `git diff --stat` của working tree hiện báo thay đổi ở `MainActivity.kt` và `PttController.kt` (8 dòng thêm, 3 dòng xóa); hai file Kotlin hands-free untracked không được tính vào thống kê diff này.
- `git diff --stat Tuan-2...Tuan-3` gồm thay đổi của nhiều thành viên; báo cáo này không tính phần TV1/TV2/TV3/TV5 là công việc TV4.
- Chưa kiểm chứng bằng chạy ứng dụng, test tay trên máy thật/ADB hoặc kết quả test tự động.
