package com.example.damthoai.ui.event

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.damthoai.data.AppStartPrefs
import com.example.damthoai.data.Member
import com.example.damthoai.data.MemberLevel
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
 * Ban tổ chức là Cấp 1; người khác mặc định Cấp 2 (cấp có thể đổi trong sheet sửa thành viên).
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
        level = if (isOrganizer) MemberLevel.LEVEL_1 else MemberLevel.LEVEL_2,
        isOnline = true
    )
}

class EventMembersViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(EventMembersUiState())
    val uiState: StateFlow<EventMembersUiState> = _uiState.asStateFlow()

    // Chỉnh sửa cục bộ của ban tổ chức (đổi cấp/vai trò) được giữ lại khi danh sách online cập nhật
    private val localEdits = mutableMapOf<String, Member>()
    private val removedIds = mutableSetOf<String>()

    init {
        val myUid = AppStartPrefs.getUserId(application)
        viewModelScope.launch {
            DamThoaiCallManager.getInstance(application).onlineMembers.collect { online ->
                val memberList = online
                    .filter { it.userId !in removedIds }
                    .map { member ->
                        val fresh = member.toUiMember(myUid)
                        localEdits[fresh.id]?.copy(isOnline = true) ?: fresh
                    }
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

    fun updateMember(updatedMember: Member) {
        localEdits[updatedMember.id] = updatedMember
        _uiState.update { state ->
            state.copy(
                members = state.members.map { member ->
                    if (member.id == updatedMember.id) updatedMember else member
                }
            )
        }
    }

    fun removeMember(memberId: String) {
        removedIds.add(memberId)
        _uiState.update { state ->
            state.copy(members = state.members.filterNot { it.id == memberId })
        }
    }
}
