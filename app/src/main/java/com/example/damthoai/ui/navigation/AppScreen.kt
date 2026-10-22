package com.example.damthoai.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppScreen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val isBottomNavItem: Boolean = true
) {
    object QuickJoin : AppScreen(
        route = "quick_join",
        title = "Vào nhanh",
        icon = Icons.Default.QrCodeScanner
    )

    object ChannelList : AppScreen(
        route = "channel_list",
        title = "Danh sách kênh",
        icon = Icons.Default.FormatListNumbered
    )

    object ActiveCall : AppScreen(
        route = "active_call",
        title = "Đàm thoại",
        icon = Icons.Default.Mic
    )

    object PocketMode : AppScreen(
        route = "pocket_mode",
        title = "Bỏ túi",
        icon = Icons.Default.ScreenLockPortrait
    )

    object CreateEvent : AppScreen(
        route = "create_event",
        title = "Tạo sự kiện",
        icon = Icons.Default.AddBox,
        isBottomNavItem = false
    )

    object Settings : AppScreen(
        route = "settings",
        title = "Cài đặt",
        icon = Icons.Default.Settings
    )

    companion object {
        val navItems = listOf(QuickJoin, ChannelList, ActiveCall, PocketMode, Settings)
        val allScreens = listOf(QuickJoin, ChannelList, ActiveCall, PocketMode, CreateEvent, Settings)
    }
}
