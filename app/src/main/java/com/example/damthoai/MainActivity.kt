/**
 * Path: app/src/main/java/com/example/damthoai/MainActivity.kt
 *
 * Khối KDoc tiếng Việt:
 * Activity chính của ứng dụng Đàm Thoại.
 * Khởi tạo chế độ Edge-to-Edge, làm tươi trạng thái quyền và thiết lập DamThoaiTheme + AdaptiveAppLayout.
 */
package com.example.damthoai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bật edge-to-edge tràn viền màn hình
        enableEdgeToEdge()

        // Làm tươi trạng thái các quyền ngay khi mở Activity
        PermissionManager.refreshPermissionStates(this, this)

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

    override fun onResume() {
        super.onResume()
        // Cập nhật lại trạng thái quyền khi ứng dụng quay lại từ Cài đặt hệ thống
        PermissionManager.refreshPermissionStates(this, this)
    }
}
