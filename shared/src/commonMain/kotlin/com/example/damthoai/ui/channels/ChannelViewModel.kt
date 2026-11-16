package com.example.damthoai.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.damthoai.data.AppStartPrefs
import com.example.damthoai.data.Channel
import com.example.damthoai.data.Seat
import com.example.damthoai.data.TeamChannels
import com.example.damthoai.data.TeamRole
import com.example.damthoai.network.model.OnlineMember
import com.example.damthoai.service.DamThoaiCallManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Danh sách kênh của MÌNH theo vai trò phân tầng:
 * - Trưởng ban: Ban chỉ huy. Trưởng nhóm: Ban chỉ huy + nhóm của mình. Thành viên: nhóm của mình.
 * Mỗi kênh có âm lượng / tắt loa riêng (áp thật vào âm thanh của từng người trong kênh),
 * danh sách người đang trong kênh và người đang nói.
 */
class ChannelViewModel : ViewModel() {

    /** Trạng thái chỉ máy này giữ (âm lượng, tắt loa, danh mục nhóm của ban tổ chức...). */
    private data class LocalState(
        val eventName: String,
        val volumes: Map<String, Float> = emptyMap(),
        val muted: Set<String> = emptySet(),
        val hidden: Set<String> = emptySet(),
        val catalog: List<Channel> = DEFAULT_CATALOG,
        val menuChannelId: String? = null,
        val roleChanged: RoleChangedUiState? = null
    )

    companion object {
        /** Các nhóm/phòng ban mặc định của sự kiện (kênh Ban chỉ huy luôn có sẵn, không nằm ở đây). */
        val DEFAULT_CATALOG = listOf(
            Channel(id = "ch_02", name = "Sân khấu"),
            Channel(id = "ch_03", name = "Kỹ thuật"),
            Channel(id = "ch_04", name = "Hậu cần – An ninh"),
            Channel(id = "ch_05", name = "Đón tiếp")
        )
    }

    private val callManager = DamThoaiCallManager.getInstance()
    private val myUserId = AppStartPrefs.getUserId()

    private val local = MutableStateFlow(
        LocalState(eventName = AppStartPrefs.getEventName().ifBlank { AppStartPrefs.DEFAULT_EVENT_NAME })
    )

    val uiState: StateFlow<ChannelListUiState> = combine(
        local,
        callManager.seat,
        callManager.onlineMembers,
        callManager.talkChannelId,
        callManager.speakers
    ) { state, seat, members, talkChannel, speakers ->
        buildUiState(state, seat, members, talkChannel, speakers)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        buildUiState(local.value, callManager.seat.value, callManager.onlineMembers.value,
            callManager.talkChannelId.value, callManager.speakers.value)
    )

    init {
        // Tên sự kiện cập nhật trực tiếp khi server gửi tên do ban tổ chức đặt
        viewModelScope.launch {
            callManager.eventName.collect { name ->
                local.update { it.copy(eventName = name.ifBlank { AppStartPrefs.DEFAULT_EVENT_NAME }) }
            }
        }
        // Quét QR vai trò mới: các kênh đã "rời" trước đó hiện lại
        viewModelScope.launch {
            callManager.seat.collect { local.update { it.copy(hidden = emptySet(), menuChannelId = null) } }
        }
        // Âm lượng / tắt loa từng kênh → âm lượng nghe thật của những người trong kênh đó
        viewModelScope.launch {
            combine(local, callManager.seat) { state, seat ->
                TeamChannels.channelsOf(seat).map { id ->
                    val off = id in state.muted || id in state.hidden
                    id to if (off) 0f else (state.volumes[id] ?: 1f)
                }
            }
                .distinctUntilChanged()
                .collect { levels -> levels.forEach { (id, v) -> callManager.setChannelVolume(id, v) } }
        }
    }

