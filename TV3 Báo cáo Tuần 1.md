# TUẦN 1 — NGƯỜI 3 (TV3): XÂY DỰNG NỀN GIAO DIỆN ANDROID BẰNG JETPACK COMPOSE

**Người phụ trách:** Thành viên 3 (TV3)  
**Thời gian commit giao diện được ghi nhận:** 22/10/2026 và 24/10/2026  
**Tiến độ:** Các hạng mục UI dưới đây có mã nguồn; phần trăm tổng thể chưa kiểm chứng.  
**Ảnh chụp màn hình:** Chưa có ảnh đính kèm trong tài liệu đã đối chiếu.

---

## 1. TIẾN ĐỘ THỰC HIỆN

- [x] Xây dựng nền thiết kế và các thành phần giao diện Compose dùng chung.
- [x] Tạo bố cục điều hướng thích ứng, thanh điều hướng nổi và giao diện quyền micro.
- [x] Cập nhật cấu hình build và tài nguyên phục vụ giao diện.

## 2. CHI TIẾT CÔNG VIỆC ĐÃ LÀM

### 2.1. Design System

- `app/src/main/java/com/example/damthoai/ui/theme/Type.kt`: khai báo font Be Vietnam Pro và các kiểu chữ `DamType`.
- `Color.kt`, `Shape.kt`, `CssGradient.kt`: định nghĩa bảng màu, hình dạng dùng chung và hàm tạo gradient.
- `ui/components/DamThoaiBackground.kt`: tạo nền với các biến thể `Default`, `Emergency`, `Pocket`.
- Tài nguyên font Be Vietnam Pro được đặt trong `app/src/main/res/font`; bộ icon giao diện dùng các tài nguyên `ic_*` trong `app/src/main/res/drawable`.

### 2.2. Thành phần và điều hướng

- `ui/components/CommonComponents.kt`: có nút chính/phụ, `GlassCard`, `TagLabel`, `KitSwitch` và modifier `dashedBorder`.
- `ui/navigation/AdaptiveAppLayout.kt`: triển khai bố cục thích ứng, thanh điều hướng nổi trên điện thoại và Navigation Rail trên màn hình rộng.

### 2.3. Giao diện quyền và cấu hình build

- `ui/permission/PermissionScreen.kt`: có `GrantPermissionScreen` và `MicrophonePermissionScreen`, gồm UI yêu cầu cấp quyền và cảnh báo quyền micro.
- `gradle/wrapper/gradle-wrapper.properties`: cấu hình Gradle 8.4.
- `app/build.gradle.kts`: cấu hình Java/Kotlin target 17 và khai báo Material Components 1.11.0.
- Thư mục tài nguyên có `res/mipmap-xxhdpi`.

## 3. KẾT QUẢ / BÀN GIAO

- Các thành phần nền tảng giao diện và theme đã có mã nguồn để các màn hình Compose dùng chung.
- Có giao diện thanh điều hướng nổi và các màn hình quyền được nêu trên.
- Cấu hình dự án ghi nhận Gradle 8.4, Java/Kotlin target 17 và Material Components 1.11.0. Kết quả build thực tế chưa kiểm chứng.

## 4. LƯU Ý VÀ CÔNG VIỆC TUẦN SAU

- Trong workspace hiện có `app/build (1).gradle.kts`; chưa kiểm chứng nguồn gốc và việc file này có liên quan đến phần việc Tuần 1 hay không.
- Công việc giao diện tiếp theo có trong commit Tuần 2: onboarding, vào kênh, danh sách kênh và các màn tạo/khôi phục sự kiện.
- Ảnh chụp màn hình và phần trăm hoàn thành tổng thể chưa có dữ liệu xác nhận.
