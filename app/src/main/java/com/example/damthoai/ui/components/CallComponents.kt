package com.example.damthoai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import com.example.damthoai.data.SpeakerInfo
import com.example.damthoai.R
import com.example.damthoai.data.fake.FakeVolumeChannel
import com.example.damthoai.hardware.HapticController
import com.example.damthoai.ui.screens.CallUiState
import com.example.damthoai.ui.screens.CallUiState.CallBusy
import com.example.damthoai.ui.screens.CallUiState.CallCut
import com.example.damthoai.ui.screens.CallUiState.CallOffline
import com.example.damthoai.ui.theme.CallPttEnd
import com.example.damthoai.ui.theme.CallPttGlow
import com.example.damthoai.ui.theme.CallPttHighlight
import com.example.damthoai.ui.theme.CallPttMid
import com.example.damthoai.ui.theme.CallPttRing
import com.example.damthoai.ui.theme.CallVolumeEnd
import com.example.damthoai.ui.theme.CallVolumeStart
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DangerText
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.PrimaryEnd
import com.example.damthoai.ui.theme.PrimaryStart
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
import com.example.damthoai.ui.theme.PureWhite
import com.example.damthoai.ui.theme.Transparent
import com.example.damthoai.ui.theme.cssGradient

@Composable
fun PttButton(
    size: Dp,
    state: CallUiState,
    modifier: Modifier = Modifier,
    onPress: () -> Unit = {},
    onRelease: (Boolean) -> Unit = {}
) {
    val enabled = state != CallBusy && state != CallCut
    val label = when (state) {
        CallOffline -> "GIỮ ĐỂ GHI"
        CallBusy -> "KÊNH ĐANG BẬN"
        CallCut -> "ĐANG BỊ CHIẾM QUYỀN"
        CallUiState.CallTalk, CallUiState.CallTake -> "ĐANG PHÁT"
        else -> "GIỮ ĐỂ NÓI"
    }
    val pointerModifier = if (enabled) {
        Modifier.pointerInput(enabled) {
            detectTapGestures(
                onPress = {
                    onPress()
                    var wasReleased = false
                    try {
                        wasReleased = tryAwaitRelease()
                    } finally {
                        onRelease(wasReleased)
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size + 84.dp)
            .drawBehind {
                val diameter = size.toPx()
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                drawCircle(
                    color = CallPttGlow,
                    radius = diameter / 2f + 30.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = CallPttRing,
                    radius = diameter / 2f + 14.dp.toPx(),
                    center = center
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to CallPttHighlight,
                                0.55f to CallPttMid,
                                1f to CallPttEnd
                            ),
                            center = Offset(this.size.width * 0.35f, this.size.height * 0.30f),
                            radius = this.size.minDimension * 0.9f
                        )
                    )
                }
                .then(pointerModifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = label,
                tint = TextPrimary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(6.dp))
            // Tuần 3: sửa - dùng cỡ chữ đầy đủ từ DamType.Button.
            Text(
                text = label,
                style = DamType.Button,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun getSpeakerStatusLabel(speakerName: String?, fallbackSpeaker: String): String {
    if (!speakerName.isNullOrBlank()) {
        return if (speakerName.endsWith("đang nói")) speakerName else "$speakerName đang nói"
    }
    val raw = fallbackSpeaker.trim()
    if (raw.isBlank() || raw.equals("yên lặng", ignoreCase = true)) {
        return "Yên lặng"
    }
    return raw
}

@Composable
fun VolumeRow(
    channel: FakeVolumeChannel,
    modifier: Modifier = Modifier,
    speakerInfo: SpeakerInfo? = null,
    onVolumeChange: (Float) -> Unit = {}
) {
    val isLowVolume = channel.volume < 0.30f
    val percentColor = if (isLowVolume) TextSecondary.copy(alpha = 0.6f) else TextSecondary
    val isDucked = channel.isDucked || channel.tag?.contains("HẠ ÂM") == true
    val statusText = getSpeakerStatusLabel(speakerInfo?.name, channel.speaker)
    val isSpeaking = speakerInfo != null || channel.speaker.contains("đang nói", ignoreCase = true)
    val isActivelySpeaking = isSpeaking && !statusText.contains("không ai", ignoreCase = true) && !statusText.contains("yên lặng", ignoreCase = true) && !statusText.contains("không có", ignoreCase = true)

    val currentDensity = LocalDensity.current
    val cappedDensity = remember(currentDensity) {
        Density(density = currentDensity.density, fontScale = currentDensity.fontScale.coerceAtMost(1.15f))
    }

    CompositionLocalProvider(LocalDensity provides cappedDensity) {
        Column(
            modifier = modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(channel.iconRes),
                    contentDescription = null,
                    tint = if (isActivelySpeaking) AccentCyan else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = channel.name,
                    modifier = Modifier.weight(1f),
                    color = if (isActivelySpeaking) AccentCyan else TextPrimary,
                    style = DamType.Body13.copy(fontWeight = FontWeight.SemiBold)
                )
                if (channel.tag != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDucked) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lock),
                                contentDescription = "Khóa âm lượng",
                                tint = AccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        TagLabel(text = channel.tag, color = AccentCyan)
                    }
                } else {
                    Text(
                        text = "${(channel.volume * 100).toInt()}%",
                        color = percentColor,
                        style = DamType.Muted
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusText,
                    color = channel.speakerColor,
                    style = DamType.Small
                )
                if (isActivelySpeaking) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_vol),
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            VolumeSlider(
                value = channel.volume,
                onValueChange = onVolumeChange,
                isLocked = isDucked,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun VolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false
) {
    val clampedValue = value.coerceIn(0f, 1f)
    val gestureModifier = if (!isLocked) {
        Modifier
            .pointerInput(onValueChange) {
                detectTapGestures { position ->
                    val inset = 8.dp.toPx()
                    onValueChange(
                        ((position.x - inset) / (size.width - inset * 2f)).coerceIn(0f, 1f)
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { position ->
                        val inset = 8.dp.toPx()
                        onValueChange(
                            ((position.x - inset) / (size.width - inset * 2f)).coerceIn(0f, 1f)
                        )
                    },
                    onDrag = { change, _ ->
                        val inset = 8.dp.toPx()
                        onValueChange(
                            ((change.position.x - inset) / (size.width - inset * 2f))
                                .coerceIn(0f, 1f)
                        )
                        change.consume()
                    }
                )
            }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(24.dp)
            .semantics(mergeDescendants = true) {
                stateDescription = "âm lượng ${(clampedValue * 100).toInt()} phần trăm"
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(clampedValue, 0f..1f)
            }
            .then(gestureModifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val centerY = size.height / 2f
            val inset = 8.dp.toPx()
            val trackHeight = 6.dp.toPx()
            val radius = trackHeight / 2f
            val totalTrackWidth = size.width - inset * 2f

            // Unfilled background track
            drawRoundRect(
                color = com.example.damthoai.ui.theme.CallVolumeInactive,
                topLeft = Offset(inset, centerY - radius),
                size = androidx.compose.ui.geometry.Size(totalTrackWidth, trackHeight),
                cornerRadius = CornerRadius(radius, radius)
            )

            // Filled track from start to knob position
            val filledWidth = totalTrackWidth * clampedValue
            if (filledWidth > 0f) {
                val alpha = (0.55f + 0.45f * clampedValue).coerceIn(0.55f, 1.0f)
                if (isLocked) {
                    drawRoundRect(
                        color = com.example.damthoai.ui.theme.CallDuckedFill,
                        topLeft = Offset(inset, centerY - radius),
                        size = androidx.compose.ui.geometry.Size(filledWidth, trackHeight),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                } else {
                    drawRoundRect(
                        brush = cssGradient(90f, CallVolumeStart, CallVolumeEnd),
                        topLeft = Offset(inset, centerY - radius),
                        size = androidx.compose.ui.geometry.Size(filledWidth, trackHeight),
                        cornerRadius = CornerRadius(radius, radius),
                        alpha = alpha
                    )
                }
            }

            val knobX = inset + filledWidth
            drawCircle(
                color = if (isLocked) PureWhite.copy(alpha = 0.75f) else PureWhite,
                radius = 8.dp.toPx(),
                center = Offset(knobX, centerY)
            )
        }
    }
}

@Composable
fun CallStatusBanner(
    state: CallUiState,
    title: String,
    description: String,
    iconRes: Int,
    color: Color,
    backgroundColor: Color,
    queuedCount: Int = 1,
    modifier: Modifier = Modifier
) {
    // Tuần 3: sửa - dùng typography DamType thay cho cỡ chữ thu nhỏ.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = DamType.Heading, color = color)
            Text(
                text = if (state == CallOffline) "$description · $queuedCount tin chờ gửi" else description,
                style = DamType.Small,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun CallBottomActions(
    onNavigateToPocket: () -> Unit,
    onNavigateToEmergency: () -> Unit,
    modifier: Modifier = Modifier,
    speakerSelected: Boolean = false,
    headsetSelected: Boolean = false,
    headsetEnabled: Boolean = true,
    emergencyEnabled: Boolean = true,
    onSelectSpeaker: () -> Unit = {},
    onSelectHeadset: () -> Unit = {}
) {
    val view = LocalView.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionItem(
            R.drawable.ic_vol,
            "Loa ngoài",
            onClick = {
                HapticController.tick(view)
                onSelectSpeaker()
            },
            selected = speakerSelected
        )
        ActionItem(
            R.drawable.ic_head,
            "Tai nghe",
            onClick = {
                HapticController.tick(view)
                onSelectHeadset()
            },
            selected = headsetSelected,
            enabled = headsetEnabled
        )
        ActionItem(
            R.drawable.ic_phone,
            "Bỏ túi",
            onClick = {
                HapticController.tick(view)
                onNavigateToPocket()
            }
        )
        ActionItem(
            R.drawable.ic_alert,
            "Khẩn cấp",
            onClick = {
                HapticController.tick(view)
                onNavigateToEmergency()
            },
            enabled = emergencyEnabled
        )
    }
}

@Composable
private fun ActionItem(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    // Tuần 3: sửa - nút tròn 54dp, nhãn DamType.Small có cỡ 11.5sp.
    Column(
        modifier = Modifier
            .width(78.dp)
            .height(78.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .pointerInput(onClick, enabled) {
                if (enabled) detectTapGestures(onTap = { onClick() })
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    brush = when {
                        selected -> cssGradient(135f, PrimaryStart, PrimaryEnd)
                        label == "Khẩn cấp" -> SolidColor(AccentRed.copy(alpha = 0.25f))
                        else -> SolidColor(GlassBg)
                    }
                )
                .border(
                    1.dp,
                    if (selected || label == "Khẩn cấp") Transparent else GlassBorder,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = when {
                    label == "Khẩn cấp" -> DangerText
                    selected -> TextPrimary
                    else -> TextPrimary
                },
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = TextSecondary,
            style = DamType.Small,
            textAlign = TextAlign.Center
        )
    }
}
