/**
 * Khối KDoc tiếng Việt:
 * Activity chính của ứng dụng Đàm Thoại.
 * Khởi tạo chế độ Edge-to-Edge, cài đặt Theme/Layout,
 * và đánh chặn sự kiện phím cứng Volume / Tai nghe Bluetooth biến thành nút PTT.
 */
package com.example.damthoai

import android.content.Intent
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
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

    // [MỚI THÊM] Khai báo biến quản lý MediaSession cho tai nghe Bluetooth
    private var mediaSession: MediaSessionCompat? = null

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // [MỚI THÊM] Khởi tạo hệ thống bắt sự kiện phím tai nghe Bluetooth
        setupMediaSessionForBluetooth()

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
     * [MỚI THÊM] Hàm cấu hình MediaSession để giành quyền ưu tiên nhận sự kiện
     * nút bấm (Play/Pause/Call) từ tai nghe Bluetooth và có dây.
     */
    private fun setupMediaSessionForBluetooth() {
        mediaSession = MediaSessionCompat(this, "DamThoaiPTT").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

            // Giả lập trạng thái "Đang phát media" để HĐH ưu tiên gửi phím bấm về app này
            val state = PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()
            setPlaybackState(state)

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    // Trích xuất KeyEvent từ Intent của Bluetooth
                    val keyEvent = mediaButtonEvent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (keyEvent != null) {
                        when (keyEvent.action) {
                            KeyEvent.ACTION_DOWN -> {
                                HardwareKeyHandler.handleKeyDown(keyEvent.keyCode, keyEvent)
                            }
                            KeyEvent.ACTION_UP -> {
                                HardwareKeyHandler.handleKeyUp(keyEvent.keyCode, keyEvent)
                            }
                        }
                        return true // Báo cho HĐH là ứng dụng đã "nuốt" phím Media này
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })
            // Kích hoạt MediaSession
            isActive = true
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
    }

    /**
     * [MỚI THÊM] Giải phóng MediaSession khi thoát ứng dụng hoàn toàn
     */
    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.isActive = false
        mediaSession?.release()
    }
}