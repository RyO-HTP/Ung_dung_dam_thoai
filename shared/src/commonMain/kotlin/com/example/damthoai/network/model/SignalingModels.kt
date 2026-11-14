package com.example.damthoai.network.model

import com.example.damthoai.platform.IceServerSpec
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Các loại gói tin Signaling chuẩn theo định nghĩa của Tuần 1 (Signaling Server)
 */
object SignalingType {
    const val REGISTER = "register"
    const val WEBRTC_CONFIG = "webrtc_config"
    const val OFFER = "offer"
    const val ANSWER = "answer"
    const val ICE_CANDIDATE = "ice_candidate"
    const val USER_LIST = "user_list"
    const val EVENT_INFO = "event_info"
}

/**
 * Gói tin cơ bản để xác định loại thông điệp (type)
 */
@Serializable
data class BaseSignalingMessage(
    @SerialName("type")
    val type: String
)

/**
 * Gói tin đăng ký (Handshake) gửi từ thiết bị lên Server
 * Ví dụ: { "type": "register", "user_id": "Tong_Dao_Dien" }
 */
@Serializable
data class RegisterMessage(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    @SerialName("type")
    val type: String = SignalingType.REGISTER,
    @SerialName("user_id")
    val userId: String,
    /** Mã PIN sự kiện: server gom các thiết bị cùng PIN vào một nhóm đàm thoại. */
    @SerialName("event_pin")
    val eventPin: String? = null,
    /** Tên hiển thị trong danh sách thành viên. */
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("role_title")
    val roleTitle: String? = null,
    /** true nếu thiết bị là ban tổ chức (người tạo sự kiện). */
    @SerialName("is_organizer")
    val isOrganizer: Boolean = false,
    /** Tên sự kiện do ban tổ chức đặt (chỉ ban tổ chức gửi). */
    @SerialName("event_name")
    val eventName: String? = null
)

/**
 * Cấu hình từng STUN / TURN server từ Metered hoặc Google
 */
@Serializable
data class IceServerConfig(
    @SerialName("urls")
    val urls: JsonElement? = null, // Có thể là String ("stun:...") hoặc List<String>
    @SerialName("username")
    val username: String? = null,
    @SerialName("credential")
    val credential: String? = null
) {
    /** Chuyển sang cấu hình ICE độc lập nền tảng (null nếu không có URL nào). */
    fun toIceServerSpec(): IceServerSpec? {
        val urlList = when (urls) {
            is JsonPrimitive -> listOfNotNull(urls.contentOrNull)
            is JsonArray -> urls.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            else -> emptyList()
        }.filter { it.isNotBlank() }
        if (urlList.isEmpty()) return null
        return IceServerSpec(
            urls = urlList,
            username = username?.takeIf { it.isNotEmpty() },
            credential = credential?.takeIf { it.isNotEmpty() }
        )
    }
}

/**
 * Cụm cấu hình ICE Servers
 */
@Serializable
data class WebRtcConfigData(
    @SerialName("iceServers")
    val iceServers: List<IceServerConfig>? = null
)

/**
 * Gói tin nhận cấu hình WebRTC từ server sau khi đăng ký
 */
@Serializable
data class WebRtcConfigMessage(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    @SerialName("type")
    val type: String = SignalingType.WEBRTC_CONFIG,
    @SerialName("config")
    val config: WebRtcConfigData? = null
)

/**
 * Gói tin gửi / nhận Offer hoặc Answer SDP
 */
@Serializable
data class SdpSignalingMessage(
    @SerialName("type")
    val type: String, // "offer" hoặc "answer"
    @SerialName("target_id")
    val targetId: String? = null,
    @SerialName("sender_id")
    val senderId: String? = null,
    @SerialName("sdp")
    val sdp: String
)

/**
 * Gói tin ICE Candidate trao đổi giữa 2 thiết bị
 */
@Serializable
data class IceCandidateSignalingMessage(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    @SerialName("type")
    val type: String = SignalingType.ICE_CANDIDATE,
    @SerialName("target_id")
    val targetId: String? = null,
    @SerialName("sender_id")
    val senderId: String? = null,
    @SerialName("sdpMid")
    val sdpMid: String? = null,
    @SerialName("sdpMLineIndex")
    val sdpMLineIndex: Int = 0,
    @SerialName("candidate")
    val candidate: String
)

/**
 * Gói tin nhận danh sách các thiết bị/người dùng online từ server
 */
@Serializable
data class UserListMessage(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    @SerialName("type")
    val type: String = SignalingType.USER_LIST,
    @SerialName("users")
    val users: List<String>? = null,
    /** Danh sách chi tiết (tên, vai trò). Server bản cũ không gửi trường này. */
    @SerialName("members")
    val members: List<OnlineMember>? = null
)

/**
 * Một thành viên đang online trong sự kiện.
 */
@Serializable
data class OnlineMember(
    @SerialName("user_id")
    val userId: String = "",
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("role_title")
    val roleTitle: String? = null,
    @SerialName("is_organizer")
    val isOrganizer: Boolean = false
)

/**
 * Thông tin sự kiện server trả về sau khi đăng ký bằng mã PIN.
 */
@Serializable
data class EventInfoMessage(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    @SerialName("type")
    val type: String = SignalingType.EVENT_INFO,
    @SerialName("event_pin")
    val eventPin: String? = null,
    @SerialName("event_name")
    val eventName: String? = null,
    @SerialName("found")
    val found: Boolean = false
)
