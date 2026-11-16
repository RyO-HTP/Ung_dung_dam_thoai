package com.example.damthoai.network

import android.util.Log
import com.example.damthoai.network.model.BaseSignalingMessage
import com.example.damthoai.network.model.IceCandidatePayload
import com.example.damthoai.network.model.IceCandidateSignalingMessage
import com.example.damthoai.network.model.IceCandidatesRemovedMessage
import com.example.damthoai.network.model.SdpSignalingMessage
import com.example.damthoai.network.model.SignalingType
import com.example.damthoai.network.model.SimpleSignalingMessage
import com.example.damthoai.network.model.WebRtcConfigMessage
import com.google.gson.Gson
import kotlinx.coroutines.flow.SharedFlow

/**
 * Đường truyền tín hiệu (Signaling) trừu tượng.
 *
 * Có 2 cài đặt:
 *  - [SignalingClient]: qua WebSocket tới máy chủ trung tâm (Internet hoặc máy chủ LAN tìm qua mDNS).
 *  - [LocalLanSignaling]: Tuần 5 - Local LAN Fallback, các điện thoại tự tìm nhau qua mDNS và
 *    trao đổi SDP/ICE trực tiếp khi hội trường mất Internet.
 *
 * DamThoaiCallManager chỉ làm việc với interface này nên có thể đổi đường truyền giữa cuộc gọi.
 */
interface SignalingChannel {
    val events: SharedFlow<SignalingEvent>
    fun sendOffer(targetId: String, sdp: String): Boolean
    fun sendAnswer(targetId: String, sdp: String): Boolean
    fun sendIceCandidate(targetId: String, sdpMid: String?, sdpMLineIndex: Int, candidate: String): Boolean
    fun sendIceCandidatesRemoved(targetId: String, candidates: List<IceCandidatePayload>): Boolean
    fun sendHangup(targetId: String): Boolean
}

/**
 * Bộ giải mã gói tin Signaling dùng chung cho WebSocket và LAN.
 */
object SignalingMessageParser {
    private const val TAG = "SignalingParser"

    fun parse(gson: Gson, jsonStr: String): SignalingEvent? = try {
        val baseMsg = gson.fromJson(jsonStr, BaseSignalingMessage::class.java)
        when (baseMsg?.type) {
            SignalingType.WEBRTC_CONFIG -> {
                val configMsg = gson.fromJson(jsonStr, WebRtcConfigMessage::class.java)
                val iceServers = (configMsg.config?.iceServers ?: emptyList())
                    .mapNotNull { it.toPeerConnectionIceServer() }
                SignalingEvent.ConfigReceived(iceServers)
            }
            SignalingType.OFFER -> {
                val m = gson.fromJson(jsonStr, SdpSignalingMessage::class.java)
                SignalingEvent.OfferReceived(m.senderId, m.sdp)
            }
            SignalingType.ANSWER -> {
                val m = gson.fromJson(jsonStr, SdpSignalingMessage::class.java)
                SignalingEvent.AnswerReceived(m.senderId, m.sdp)
            }
            SignalingType.ICE_CANDIDATE -> {
                val m = gson.fromJson(jsonStr, IceCandidateSignalingMessage::class.java)
                SignalingEvent.IceCandidateReceived(m.senderId, m.sdpMid, m.sdpMLineIndex, m.candidate)
            }
            SignalingType.ICE_CANDIDATES_REMOVED -> {
                val m = gson.fromJson(jsonStr, IceCandidatesRemovedMessage::class.java)
                SignalingEvent.IceCandidatesRemoved(m.senderId, m.candidates)
            }
            SignalingType.HANGUP -> {
                val m = gson.fromJson(jsonStr, SimpleSignalingMessage::class.java)
                SignalingEvent.HangupReceived(m.senderId)
            }
            else -> {
                Log.w(TAG, "Loại gói tin không xác định: ${baseMsg?.type}")
                null
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Lỗi giải mã gói tin signaling: ${e.message}", e)
        null
    }

    fun offer(gson: Gson, senderId: String?, targetId: String, sdp: String): String =
        gson.toJson(SdpSignalingMessage(SignalingType.OFFER, targetId, senderId, sdp))

    fun answer(gson: Gson, senderId: String?, targetId: String, sdp: String): String =
        gson.toJson(SdpSignalingMessage(SignalingType.ANSWER, targetId, senderId, sdp))

    fun iceCandidate(
        gson: Gson,
        senderId: String?,
        targetId: String,
        sdpMid: String?,
        sdpMLineIndex: Int,
        candidate: String
    ): String = gson.toJson(
        IceCandidateSignalingMessage(
            targetId = targetId,
            senderId = senderId,
            sdpMid = sdpMid,
            sdpMLineIndex = sdpMLineIndex,
            candidate = candidate
        )
    )

    fun iceCandidatesRemoved(
        gson: Gson,
        senderId: String?,
        targetId: String,
        candidates: List<IceCandidatePayload>
    ): String = gson.toJson(IceCandidatesRemovedMessage(targetId = targetId, senderId = senderId, candidates = candidates))

    fun hangup(gson: Gson, senderId: String?, targetId: String): String =
        gson.toJson(SimpleSignalingMessage(type = SignalingType.HANGUP, targetId = targetId, senderId = senderId))
}
