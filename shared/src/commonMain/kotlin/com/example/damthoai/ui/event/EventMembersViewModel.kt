package com.example.damthoai.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.damthoai.data.AppStartPrefs
import com.example.damthoai.data.Member
import com.example.damthoai.data.MemberLevel
import com.example.damthoai.data.Seat
import com.example.damthoai.network.model.OnlineMember
import com.example.damthoai.service.DamThoaiCallManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EventMembersUiState(
    val members: List<Member> = emptyList(),
    val searchQuery: String = "",
    val selectedLevel: MemberLevel? = null
) {
    val filteredMembers: List<Member>
        get() = members.filter { member ->
            val matchesQuery = searchQuery.isBlank() ||
                    member.name.contains(searchQuery, ignoreCase = true) ||
                    member.roleTitle.contains(searchQuery, ignoreCase = true)
            matchesQuery && (selectedLevel == null || member.level == selectedLevel)
        }
}

/** Tạo chữ viết tắt từ tên hiển thị (VD: "Huỳnh Tấn Phát" -> "HP"). */
internal fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
        parts.isNotEmpty() -> parts.first().take(2).uppercase()
        else -> "?"
    }
}

/**
 * Chuyển thành viên online từ server sang model UI.
 * Cấp suy ra từ vai trò trong QR: Trưởng ban = Cấp 1, Trưởng nhóm = Cấp 2, Thành viên = Cấp 3.
 * Chủ sự kiện luôn là Cấp 1.
 */
fun OnlineMember.toUiMember(myUserId: String): Member {
    val baseName = displayName?.takeIf { it.isNotBlank() } ?: userId
    val name = if (userId == myUserId) "$baseName (Bạn)" else baseName
    val role = roleTitle?.takeIf { it.isNotBlank() }
        ?: if (isOrganizer) "Ban tổ chức" else "Thành viên"
    return Member(
        id = userId,
        name = name,
        roleTitle = role,
        initials = initialsOf(baseName),
        level = if (isOrganizer) MemberLevel.LEVEL_1 else seat().role.memberLevel,
        isOnline = true,
        seat = seat(),
        isOrganizer = isOrganizer
    )
}

class EventMembersViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EventMembersUiState())
    val uiState: StateFlow<EventMembersUiState> = _uiState.asStateFlow()

    private val removedIds = mutableSetOf<String>()

    init {
        val myUid = AppStartPrefs.getUserId()
        viewModelScope.launch {
            DamThoaiCallManager.getInstance().onlineMembers.collect { online ->
                val memberList = online
                    .filter { it.userId !in removedIds }
                    .map { member -> member.toUiMember(myUid) }
                _uiState.update { it.copy(members = memberList) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectLevel(level: MemberLevel?) {
        _uiState.update { it.copy(selectedLevel = level) }
    }

    /** Chỉ chủ sự kiện mới được đổi vai trò/phòng ban của người khác. */
    val canEditMembers: Boolean get() = AppStartPrefs.isOrganizer()

    /**
     * Chủ sự kiện đổi vai trò/phòng ban: gửi lên server (server kiểm tra quyền, chuyển tới máy đó
     * và phát lại danh sách thành viên). Không sửa tạm trên máy để danh sách luôn theo server.
     */
    fun assignSeat(member: Member, seat: Seat): Boolean {
        if (!canEditMembers || member.isOrganizer) return false
        return DamThoaiCallManager.getInstance().assignSeat(member.id, seat)
    }

    /**
     * Chủ sự kiện xoá người này khỏi sự kiện thật sự: server đẩy máy đó ra, chặn vào lại bằng PIN/QR
     * và phát danh sách mới (người đó biến mất khỏi danh sách của mọi người).
     */
    fun kick(member: Member): Boolean {
        if (!canEditMembers || member.isOrganizer) return false
        val sent = DamThoaiCallManager.getInstance().kickMember(member.id)
        if (sent) {
            removedIds.add(member.id) // Ẩn ngay, không chờ danh sách mới từ server
            _uiState.update { state -> state.copy(members = state.members.filterNot { it.id == member.id }) }
        }
        return sent
    }
}
