package com.example.damthoai.ui.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.damthoai.data.model.Member
import com.example.damthoai.data.model.MemberLevel
import com.example.damthoai.data.remote.RemoteEventRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class EventMembersUiState(
    val members: List<Member> = emptyList(),
    val searchQuery: String = "",
    val selectedLevel: MemberLevel? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
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

    private var pollJob: Job? = null

    init {
        loadMembers()
        startPolling()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(10000L)
                silentRefresh()
            }
        }
    }

    fun loadMembers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val res = RemoteEventRepository.listMembers()
            if (res.isSuccess) {
                val list = res.getOrNull().orEmpty()
                _uiState.update { it.copy(members = list, isLoading = false, errorMessage = null) }
            } else {
                val err = res.exceptionOrNull()
                _uiState.update { it.copy(isLoading = false, errorMessage = err?.message ?: "Lỗi tải danh sách thành viên") }
            }
        }
    }

    private fun silentRefresh() {
        viewModelScope.launch {
            val res = RemoteEventRepository.listMembers()
            if (res.isSuccess) {
                val list = res.getOrNull().orEmpty()
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

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun updateMember(updatedMember: Member) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val res = RemoteEventRepository.updateMember(updatedMember.id, updatedMember.level.code, updatedMember.roleTitle)
            if (res.isSuccess) {
                _uiState.update { state ->
                    state.copy(
                        members = state.members.map { member ->
                            if (member.id == updatedMember.id) updatedMember else member
                        },
                        isLoading = false,
                        errorMessage = null
                    )
                }
                loadMembers()
            } else {
                val err = res.exceptionOrNull()
                _uiState.update { it.copy(isLoading = false, errorMessage = err?.message ?: "Lỗi cập nhật thành viên") }
            }
        }
    }

    fun removeMember(memberId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val res = RemoteEventRepository.removeMember(memberId)
            if (res.isSuccess) {
                _uiState.update { state ->
                    state.copy(
                        members = state.members.filterNot { it.id == memberId },
                        isLoading = false,
                        errorMessage = null
                    )
                }
                loadMembers()
            } else {
                val err = res.exceptionOrNull()
                _uiState.update { it.copy(isLoading = false, errorMessage = err?.message ?: "Lỗi xóa thành viên") }
            }
        }
    }
}
