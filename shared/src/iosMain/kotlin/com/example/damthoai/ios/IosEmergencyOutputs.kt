package com.example.damthoai.ios

import com.example.damthoai.hardware.emergency.EmergencyAlert
import com.example.damthoai.hardware.emergency.EmergencyOutputs
import com.example.damthoai.hardware.emergency.VibrationStartResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.AVFoundation.*
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AudioToolbox.kSystemSoundID_Vibrate
import platform.UserNotifications.*

/**
 * iOS: rung lặp lại, nháy đèn flash và thông báo cho cảnh báo khẩn cấp.
 */
@OptIn(ExperimentalForeignApi::class)
class IosEmergencyOutputs : EmergencyOutputs {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var vibrationJob: Job? = null

    override fun startVibration(): VibrationStartResult {
        vibrationJob?.cancel()
        vibrationJob = scope.launch {
            while (isActive) {
                AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
                delay(VIBRATION_INTERVAL_MS)
            }
        }
        return VibrationStartResult(started = true)
    }

    override fun stopVibration() {
        vibrationJob?.cancel()
        vibrationJob = null
    }

    override suspend fun flash(onFlashingChanged: (Boolean) -> Unit, onUnavailable: (String) -> Unit): String? {
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (device == null || !device.hasTorch) return "Thiết bị không có đèn flash"
        try {
            while (true) {
                if (!setTorch(device, on = true)) return "Không bật được đèn flash"
                onFlashingChanged(true)
                delay(FLASH_ON_MS)
                setTorch(device, on = false)
                onFlashingChanged(false)
                delay(FLASH_OFF_MS)
            }
        } finally {
            setTorch(device, on = false)
            onFlashingChanged(false)
        }
    }

    override fun stopTorch() {
        AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)?.let { setTorch(it, on = false) }
    }

    private fun setTorch(device: AVCaptureDevice, on: Boolean): Boolean {
        if (!device.hasTorch) return false
        if (!device.lockForConfiguration(null)) return false
        return try {
            device.torchMode = if (on) AVCaptureTorchModeOn else AVCaptureTorchModeOff
            true
        } finally {
            device.unlockForConfiguration()
        }
    }

    override fun showNotification(alert: EmergencyAlert) {
        val content = UNMutableNotificationContent().apply {
            setTitle("CẢNH BÁO KHẨN CẤP")
            setBody("${alert.fromName} (${alert.fromRole}) đang gọi khẩn cấp")
            setSound(UNNotificationSound.defaultSound)
        }
        val request = UNNotificationRequest.requestWithIdentifier(NOTIFICATION_ID, content, null)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
    }

    override fun cancelNotification() {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(NOTIFICATION_ID))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(NOTIFICATION_ID))
    }

    private companion object {
        const val NOTIFICATION_ID = "damthoai_emergency"
        const val VIBRATION_INTERVAL_MS = 1_000L
        const val FLASH_ON_MS = 400L
        const val FLASH_OFF_MS = 400L
    }
}
