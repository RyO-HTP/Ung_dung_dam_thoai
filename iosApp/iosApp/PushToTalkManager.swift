import AVFoundation
import Foundation
import PushToTalk
import Shared

/// Kênh PushToTalk của hệ thống (iOS 16+): hiện nút nói trên màn hình khoá / Dynamic Island,
/// cho phép nói khi app đang chạy nền. Bấm nút hệ thống -> PttController (Kotlin) như nút PTT trong app.
///
/// Cần capability "Push to Talk" (entitlement com.apple.developer.push-to-talk) trên tài khoản
/// Apple Developer trả phí. Thiếu entitlement / chạy máy ảo: tạo kênh thất bại, app vẫn chạy bình thường.
final class PushToTalkManager: NSObject, PTChannelManagerDelegate, PTChannelRestorationDelegate {
    private static let channelUUIDKey = "damthoai_ptt_channel_uuid"

    private var manager: PTChannelManager?
    private var joined = false
    private var channelName = "Đàm Thoại"

    /// UUID kênh cố định cho thiết bị (hệ thống dùng để khôi phục kênh sau khi app khởi động lại).
    private let channelUUID: UUID = {
        let defaults = UserDefaults.standard
        if let stored = defaults.string(forKey: PushToTalkManager.channelUUIDKey),
           let uuid = UUID(uuidString: stored) {
            return uuid
        }
        let uuid = UUID()
        defaults.set(uuid.uuidString, forKey: PushToTalkManager.channelUUIDKey)
        return uuid
    }()

    func join(channelName: String) {
        self.channelName = channelName.isEmpty ? "Đàm Thoại" : channelName
        Task { @MainActor in
            guard let manager = await self.ensureManager() else { return }
            let descriptor = PTChannelDescriptor(name: self.channelName, image: nil)
            if self.joined {
                try? await manager.setChannelDescriptor(descriptor, channelUUID: self.channelUUID)
            } else {
                manager.requestJoinChannel(channelUUID: self.channelUUID, descriptor: descriptor)
            }
        }
    }

    func leave() {
        guard joined, let manager else { return }
        manager.leaveChannel(channelUUID: channelUUID)
    }

    @MainActor
    private func ensureManager() async -> PTChannelManager? {
        if let manager { return manager }
        do {
            let created = try await PTChannelManager.channelManager(delegate: self, restorationDelegate: self)
            manager = created
            return created
        } catch {
            NSLog("[PushToTalk] Không tạo được kênh hệ thống: \(error.localizedDescription)")
            return nil
        }
    }

    // MARK: PTChannelManagerDelegate

    func channelManager(_ channelManager: PTChannelManager, didJoinChannel channelUUID: UUID, reason: PTChannelJoinReason) {
        joined = true
    }

    func channelManager(_ channelManager: PTChannelManager, didLeaveChannel channelUUID: UUID, reason: PTChannelLeaveReason) {
        joined = false
        DispatchQueue.main.async { IosEntryPointsKt.onSystemForceReleasePtt() }
    }

    func channelManager(_ channelManager: PTChannelManager, channelUUID: UUID, didBeginTransmittingFrom source: PTChannelTransmitRequestSource) {
        DispatchQueue.main.async { IosEntryPointsKt.onSystemPushToTalkBegin() }
    }

    func channelManager(_ channelManager: PTChannelManager, channelUUID: UUID, didEndTransmittingFrom source: PTChannelTransmitRequestSource) {
        DispatchQueue.main.async { IosEntryPointsKt.onSystemPushToTalkEnd() }
    }

    func channelManager(_ channelManager: PTChannelManager, receivedEphemeralPushToken pushToken: Data) {
        // Chưa dùng push APNs: âm thanh đi qua WebRTC P2P đang mở sẵn
    }

    func incomingPushResult(channelManager: PTChannelManager, channelUUID: UUID, pushPayload: [String: Any]) -> PTPushResult {
        // Server chưa gửi push PTT; nếu có push lạ thì rời kênh để hệ thống không giữ phiên vô ích
        return .leaveChannel
    }

    func channelManager(_ channelManager: PTChannelManager, didActivate audioSession: AVAudioSession) {}

    func channelManager(_ channelManager: PTChannelManager, didDeactivate audioSession: AVAudioSession) {}

    // MARK: PTChannelRestorationDelegate

    func channelDescriptor(restoredChannelUUID channelUUID: UUID) -> PTChannelDescriptor {
        PTChannelDescriptor(name: channelName, image: nil)
    }
}
