import Foundation
import WebRTC
import Shared

/// Âm thanh P2P WebRTC cho PTT trên iOS — tương đương WebRTCClient.kt bên Android.
/// Cài đặt IosRtcBackend (shared/src/iosMain/.../ios/IosRtcEngine.kt).
/// Dạng MESH: mỗi thành viên khác trong kênh một RTCPeerConnection (khóa bằng peerId),
/// tất cả dùng chung một track micro.
final class WebRTCEngine: NSObject, IosRtcBackend, RTCPeerConnectionDelegate {
    private static let localAudioTrackId = "ARDAMSa0"
    private static let localStreamId = "ARDAMS"

    private static let factory: RTCPeerConnectionFactory = {
        RTCInitializeSSL()
        return RTCPeerConnectionFactory(
            encoderFactory: RTCDefaultVideoEncoderFactory(),
            decoderFactory: RTCDefaultVideoDecoderFactory()
        )
    }()

    /// Một kết nối tới một thành viên. ICE candidate có thể đến trước khi đặt xong
    /// remote description: xếp hàng chờ trong pendingCandidates.
    private final class Peer {
        let id: String
        let pc: RTCPeerConnection
        let listener: IosPeerListener
        let localTrack: RTCAudioTrack
        var remoteTracks: [RTCAudioTrack] = []
        var pendingCandidates: [RTCIceCandidate] = []
        var remoteDescriptionSet = false
        /// Có gửi tiếng mình tới người này không (chỉ người cùng kênh đang phát)
        var sending = false
        /// Âm lượng nghe người này (0...1), theo âm lượng kênh chung
        var volume: Double = 1.0

        init(id: String, pc: RTCPeerConnection, listener: IosPeerListener, localTrack: RTCAudioTrack) {
            self.id = id
            self.pc = pc
            self.listener = listener
            self.localTrack = localTrack
        }
    }

    private let lock = NSLock()
    private var peers: [String: Peer] = [:]
    // Nguồn thu micro DÙNG CHUNG; mỗi peer một track riêng từ nguồn này để bật/tắt gửi theo từng người
    private var audioSource: RTCAudioSource?
    private var trackCounter = 0
    private var iceServers: [RTCIceServer] = [RTCIceServer(urlStrings: ["stun:stun.l.google.com:19302"])]

    private var micEnabled = false
    private var outputMuted = false

    /// Gọi khi đang giữ lock.
    private func applySending(_ peer: Peer) {
        peer.localTrack.isEnabled = micEnabled && peer.sending
    }

    private func locked<T>(_ body: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }

    /// Tìm Peer sở hữu RTCPeerConnection (gọi khi đang giữ lock).
    private func peerFor(_ pc: RTCPeerConnection) -> Peer? {
        peers.values.first { $0.pc === pc }
    }

    private var allRemoteTracks: [RTCAudioTrack] {
        peers.values.flatMap { $0.remoteTracks }
    }

    // MARK: IosRtcBackend

    func updateIceServers(servers: [IceServerSpec]) {
        let mapped = servers.compactMap { spec -> RTCIceServer? in
            guard !spec.urls.isEmpty else { return nil }
            return RTCIceServer(urlStrings: spec.urls, username: spec.username, credential: spec.credential)
        }
        guard !mapped.isEmpty else { return }
        locked { iceServers = mapped }
    }

    func hasPeerConnection(peerId: String) -> Bool {
        locked { peers[peerId] != nil }
    }

    func createPeerConnection(peerId: String, listener: IosPeerListener) {
        // Chỉ đóng kết nối cũ của ĐÚNG peer này; các thành viên khác giữ nguyên
        locked({ peers.removeValue(forKey: peerId) })?.pc.close()

        let config = RTCConfiguration()
        config.iceServers = locked { iceServers }
        config.sdpSemantics = .unifiedPlan
        // Tiếp tục dò đường truyền khi đổi Wi-Fi <-> 4G (Seamless Handover)
        config.continualGatheringPolicy = .gatherContinually

        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        guard let pc = Self.factory.peerConnection(with: config, constraints: constraints, delegate: self) else {
            NSLog("[WebRTCEngine] Không tạo được PeerConnection tới \(peerId)")
            return
        }

        let track: RTCAudioTrack = locked {
            // Track micro riêng cho peer này (cùng nguồn thu), mặc định chưa gửi
            trackCounter += 1
            let track = Self.factory.audioTrack(with: ensureAudioSource(), trackId: "\(Self.localAudioTrackId)_\(trackCounter)")
            let peer = Peer(id: peerId, pc: pc, listener: listener, localTrack: track)
            peers[peerId] = peer
            applySending(peer)
            return track
        }
        pc.add(track, streamIds: [Self.localStreamId])
    }

