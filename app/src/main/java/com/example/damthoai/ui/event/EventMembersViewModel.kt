package com.example.damthoai.ui.event

import androidx.lifecycle.ViewModel
import com.example.damthoai.data.Member
import com.example.damthoai.data.MemberLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

class EventMembersViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EventMembersUiState())
    val uiState: StateFlow<EventMembersUiState> = _uiState.asStateFlow()

    init {
        // ĐÃ BỔ SUNG ĐẦY ĐỦ THAM SỐ 'initials' CHO CÁC MẪU THÀNH VIÊN
        _uiState.update {
            it.copy(
                members = listOf(
                    Member(id = "m_1", name = "Huỳnh Tấn Phát", roleTitle = "Tech Lead / Tổng đạo diễn", level = MemberLevel.LEVEL_1, initials = "HP"),
                    Member(id = "m_2", name = "Nguyễn Văn A", roleTitle = "Hậu đài âm thanh", level = MemberLevel.LEVEL_2, initials = "NA"),
                    Member(id = "m_3", name = "Trần Thị B", roleTitle = "An ninh sự kiện", level = MemberLevel.LEVEL_3, initials = "TB")
                )
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectLevel(level: MemberLevel?) {
        _uiState.update { it.copy(selectedLevel = level) }
    }

    fun updateMember(updatedMember: Member) {
        _uiState.update { state ->
            state.copy(
                members = state.members.map { member ->
                    if (member.id == updatedMember.id) updatedMember else member
                }
            )
        }
    }

    fun removeMember(memberId: String) {
        _uiState.update { state ->
            state.copy(members = state.members.filterNot { it.id == memberId })
        }
    }
}