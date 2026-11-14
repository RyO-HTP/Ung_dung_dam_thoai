package com.example.damthoai.ui.navigation

import org.jetbrains.compose.resources.DrawableResource
import com.example.damthoai.resources.*
sealed class AppScreen(
    val route: String,
    val title: String,
    val iconRes: DrawableResource,
    val isBottomNavItem: Boolean = true
) {
    val icon: DrawableResource get() = iconRes

    object QuickJoin : AppScreen(
        route = "quick_join",
        title = "Vào nhanh",
        iconRes = Res.drawable.ic_qr
    )

    object ChannelList : AppScreen(
        route = "channel_list",
        title = "Danh sách kênh",
        iconRes = Res.drawable.ic_list
    )

    object ActiveCall : AppScreen(
        route = "active_call",
        title = "Đàm thoại",
        iconRes = Res.drawable.ic_mic
    )

    object PocketMode : AppScreen(
        route = "pocket_mode",
        title = "Bỏ túi",
        iconRes = Res.drawable.ic_phone
    )

    object CreateEvent : AppScreen(
        route = "create_event",
        title = "Tạo sự kiện",
        iconRes = Res.drawable.ic_plus,
        isBottomNavItem = false
    )

    object Settings : AppScreen(
        route = "settings",
        title = "Cài đặt",
        iconRes = Res.drawable.ic_sliders
    )

    companion object {
        val navItems = listOf(QuickJoin, ChannelList, ActiveCall, PocketMode, Settings)
        val allScreens = listOf(QuickJoin, ChannelList, ActiveCall, PocketMode, CreateEvent, Settings)
    }
}