    /// Tạo (một lần) nguồn thu micro dùng chung (gọi khi đang giữ lock).
    private func ensureAudioSource() -> RTCAudioSource {
        if let existing = audioSource { return existing }
        let audioConstraints = RTCMediaConstraints(
            mandatoryConstraints: nil,
            optionalConstraints: [
                "googEchoCancellation": "true",
                "googNoiseSuppression": "true",
                "googAutoGainControl": "true"
            ]
        )
        let source = Self.factory.audioSource(with: audioConstraints)
        audioSource = source
        return source
    }

    func createOffer(peerId: String, callback: SdpCallback) {
        makeOffer(peerId: peerId, iceRestart: false, callback: callback)
    }

    func restartIce(peerId: String, callback: SdpCallback) {
        makeOffer(peerId: peerId, iceRestart: true, callback: callback)
    }

    private func makeOffer(peerId: String, iceRestart: Bool, callback: SdpCallback) {
        guard let pc = locked({ peers[peerId]?.pc }) else {
            return deliverError(callback, "Chưa có PeerConnection tới \(peerId)")
        }
        var mandatory = ["OfferToReceiveAudio": "true"]
        if iceRestart { mandatory["IceRestart"] = "true" }
        let constraints = RTCMediaConstraints(mandatoryConstraints: mandatory, optionalConstraints: nil)
        pc.offer(for: constraints) { [weak self] sdp, error in
            self?.applyLocal(pc: pc, sdp: sdp, error: error, fallback: "Lỗi tạo Offer", callback: callback)
        }
    }

    func createAnswer(peerId: String, callback: SdpCallback) {
        guard let pc = locked({ peers[peerId]?.pc }) else {
            return deliverError(callback, "Chưa có PeerConnection tới \(peerId)")
        }
        let constraints = RTCMediaConstraints(mandatoryConstraints: ["OfferToReceiveAudio": "true"], optionalConstraints: nil)
        pc.answer(for: constraints) { [weak self] sdp, error in
            self?.applyLocal(pc: pc, sdp: sdp, error: error, fallback: "Lỗi tạo Answer", callback: callback)
        }
    }

    /// Chỉ gửi SDP đi sau khi đặt local description thành công.
    private func applyLocal(pc: RTCPeerConnection, sdp: RTCSessionDescription?, error: Error?, fallback: String, callback: SdpCallback) {
        guard let sdp else {
            return deliverError(callback, error?.localizedDescription ?? fallback)
        }
        pc.setLocalDescription(sdp) { setError in
            DispatchQueue.main.async {
                if let setError {
                    callback.onError(message: setError.localizedDescription)
                } else {
                    callback.onSdp(sdp: sdp.sdp)
                }
            }
        }
    }

    func setRemoteDescription(peerId: String, isOffer: Bool, sdp: String, callback: CompletionCallback) {
        guard let peer = locked({ peers[peerId] }) else {
            DispatchQueue.main.async { callback.onError(message: "Chưa có PeerConnection tới \(peerId)") }
            return
        }
        let pc = peer.pc
        let description = RTCSessionDescription(type: isOffer ? .offer : .answer, sdp: sdp)
        pc.setRemoteDescription(description) { [weak self] error in
            guard let self else { return }
            if let error {
                DispatchQueue.main.async { callback.onError(message: error.localizedDescription) }
                return
            }
            let queued: [RTCIceCandidate] = self.locked {
                guard self.peers[peerId] === peer else { return [] }
                peer.remoteDescriptionSet = true
                let items = peer.pendingCandidates
                peer.pendingCandidates.removeAll()
                return items
            }
            queued.forEach { pc.add($0) { _ in } }
            DispatchQueue.main.async { callback.onDone() }
        }
    }

