package com.example.damthoai.ui.navigation

import androidx.annotation.DrawableRes
import com.example.damthoai.R

sealed class AppScreen(
    val route: String,
    val title: String,
    @DrawableRes val iconRes: Int,
    val isBottomNavItem: Boolean = true
) {
    val icon: Int get() = iconRes

    object QuickJoin : AppScreen(
        route = "quick_join",
        title = "Vào nhanh",
        iconRes = R.drawable.ic_qr
    )

    object ChannelList : AppScreen(
        route = "channel_list",
        title = "Danh sách kênh",
        iconRes = R.drawable.ic_list
    )

    object ActiveCall : AppScreen(
        route = "active_call",
        title = "Đàm thoại",
        iconRes = R.drawable.ic_mic
    )

    object PocketMode : AppScreen(
        route = "pocket_mode",
        title = "Bỏ túi",
        iconRes = R.drawable.ic_phone
    )

    object CreateEvent : AppScreen(
        route = "create_event",
        title = "Tạo sự kiện",
        iconRes = R.drawable.ic_plus,
        isBottomNavItem = false
    )

    object Settings : AppScreen(
        route = "settings",
        title = "Cài đặt",
        iconRes = R.drawable.ic_sliders
    )

    companion object {
        val navItems = listOf(QuickJoin, ChannelList, ActiveCall, PocketMode, Settings)
        val allScreens = listOf(QuickJoin, ChannelList, ActiveCall, PocketMode, CreateEvent, Settings)
    }
}
