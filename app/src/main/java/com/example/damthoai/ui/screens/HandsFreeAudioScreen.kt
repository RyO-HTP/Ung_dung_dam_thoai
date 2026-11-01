package com.example.damthoai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.damthoai.R
import com.example.damthoai.hardware.AccessibilityStatus
import com.example.damthoai.hardware.HandsFreeSettings
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.KitSwitch
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.components.SectionLabel
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun HandsFreeAudioScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AccessibilityStatus.refresh(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        AccessibilityStatus.refresh(context)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val accessibilityEnabled by AccessibilityStatus.isServiceEnabled.collectAsState()
    val headsetButtonEnabled by HandsFreeSettings.headsetButtonEnabled.collectAsState()
    val volumeDownHoldEnabled by HandsFreeSettings.volumeDownHoldEnabled.collectAsState()
    val noiseSuppressionEnabled by HandsFreeSettings.noiseSuppressionEnabled.collectAsState()
    val autoMicGainEnabled by HandsFreeSettings.autoMicGainEnabled.collectAsState()
    val duckSecondaryEnabled by HandsFreeSettings.duckSecondaryEnabled.collectAsState()
    val beepHapticEnabled by HandsFreeSettings.beepHapticEnabled.collectAsState()
    val emergencyAlertEnabled by HandsFreeSettings.emergencyAlertEnabled.collectAsState()

    HandsFreeAudioContent(
        onBack = onBack,
        modifier = modifier,
        accessibilityEnabled = accessibilityEnabled,
        headsetButtonEnabled = headsetButtonEnabled,
        volumeDownHoldEnabled = volumeDownHoldEnabled,
        noiseSuppressionEnabled = noiseSuppressionEnabled,
        autoMicGainEnabled = autoMicGainEnabled,
        duckSecondaryEnabled = duckSecondaryEnabled,
        beepHapticEnabled = beepHapticEnabled,
        emergencyAlertEnabled = emergencyAlertEnabled,
        onOpenAccessibilitySettings = { AccessibilityStatus.openSettings(context) },
        onHeadsetButtonChange = HandsFreeSettings::setHeadsetButtonEnabled,
        onVolumeDownHoldChange = HandsFreeSettings::setVolumeDownHoldEnabled,
        onNoiseSuppressionChange = HandsFreeSettings::setNoiseSuppressionEnabled,
        onAutoMicGainChange = HandsFreeSettings::setAutoMicGainEnabled,
        onDuckSecondaryChange = HandsFreeSettings::setDuckSecondaryEnabled,
        onBeepHapticChange = HandsFreeSettings::setBeepHapticEnabled,
        onEmergencyAlertChange = HandsFreeSettings::setEmergencyAlertEnabled
    )
}

