package com.example.damthoai.hardware

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Lọc riêng phím Volume Down để PTT có thể hoạt động khi Activity không ở foreground.
 *
 * ĐIỂM NỐI TV3: trạng thái bật dịch vụ nằm trong [AccessibilityStatus.isServiceEnabled].
 */
class VolumeKeyAccessibilityService : AccessibilityService() {

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN ||
            !HandsFreeSettings.volumeDownHoldEnabled.value ||
            (!PttController.backgroundSessionActive.value && !PttController.isCaptureActive.value)
        ) {
            return false
        }

        return try {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        PttController.onKeyPressed(PttSource.VOLUME_DOWN)
                    }
                    true
                }

                KeyEvent.ACTION_UP -> {
                    PttController.onKeyReleased(PttSource.VOLUME_DOWN)
                    true
                }

                else -> false
            }
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể chuyển phím Volume Down tới PttController", exception)
            false
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "PttController không thể xử lý phím Volume Down", exception)
            false
        }
    }

    /**
     * Dịch vụ chỉ đọc phím Volume, không xử lý sự kiện giao diện nên để trống.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        /* không dùng */
    }

    /**
     * Dịch vụ chỉ đọc phím Volume, không xử lý sự kiện giao diện nên để trống.
     */
    override fun onInterrupt() {
        /* không dùng */
    }

    private companion object {
        const val TAG = "VolumeKeyAccessibility"
    }
}
