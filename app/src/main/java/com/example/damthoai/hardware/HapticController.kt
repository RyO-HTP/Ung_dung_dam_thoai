package com.example.damthoai.hardware

import android.view.HapticFeedbackConstants
import android.view.View

object HapticController {
    fun tick(view: View) {
        if (HandsFreeSettings.beepHapticEnabled.value) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}
