package com.example.damthoai.ios

import com.example.damthoai.platform.IceServerSpec
import com.example.damthoai.platform.RtcEngine
import com.example.damthoai.platform.RtcIceState
import com.example.damthoai.platform.RtcPeerListener
import com.example.damthoai.platform.SdpType

/*
 * Hợp đồng WebRTC mà Swift cài đặt (iosApp/iosApp/WebRTCEngine.swift).
 * Chỉ dùng interface callback + String/Int/Bool để chữ ký Swift rõ ràng
 * (tránh kiểu hàm Kotlin và enum Kotlin khi xuất sang Objective-C/Swift).
 * Mọi thao tác theo từng thành viên khóa bằng peerId (mesh: mỗi thành viên một PeerConnection).
 */

interface SdpCallback {
    fun onSdp(sdp: String)
    fun onError(message: String)
}

interface CompletionCallback {
    fun onDone()
    fun onError(message: String)
}

interface IosPeerListener {
    fun onIceCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String)

    /** "new", "checking", "connected", "completed", "disconnected", "failed", "closed". */
    fun onIceConnectionState(state: String)
}

interface IosRtcBackend {
    fun updateIceServers(servers: List<IceServerSpec>)
    fun hasPeerConnection(peerId: String): Boolean
    fun createPeerConnection(peerId: String, listener: IosPeerListener)
    fun createOffer(peerId: String, callback: SdpCallback)
    fun createAnswer(peerId: String, callback: SdpCallback)
    fun setRemoteDescription(peerId: String, isOffer: Boolean, sdp: String, callback: CompletionCallback)
    fun addIceCandidate(peerId: String, sdpMid: String?, sdpMLineIndex: Int, candidate: String)
    fun restartIce(peerId: String, callback: SdpCallback)
    fun closePeer(peerId: String)
    fun setMicrophoneEnabled(enabled: Boolean)
    fun setPeerSending(peerId: String, enabled: Boolean)
    fun setOutputMuted(muted: Boolean)
    fun setPeerVolume(peerId: String, volume: Float)
    fun close()
}

/** Chuyển [IosRtcBackend] (Swift) thành [RtcEngine] dùng chung. */
class IosRtcEngine(private val backend: IosRtcBackend) : RtcEngine {

    private fun sdpCallback(onSuccess: (String) -> Unit, onError: (String) -> Unit) = object : SdpCallback {
        override fun onSdp(sdp: String) = onSuccess(sdp)
        override fun onError(message: String) = onError(message)
    }

    override fun updateIceServers(servers: List<IceServerSpec>) = backend.updateIceServers(servers)

    override fun hasPeerConnection(peerId: String): Boolean = backend.hasPeerConnection(peerId)

    override fun createPeerConnection(peerId: String, listener: RtcPeerListener) {
        backend.createPeerConnection(peerId, object : IosPeerListener {
            override fun onIceCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String) =
                listener.onIceCandidate(sdpMid, sdpMLineIndex, candidate)

            override fun onIceConnectionState(state: String) =
                listener.onIceConnectionChange(
                    when (state.lowercase()) {
                        "new" -> RtcIceState.NEW
                        "checking" -> RtcIceState.CHECKING
                        "connected" -> RtcIceState.CONNECTED
                        "completed" -> RtcIceState.COMPLETED
                        "disconnected" -> RtcIceState.DISCONNECTED
                        "failed" -> RtcIceState.FAILED
                        else -> RtcIceState.CLOSED
                    }
                )
        })
    }

    override fun createOffer(peerId: String, onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit) =
        backend.createOffer(peerId, sdpCallback(onSuccess, onError))

    override fun createAnswer(peerId: String, onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit) =
        backend.createAnswer(peerId, sdpCallback(onSuccess, onError))

    override fun setRemoteDescription(
        peerId: String,
        type: SdpType,
        sdp: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) = backend.setRemoteDescription(peerId, type == SdpType.OFFER, sdp, object : CompletionCallback {
        override fun onDone() = onSuccess()
        override fun onError(message: String) = onError(message)
    })

    override fun addIceCandidate(peerId: String, sdpMid: String?, sdpMLineIndex: Int, candidate: String) =
        backend.addIceCandidate(peerId, sdpMid, sdpMLineIndex, candidate)

    override fun restartIce(peerId: String, onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit) =
        backend.restartIce(peerId, sdpCallback(onSuccess, onError))

    override fun closePeer(peerId: String) = backend.closePeer(peerId)
    override fun setMicrophoneEnabled(enabled: Boolean) = backend.setMicrophoneEnabled(enabled)
    override fun setPeerSending(peerId: String, enabled: Boolean) = backend.setPeerSending(peerId, enabled)
    override fun setOutputMuted(muted: Boolean) = backend.setOutputMuted(muted)
    override fun setPeerVolume(peerId: String, volume: Float) = backend.setPeerVolume(peerId, volume)
    override fun close() = backend.close()
}
