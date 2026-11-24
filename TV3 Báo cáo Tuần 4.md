# TUẦN 4 — NGƯỜI 3 (TV3): HOÀN THIỆN TOÀN BỘ GIAO DIỆN 
**Người phụ trách:** Thành viên 3 (TV3)  
**Thời gian commit được ghi nhận:** 28/10/2026 và 01/11/2026  
**Tiến độ:** Hoàn thành toàn bộ phạm vi giao diện TV3 tuần 4 theo mã nguồn và các commit đã ghi nhận. Chưa kiểm thử thực tế trên thiết bị.  


---

## 1. TIẾN ĐỘ THỰC HIỆN

- [x] Hoàn thiện các thành phần giao diện dùng chung và trạng thái chất lượng mạng trên màn hình cuộc gọi.
- [x] Hoàn thiện giao diện ba màn hình trong luồng gọi/cảnh báo khẩn cấp.
- [x] Hoàn thiện giao diện kết nối đa tầng và quản lý thành viên sự kiện, gồm chỉnh cấp thành viên.
- [x] Hoàn thiện giao diện thông tin vòng đời sự kiện và mã QR.
- [ ] Kiểm thử thực tế toàn bộ giao diện trên thiết bị: chưa thực hiện/chưa có kết quả xác nhận.

## 2. CHI TIẾT CÔNG VIỆC ĐÃ LÀM

### 2.1. Thành phần dùng chung và trạng thái mạng

- `ui/components/CommonComponents.kt`: bổ sung các thành phần Compose dùng chung cho giao diện.
- `data/fake/FakeNetworkStatus.kt`: định nghĩa trạng thái `Good`, `Weak`, `Offline` và dữ liệu minh họa độ trễ.
- `ui/screens/ActiveCallScreen.kt`: hiển thị trạng thái mạng/độ trễ trong phần đầu màn hình cuộc gọi.
- Trạng thái mạng hiện lấy từ `FakeNetworkStatus` để hiển thị các trạng thái trên giao diện; chưa xác nhận phép đo ping hoặc kết nối mạng thực tế.

### 2.2. Luồng gọi và cảnh báo khẩn cấp

- `ui/screens/EmergencyScreens.kt`: bổ sung giao diện chọn người nhận cuộc gọi khẩn cấp, nhận cảnh báo đến và cuộc gọi khẩn cấp đang diễn ra.
- `ui/navigation/Routes.kt` và `AppNavHost.kt`: khai báo route và nối các màn hình vào luồng điều hướng.
- Màn hình cảnh báo nhận trạng thái cảnh báo, rung và đèn flash qua các luồng trạng thái được truyền vào. Phần giao diện đã hoàn thiện; việc hoạt động thực tế của phần cứng chưa được kiểm thử/xác nhận.
- `data/fake/FakeEmergencyUiData.kt`: cung cấp dữ liệu mẫu cho người nhận, người tham gia và trạng thái cảnh báo/flash.

### 2.3. Kết nối đa tầng

- `ui/screens/RemainingScreens.kt`: bổ sung màn hình bật/tắt các lựa chọn Internet, mạng cục bộ và Bluetooth trực tiếp.
- `data/fake/FakeMultiLevelConnectionData.kt`: lưu trạng thái lựa chọn cho giao diện bằng `StateFlow`.
- Các lựa chọn trạng thái trên giao diện đã hoàn thiện; trạng thái hiện được quản lý bằng dữ liệu giả lập, chưa xác nhận chuyển tuyến hoặc truyền thông thực tế qua các loại kết nối này.

### 2.4. Quản lý thành viên và vòng đời sự kiện

- `ui/screens/RemainingScreens.kt`: bổ sung giao diện quản lý sự kiện, mã QR của tôi, danh sách thành viên, sự kiện kết thúc và thành viên bị xóa khỏi sự kiện.
- `ui/event/EventMembersViewModel.kt`: hỗ trợ tìm kiếm, lọc thành viên theo cấp, cập nhật thông tin/cấp và xóa thành viên trên dữ liệu repository hiện có.
- `ui/components/KitOverlays.kt`: bổ sung các sheet/dialog dùng chung, trong đó có luồng thao tác chỉnh cấp thành viên.
- Luồng giao diện thành viên đã hoàn thiện và hiện thao tác với `FakeEventRepository`; việc đồng bộ backend hoặc phân quyền trên máy chủ chưa được xác nhận.

## 3. KẾT QUẢ / BÀN GIAO

- Commit `71162ca` ghi nhận nền thành phần dùng chung, trạng thái mạng minh họa và cập nhật giao diện liên quan.
- Commit `1f32011` ghi nhận ba màn hình khẩn cấp, giao diện thành viên/sheet chỉnh cấp và kết nối đa tầng.
- Các màn hình đã được khai báo route trong `AppNavHost`; phạm vi giao diện tuần 4 được xem là hoàn thành.
- Dữ liệu demo và repository giả lập được dùng để thể hiện trạng thái, thao tác trên giao diện; đây không phải xác nhận tích hợp backend.
- Chưa có bằng chứng build đầy đủ, kiểm thử tự động hoặc kiểm thử thực tế trên thiết bị trong tài liệu đã đối chiếu.

## 4. KIỂM THỬ THỰC TẾ VÀ TÍCH HỢP TIẾP THEO

- Kiểm thử toàn bộ giao diện và luồng điều hướng trên thiết bị; ghi nhận kết quả và bổ sung ảnh chụp màn hình sau khi thực hiện.
- Xác minh trạng thái mạng, kết nối đa tầng và điều khiển rung/flash với các nguồn/phần cứng thực tế.
- Khi backend sẵn sàng, thay nguồn dữ liệu giả lập và xác minh thao tác thành viên được đồng bộ, phân quyền đúng.
- Tiến độ hoàn thành giao diện TV3 tuần 4: 100%; kiểm thử thực tế và tích hợp thực tế chưa xác nhận.
