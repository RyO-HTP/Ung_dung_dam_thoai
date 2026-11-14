package com.example.damthoai.hardware

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** Rung nhẹ khi bấm PTT (tôn trọng cài đặt "Tiếng bíp & rung"). Dùng LocalHapticFeedback.current. */
object HapticController {
    fun tick(haptic: HapticFeedback) {
        if (HandsFreeSettings.beepHapticEnabled.value) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}
