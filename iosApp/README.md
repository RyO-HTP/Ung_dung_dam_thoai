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
