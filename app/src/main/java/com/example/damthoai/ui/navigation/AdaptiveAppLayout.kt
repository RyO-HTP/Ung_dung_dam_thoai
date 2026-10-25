/**
 * Path: app/src/main/java/com/example/damthoai/ui/navigation/AdaptiveAppLayout.kt
 *
 * Khối KDoc tiếng Việt:
 * Khung Bố cục Thích ứng (Adaptive Layout) cho toàn app Đàm Thoại.
 * Tự động điều chỉnh giao diện tùy theo màn hình thiết bị:
 * - Điện thoại (Compact): Thanh Bottom Bar nổi dạng viên thuốc (Glassmorphism Pill) với Nút Mic PTT tròn nhô cao ở giữa.
 * - Tablet / Màn hình gập (Medium/Expanded): Bố cục Hai cột List-Detail (Kênh bên trái, Đàm thoại bên phải) kết hợp Navigation Rail bên hông.
 * - Tự động ẩn Bottom Bar ở các màn hình toàn tập trung (Onboarding, Quét QR, Bỏ túi, Khẩn cấp...).
 */
package com.example.damthoai.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.R
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.NavBarShape
import com.example.damthoai.ui.theme.NavBg
import com.example.damthoai.ui.theme.NavBorder
import com.example.damthoai.ui.theme.NavGlow
import com.example.damthoai.ui.theme.NavShadow
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.PrimaryBrush
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

/**
 * Khung layout chính hỗ trợ Adaptive Điện thoại / Tablet / Màn hình gập
 */
@Composable
fun AdaptiveAppLayout(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoute.Onboarding.route

    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    // Tuần 3: sửa - ẩn thanh dưới ở màn đàm thoại và Bỏ túi.
    val mainBottomNavRoutes = listOf(
        AppRoute.ChannelList.route,
        AppRoute.Settings.route
    )
    val shouldShowBottomBar = isCompact && currentRoute in mainBottomNavRoutes

    if (isCompact) {
        // --- BỐ CỤC ĐIỆN THOẠI (COMPACT) ---
        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = shouldShowBottomBar,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    FloatingPillBottomBar(
                        currentRoute = currentRoute,
                        onNavigateToTab = { route ->
                            if (currentRoute != route) {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        // Tuần 3: sửa - điều hướng từ nút micro vào màn đàm thoại.
                        onMicClick = { navController.navigate(AppRoute.ActiveCall.route) }
                    )
                }
            },
            modifier = modifier
        ) { innerPadding ->
            AppNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding)
            )
        }
    } else {
        // --- BỐ CỤC TABLET / MÀN HÌNH GẬP (MEDIUM / EXPANDED) ---
        // Thanh Navigation Rail bên trái + Bố cục 2 cột List - Detail
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = GlassBg,
                contentColor = TextPrimary
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                NavigationRailItem(
                    selected = currentRoute == AppRoute.ChannelList.route,
                    onClick = {
                        navController.navigate(AppRoute.ChannelList.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(painterResource(R.drawable.ic_radio), contentDescription = "Kênh") },
                    label = { Text("Kênh") }
                )
                NavigationRailItem(
                    selected = currentRoute == AppRoute.Settings.route,
                    onClick = {
                        navController.navigate(AppRoute.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(painterResource(R.drawable.ic_sliders), contentDescription = "Cài đặt") },
                    label = { Text("Cài đặt") }
                )
            }

            // Màn hình chính nội dung
            Row(modifier = Modifier.weight(1f)) {
                // Cột 1: Navigation Host (Kênh / Cài đặt)
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    AppNavHost(
                        navController = navController,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Thanh Bottom Bar viên thuốc nổi (Floating Pill) kính mờ
 * Tích hợp Nút Mic tròn nhô cao ở giữa với vòng hào quang
 */
@Composable
private fun FloatingPillBottomBar(
    currentRoute: String,
    onNavigateToTab: (String) -> Unit,
    // Tuần 3: sửa - nút micro mở màn đàm thoại.
    onMicClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 44.dp, vertical = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .shadow(40.dp, NavBarShape, spotColor = NavShadow)
                .clip(NavBarShape)
                .background(NavBg)
                .border(1.dp, NavBorder, NavBarShape)
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab Trái: KÊNH
                val isChannelSelected = currentRoute == AppRoute.ChannelList.route
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onNavigateToTab(AppRoute.ChannelList.route) }
                        .padding(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_radio),
                        contentDescription = "Kênh",
                        tint = if (isChannelSelected) AccentCyan else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Kênh",
                        color = if (isChannelSelected) AccentCyan else TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Khoảng trống ở giữa nhường chỗ cho Nút Mic nhô cao
                Spacer(modifier = Modifier.width(64.dp))

                // Tab Phải: CÀI ĐẶT
                val isSettingsSelected = currentRoute == AppRoute.Settings.route
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onNavigateToTab(AppRoute.Settings.route) }
                        .padding(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sliders),
                        contentDescription = "Cài đặt",
                        tint = if (isSettingsSelected) AccentCyan else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Cài đặt",
                        color = if (isSettingsSelected) AccentCyan else TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // NÚT MIC TRÒN LỚN NHÔ CAO Ở GIỮA
        Box(
            modifier = Modifier
                .offset(y = (-26).dp)
                .size(76.dp)
                .shadow(elevation = 30.dp, shape = CircleShape, spotColor = NavShadow)
                .clip(CircleShape)
                .background(NavGlow)
                // Tuần 3: sửa - gọi callback điều hướng khi nhấn nút micro.
                .clickable(onClick = onMicClick),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(PrimaryBrush),
                contentAlignment = Alignment.Center
            ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = "PTT Mic",
                tint = TextPrimary,
                modifier = Modifier.size(26.dp)
            )
            }
        }
    }
}
