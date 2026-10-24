package com.example.damthoai.ui.navigation

import com.example.damthoai.R

sealed class AppScreen(
    val route: String,
    val title: String,
    val icon: Int,
    val isBottomNavItem: Boolean = true
) {
    object QuickJoin : AppScreen(
        route = "quick_join",
        title = "Vào nhanh",
        icon = R.drawable.ic_qr
    )

    object ChannelList : AppScreen(
        route = "channel_list",
        title = "Danh sách kênh",
        icon = R.drawable.ic_list
    )

    object ActiveCall : AppScreen(
        route = "active_call",
        title = "Đàm thoại",
        icon = R.drawable.ic_mic
    )

    object PocketMode : AppScreen(
        route = "pocket_mode",
        title = "Bỏ túi",
        icon = R.drawable.ic_phone
    )

    object CreateEvent : AppScreen(
        route = "create_event",
        title = "Tạo sự kiện",
        icon = R.drawable.ic_plus,
        isBottomNavItem = false
    )

    object Settings : AppScreen(
        route = "settings",
        title = "Cài đặt",
        icon = R.drawable.ic_sliders
    )

    companion object {
        val navItems = listOf(QuickJoin, ChannelList, ActiveCall, PocketMode, Settings)
        val allScreens = listOf(QuickJoin, ChannelList, ActiveCall, PocketMode, CreateEvent, Settings)
    }
}
