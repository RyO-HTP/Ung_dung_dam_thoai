import Foundation
import WebRTC
import Shared

/// Âm thanh P2P WebRTC cho PTT trên iOS — tương đương WebRTCClient.kt bên Android.
/// Cài đặt IosRtcBackend (shared/src/iosMain/.../ios/IosRtcEngine.kt).
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

    private let lock = NSLock()
    private var peerConnection: RTCPeerConnection?
    private var peerListener: IosPeerListener?
    private var localAudioTrack: RTCAudioTrack?
    private var remoteAudioTracks: [RTCAudioTrack] = []
    private var iceServers: [RTCIceServer] = [RTCIceServer(urlStrings: ["stun:stun.l.google.com:19302"])]

    private var micEnabled = false
    private var outputMuted = false
    private var remoteVolume: Double = 1.0

    // ICE candidate của đối tác có thể đến trước khi đặt xong remote description: xếp hàng chờ
    private var pendingRemoteCandidates: [RTCIceCandidate] = []
    private var remoteDescriptionSet = false

    private func locked<T>(_ body: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body()
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

    func hasPeerConnection() -> Bool {
        locked { peerConnection != nil }
    }

    func createPeerConnection(listener: IosPeerListener) {
        close()
        let config = RTCConfiguration()
        config.iceServers = locked { iceServers }
        config.sdpSemantics = .unifiedPlan
        // Tiếp tục dò đường truyền khi đổi Wi-Fi <-> 4G (Seamless Handover)
        config.continualGatheringPolicy = .gatherContinually

        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)
        guard let pc = Self.factory.peerConnection(with: config, constraints: constraints, delegate: self) else {
            NSLog("[WebRTCEngine] Không tạo được PeerConnection")
            return
        }

        let audioConstraints = RTCMediaConstraints(
            mandatoryConstraints: nil,
            optionalConstraints: [
                "googEchoCancellation": "true",
                "googNoiseSuppression": "true",
                "googAutoGainControl": "true"
            ]
        )
        let source = Self.factory.audioSource(with: audioConstraints)
        let track = Self.factory.audioTrack(with: source, trackId: Self.localAudioTrackId)
        locked {
            track.isEnabled = micEnabled // Mặc định tắt mic khi chưa giữ phím PTT
            peerConnection = pc
            peerListener = listener
            localAudioTrack = track
        }
        pc.add(track, streamIds: [Self.localStreamId])
    }

    func createOffer(callback: SdpCallback) {
        makeOffer(iceRestart: false, callback: callback)
    }

    func restartIce(callback: SdpCallback) {
        makeOffer(iceRestart: true, callback: callback)
    }

    private func makeOffer(iceRestart: Bool, callback: SdpCallback) {
        guard let pc = locked({ peerConnection }) else {
            return deliverError(callback, "Chưa có PeerConnection")
        }
        var mandatory = ["OfferToReceiveAudio": "true"]
        if iceRestart { mandatory["IceRestart"] = "true" }
        let constraints = RTCMediaConstraints(mandatoryConstraints: mandatory, optionalConstraints: nil)
        pc.offer(for: constraints) { [weak self] sdp, error in
            self?.applyLocal(pc: pc, sdp: sdp, error: error, fallback: "Lỗi tạo Offer", callback: callback)
        }
    }

    func createAnswer(callback: SdpCallback) {
        guard let pc = locked({ peerConnection }) else {
            return deliverError(callback, "Chưa có PeerConnection")
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

    func setRemoteDescription(isOffer: Bool, sdp: String, callback: CompletionCallback) {
        guard let pc = locked({ peerConnection }) else {
            DispatchQueue.main.async { callback.onError(message: "Chưa có PeerConnection") }
            return
        }
        let description = RTCSessionDescription(type: isOffer ? .offer : .answer, sdp: sdp)
        pc.setRemoteDescription(description) { [weak self] error in
            guard let self else { return }
            if let error {
                DispatchQueue.main.async { callback.onError(message: error.localizedDescription) }
                return
            }
            let queued: [RTCIceCandidate] = self.locked {
                guard self.peerConnection === pc else { return [] }
                self.remoteDescriptionSet = true
                let items = self.pendingRemoteCandidates
                self.pendingRemoteCandidates.removeAll()
                return items
            }
            queued.forEach { pc.add($0) { _ in } }
            DispatchQueue.main.async { callback.onDone() }
        }
    }

    func addIceCandidate(sdpMid: String?, sdpMLineIndex: Int32, candidate: String) {
        let ice = RTCIceCandidate(sdp: candidate, sdpMLineIndex: sdpMLineIndex, sdpMid: sdpMid)
        let pcReady: RTCPeerConnection? = locked {
            guard let pc = peerConnection, remoteDescriptionSet else {
                pendingRemoteCandidates.append(ice)
                return nil
            }
            return pc
        }
        pcReady?.add(ice) { error in
            if let error { NSLog("[WebRTCEngine] Lỗi thêm ICE: \(error.localizedDescription)") }
        }
    }

    func setMicrophoneEnabled(enabled: Bool) {
        locked {
            micEnabled = enabled
            localAudioTrack?.isEnabled = enabled
        }
    }

    func setOutputMuted(muted: Bool) {
        locked {
            outputMuted = muted
            remoteAudioTracks.forEach { $0.isEnabled = !muted }
        }
    }

    func setRemoteVolume(volume: Float) {
        locked {
            remoteVolume = Double(min(max(volume, 0), 1))
            remoteAudioTracks.forEach(applyVolume)
        }
    }

    /// RTCAudioSource.volume nhận 0...10 (1 = mức gốc)
    private func applyVolume(_ track: RTCAudioTrack) {
        track.source.volume = remoteVolume
    }

    func close() {
        let pc: RTCPeerConnection? = locked {
            let current = peerConnection
            peerConnection = nil
            peerListener = nil
            localAudioTrack = nil
            remoteAudioTracks.removeAll()
            pendingRemoteCandidates.removeAll()
            remoteDescriptionSet = false
            return current
        }
        pc?.close()
    }

    private func deliverError(_ callback: SdpCallback, _ message: String) {
        DispatchQueue.main.async { callback.onError(message: message) }
    }

    private func registerRemoteTrack(_ track: RTCMediaStreamTrack?) {
        guard let audio = track as? RTCAudioTrack else { return }
        locked {
            if !remoteAudioTracks.contains(where: { $0 === audio }) {
                remoteAudioTracks.append(audio)
            }
            audio.isEnabled = !outputMuted
            applyVolume(audio)
        }
    }

    // MARK: RTCPeerConnectionDelegate

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {
        stream.audioTracks.forEach { registerRemoteTrack($0) }
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {
        locked {
            remoteAudioTracks.removeAll { track in stream.audioTracks.contains { $0 === track } }
        }
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd rtpReceiver: RTCRtpReceiver, streams mediaStreams: [RTCMediaStream]) {
        registerRemoteTrack(rtpReceiver.track)
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
        guard let listener = locked({ self.peerConnection === peerConnection ? peerListener : nil }) else { return }
        DispatchQueue.main.async { listener.onIceConnectionState(state: name) }
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        guard let listener = locked({ self.peerConnection === peerConnection ? peerListener : nil }) else { return }
        listener.onIceCandidate(sdpMid: candidate.sdpMid, sdpMLineIndex: candidate.sdpMLineIndex, candidate: candidate.sdp)
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {}
}
