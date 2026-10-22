/**
 * Path: app/src/main/java/com/example/damthoai/MainActivity.kt
 *
 * Khối KDoc tiếng Việt:
 * Activity chính của ứng dụng Đàm Thoại.
 * Khởi tạo chế độ Edge-to-Edge, xin các quyền cần thiết và thiết lập DamThoaiTheme + AdaptiveAppLayout.
 */
package com.example.damthoai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import com.example.damthoai.permission.PermissionManager
import com.example.damthoai.ui.navigation.AdaptiveAppLayout
import com.example.damthoai.ui.theme.BgGradientTop
import com.example.damthoai.ui.theme.DamThoaiTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            // Xử lý khi người dùng từ chối bớt quyền
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bật edge-to-edge tràn viền màn hình
        enableEdgeToEdge()

        // Kiểm tra và xin các quyền ban đầu
        checkAndRequestPermissions()

        setContent {
            DamThoaiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgGradientTop
                ) {
                    val windowSizeClass = calculateWindowSizeClass(this)
                    AdaptiveAppLayout(windowSizeClass = windowSizeClass)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (!PermissionManager.isIgnoringBatteryOptimizations(this)) {
            PermissionManager.requestIgnoreBatteryOptimizations(this)
        }

        if (!PermissionManager.hasAllPermissions(this)) {
            permissionLauncher.launch(PermissionManager.ALL_REQUIRED_PERMISSIONS)
        }
    }
}
