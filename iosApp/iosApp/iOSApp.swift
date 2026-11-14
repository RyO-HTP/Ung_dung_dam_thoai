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
        }
    }
}

struct ComposeRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(bridge: NativeBridge.shared)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
