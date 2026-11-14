package com.example.damthoai.ui.event

import androidx.lifecycle.ViewModel
import com.example.damthoai.data.Member
import com.example.damthoai.data.MemberLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CreateEventViewModel : ViewModel() {

    // Trạng thái lưu thông tin người tạo, có thể fetch từ local session/API sau này
    private val _creator = MutableStateFlow(
        Member(
            id = "user_default",
            name = "",
            roleTitle = "",
            initials = "",
            level = MemberLevel.LEVEL_1
        )
    )
    val creator: StateFlow<Member> = _creator.asStateFlow()

    fun updateCreator(name: String, roleTitle: String) {
        val trimmedName = name.trim()
        val trimmedRole = roleTitle.trim()
        val initials = trimmedName.split(" ").filter { it.isNotEmpty() }
            .let { parts ->
                when {
                    parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
                    parts.isNotEmpty() -> parts.first().take(2).uppercase()
                    else -> ""
                }
            }
        _creator.value = _creator.value.copy(
            name = trimmedName,
            roleTitle = if (trimmedRole.isNotBlank()) trimmedRole else "Quản trị viên",
            initials = initials
        )
    }
}