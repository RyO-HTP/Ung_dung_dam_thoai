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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.damthoai.R
import com.example.damthoai.data.fake.FakeVolumeChannel
import com.example.damthoai.ui.screens.CallUiState
import com.example.damthoai.ui.screens.CallUiState.CallBusy
import com.example.damthoai.ui.screens.CallUiState.CallCut
import com.example.damthoai.ui.screens.CallUiState.CallOffline
import com.example.damthoai.ui.theme.CallActionBg
import com.example.damthoai.ui.theme.CallActionBorder
import com.example.damthoai.ui.theme.CallPttEnd
import com.example.damthoai.ui.theme.CallPttGlow
import com.example.damthoai.ui.theme.CallPttHighlight
import com.example.damthoai.ui.theme.CallPttMid
import com.example.damthoai.ui.theme.CallPttRing
import com.example.damthoai.ui.theme.CallStatusOffline
import com.example.damthoai.ui.theme.CallVolumeEnd
import com.example.damthoai.ui.theme.CallVolumeStart
import com.example.damthoai.ui.theme.CallVolumeTrack
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
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
                    brush = Brush.radialGradient(
                        colors = listOf(CallPttGlow, Color.Transparent),
                        center = center.copy(y = center.y + 12.dp.toPx()),
                        radius = diameter / 2f + 30.dp.toPx()
                    ),
                    radius = diameter / 2f + 30.dp.toPx(),
                    center = center.copy(y = center.y + 12.dp.toPx())
                )
                drawCircle(
                    color = CallPttRing,
                    radius = diameter / 2f + 6.dp.toPx(),
                    center = center,
                    style = Stroke(width = 6.dp.toPx())
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

@Composable
fun VolumeRow(
    channel: FakeVolumeChannel,
    modifier: Modifier = Modifier,
    onVolumeChange: (Float) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CallActionBg)
            .border(1.dp, CallActionBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(channel.iconRes),
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = channel.name,
                modifier = Modifier.weight(1f),
                color = TextPrimary,
                style = DamType.Small
            )
            if (channel.tag != null) {
                TagLabel(text = channel.tag)
            } else {
                Text(channel.valueLabel, color = TextSecondary, style = DamType.Small)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(channel.speaker, color = channel.speakerColor, style = DamType.Small)
        }
        VolumeSlider(
            value = channel.volume,
            onValueChange = onVolumeChange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun VolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(18.dp)
            .pointerInput(onValueChange) {
                detectTapGestures { position ->
                    onValueChange((position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { position ->
                        onValueChange((position.x / size.width).coerceIn(0f, 1f))
                    },
                    onDrag = { change, _ ->
                        onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                        change.consume()
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val centerY = size.height / 2f
            val radius = 3.dp.toPx()
            drawLine(
                color = CallVolumeTrack,
                start = Offset(radius, centerY),
                end = Offset(size.width - radius, centerY),
                strokeWidth = 6.dp.toPx()
            )
            val knobX = radius + (size.width - radius * 2f) * value.coerceIn(0f, 1f)
            drawLine(
                brush = cssGradient(90f, CallVolumeStart, CallVolumeEnd),
                start = Offset(radius, centerY),
                end = Offset(knobX, centerY),
                strokeWidth = 6.dp.toPx()
            )
            drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(knobX, centerY))
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionItem(R.drawable.ic_vol, "Loa ngoài", onClick = {})
        ActionItem(R.drawable.ic_head, "Tai nghe", onClick = {})
        ActionItem(R.drawable.ic_phone, "Bỏ túi", onClick = onNavigateToPocket)
        ActionItem(R.drawable.ic_alert, "Khẩn cấp", onClick = onNavigateToEmergency)
    }
}

@Composable
private fun ActionItem(iconRes: Int, label: String, onClick: () -> Unit) {
    // Tuần 3: sửa - nút tròn 54dp, nhãn DamType.Small có cỡ 11.5sp.
    Column(
        modifier = Modifier
            .width(78.dp)
            .height(78.dp)
            .pointerInput(onClick) {
                detectTapGestures(onTap = { onClick() })
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(CallActionBg)
                .border(1.dp, CallActionBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = if (label == "Khẩn cấp") CallStatusOffline else TextPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = TextSecondary,
            style = DamType.Small,
            textAlign = TextAlign.Center
        )
    }
}
