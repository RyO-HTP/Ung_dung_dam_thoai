package com.example.damthoai.hardware.emergency

import android.content.Context

/** Android: nối [EmergencyAlertManager] dùng chung với bộ rung, đèn flash và thông báo full-screen. */
class AndroidEmergencyOutputs(context: Context) : EmergencyOutputs {
    private val appContext = context.applicationContext

    override fun startVibration(): VibrationStartResult {
        val result = EmergencyVibrator.start(appContext)
        return VibrationStartResult(result.started, result.unavailableReason)
    }

    override fun stopVibration() = EmergencyVibrator.stop()

    override suspend fun flash(onFlashingChanged: (Boolean) -> Unit, onUnavailable: (String) -> Unit): String? =
        EmergencyTorch.flash(appContext, onFlashingChanged, onUnavailable)

    override fun stopTorch() = EmergencyTorch.stop()

    override fun showNotification(alert: EmergencyAlert) = EmergencyNotifier.showIfAllowed(appContext, alert)

    override fun cancelNotification() = EmergencyNotifier.cancel(appContext)
}