@Composable
private fun HandsFreeAudioContent(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    accessibilityEnabled: Boolean,
    headsetButtonEnabled: Boolean,
    volumeDownHoldEnabled: Boolean,
    noiseSuppressionEnabled: Boolean,
    autoMicGainEnabled: Boolean,
    duckSecondaryEnabled: Boolean,
    beepHapticEnabled: Boolean,
    emergencyAlertEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onHeadsetButtonChange: (Boolean) -> Unit,
    onVolumeDownHoldChange: (Boolean) -> Unit,
    onNoiseSuppressionChange: (Boolean) -> Unit,
    onAutoMicGainChange: (Boolean) -> Unit,
    onDuckSecondaryChange: (Boolean) -> Unit,
    onBeepHapticChange: (Boolean) -> Unit,
    onEmergencyAlertChange: (Boolean) -> Unit
) {
    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Rảnh tay và âm thanh",
                    color = TextPrimary,
                    style = DamType.Title.copy(fontSize = 21.sp, lineHeight = 26.sp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionLabel("RẢNH TAY")
                HandsFreeSettingRow(
                    title = "Nút Play/Pause tai nghe",
                    description = "Bấm để mở hoặc tắt mic, có tiếng bíp",
                    checked = headsetButtonEnabled,
                    onCheckedChange = onHeadsetButtonChange
                )
                HandsFreeSettingRow(
                    title = "Giữ phím giảm âm lượng",
                    description = "Nói khi tắt màn hình, thả ra để ngắt",
                    checked = volumeDownHoldEnabled,
                    onCheckedChange = onVolumeDownHoldChange
                )
                if (!accessibilityEnabled) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Cần bật Trợ năng",
                                color = TextPrimary,
                                style = DamType.Heading
                            )
                            Text(
                                text = "Để nhận phím âm lượng khi tắt màn hình",
                                color = TextSecondary,
                                style = DamType.Small
                            )
                            PrimaryButton(
                                text = "Mở cài đặt",
                                onClick = onOpenAccessibilitySettings,
                                modifier = Modifier.fillMaxWidth(),
                                height = 46.dp
                            )
                        }
                    }
                }
                SectionLabel("ÂM THANH VÀ CẢNH BÁO")
                HandsFreeSettingRow(
                    title = "Lọc ồn, khử vọng",
                    description = "Noise suppression, echo cancellation",
                    checked = noiseSuppressionEnabled,
                    onCheckedChange = onNoiseSuppressionChange
                )
                HandsFreeSettingRow(
                    title = "Tự cân bằng mic",
                    description = "Tự khuếch đại khi bạn nói nhỏ",
                    checked = autoMicGainEnabled,
                    onCheckedChange = onAutoMicGainChange
                )
                HandsFreeSettingRow(
                    title = "Hạ âm kênh phụ",
                    description = "Khi kênh ưu tiên có người nói",
                    checked = duckSecondaryEnabled,
                    onCheckedChange = onDuckSecondaryChange
                )
                HandsFreeSettingRow(
                    title = "Bíp và rung khi thao tác",
                    description = "Roger beep, phản hồi xúc giác",
                    checked = beepHapticEnabled,
                    onCheckedChange = onBeepHapticChange
                )
                HandsFreeSettingRow(
                    title = "Rung và nháy đèn khẩn cấp",
                    description = "Cho nơi quá ồn",
                    checked = emergencyAlertEnabled,
                    onCheckedChange = onEmergencyAlertChange
                )
            }
        }
    }
}

@Composable
private fun HandsFreeSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    style = DamType.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                )
                Text(text = description, color = TextSecondary, style = DamType.Small)
            }
            Spacer(Modifier.width(12.dp))
            KitSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HandsFreeAudioScreenPreview() {
    DamThoaiTheme {
        HandsFreeAudioContent(
            onBack = {},
            accessibilityEnabled = false,
            headsetButtonEnabled = true,
            volumeDownHoldEnabled = true,
            noiseSuppressionEnabled = true,
            autoMicGainEnabled = true,
            duckSecondaryEnabled = true,
            beepHapticEnabled = true,
            emergencyAlertEnabled = true,
            onOpenAccessibilitySettings = {},
            onHeadsetButtonChange = {},
            onVolumeDownHoldChange = {},
            onNoiseSuppressionChange = {},
            onAutoMicGainChange = {},
            onDuckSecondaryChange = {},
            onBeepHapticChange = {},
            onEmergencyAlertChange = {}
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HandsFreeAudioAccessibilityEnabledPreview() {
    DamThoaiTheme {
        HandsFreeAudioContent(
            onBack = {},
            accessibilityEnabled = true,
            headsetButtonEnabled = true,
            volumeDownHoldEnabled = true,
            noiseSuppressionEnabled = true,
            autoMicGainEnabled = true,
            duckSecondaryEnabled = true,
            beepHapticEnabled = true,
            emergencyAlertEnabled = true,
            onOpenAccessibilitySettings = {},
            onHeadsetButtonChange = {},
            onVolumeDownHoldChange = {},
            onNoiseSuppressionChange = {},
            onAutoMicGainChange = {},
            onDuckSecondaryChange = {},
            onBeepHapticChange = {},
            onEmergencyAlertChange = {}
        )
    }
}
