package com.example.damthoai.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.damthoai.data.model.Member
import com.example.damthoai.data.model.MemberLevel
import com.example.damthoai.data.remote.RemoteEventRepository
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

class EventMembersViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EventMembersUiState())
    val uiState: StateFlow<EventMembersUiState> = _uiState.asStateFlow()

    init {
        loadMembers()
    }

    fun loadMembers() {
        viewModelScope.launch {
            val res = RemoteEventRepository.listMembers()
            val list = res.getOrNull().orEmpty()
            if (list.isNotEmpty()) {
                _uiState.update { it.copy(members = list) }
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
        viewModelScope.launch {
            RemoteEventRepository.updateMember(updatedMember.id, updatedMember.level.code, updatedMember.roleTitle)
        }
        _uiState.update { state ->
            state.copy(
                members = state.members.map { member ->
                    if (member.id == updatedMember.id) updatedMember else member
                }
            )
        }
    }

    fun removeMember(memberId: String) {
        viewModelScope.launch {
            RemoteEventRepository.removeMember(memberId)
        }
        _uiState.update { state ->
            state.copy(members = state.members.filterNot { it.id == memberId })
        }
    }
}