    func addIceCandidate(peerId: String, sdpMid: String?, sdpMLineIndex: Int32, candidate: String) {
        let ice = RTCIceCandidate(sdp: candidate, sdpMLineIndex: sdpMLineIndex, sdpMid: sdpMid)
        let pcReady: RTCPeerConnection? = locked {
            guard let peer = peers[peerId] else { return nil }
            guard peer.remoteDescriptionSet else {
                peer.pendingCandidates.append(ice)
                return nil
            }
            return peer.pc
        }
        pcReady?.add(ice) { error in
            if let error { NSLog("[WebRTCEngine] Lỗi thêm ICE (\(peerId)): \(error.localizedDescription)") }
        }
    }

    func closePeer(peerId: String) {
        let pc: RTCPeerConnection? = locked {
            let removed = peers.removeValue(forKey: peerId)
            if peers.isEmpty { audioSource = nil } // Không còn ai: nhả micro
            return removed?.pc
        }
        pc?.close()
    }

    func setMicrophoneEnabled(enabled: Bool) {
        locked {
            micEnabled = enabled
            peers.values.forEach(applySending)
        }
    }

    func setPeerSending(peerId: String, enabled: Bool) {
        locked {
            guard let peer = peers[peerId] else { return }
            peer.sending = enabled
            applySending(peer)
        }
    }

    func setOutputMuted(muted: Bool) {
        locked {
            outputMuted = muted
            allRemoteTracks.forEach { $0.isEnabled = !muted }
        }
    }

    func setPeerVolume(peerId: String, volume: Float) {
        locked {
            guard let peer = peers[peerId] else { return }
            peer.volume = Double(min(max(volume, 0), 1))
            peer.remoteTracks.forEach { applyVolume($0, peer.volume) }
        }
    }

    /// RTCAudioSource.volume nhận 0...10 (1 = mức gốc)
    private func applyVolume(_ track: RTCAudioTrack, _ volume: Double) {
        track.source.volume = volume
    }

    func close() {
        let pcs: [RTCPeerConnection] = locked {
            let all = peers.values.map { $0.pc }
            peers.removeAll()
            audioSource = nil
            return all
        }
        pcs.forEach { $0.close() }
    }

    private func deliverError(_ callback: SdpCallback, _ message: String) {
        DispatchQueue.main.async { callback.onError(message: message) }
    }

    private func registerRemoteTrack(_ track: RTCMediaStreamTrack?, from pc: RTCPeerConnection) {
        guard let audio = track as? RTCAudioTrack else { return }
        locked {
            guard let peer = peerFor(pc) else { return }
            if !peer.remoteTracks.contains(where: { $0 === audio }) {
                peer.remoteTracks.append(audio)
            }
            audio.isEnabled = !outputMuted
            applyVolume(audio, peer.volume)
        }
    }

    // MARK: RTCPeerConnectionDelegate

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {
        stream.audioTracks.forEach { registerRemoteTrack($0, from: peerConnection) }
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {
        locked {
            guard let peer = peerFor(peerConnection) else { return }
            peer.remoteTracks.removeAll { track in stream.audioTracks.contains { $0 === track } }
        }
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd rtpReceiver: RTCRtpReceiver, streams mediaStreams: [RTCMediaStream]) {
        registerRemoteTrack(rtpReceiver.track, from: peerConnection)
    }

    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
        let name: String
        switch newState {
        case .new: name = "new"
        case .checking: name = "checking"
        case .connected: name = "connected"
        case .completed: name = "completed"
        case .disconnected: name = "disconnected"
        case .failed: name = "failed"
        default: name = "closed"
        }
        // PeerConnection đã bị thay/đóng thì không báo nữa
        guard let listener = locked({ peerFor(peerConnection)?.listener }) else { return }
        DispatchQueue.main.async { listener.onIceConnectionState(state: name) }
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        guard let listener = locked({ peerFor(peerConnection)?.listener }) else { return }
        listener.onIceCandidate(sdpMid: candidate.sdpMid, sdpMLineIndex: candidate.sdpMLineIndex, candidate: candidate.sdp)
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {}
}
