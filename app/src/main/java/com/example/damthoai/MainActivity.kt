/**
 * Khối KDoc tiếng Việt:
 * Activity chính của ứng dụng Đàm Thoại.
 * Khởi tạo chế độ Edge-to-Edge, cài đặt Theme/Layout,
 * và chuyển sự kiện phím Volume cứng tới bộ điều khiển PTT.
 */
package com.example.damthoai

import android.content.Intent
import android.os.Build
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.damthoai.hardware.AccessibilityStatus
import com.example.damthoai.hardware.audio.AudioRouteGuard
import com.example.damthoai.hardware.HandsFreeSession
import com.example.damthoai.hardware.HandsFreeSettings
import com.example.damthoai.hardware.HardwareKeyHandler
import com.example.damthoai.hardware.PttController
import com.example.damthoai.hardware.emergency.EmergencyAlertManager
import com.example.damthoai.hardware.emergency.EmergencyNotifier
import com.example.damthoai.permission.PermissionManager
import com.example.damthoai.ui.navigation.AdaptiveAppLayout
import com.example.damthoai.ui.theme.BgGradientTop
import com.example.damthoai.ui.theme.DamThoaiTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleEmergencyAction(intent)
        HandsFreeSettings.initialize(applicationContext)
        HandsFreeSession.initialize(this)
        AudioRouteGuard.start(applicationContext)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                EmergencyAlertManager.currentAlert.collect { alert ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        setShowWhenLocked(alert != null)
                        setTurnScreenOn(alert != null)
                    }
                }
            }
        }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEmergencyAction(intent)
    }

    private fun handleEmergencyAction(intent: Intent?) {
        when (intent?.action) {
            EmergencyNotifier.ACTION_ACKNOWLEDGE -> EmergencyAlertManager.acknowledge(this)
            EmergencyNotifier.ACTION_SNOOZE -> EmergencyAlertManager.snooze(this)
        }
    }

    /**
     * BẮT SỰ KIỆN PHÍM CỨNG NHẤN XUỐNG (Volume Up / Down).
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
        AccessibilityStatus.refresh(this)
    }

    override fun onDestroy() {
        AudioRouteGuard.stop()
        super.onDestroy()
    }
}