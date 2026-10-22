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

import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.PrimaryEnd
import com.example.damthoai.ui.theme.PrimaryGlow
import com.example.damthoai.ui.theme.PrimaryStart
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

    // Các màn hình chính hiển thị Bottom Bar
    val mainBottomNavRoutes = listOf(
        AppRoute.ChannelList.route,
        AppRoute.ActiveCall.route,
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
                        }
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
                    icon = { Icon(Icons.Default.Radio, contentDescription = "Kênh") },
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
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Cài đặt") },
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
    onNavigateToTab: (String) -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Thanh Kính Mờ Viên Thuốc
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(CircleShape)
                .background(GlassBg)
                .border(1.dp, GlassBorder, CircleShape)
                .padding(horizontal = 24.dp),
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
                        imageVector = Icons.Default.Radio,
                        contentDescription = "Kênh",
                        tint = if (isChannelSelected) AccentCyan else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Kênh",
                        color = if (isChannelSelected) AccentCyan else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isChannelSelected) FontWeight.Bold else FontWeight.Normal
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
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Cài đặt",
                        tint = if (isSettingsSelected) AccentCyan else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Cài đặt",
                        color = if (isSettingsSelected) AccentCyan else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSettingsSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // NÚT MIC TRÒN LỚN NHÔ CAO Ở GIỮA
        val micGradient = Brush.verticalGradient(
            colors = listOf(PrimaryStart, PrimaryEnd)
        )

        Box(
            modifier = Modifier
                .offset(y = (-12).dp)
                .size(68.dp)
                .shadow(elevation = 12.dp, shape = CircleShape, spotColor = PrimaryGlow)
                .clip(CircleShape)
                .background(micGradient)
                .border(2.dp, AccentCyan.copy(alpha = 0.6f), CircleShape)
                .clickable {
                    // Logic PTT Mic giả lập cho Tuần 1
                    Toast.makeText(context, "Đã giữ Mic PTT (Mock - Tuần 1)", Toast.LENGTH_SHORT).show()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "PTT Mic",
                tint = TextPrimary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
