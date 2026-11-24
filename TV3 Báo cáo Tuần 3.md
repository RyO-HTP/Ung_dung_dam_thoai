# TUẦN 3 — NGƯỜI 3 (TV3): HOÀN THIỆN GIAO DIỆN ĐÀM THOẠI VÀ CHẾ ĐỘ BỎ TÚI

**Người phụ trách:** Thành viên 3 (TV3)  
**Thời gian commit giao diện được ghi nhận:** 25/10/2026  
**Tiến độ:** Các hạng mục UI dưới đây có mã nguồn/diff; phần trăm tổng thể chưa kiểm chứng.  
**Ảnh chụp màn hình:** Chưa có ảnh đính kèm trong tài liệu đã đối chiếu.

---

## 1. TIẾN ĐỘ THỰC HIỆN

- [x] Bổ sung giao diện Đàm thoại với sáu trạng thái.
- [x] Bổ sung giao diện Bỏ túi, vuốt mở khóa và giới hạn vùng chạm khi khóa.
- [x] Điều chỉnh điều hướng để thanh dưới không hiển thị trên màn gọi và Bỏ túi.
- [ ] Màn quản lý sự kiện riêng và QR Ban thực tế: chưa kiểm chứng trong thay đổi UI Tuần 3 đã đối chiếu.

## 2. CHI TIẾT CÔNG VIỆC ĐÃ LÀM

### 2.1. Màn hình Đàm thoại

- `ui/screens/ActiveCallScreen.kt`: định nghĩa sáu trạng thái `CallIdle`, `CallTalk`, `CallTake`, `CallBusy`, `CallCut`, `CallOffline`.
- `ui/components/CallComponents.kt`: có các thành phần cuộc gọi như nút PTT, banner trạng thái, thông tin âm lượng và hành động dưới màn hình.
- `data/fake/FakeCallData.kt`: cung cấp nội dung minh họa theo trạng thái; đây là dữ liệu giả lập giao diện, không phải kết quả kết nối cuộc gọi thực tế.

### 2.2. Chế độ Bỏ túi

- `ui/screens/PocketModeScreen.kt`: có thanh vuốt để mở khóa.
- Khi khóa, vùng chạm ngoài nút PTT và thanh mở khóa được chặn; vị trí chạm được đối chiếu theo tọa độ màn hình.
- Trong diff làm việc hiện tại có điều chỉnh xử lý gesture/vùng chạm; kết quả kiểm thử thực tế trên thiết bị chưa kiểm chứng.

### 2.3. Điều hướng và các mục liên quan sự kiện/QR

- `ui/navigation/AdaptiveAppLayout.kt`: giới hạn thanh dưới ở các route danh sách kênh/cài đặt, không hiển thị trên route Đàm thoại và Bỏ túi.
- `ui/join/JoinChannelScreen.kt` có hướng dẫn quét QR của ban và chuỗi QR mẫu `MOCK_QR_EVENT_889900`. Điều này xác nhận giao diện/mẫu dữ liệu, không xác nhận QR thật được tạo hoặc đọc.
- `ui/event/CreateEventScreen.kt` có nội dung đề cập quản lý mã QR; màn quản lý sự kiện riêng và chức năng QR Ban thực tế trong phạm vi Tuần 3 chưa kiểm chứng.

## 3. KẾT QUẢ / BÀN GIAO

- Commit `8f235f8` ghi nhận hoàn thiện màn gọi và chế độ Bỏ túi; diff làm việc hiện tại có các chỉnh sửa liên quan `ActiveCallScreen.kt`, `PocketModeScreen.kt`, `CallComponents.kt` và `AdaptiveAppLayout.kt`.
- Trong diff hiện tại, phần nội dung cuộc gọi được cuộn độc lập với vùng nút thao tác; thanh dưới bị ẩn khỏi các màn gọi/Bỏ túi.
- Không có bằng chứng build, kiểm thử tự động hoặc chạy trên thiết bị đã hoàn tất.

## 4. LƯU Ý VÀ CÔNG VIỆC TIẾP THEO

- Công việc tiếp theo của tuần sau chưa kiểm chứng trong các commit/tài liệu đã đối chiếu.
- Cần bổ sung ảnh chụp màn hình và kết quả kiểm thử nếu có.
- Phần trăm hoàn thành tổng thể chưa có số liệu xác nhận.
