package com.example.damthoai.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.damthoai.ui.screens.ActiveCallScreen
import com.example.damthoai.ui.screens.ChannelListScreen
import com.example.damthoai.ui.screens.CreateEventScreen
import com.example.damthoai.ui.screens.PocketModeScreen
import com.example.damthoai.ui.screens.QuickJoinScreen
import com.example.damthoai.ui.screens.SettingsScreen

@Composable
fun AdaptiveAppLayout(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppScreen.QuickJoin.route

    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    if (isCompact) {
        // Phone Layout with Bottom Navigation Bar
        Scaffold(
            bottomBar = {
                NavigationBar {
                    AppScreen.navItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(imageVector = screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
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
        // Tablet/Foldable/Wide Screen Layout with Side Navigation Rail
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail {
                AppScreen.navItems.forEach { screen ->
                    NavigationRailItem(
                        icon = { Icon(imageVector = screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
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
            }

            AppNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppScreen.QuickJoin.route,
        modifier = modifier
    ) {
        composable(AppScreen.QuickJoin.route) {
            QuickJoinScreen(
                onNavigateToChannels = {
                    navController.navigate(AppScreen.ChannelList.route)
                }
            )
        }

        composable(AppScreen.ChannelList.route) {
            ChannelListScreen(
                onNavigateToActiveCall = {
                    navController.navigate(AppScreen.ActiveCall.route)
                },
                onNavigateToCreateEvent = {
                    navController.navigate(AppScreen.CreateEvent.route)
                }
            )
        }

        composable(AppScreen.ActiveCall.route) {
            ActiveCallScreen(
                onNavigateToPocketMode = {
                    navController.navigate(AppScreen.PocketMode.route)
                }
            )
        }

        composable(AppScreen.PocketMode.route) {
            PocketModeScreen(
                onUnlock = {
                    navController.popBackStack()
                }
            )
        }

        composable(AppScreen.CreateEvent.route) {
            CreateEventScreen(
                onEventCreated = {
                    navController.navigate(AppScreen.ChannelList.route) {
                        popUpTo(AppScreen.ChannelList.route) { inclusive = true }
                    }
                }
            )
        }

        composable(AppScreen.Settings.route) {
            SettingsScreen()
        }
    }
}
