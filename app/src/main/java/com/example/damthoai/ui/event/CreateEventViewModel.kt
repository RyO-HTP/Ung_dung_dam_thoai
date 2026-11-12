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
            name = "Thành viên sự kiện",
            roleTitle = "Quản trị viên",
            initials = "TT",
            level = MemberLevel.LEVEL_1
        )
    )
    val creator: StateFlow<Member> = _creator.asStateFlow()
}