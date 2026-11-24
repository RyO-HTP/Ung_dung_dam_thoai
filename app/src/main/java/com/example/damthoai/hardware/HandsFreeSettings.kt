package com.example.damthoai.hardware

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lưu các lựa chọn rảnh tay và cung cấp StateFlow cho các điểm nối phần cứng/UI.
 *
 * ĐIỂM NỐI TV3: đọc các StateFlow để bind trạng thái cài đặt; riêng beepHapticEnabled là cờ
 * chung cho HapticController và RogerBeepPlayer.
 * ĐIỂM NỐI TV2: các cờ xử lý âm thanh chỉ được lưu tại đây, không áp dụng hiệu ứng âm thanh.
 */
object HandsFreeSettings {

    private const val TAG = "HandsFreeSettings"
    private const val PREFERENCES_NAME = "hands_free_settings"
    private const val KEY_HEADSET_BUTTON_ENABLED = "headset_button_enabled"
    private const val KEY_HEADSET_PTT_MODE = "headset_ptt_mode"
    private const val KEY_VOLUME_DOWN_HOLD_ENABLED = "volume_down_hold_enabled"
    private const val KEY_BEEP_HAPTIC_ENABLED = "beep_haptic_enabled"
    private const val KEY_EMERGENCY_ALERT_ENABLED = "emergency_alert_enabled"
    private const val KEY_NOISE_SUPPRESSION_ENABLED = "noise_suppression_enabled"
    private const val KEY_AUTO_MIC_GAIN_ENABLED = "auto_mic_gain_enabled"
    private const val KEY_DUCK_SECONDARY_ENABLED = "duck_secondary_enabled"

    private var preferences: SharedPreferences? = null

    private val _headsetButtonEnabled = MutableStateFlow(true)
    val headsetButtonEnabled: StateFlow<Boolean> = _headsetButtonEnabled.asStateFlow()

    private val _headsetPttMode = MutableStateFlow(HeadsetPttMode.TOGGLE)
    val headsetPttMode: StateFlow<HeadsetPttMode> = _headsetPttMode.asStateFlow()

    private val _volumeDownHoldEnabled = MutableStateFlow(true)
    val volumeDownHoldEnabled: StateFlow<Boolean> = _volumeDownHoldEnabled.asStateFlow()

    private val _beepHapticEnabled = MutableStateFlow(true)
    val beepHapticEnabled: StateFlow<Boolean> = _beepHapticEnabled.asStateFlow()

    private val _emergencyAlertEnabled = MutableStateFlow(true)
    val emergencyAlertEnabled: StateFlow<Boolean> = _emergencyAlertEnabled.asStateFlow()

    private val _noiseSuppressionEnabled = MutableStateFlow(true)
    val noiseSuppressionEnabled: StateFlow<Boolean> = _noiseSuppressionEnabled.asStateFlow()

    private val _autoMicGainEnabled = MutableStateFlow(true)
    val autoMicGainEnabled: StateFlow<Boolean> = _autoMicGainEnabled.asStateFlow()

    private val _duckSecondaryEnabled = MutableStateFlow(true)
    val duckSecondaryEnabled: StateFlow<Boolean> = _duckSecondaryEnabled.asStateFlow()

