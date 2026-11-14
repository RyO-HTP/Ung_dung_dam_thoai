# Đàm Thoại — bản iOS

Giao diện và logic dùng chung với Android nằm trong module `shared/` (Kotlin Multiplatform + Compose Multiplatform).
Thư mục này chỉ chứa phần Swift bắt buộc phải native:

| File | Việc |
|---|---|
| `iosApp/iOSApp.swift` | Điểm vào app, nhúng giao diện Compose (`MainViewControllerKt.MainViewController`) |
| `iosApp/NativeBridge.swift` | Cài đặt `IosNativeBridge` (Kotlin): Bonjour tìm máy chủ LAN, nút tai nghe |
| `iosApp/WebRTCEngine.swift` | Âm thanh P2P WebRTC (gói SPM `stasel/WebRTC`) — tương đương `WebRTCClient.kt` |
| `iosApp/QrScannerView.swift` | Camera quét QR (AVFoundation) |
| `iosApp/PushToTalkManager.swift` | Nút nói của hệ thống trên màn hình khoá (framework PushToTalk, iOS 16+) |
| `project.yml` | Cấu hình project Xcode (XcodeGen) |

## Không có Mac: build trên GitHub Actions

Mỗi lần push lên nhánh `ios-kmp` / `main`, workflow `.github/workflows/ios.yml` chạy trên máy macOS:

1. Chạy test Kotlin dùng chung trên iOS Simulator.
2. Build app cho iOS Simulator (không ký) và đính kèm `DamThoai.app` ở mục **Artifacts** của lần chạy.

Trên Windows vẫn kiểm tra được phần Kotlin của iOS (không cần Mac):

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64
```

## Không có Mac: cài lên iPhone thật bằng xtool (Windows/WSL)

[xtool](https://github.com/xtool-org/xtool) ký file `.ipa` bằng Apple ID của bạn (miễn phí cũng được) và cài qua USB,
không cần Mac. App vẫn do GitHub Actions build (bước nối framework Kotlin cho iOS bắt buộc chạy trên macOS),
xtool chỉ lo **ký + cài**.

### Chuẩn bị một lần

1. **WSL (Ubuntu)** — PowerShell (Admin): `wsl --install`, khởi động lại máy.
2. **Cho WSL thấy iPhone qua USB** — PowerShell (Admin):
   ```powershell
   winget install usbipd
   usbipd list                       # tìm dòng "Apple Mobile Device", nhớ BUSID (vd 2-3)
   usbipd bind --busid 2-3
   usbipd attach --wsl --busid 2-3   # làm lại mỗi lần cắm lại iPhone
   ```
   Gặp lỗi `AFCClient.Error.muxError`: làm theo
   [hướng dẫn này](https://github.com/xtool-org/xtool/issues/19#issuecomment-2898986718) (dùng kết nối của iTunes thay cho usbipd).
3. **Trong WSL** cài công cụ:
   ```bash
   sudo apt update && sudo apt install -y usbmuxd libimobiledevice-utils gh
   # Swift cho Linux: https://swift.org/install/linux (bản đúng như trang cài đặt xtool yêu cầu)
   curl -fL "https://github.com/xtool-org/xtool/releases/latest/download/xtool-$(uname -m).AppImage" -o xtool
   chmod +x xtool && sudo mv xtool /usr/local/bin/
   ```
4. **Tải Xcode.xip** (đăng nhập Apple ID) từ <https://developer.apple.com/download/all/?q=Xcode>
   (đúng phiên bản trang cài đặt xtool ghi), rồi chạy `xtool setup`:
   - chọn **1: Password** nếu dùng Apple ID miễn phí (nên dùng Apple ID phụ — xtool đăng nhập qua API không chính thức),
   - chọn **0: API Key** nếu có tài khoản Apple Developer trả phí.
5. **GitHub CLI**: `gh auth login` (để script tự tải bản build mới nhất).
6. **iPhone**: bật *Cài đặt → Quyền riêng tư & Bảo mật → Chế độ nhà phát triển*.

### Mỗi lần muốn cài bản mới

```bash
cd /mnt/e/Ung_dung_dam_thoai          # thư mục dự án nhìn từ WSL
scripts/ios/install-iphone.sh         # tải .ipa mới nhất của nhánh ios-kmp, ký và cài
# hoặc: scripts/ios/install-iphone.sh ~/Downloads/DamThoai-unsigned.ipa
```

Ghi chú:
- xtool thêm tiền tố vào bundle ID (vd `XTL-1234.com.example.damthoai.ios`) — bình thường, để không trùng với người khác.
- Bản `.ipa` của CI đã bỏ entitlement PushToTalk (Apple ID miễn phí không có), nên nút nói trên màn hình khoá không có;
  các tính năng khác dùng bình thường.
- Apple ID miễn phí: app hết hạn sau 7 ngày, tối đa 3 app tự cài — chạy lại script để cài lại.

## Có Mac: chạy trên máy ảo / iPhone thật

Cần: macOS + Xcode 16 trở lên, JDK 17, [XcodeGen](https://github.com/yonaskolb/XcodeGen).

```bash
brew install xcodegen
cd iosApp
xcodegen generate
open iosApp.xcodeproj
```

- Máy ảo: chọn một iPhone Simulator rồi bấm Run.
- iPhone thật: Xcode → target `iosApp` → *Signing & Capabilities* → chọn Team (Apple ID).
  - Apple ID miễn phí: cài được nhưng app hết hạn sau 7 ngày, và **phải xoá dòng
    `com.apple.developer.push-to-talk` trong `project.yml`** (tài khoản miễn phí không có quyền này).
  - Tài khoản Apple Developer (99 USD/năm): giữ nguyên, phân phối qua TestFlight được.

## Khác biệt so với Android

- iOS không cho dùng **phím âm lượng** làm nút PTT → dùng nút trên màn hình, nút Play/Pause tai nghe
  (bấm để bật / bấm lại để tắt mic), hoặc nút nói PushToTalk của hệ thống.
- Địa chỉ máy chủ đám mây mặc định nằm ở `IosPlatform.CLOUD_SIGNALING_URL`
  (Android lấy từ `gradle.properties`).
