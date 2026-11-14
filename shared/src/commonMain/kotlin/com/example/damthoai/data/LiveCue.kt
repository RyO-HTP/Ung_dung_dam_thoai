package com.example.damthoai.data

data class LiveCue(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val message: String = ""
    // Nếu LiveCueCard.kt báo đỏ ở các biến khác (như cue.time, cue.message), bạn hãy thêm các biến đó vào đây cho khớp.
)