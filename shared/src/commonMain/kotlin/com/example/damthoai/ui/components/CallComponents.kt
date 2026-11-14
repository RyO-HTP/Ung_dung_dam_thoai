package com.example.damthoai.ui.components

import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.example.damthoai.resources.*
import com.example.damthoai.data.Channel
import com.example.damthoai.hardware.HapticController
import com.example.damthoai.ui.screens.CallUiState
import com.example.damthoai.ui.screens.CallUiState.CallBusy
import com.example.damthoai.ui.screens.CallUiState.CallCut
import com.example.damthoai.ui.screens.CallUiState.CallOffline
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.CallPttEnd
import com.example.damthoai.ui.theme.CallPttGlow
import com.example.damthoai.ui.theme.CallPttHighlight
import com.example.damthoai.ui.theme.CallPttMid
import com.example.damthoai.ui.theme.CallPttRing
import com.example.damthoai.ui.theme.CallVolumeEnd
import com.example.damthoai.ui.theme.CallVolumeStart
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DangerText
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.PrimaryEnd
import com.example.damthoai.ui.theme.PrimaryStart
import com.example.damthoai.ui.theme.PureWhite
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
import com.example.damthoai.ui.theme.Transparent
import com.example.damthoai.ui.theme.cssGradient

@Composable
fun PttButton(
    state: CallUiState,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
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
                painter = painterResource(Res.drawable.ic_mic),
                contentDescription = label,
                tint = TextPrimary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = DamType.Button,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun VolumeRow(
    channel: Channel,
    volume: Float,
    modifier: Modifier = Modifier,
    isAudioDucked: Boolean = false,
    tag: String? = null,
    iconRes: DrawableResource = Res.drawable.ic_vol,
    onVolumeChange: (Float) -> Unit = {}
) {
    val isLowVolume = volume < 0.30f
    val percentColor = if (isLowVolume) TextSecondary.copy(alpha = 0.6f) else TextSecondary
    val isDucked = isAudioDucked || tag?.contains(other = "HẠ ÂM") == true

    val currentDensity = LocalDensity.current
    val cappedDensity = remember(currentDensity) {
        Density(density = currentDensity.density, fontScale = currentDensity.fontScale.coerceAtMost(1.15f))
    }

    CompositionLocalProvider(LocalDensity provides cappedDensity) {
        Column(
            modifier = modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = channel.name,
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    style = DamType.Body13.copy(fontWeight = FontWeight.SemiBold)
                )
                if (tag != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDucked) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_lock),
                                contentDescription = "Khóa âm lượng",
                                tint = AccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        // Nếu TagLabel báo lỗi, hãy đảm bảo bạn đã import hoặc khai báo nó ở file khác
                        TagLabel(text = tag, color = AccentCyan)
                    }
                } else {
                    Text(
                        text = "${(volume * 100).toInt()}%",
                        color = percentColor,
                        style = DamType.Muted
                    )
                }
            }

            // Xóa phần channel.speaker nếu data class Channel thật không có, hoặc giữ lại nếu có.
            // Text(
            //     text = channel.speaker,
            //     modifier = Modifier.padding(start = 24.dp, top = 1.dp, bottom = 2.dp),
            //     color = channel.speakerColor,
            //     style = DamType.Small
            // )

            VolumeSlider(
                value = volume,
                onValueChange = onVolumeChange,
                isLocked = isDucked,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Vị trí ngón tay trên thanh trượt → giá trị 0..1 (thanh chừa lề 8dp hai bên cho núm tròn). */
private fun androidx.compose.ui.input.pointer.PointerInputScope.sliderFraction(x: Float): Float {
    val inset = 8.dp.toPx()
    return ((x - inset) / (size.width - inset * 2f)).coerceIn(0f, 1f)
}

@Composable
private fun VolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false
) {
    val clampedValue = value.coerceIn(0f, 1f)
    // pointerInput(Unit) chỉ chạy một lần: phải đọc callback mới nhất qua rememberUpdatedState,
    // nếu không thao tác kéo sẽ gọi callback cũ của lần vẽ đầu tiên.
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val gestureModifier = if (!isLocked) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures { position -> currentOnValueChange(sliderFraction(position.x)) }
            }
            .pointerInput(Unit) {
                // Kéo NGANG: không tranh chấp với cuộn dọc của màn hình
                detectHorizontalDragGestures(
                    onDragStart = { position -> currentOnValueChange(sliderFraction(position.x)) },
                    onHorizontalDrag = { change, _ ->
                        currentOnValueChange(sliderFraction(change.position.x))
                        change.consume()
                    }
                )
            }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .height(40.dp) // Vùng chạm 40dp (thanh hiển thị vẫn 24dp) cho dễ bấm
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

            drawRoundRect(
                color = com.example.damthoai.ui.theme.CallVolumeInactive,
                topLeft = Offset(inset, centerY - radius),
                size = androidx.compose.ui.geometry.Size(totalTrackWidth, trackHeight),
                cornerRadius = CornerRadius(radius, radius)
            )

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
    iconRes: DrawableResource,
    color: Color,
    backgroundColor: Color,
    queuedCount: Int = 1,
    modifier: Modifier = Modifier
) {
    // Đảm bảo nếu iconRes bằng 0 thì dùng tạm một icon hợp lệ (ví dụ ic_vol hoặc ic_alert) để không bị crash
    val safeIconRes = iconRes

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
            painter = painterResource(safeIconRes),
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
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionItem(
            Res.drawable.ic_vol,
            "Loa ngoài",
            onClick = {
                HapticController.tick(haptic)
                onSelectSpeaker()
            },
            selected = speakerSelected
        )
        ActionItem(
            Res.drawable.ic_head,
            "Tai nghe",
            onClick = {
                HapticController.tick(haptic)
                onSelectHeadset()
            },
            selected = headsetSelected,
            enabled = headsetEnabled
        )
        ActionItem(
            Res.drawable.ic_phone,
            "Bỏ túi",
            onClick = {
                HapticController.tick(haptic)
                onNavigateToPocket()
            }
        )
        ActionItem(
            Res.drawable.ic_alert,
            "Khẩn cấp",
            onClick = {
                HapticController.tick(haptic)
                onNavigateToEmergency()
            },
            enabled = emergencyEnabled
        )
    }
}

@Composable
private fun ActionItem(
    iconRes: DrawableResource,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true
) {
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
@org.jetbrains.compose.ui.tooling.preview.Preview
@Composable
fun CallComponentsPreview() {
    // Hiển thị thử nút PTT
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.padding(16.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        PttButton(state = CallUiState.CallOffline)
    }
}