    private fun buildUiState(
        state: LocalState,
        seat: Seat,
        members: List<OnlineMember>,
        talkChannel: String,
        speakers: Map<String, Set<String>>
    ): ChannelListUiState {
        fun nameOf(m: OnlineMember) = m.displayName?.takeIf { it.isNotBlank() } ?: m.userId

        val channels = TeamChannels.channelsOf(seat).filter { it !in state.hidden }.map { id ->
            val talking = speakers[id].orEmpty()
            val inChannel = members
                .filter { id in TeamChannels.channelsOf(it.seat()) }
                .sortedWith(compareBy({ it.seat().role.ordinal }, { nameOf(it).lowercase() }))
                .map { m ->
                    ChannelMemberUi(
                        userId = m.userId,
                        displayName = nameOf(m),
                        teamRoleTitle = m.seat().role.title,
                        roleTitle = m.roleTitle.orEmpty(),
                        isMe = m.userId == myUserId,
                        isTalking = m.userId in talking
                    )
                }
            val speakerNames = inChannel.filter { it.isTalking }.joinToString { it.displayName }
            ChannelItemUiState(
                channel = Channel(
                    id = id,
                    name = TeamChannels.channelName(id, seat.groupName),
                    isDefault = id == TeamChannels.COMMAND_ID,
                    isPriority = id == TeamChannels.COMMAND_ID,
                    activeMembers = inChannel.size,
                    currentSpeaker = speakerNames.ifBlank { null }
                ),
                volume = state.volumes[id] ?: 1f,
                isMuted = id in state.muted,
                isTransmittingMic = id == talkChannel,
                members = inChannel
            )
        }
        val roleLabel = when (seat.role) {
            TeamRole.TRUONG_BAN -> seat.role.title
            else -> "${seat.role.title} · ${TeamChannels.channelName(seat.groupId, seat.groupName)}"
        }
        return ChannelListUiState(
            eventName = state.eventName,
            channels = channels,
            activeMicChannelId = talkChannel,
            selectedMenuChannel = channels.firstOrNull { it.channel.id == state.menuChannelId },
            roleChangedNotification = state.roleChanged,
            catalog = state.catalog,
            myRoleLabel = roleLabel
        )
    }

    fun refreshEventName() {
        val currentName = AppStartPrefs.getEventName().ifBlank { AppStartPrefs.DEFAULT_EVENT_NAME }
        local.update { it.copy(eventName = currentName) }
    }

    // 1. Chọn kênh để phát Mic (PTT chỉ tới người trong kênh này)
    fun selectMicChannel(channelId: String) {
        callManager.setTalkChannel(channelId)
    }

    // 2. Mở menu tùy chọn của kênh
    fun openChannelMenu(channelItem: ChannelItemUiState) {
        local.update { it.copy(menuChannelId = channelItem.channel.id) }
    }

    // 3. Đóng menu tùy chọn của kênh
    fun closeChannelMenu() {
        local.update { it.copy(menuChannelId = null) }
    }

    // 4. Cập nhật âm lượng kênh
    fun updateChannelVolume(channelId: String, newVolume: Float) {
        local.update { it.copy(volumes = it.volumes + (channelId to newVolume.coerceIn(0f, 1f))) }
    }

    // 5. Bật/Tắt loa (mute) của kênh
    fun toggleMuteChannel(channelId: String, isMuted: Boolean) {
        local.update { it.copy(muted = if (isMuted) it.muted + channelId else it.muted - channelId) }
    }

    // 6. Rời khỏi kênh: ẩn kênh và không nghe kênh đó nữa (vào lại bằng QR/PIN)
    fun leaveChannel(channelId: String) {
        local.update { it.copy(hidden = it.hidden + channelId, menuChannelId = null) }
    }

    // 7. Thêm phòng ban / nhóm mới (Admin) — dùng để tạo mã QR cho nhóm đó
    fun addChannel(name: String) {
        if (name.isBlank()) return
        @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
        val newId = "ch_${kotlin.uuid.Uuid.random().toString().take(8)}"
        local.update { it.copy(catalog = it.catalog + Channel(id = newId, name = name.trim())) }
    }

    // 8. Đổi tên phòng ban / nhóm (Admin)
    fun renameChannel(channelId: String, newName: String) {
        if (newName.isBlank()) return
        local.update { state ->
            state.copy(
                catalog = state.catalog.map { if (it.id == channelId) it.copy(name = newName.trim()) else it },
                menuChannelId = null
            )
        }
    }

    // 9. Xóa phòng ban / nhóm (Admin)
    fun deleteChannel(channelId: String) {
        local.update { state ->
            state.copy(catalog = state.catalog.filterNot { it.id == channelId }, menuChannelId = null)
        }
    }

    /**
     * Phương thức thực tế dùng để cập nhật thông báo khi cấp trên/quản trị viên thay đổi chức vụ của bạn.
     */
    fun showRoleChangedNotification(adminName: String, newRole: String, level: Int) {
        local.update {
            it.copy(
                roleChanged = RoleChangedUiState(
                    changedByName = adminName,
                    newRoleTitle = newRole,
                    newLevelCode = level
                )
            )
        }
    }

    /**
     * Đóng/ẩn thông báo khi người dùng đã xem xong.
     */
    fun dismissRoleChangedBanner() {
        local.update { it.copy(roleChanged = null) }
    }
}
