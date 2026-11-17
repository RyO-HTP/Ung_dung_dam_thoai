package com.example.damthoai.network.model

import com.example.damthoai.data.Seat
import com.example.damthoai.data.TeamChannels
import com.example.damthoai.data.TeamRole
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
    /** Báo bắt đầu/ngừng nói trên một kênh (server chuyển cho cả sự kiện, máy nhận tự lọc theo kênh). */
    const val PTT = "ptt"

    // Giành lượt nói (server quyết định): app gửi request/release, server trả granted/denied
    // cho người xin và phát floor_state (ai đang giữ lượt) cho cả sự kiện.
    const val FLOOR_REQUEST = "floor_request"
    const val FLOOR_RELEASE = "floor_release"
    const val FLOOR_GRANTED = "floor_granted"
    const val FLOOR_DENIED = "floor_denied"
    const val FLOOR_STATE = "floor_state"
    /** Người giữ lượt báo "vẫn đang nói" mỗi 2 giây; im quá 5 giây (mất sóng) thì server thu hồi. */
    const val FLOOR_KEEPALIVE = "floor_keepalive"

    /** Chủ sự kiện đổi vai trò/phòng ban của người khác (server chỉ nhận từ chủ sự kiện). */
    const val SET_SEAT = "set_seat"
    /** Server báo cho một máy: chủ sự kiện đã đổi vai trò/phòng ban của bạn. */
    const val SEAT_ASSIGNED = "seat_assigned"
    /** Server từ chối set_seat (không phải chủ sự kiện, người đó không còn online...). */
    const val SET_SEAT_DENIED = "set_seat_denied"

    /** Chủ sự kiện xoá một người khỏi sự kiện (server kiểm tra quyền). */
    const val KICK = "kick"
    /** Server báo cho một máy: bạn đã bị xoá khỏi sự kiện (và không vào lại được). */
    const val KICKED = "kicked"
    /** Server không công nhận máy này là chủ sự kiện (sai mã quản trị / quyền đã chuyển máy khác). */
    const val ORGANIZER_DENIED = "organizer_denied"
}

/**
 * Gói tin giành lượt nói, dùng chung cho cả 5 loại floor_*.
 * - floor_denied: [holderId] = người đang giữ lượt, [reason] = "busy" / "not_member".
 * - floor_state: [holderId] null = kênh trống; [preemptedId] = người vừa bị chen ngang;
 *   [reason] = "preempted" / "released" / "timeout" / "left".
 */
@Serializable
data class FloorMessage(
    @SerialName("type")
    val type: String,
    @SerialName("channel_id")
    val channelId: String = "",
    @SerialName("holder_id")
    val holderId: String? = null,
    @SerialName("preempted_id")
    val preemptedId: String? = null,
    @SerialName("reason")
    val reason: String? = null,
    /** Chỉ trong floor_request: true = app sẽ gửi floor_keepalive, server được thu hồi khi im lặng. */
    @SerialName("keepalive")
    val keepalive: Boolean? = null
)

/**
 * Gói tin trạng thái PTT: { "type": "ptt", "channel_id": "ch_02", "talking": true }.
 * Server tự gắn sender_id.
 */
@Serializable
data class PttMessage(
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    @SerialName("type")
    val type: String = SignalingType.PTT,
    @SerialName("sender_id")
    val senderId: String? = null,
    @SerialName("channel_id")
    val channelId: String = "",
    @SerialName("talking")
    val talking: Boolean = false
)

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
    val eventName: String? = null,
    /** Vai trò phân tầng: "ban" / "nhom" / "tv" (xem TeamRole). */
    @SerialName("team_role")
    val teamRole: String? = null,
    /** Kênh nhóm của người này (trưởng ban: kênh Ban chỉ huy). */
    @SerialName("channel_id")
    val channelId: String? = null,
    @SerialName("channel_name")
    val channelName: String? = null,
    /** Mã quản trị (chỉ chủ sự kiện gửi): server chỉ công nhận chủ sự kiện khi mã khớp. */
    @SerialName("admin_code")
    val adminCode: String? = null,
    /** Danh mục nhóm/phòng ban (chỉ chủ sự kiện gửi) để server lưu lại. */
    @SerialName("catalog")
    val catalog: List<CatalogEntry>? = null,
    /** true khi đang "Khôi phục bằng mã quản trị" trên máy mới (máy cũ sẽ mất quyền). */
    @SerialName("restore")
    val restore: Boolean = false
)

/**
 * Chủ sự kiện đổi vai trò/phòng ban của một người (gửi lên server), hoặc server báo cho
 * người đó biết vai trò mới ([SignalingType.SEAT_ASSIGNED], kèm [byName] là tên chủ sự kiện).
 */
@Serializable
data class SeatMessage(
    @SerialName("type")
    val type: String,
    @SerialName("target_id")
    val targetId: String? = null,
    @SerialName("team_role")
    val teamRole: String = "",
    @SerialName("channel_id")
    val channelId: String = "",
    @SerialName("channel_name")
    val channelName: String = "",
    @SerialName("by_name")
    val byName: String? = null,
    /** Lý do server chuyển chỗ: "" (chủ sự kiện đổi vai trò), "channel_renamed", "channel_deleted". */
    @SerialName("reason")
    val reason: String? = null
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
    val isOrganizer: Boolean = false,
    /** Server/app bản cũ không gửi các trường dưới: coi là thành viên kênh chung. */
    @SerialName("team_role")
    val teamRole: String? = null,
    @SerialName("channel_id")
    val channelId: String? = null,
    @SerialName("channel_name")
    val channelName: String? = null,
    /** Mã phiên kết nối của máy này trên server; đổi = máy vừa mở socket mới (kết nối P2P cũ đã chết). */
    @SerialName("conn_id")
    val connId: Int? = null
) {
    fun seat(): Seat = Seat(
        role = TeamRole.fromWire(teamRole),
        groupId = channelId?.takeIf { it.isNotBlank() } ?: TeamChannels.GENERAL_ID,
        groupName = channelName.orEmpty()
    )
}

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
    val found: Boolean = false,
    /** Danh mục nhóm/phòng ban chủ sự kiện đã lưu trên server (để khôi phục trên máy khác). */
    @SerialName("catalog")
    val catalog: List<CatalogEntry>? = null
)

/** Một nhóm/phòng ban trong danh mục của chủ sự kiện. */
@Serializable
data class CatalogEntry(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String
)

/**
 * Gói server gửi riêng cho một máy:
 * - [SignalingType.KICKED]: chủ sự kiện đã xoá bạn khỏi sự kiện ([byName]).
 * - [SignalingType.ORGANIZER_DENIED]: server không công nhận máy này là chủ sự kiện
 *   ([reason] "bad_code" = sai mã quản trị, "revoked" = quyền đã chuyển sang máy khác).
 */
@Serializable
data class NoticeMessage(
    @SerialName("type") val type: String,
    @SerialName("event_pin") val eventPin: String? = null,
    @SerialName("target_id") val targetId: String? = null,
    @SerialName("by_name") val byName: String? = null,
    @SerialName("reason") val reason: String? = null
)
