# TUẦN 2 — NGƯỜI 3 (TV3): HOÀN THIỆN GIAO DIỆN ONBOARDING, VÀO KÊNH VÀ DANH SÁCH KÊNH

**Người phụ trách:** Thành viên 3 (TV3)  
**Thời gian commit giao diện được ghi nhận:** 22/10/2026 và 24/10/2026  
**Tiến độ:** Các hạng mục UI dưới đây có mã nguồn; phần trăm tổng thể chưa kiểm chứng.  
**Ảnh chụp màn hình:** Chưa có ảnh đính kèm trong tài liệu đã đối chiếu.

---

## 1. TIẾN ĐỘ THỰC HIỆN

- [x] Xây dựng onboarding ba trang.
- [x] Bổ sung giao diện vào kênh, danh sách kênh, sheet thao tác và thông báo đổi vai trò.
- [x] Bổ sung giao diện tạo/khôi phục sự kiện và màn demo PTT.
- [x] Sửa lỗi biên dịch được ghi nhận sau khi ghép nhánh.

## 2. CHI TIẾT CÔNG VIỆC ĐÃ LÀM

### 2.1. Onboarding và luồng vào kênh

- `ui/onboarding/OnboardingScreen.kt` và `OnboardingVisuals.kt`: giao diện giới thiệu dạng pager ba trang.
- `ui/join/JoinChannelScreen.kt`: giao diện lựa chọn QR/PIN và luồng nhập/vào kênh.
- `ui/join/JoinErrorScreen.kt`: giao diện lỗi khi vào kênh.
- `ui/join/UserInfoScreen.kt`: giao diện thông tin người dùng và vai trò.

### 2.2. Danh sách kênh và sự kiện

- `ui/channels/ChannelListScreen.kt`: giao diện danh sách kênh.
- `ui/channels/ChannelMenuSheet.kt`: sheet thao tác với kênh.
- `ui/channels/RoleChangedBanner.kt`: banner thông báo thay đổi vai trò.
- `ui/event/CreateEventScreen.kt` và `RestoreEventScreen.kt`: giao diện tạo và khôi phục sự kiện.

### 2.3. Màn demo và sửa lỗi sau ghép nhánh

- `hardware/PttKeyDemoScreen.kt`: màn hình Compose demo PTT, hiển thị trạng thái thao tác và cấu hình demo. Phần xử lý phím cứng không tính là phạm vi UI báo cáo.
- Commit `a8ddff7` ghi nhận sửa lỗi biên dịch liên quan `PttKeyDemoScreen`, `AdaptiveAppLayout` và `ChannelListScreen` sau khi ghép nhánh.

## 3. KẾT QUẢ / BÀN GIAO

- Có mã giao diện cho ba trang onboarding, các trạng thái vào kênh, danh sách kênh, sheet, banner vai trò và tạo/khôi phục sự kiện.
- QR/PIN được thể hiện ở cấp giao diện. Việc quét QR bằng camera, xác thực PIN và kết nối backend chưa kiểm chứng trong phần UI đã đối chiếu.
- Có commit ghi nhận sửa lỗi biên dịch sau ghép nhánh; build hoặc kiểm thử sau sửa chưa kiểm chứng.

## 4. LƯU Ý VÀ CÔNG VIỆC TUẦN SAU

- Phần UI cuộc gọi và chế độ Bỏ túi xuất hiện trong thay đổi giao diện Tuần 3.
- Màn PTT trong báo cáo là demo Compose; các tính năng thiết bị/phím cứng thuộc phạm vi ngoài UI và không được kết luận ở đây.
- Ảnh chụp màn hình, kết quả build/kiểm thử và phần trăm hoàn thành tổng thể chưa có dữ liệu xác nhận.