    /**
     * Khởi tạo từ application context; gọi một lần tại MainActivity.onCreate.
     */
    @Synchronized
    fun initialize(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            preferences = prefs
            _headsetButtonEnabled.value = prefs.getBoolean(KEY_HEADSET_BUTTON_ENABLED, true)
            _headsetPttMode.value = prefs.getString(KEY_HEADSET_PTT_MODE, HeadsetPttMode.TOGGLE.name)
                ?.let { stored ->
                    HeadsetPttMode.entries.firstOrNull { it.name == stored }
                } ?: HeadsetPttMode.TOGGLE
            _volumeDownHoldEnabled.value = prefs.getBoolean(KEY_VOLUME_DOWN_HOLD_ENABLED, true)
            _beepHapticEnabled.value = prefs.getBoolean(KEY_BEEP_HAPTIC_ENABLED, true)
            _emergencyAlertEnabled.value = prefs.getBoolean(KEY_EMERGENCY_ALERT_ENABLED, true)
            _noiseSuppressionEnabled.value = prefs.getBoolean(KEY_NOISE_SUPPRESSION_ENABLED, true)
            _autoMicGainEnabled.value = prefs.getBoolean(KEY_AUTO_MIC_GAIN_ENABLED, true)
            _duckSecondaryEnabled.value = prefs.getBoolean(KEY_DUCK_SECONDARY_ENABLED, true)
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể đọc cài đặt rảnh tay", exception)
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Cài đặt rảnh tay không thể khởi tạo", exception)
        }
    }

    fun setHeadsetButtonEnabled(enabled: Boolean) {
        _headsetButtonEnabled.value = enabled
        persist(KEY_HEADSET_BUTTON_ENABLED) { putBoolean(KEY_HEADSET_BUTTON_ENABLED, enabled) }
        if (!enabled && PttController.activeSource.value == PttSource.HEADSET) {
            PttController.onKeyReleased(PttSource.HEADSET)
        }
    }

    fun setHeadsetPttMode(mode: HeadsetPttMode) {
        if (_headsetPttMode.value != mode &&
            PttController.activeSource.value == PttSource.HEADSET
        ) {
            PttController.onKeyReleased(PttSource.HEADSET)
        }
        _headsetPttMode.value = mode
        persist(KEY_HEADSET_PTT_MODE) { putString(KEY_HEADSET_PTT_MODE, mode.name) }
    }

    fun setVolumeDownHoldEnabled(enabled: Boolean) {
        _volumeDownHoldEnabled.value = enabled
        persist(KEY_VOLUME_DOWN_HOLD_ENABLED) { putBoolean(KEY_VOLUME_DOWN_HOLD_ENABLED, enabled) }
        if (!enabled && PttController.activeSource.value == PttSource.VOLUME_DOWN) {
            PttController.onKeyReleased(PttSource.VOLUME_DOWN)
        }
    }

    fun setBeepHapticEnabled(enabled: Boolean) {
        _beepHapticEnabled.value = enabled
        persist(KEY_BEEP_HAPTIC_ENABLED) { putBoolean(KEY_BEEP_HAPTIC_ENABLED, enabled) }
    }

    fun setEmergencyAlertEnabled(enabled: Boolean) {
        _emergencyAlertEnabled.value = enabled
        persist(KEY_EMERGENCY_ALERT_ENABLED) { putBoolean(KEY_EMERGENCY_ALERT_ENABLED, enabled) }
    }

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        _noiseSuppressionEnabled.value = enabled
        persist(KEY_NOISE_SUPPRESSION_ENABLED) { putBoolean(KEY_NOISE_SUPPRESSION_ENABLED, enabled) }
    }

    fun setAutoMicGainEnabled(enabled: Boolean) {
        _autoMicGainEnabled.value = enabled
        persist(KEY_AUTO_MIC_GAIN_ENABLED) { putBoolean(KEY_AUTO_MIC_GAIN_ENABLED, enabled) }
    }

    fun setDuckSecondaryEnabled(enabled: Boolean) {
        _duckSecondaryEnabled.value = enabled
        persist(KEY_DUCK_SECONDARY_ENABLED) { putBoolean(KEY_DUCK_SECONDARY_ENABLED, enabled) }
    }

    private inline fun persist(key: String, editorAction: SharedPreferences.Editor.() -> Unit) {
        val prefs = preferences
        if (prefs == null) {
            Log.e(TAG, "Không thể lưu cài đặt '$key' trước khi initialize")
            return
        }
        try {
            prefs.edit().apply(editorAction).apply()
        } catch (exception: SecurityException) {
            Log.e(TAG, "Không thể lưu cài đặt '$key'", exception)
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Cài đặt '$key' không thể được lưu ở trạng thái hiện tại", exception)
        }
    }
}
