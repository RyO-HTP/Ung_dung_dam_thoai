package com.example.damthoai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.example.damthoai.ui.components.SectionLabel
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.CueBorder
import com.example.damthoai.ui.theme.GlassBorder
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
                    style = DamType.Title21
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
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 14.dp,
                        vertical = 2.dp
                    )
                ) {
                    Column {
                        HandsFreeSettingRow(
                            iconRes = R.drawable.ic_head,
                            title = "Nút Play/Pause tai nghe",
                            description = "Bấm để mở hoặc tắt mic, có tiếng bíp",
                            checked = headsetButtonEnabled,
                            onCheckedChange = onHeadsetButtonChange
                        )
                        SettingsDivider()
                        HandsFreeSettingRow(
                            iconRes = R.drawable.ic_vol,
                            title = "Giữ phím giảm âm lượng",
                            description = "Nói khi tắt màn hình, thả ra để ngắt",
                            checked = volumeDownHoldEnabled,
                            onCheckedChange = onVolumeDownHoldChange
                        )
                    }
                }
                if (!accessibilityEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(AccentAmber.copy(alpha = 0.12f))
                            .border(1.dp, CueBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_alert),
                            contentDescription = null,
                            tint = AccentAmber,
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Cần bật Trợ năng",
                                color = TextPrimary,
                                style = DamType.Heading13_5
                            )
                            Text(
                                text = "Để nhận phím âm lượng khi tắt màn hình",
                                color = TextSecondary,
                                style = DamType.Small
                            )
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clickable(onClick = onOpenAccessibilitySettings),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(AccentAmber.copy(alpha = 0.16f))
                                        .border(1.dp, CueBorder, RoundedCornerShape(50))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Mở cài đặt",
                                        color = AccentAmber,
                                        style = DamType.Small11Bold
                                    )
                                }
                            }
                        }
                    }
                }
                SectionLabel("ÂM THANH VÀ CẢNH BÁO")
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 14.dp,
                        vertical = 2.dp
                    )
                ) {
                    Column {
                        HandsFreeSettingRow(
                            iconRes = R.drawable.ic_mic,
                            title = "Lọc ồn, khử vọng",
                            description = "Noise suppression, echo cancellation",
                            checked = noiseSuppressionEnabled,
                            onCheckedChange = onNoiseSuppressionChange
                        )
                        SettingsDivider()
                        HandsFreeSettingRow(
                            iconRes = R.drawable.ic_zap,
                            title = "Tự cân bằng mic",
                            description = "Tự khuếch đại khi bạn nói nhỏ",
                            checked = autoMicGainEnabled,
                            onCheckedChange = onAutoMicGainChange
                        )
                        SettingsDivider()
                        HandsFreeSettingRow(
                            iconRes = R.drawable.ic_vol,
                            title = "Hạ âm kênh phụ",
                            description = "Khi kênh ưu tiên có người nói",
                            checked = duckSecondaryEnabled,
                            onCheckedChange = onDuckSecondaryChange
                        )
                        SettingsDivider()
                        HandsFreeSettingRow(
                            iconRes = R.drawable.ic_bell,
                            title = "Bíp và rung khi thao tác",
                            description = "Roger beep, phản hồi xúc giác",
                            checked = beepHapticEnabled,
                            onCheckedChange = onBeepHapticChange
                        )
                        SettingsDivider()
                        HandsFreeSettingRow(
                            iconRes = R.drawable.ic_light,
                            title = "Rung và nháy đèn khẩn cấp",
                            description = "Cho nơi quá ồn",
                            checked = emergencyAlertEnabled,
                            onCheckedChange = onEmergencyAlertChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HandsFreeSettingRow(
    iconRes: Int,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                style = DamType.Body14SemiBold
            )
            Text(text = description, color = TextSecondary, style = DamType.Small)
        }
        Spacer(Modifier.width(12.dp))
        KitSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GlassBorder)
    )
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
