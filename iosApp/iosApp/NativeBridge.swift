import Foundation
import MediaPlayer
import UIKit
import Shared

/// Cài đặt IosNativeBridge (khai báo trong shared/src/iosMain/.../ios/IosNativeBridge.kt).
final class NativeBridge: NSObject, IosNativeBridge {
    static let shared = NativeBridge()

    let rtcBackend: IosRtcBackend = WebRTCEngine()

    private let bonjour = BonjourBrowser()
    private let headsetRemote = HeadsetRemote()
    private let pushToTalk = PushToTalkManager()

    func startBonjour(listener: ServerFoundListener) {
        bonjour.start(listener: listener)
    }

    func stopBonjour() {
        bonjour.stop()
    }

    func createQrScannerView(listener: QrCodeListener) -> UIView {
        QrScannerView(listener: listener)
    }

    func releaseQrScannerView(view: UIView) {
        (view as? QrScannerView)?.stop()
    }

    func pushToTalkJoin(channelName: String) {
        pushToTalk.join(channelName: channelName)
    }

    func pushToTalkLeave() {
        pushToTalk.leave()
    }

    func setHeadsetRemoteEnabled(enabled: Bool) {
        headsetRemote.setEnabled(enabled)
    }
}

// MARK: - Bonjour (_bodam._tcp, tương thích mdns_broadcaster.py)

final class BonjourBrowser: NSObject, NetServiceBrowserDelegate, NetServiceDelegate {
    private static let serviceType = "_bodam._tcp."

    private var browser: NetServiceBrowser?
    private var resolving: [NetService] = []
    private var listener: ServerFoundListener?

    func start(listener: ServerFoundListener) {
        stop()
        self.listener = listener
        let browser = NetServiceBrowser()
        browser.delegate = self
        browser.searchForServices(ofType: Self.serviceType, inDomain: "local.")
        self.browser = browser
    }

    func stop() {
        browser?.stop()
        browser = nil
        resolving.forEach { $0.stop() }
        resolving.removeAll()
        listener = nil
    }

    func netServiceBrowser(_ browser: NetServiceBrowser, didFind service: NetService, moreComing: Bool) {
        resolving.append(service)
        service.delegate = self
        service.resolve(withTimeout: 5)
    }

    func netServiceDidResolveAddress(_ sender: NetService) {
        guard let host = Self.ipv4Address(of: sender) else { return }
        listener?.onServerFound(name: sender.name, host: host, port: Int32(sender.port))
    }

    private static func ipv4Address(of service: NetService) -> String? {
        for data in service.addresses ?? [] {
            let ip: String? = data.withUnsafeBytes { raw in
                guard let base = raw.baseAddress,
                      raw.count >= MemoryLayout<sockaddr_in>.size,
                      base.assumingMemoryBound(to: sockaddr.self).pointee.sa_family == sa_family_t(AF_INET)
                else { return nil }
                var addr = base.assumingMemoryBound(to: sockaddr_in.self).pointee.sin_addr
                var buffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
                guard inet_ntop(AF_INET, &addr, &buffer, socklen_t(INET_ADDRSTRLEN)) != nil else { return nil }
                return String(cString: buffer)
            }
            if let ip { return ip }
        }
        return nil
    }
}

// MARK: - Nút Play/Pause tai nghe (có dây / Bluetooth)

final class HeadsetRemote {
    private var target: Any?

    func setEnabled(_ enabled: Bool) {
        let command = MPRemoteCommandCenter.shared().togglePlayPauseCommand
        if enabled, target == nil {
            command.isEnabled = true
            target = command.addTarget { _ in
                IosEntryPointsKt.onHeadsetPlayPause()
                return .success
            }
        } else if !enabled, let current = target {
            command.removeTarget(current)
            target = nil
        }
    }
}
