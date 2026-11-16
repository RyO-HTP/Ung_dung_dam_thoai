package com.example.damthoai.ios

import com.example.damthoai.platform.PttTones
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AudioToolbox.AudioServicesPlaySystemSound

/** Âm báo PTT trên iOS bằng âm hệ thống (không cần file âm thanh kèm app). */
object IosPttTones : PttTones {
    /** Âm "begin_record" của iOS: tiếng bíp ngắn như khi bắt đầu ghi âm. */
    private const val SYSTEM_SOUND_BEGIN_RECORD = 1113u

    @OptIn(ExperimentalForeignApi::class)
    override fun playTalkPermit() {
        AudioServicesPlaySystemSound(SYSTEM_SOUND_BEGIN_RECORD)
    }
}
