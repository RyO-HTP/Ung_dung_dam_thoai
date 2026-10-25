/**
 * Path: app/src/main/java/com/example/damthoai/MainActivity.kt
 *
 * Khối KDoc tiếng Việt:
 * Activity chính của ứng dụng Đàm Thoại.
 * Khởi tạo chế độ Edge-to-Edge, cài đặt Theme/Layout,
 * và đánh chặn sự kiện phím cứng Volume (Hardware Keys) biến phím Volume thành nút PTT.
 */
package com.example.damthoai

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import com.example.damthoai.hardware.HandsFreeSession
import com.example.damthoai.hardware.HardwareKeyHandler
import com.example.damthoai.hardware.PttController
import com.example.damthoai.permission.PermissionManager
import com.example.damthoai.ui.navigation.AdaptiveAppLayout
import com.example.damthoai.ui.theme.BgGradientTop
import com.example.damthoai.ui.theme.DamThoaiTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        HandsFreeSession.initialize(this)

        // Bật edge-to-edge tràn viền màn hình
        enableEdgeToEdge()

        // Cập nhật trạng thái quyền ban đầu
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

    /**
     * BẮT SỰ KIỆN PHÍM CỨNG NHẤN XUỐNG (Volume Up / Down).
     *
     * @param keyCode Mã phím bấm phần cứng.
     * @param event Chi tiết sự kiện phím.
     * @return `true` nếu phím được xử lý làm PTT ("nuốt" sự kiện), HĐH Android sẽ DỪNG
     *         xử lý tiếp và ÂM LƯỢNG MÁY SẼ KHÔNG BỊ THAY ĐỔI.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Ủy quyền xử lý phím cho HardwareKeyHandler (TV4)
        if (HardwareKeyHandler.handleKeyDown(keyCode, event)) {
            return true // Nuốt phím thành công -> Không đổi âm lượng
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * BẮT SỰ KIỆN PHÍM CỨNG THẢ RA.
     *
     * @param keyCode Mã phím bấm phần cứng.
     * @param event Chi tiết sự kiện phím.
     * @return `true` nếu phím được xử lý thả PTT ("nuốt" sự kiện).
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (HardwareKeyHandler.handleKeyUp(keyCode, event)) {
            return true // Nuốt phím thành công
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * AN TOÀN HỆ THỐNG: Ngắt Micro khi Activity tạm dừng, trừ khi capture hoặc phiên chạy nền đang hoạt động.
     */
    override fun onPause() {
        super.onPause()
        // Giữ PTT khi capture đang chạy hoặc TV2 đã đăng ký phiên chạy nền.
        if (!PttController.backgroundSessionActive.value && !HandsFreeSession.isRunning) {
            PttController.forceReleasePtt()
        }
    }

    override fun onResume() {
        super.onResume()
        // Cập nhật lại trạng thái quyền khi ứng dụng quay lại foreground
        PermissionManager.refreshPermissionStates(this, this)
    }
}
