package com.example.damthoai.ui.join
import com.example.damthoai.ui.theme.CameraBadgeBg
import com.example.damthoai.ui.theme.QrBlock
import com.example.damthoai.ui.theme.QrFrame


import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.R
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.components.SecondaryButton
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.LcdBg
import com.example.damthoai.ui.theme.LcdBorder
import com.example.damthoai.ui.theme.PillShape
import com.example.damthoai.ui.theme.PrimaryBrush
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun JoinChannelScreen(
    onBack: () -> Unit,
    onSuccessJoin: () -> Unit,
    onNavigateToCreateEvent: () -> Unit,
    modifier: Modifier = Modifier,
    onJoinError: () -> Unit = {},
    initialTab: Int = 0
) {
    JoinChannelScreenContent(
        onBack = onBack,
        onSuccessJoin = onSuccessJoin,
        onNavigateToCreateEvent = onNavigateToCreateEvent,
        onJoinError = onJoinError,
        modifier = modifier,
        initialTab = initialTab
    )
}

@Composable
private fun JoinChannelScreenContent(
    onBack: () -> Unit,
    onSuccessJoin: () -> Unit,
    onNavigateToCreateEvent: () -> Unit,
    onJoinError: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: Int = 0,
    cameraGranted: Boolean = true,
    initialPin: String = ""
) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    var isCameraGranted by remember { mutableStateOf(cameraGranted) }

    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Vào kênh", color = TextPrimary, style = DamType.Heading.copy(fontSize = 18.sp))
                }
                Spacer(Modifier.size(40.dp))
            }

            Spacer(Modifier.height(12.dp))
            JoinTabs(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it }
            )
            Spacer(Modifier.height(12.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (selectedTab == 0) {
                    QrScanTab(
                        isCameraGranted = isCameraGranted,
                        onRequestCamera = { isCameraGranted = true },
                        onSwitchToPin = { selectedTab = 1 },
                        onQrScanned = { onSuccessJoin() }
                    )
                } else {
                    PinInputTab(
                        initialPin = initialPin,
                        onPinSubmit = { pin ->
                            if (pin == "000000") onJoinError() else onSuccessJoin()
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Muốn tạo sự kiện mới? Tạo sự kiện  ›",
                color = AccentCyan,
                style = DamType.Button,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onNavigateToCreateEvent).padding(6.dp)
            )
        }
    }
}

@Composable
private fun JoinTabs(selectedTab: Int, onSelectTab: (Int) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(PillShape)
            .background(GlassBg)
            .border(1.dp, GlassBorder, PillShape)
            .padding(4.dp)
    ) {
        Row(Modifier.fillMaxSize()) {
            JoinTab(
                text = "Quét QR",
                icon = R.drawable.ic_qr,
                selected = selectedTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(0) }
            )
            JoinTab(
                text = "Nhập tần số",
                icon = R.drawable.ic_radio,
                selected = selectedTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(1) }
            )
        }
    }
}

@Composable
private fun JoinTab(text: String, icon: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.fillMaxSize()
            .clip(PillShape)
            .background(if (selected) PrimaryBrush else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (selected) TextPrimary else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(text, color = if (selected) TextPrimary else TextSecondary, style = DamType.Button)
    }
}

