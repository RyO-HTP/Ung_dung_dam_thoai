package com.example.damthoai.ui.screens

import org.jetbrains.compose.resources.DrawableResource
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
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.damthoai.resources.*
import com.example.damthoai.platform.LocalPlatformUi
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
    val platformUi = LocalPlatformUi.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                platformUi.refreshVolumeKeyService()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        platformUi.refreshVolumeKeyService()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val accessibilityEnabled by platformUi.volumeKeyServiceEnabled.collectAsState()
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
        supportsVolumeKey = platformUi.supportsVolumeKeyPtt,
        headsetButtonEnabled = headsetButtonEnabled,
        volumeDownHoldEnabled = volumeDownHoldEnabled,
        noiseSuppressionEnabled = noiseSuppressionEnabled,
        autoMicGainEnabled = autoMicGainEnabled,
        duckSecondaryEnabled = duckSecondaryEnabled,
        beepHapticEnabled = beepHapticEnabled,
        emergencyAlertEnabled = emergencyAlertEnabled,
        onOpenAccessibilitySettings = { platformUi.openVolumeKeyServiceSettings() },
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
    /** iOS không cho dùng phím âm lượng làm PTT: ẩn tuỳ chọn và cảnh báo Trợ năng. */
    supportsVolumeKey: Boolean = true,
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
                            iconRes = Res.drawable.ic_head,
                            title = "Nút Play/Pause tai nghe",
                            description = "Bấm để mở hoặc tắt mic, có tiếng bíp",
                            checked = headsetButtonEnabled,
                            onCheckedChange = onHeadsetButtonChange
                        )
                        if (supportsVolumeKey) {
                            SettingsDivider()
                            HandsFreeSettingRow(
                                iconRes = Res.drawable.ic_vol,
                                title = "Giữ phím giảm âm lượng",
                                description = "Nói khi tắt màn hình, thả ra để ngắt",
                                checked = volumeDownHoldEnabled,
                                onCheckedChange = onVolumeDownHoldChange
                            )
                        }
                    }
                }
                if (supportsVolumeKey && !accessibilityEnabled) {
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
                            painter = painterResource(Res.drawable.ic_alert),
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
                            iconRes = Res.drawable.ic_mic,
                            title = "Lọc ồn, khử vọng",
                            description = "Giảm tiếng ồn nền và tiếng vọng",
                            checked = noiseSuppressionEnabled,
                            onCheckedChange = onNoiseSuppressionChange
                        )
                        SettingsDivider()
                        HandsFreeSettingRow(
                            iconRes = Res.drawable.ic_zap,
                            title = "Tự cân bằng mic",
                            description = "Tự khuếch đại khi bạn nói nhỏ",
                            checked = autoMicGainEnabled,
                            onCheckedChange = onAutoMicGainChange
                        )
                        SettingsDivider()
                        HandsFreeSettingRow(
                            iconRes = Res.drawable.ic_vol,
                            title = "Hạ âm kênh phụ",
                            description = "Khi kênh ưu tiên có người nói",
                            checked = duckSecondaryEnabled,
                            onCheckedChange = onDuckSecondaryChange
                        )
                        SettingsDivider()
                        HandsFreeSettingRow(
                            iconRes = Res.drawable.ic_bell,
                            title = "Bíp và rung khi thao tác",
                            description = "Tiếng bíp khi thả nút nói, rung phản hồi",
                            checked = beepHapticEnabled,
                            onCheckedChange = onBeepHapticChange
                        )
                        SettingsDivider()
                        HandsFreeSettingRow(
                            iconRes = Res.drawable.ic_light,
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
    iconRes: DrawableResource,
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

@Preview
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

@Preview
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
