package com.example.damthoai.ios

import com.example.damthoai.hardware.PttController
import com.example.damthoai.hardware.PttSource

/*
 * Các hàm Swift gọi vào Kotlin (Swift thấy là IosEntryPointsKt.xxx()).
 * Giữ ở dạng hàm đơn giản để Swift không phải đọc StateFlow / enum Kotlin.
 */

/** Nút Play/Pause tai nghe: iOS không phân biệt nhấn-giữ, nên bấm để bật / bấm lại để tắt mic. */
fun onHeadsetPlayPause() {
    if (PttController.isPttActive.value) {
        PttController.onKeyReleased(PttSource.HEADSET)
    } else {
        PttController.onKeyPressed(PttSource.HEADSET)
    }
}

/** Người dùng bấm nút nói trên giao diện PushToTalk của hệ thống (màn hình khoá / Dynamic Island). */
fun onSystemPushToTalkBegin() = PttController.onKeyPressed(PttSource.SCREEN_BUTTON)

/** Người dùng thả nút nói của hệ thống, hoặc hệ thống kết thúc lượt nói. */
fun onSystemPushToTalkEnd() = PttController.onKeyReleased(PttSource.SCREEN_BUTTON)

/** Hệ thống buộc dừng (cuộc gọi đến, mất quyền micro...): nhả mic an toàn. */
fun onSystemForceReleasePtt() = PttController.forceReleasePtt()