@Composable
private fun QrScanTab(
    isCameraGranted: Boolean,
    onRequestCamera: () -> Unit,
    onSwitchToPin: () -> Unit,
    onQrScanned: () -> Unit
) {
    if (!isCameraGranted) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(GlassBg)
                    .border(1.dp, GlassBorder, RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(98.dp).clip(CircleShape).background(CameraBadgeBg)
                            .border(1.dp, AccentCyan.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cam),
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Cần quyền Camera để quét QR",
                        color = TextPrimary,
                        style = DamType.Title.copy(fontSize = 20.sp),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Camera chỉ dùng để đọc mã QR của ban, app không ghi hình.",
                        color = TextSecondary,
                        style = DamType.Body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = "Cấp quyền Camera",
                onClick = onRequestCamera,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Nhập PIN thay thế",
                onClick = onSwitchToPin,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp
            )
        }
    } else {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(LcdBg)
                    .border(1.dp, GlassBorder, RoundedCornerShape(30.dp))
                    .clickable(onClick = onQrScanned),
                contentAlignment = Alignment.Center
            ) {
                QrScannerView(onQrScanned = { onQrScanned() }, modifier = Modifier.size(220.dp))
                ScannerCorners(Modifier.size(220.dp))
                ScanLine(Modifier.width(186.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Quét mã QR của ban. Tên, vai trò và kênh sẽ được gán tự động.",
                color = TextSecondary,
                style = DamType.Body.copy(fontSize = 13.sp, lineHeight = 19.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun ScannerCorners(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val length = 52.dp.toPx()
        val stroke = 4.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(AccentCyan, Offset(0f, 0f), Offset(length, 0f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(AccentCyan, Offset(0f, 0f), Offset(0f, length), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(AccentCyan, Offset(w, 0f), Offset(w - length, 0f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(AccentCyan, Offset(w, 0f), Offset(w, length), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(AccentCyan, Offset(0f, h), Offset(length, h), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(AccentCyan, Offset(0f, h), Offset(0f, h - length), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(AccentCyan, Offset(w, h), Offset(w - length, h), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(AccentCyan, Offset(w, h), Offset(w, h - length), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
private fun ScanLine(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan-line")
    val progress by transition.animateFloat(
        initialValue = -100f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "scan-progress"
    )
    Box(
        modifier = modifier.offset(y = progress.dp).height(4.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, AccentCyan, Color.Transparent)))
    )
}

@Composable
fun QrScannerView(onQrScanned: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clickable { onQrScanned("MOCK_QR_EVENT_889900") },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.width / 7f
            val blocks = listOf(
                Triple(0, 0, 3), Triple(4, 0, 1), Triple(1, 1, 1), Triple(2, 2, 1),
                Triple(4, 2, 2), Triple(0, 4, 1), Triple(2, 4, 2), Triple(5, 4, 1),
                Triple(0, 5, 2), Triple(3, 5, 1), Triple(5, 6, 1)
            )
            blocks.forEach { (x, y, width) ->
                drawRoundRect(
                    QrBlock,
                    topLeft = Offset(x * unit, y * unit),
                    size = androidx.compose.ui.geometry.Size(width * unit - 3.dp.toPx(), width * unit - 3.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                )
            }
            listOf(Offset(0f, 0f), Offset(size.width - 48.dp.toPx(), 0f), Offset(0f, size.height - 48.dp.toPx())).forEach {
                drawRoundRect(
                    color = QrFrame,
                    topLeft = it,
                    size = androidx.compose.ui.geometry.Size(48.dp.toPx(), 48.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun PinInputTab(initialPin: String, onPinSubmit: (String) -> Unit) {
    var pinDigits by remember { mutableStateOf(initialPin.take(6)) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(LcdBg)
                .border(1.dp, LcdBorder, RoundedCornerShape(22.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text("TẦN SỐ ẢO · PIN 6 SỐ", color = AccentCyan, style = DamType.Label.copy(fontSize = 11.sp))
            Spacer(Modifier.height(8.dp))
            val formattedPin = buildString {
                for (index in 0 until 6) {
                    if (index == 3) append("  ")
                    append(if (index < pinDigits.length) pinDigits[index] else if (index == pinDigits.length) "_" else " ")
                }
            }
            Text(
                text = formattedPin,
                color = AccentCyan,
                style = DamType.PinBig,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(12.dp))
        GlassKeypad(
            onDigitClick = { if (pinDigits.length < 6) pinDigits += it },
            onDeleteClick = { if (pinDigits.isNotEmpty()) pinDigits = pinDigits.dropLast(1) },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "Vào kênh",
            enabled = pinDigits.length == 6,
            onClick = { onPinSubmit(pinDigits) },
            modifier = Modifier.fillMaxWidth(),
            height = 54.dp
        )
    }
}

@Composable
private fun GlassKeypad(
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "⌫"))
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier.weight(1f).height(52.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (key.isNotEmpty()) GlassBg else Color.Transparent)
                            .border(if (key.isNotEmpty()) 1.dp else 0.dp, GlassBorder, RoundedCornerShape(22.dp))
                            .clickable(enabled = key.isNotEmpty()) {
                                if (key == "⌫") onDeleteClick() else onDigitClick(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "⌫") {
                            Icon(
                                painter = painterResource(R.drawable.ic_del),
                                contentDescription = "Xóa số",
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(key, color = TextPrimary, style = DamType.PinBig.copy(fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = 0.sp))
                        }
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun JoinQrPreview() {
    DamThoaiTheme {
        JoinChannelScreenContent({}, {}, {}, {}, initialTab = 0, cameraGranted = true)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun JoinQrNoCameraPreview() {
    DamThoaiTheme {
        JoinChannelScreenContent({}, {}, {}, {}, initialTab = 0, cameraGranted = false)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun JoinPinPreview() {
    DamThoaiTheme {
        JoinChannelScreenContent({}, {}, {}, {}, initialTab = 1, initialPin = "24082")
    }
}
