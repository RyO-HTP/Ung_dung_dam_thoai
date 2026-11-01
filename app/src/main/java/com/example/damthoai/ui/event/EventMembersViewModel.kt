package com.example.damthoai.ui.event

import androidx.lifecycle.ViewModel
import com.example.damthoai.data.fake.FakeRemainingUiData
import com.example.damthoai.data.fake.Member
import com.example.damthoai.data.fake.MemberLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EventMembersUiState(
    val members: List<Member> = FakeRemainingUiData.eventMembers,
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
