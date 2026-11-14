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
    fun hasPeerConnection(): Boolean
    fun createPeerConnection(listener: IosPeerListener)
    fun createOffer(callback: SdpCallback)
    fun createAnswer(callback: SdpCallback)
    fun setRemoteDescription(isOffer: Boolean, sdp: String, callback: CompletionCallback)
    fun addIceCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String)
    fun restartIce(callback: SdpCallback)
    fun setMicrophoneEnabled(enabled: Boolean)
    fun setOutputMuted(muted: Boolean)
    fun setRemoteVolume(volume: Float)
    fun close()
}

/** Chuyển [IosRtcBackend] (Swift) thành [RtcEngine] dùng chung. */
class IosRtcEngine(private val backend: IosRtcBackend) : RtcEngine {

    private fun sdpCallback(onSuccess: (String) -> Unit, onError: (String) -> Unit) = object : SdpCallback {
        override fun onSdp(sdp: String) = onSuccess(sdp)
        override fun onError(message: String) = onError(message)
    }

    override fun updateIceServers(servers: List<IceServerSpec>) = backend.updateIceServers(servers)

    override fun hasPeerConnection(): Boolean = backend.hasPeerConnection()

    override fun createPeerConnection(listener: RtcPeerListener) {
        backend.createPeerConnection(object : IosPeerListener {
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

    override fun createOffer(onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit) =
        backend.createOffer(sdpCallback(onSuccess, onError))

    override fun createAnswer(onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit) =
        backend.createAnswer(sdpCallback(onSuccess, onError))

    override fun setRemoteDescription(type: SdpType, sdp: String, onSuccess: () -> Unit, onError: (String) -> Unit) =
        backend.setRemoteDescription(type == SdpType.OFFER, sdp, object : CompletionCallback {
            override fun onDone() = onSuccess()
            override fun onError(message: String) = onError(message)
        })

    override fun addIceCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String) =
        backend.addIceCandidate(sdpMid, sdpMLineIndex, candidate)

    override fun restartIce(onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit) =
        backend.restartIce(sdpCallback(onSuccess, onError))

    override fun setMicrophoneEnabled(enabled: Boolean) = backend.setMicrophoneEnabled(enabled)
    override fun setOutputMuted(muted: Boolean) = backend.setOutputMuted(muted)
    override fun setRemoteVolume(volume: Float) = backend.setRemoteVolume(volume)
    override fun close() = backend.close()
}
