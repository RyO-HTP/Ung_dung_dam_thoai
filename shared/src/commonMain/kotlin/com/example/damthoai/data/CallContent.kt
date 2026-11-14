package com.example.damthoai.data

data class CallContent(
    val title: String,
    val statusTitle: String = "Trạng thái kết nối",
    val statusDescription: String = "Đang sẵn sàng đàm thoại",
    val statusIconRes: org.jetbrains.compose.resources.DrawableResource? = null, // null = dùng icon loa mặc định
    val statusColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Green,
    val statusBackground: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.LightGray,
    val channels: List<com.example.damthoai.data.Channel> = emptyList() // Trỏ trực tiếp vào lớp Channel thực tế trong dự án
)

data class CallChannel(
    val id: String,
    val name: String,
    val volume: Float = 1.0f
)