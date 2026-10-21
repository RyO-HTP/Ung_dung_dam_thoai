package com.example.damthoai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import com.example.damthoai.permission.PermissionManager
import com.example.damthoai.ui.navigation.AdaptiveAppLayout
import com.example.damthoai.ui.theme.Ung_dung_dam_thoaiTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results if needed
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            // Some permissions denied; can prompt user or guide to settings
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request necessary permissions on startup (Tasks 1-4)
        checkAndRequestPermissions()

        setContent {
            Ung_dung_dam_thoaiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val windowSizeClass = calculateWindowSizeClass(this)
                    AdaptiveAppLayout(windowSizeClass = windowSizeClass)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        // Check background execution battery optimization exemption (Task 4)
        if (!PermissionManager.isIgnoringBatteryOptimizations(this)) {
            PermissionManager.requestIgnoreBatteryOptimizations(this)
        }

        // Request all required permissions (Micro, Camera, Network, Bluetooth, Background execution)
        if (!PermissionManager.hasAllPermissions(this)) {
            permissionLauncher.launch(PermissionManager.ALL_REQUIRED_PERMISSIONS)
        }
    }
}
