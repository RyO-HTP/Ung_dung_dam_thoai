import SwiftUI
import Shared

/// Điểm vào app iOS. Toàn bộ giao diện là Compose Multiplatform (module :shared),
/// Swift chỉ cung cấp các phần native qua NativeBridge (WebRTC, quét QR, Bonjour, PushToTalk).
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeRootView()
                .ignoresSafeArea(.all)
                // Mã QR quét bằng Camera của máy: vào đúng kênh và vai trò trong QR
                .onOpenURL { url in
                    guard url.scheme == "damthoai", url.host == "join" else { return }
                    QrJoin.shared.handleExternalLink(url: url.absoluteString)
                }
        }
    }
}

struct ComposeRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(bridge: NativeBridge.shared)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
