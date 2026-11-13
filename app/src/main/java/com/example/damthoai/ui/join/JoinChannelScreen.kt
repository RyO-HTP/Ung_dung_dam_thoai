package com.example.damthoai.ui.join

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.damthoai.R
import com.example.damthoai.permission.AppPermission
import com.example.damthoai.permission.PermissionManager
import com.example.damthoai.permission.PermissionState
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.ButtonShape
import com.example.damthoai.ui.theme.CameraBadgeBg
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.LcdBg
import com.example.damthoai.ui.theme.LcdBorder
import com.example.damthoai.ui.theme.PillShape
import com.example.damthoai.ui.theme.PrimaryBrush
import com.example.damthoai.ui.theme.QrBlock
import com.example.damthoai.ui.theme.QrFrame
import com.example.damthoai.ui.theme.TagCyanBg
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
import com.example.damthoai.ui.theme.Transparent

/**
 * Interface giải mã QR cho ứng dụng.
 * ĐIỂM NỐI: cài đặt thật bằng ML Kit ở PROMPT 1B
 */
interface QrDecoder {
    suspend fun decode(uri: android.net.Uri): String?
}

/**
 * Mock implementation của QrDecoder.
 * ĐIỂM NỐI: cài đặt thật bằng ML Kit ở PROMPT 1B
 */
class MockQrDecoder : QrDecoder {
    override suspend fun decode(uri: android.net.Uri): String = "MOCK_QR_EVENT_889900"
}

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
    cameraGranted: Boolean? = null,
    initialPin: String = ""
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var selectedTab by remember { mutableStateOf(initialTab) }
    var realCameraState by remember {
        mutableStateOf(PermissionManager.checkPermissionState(context, AppPermission.CAMERA, activity))
    }
    var hasBeenRequested by remember { mutableStateOf(false) }

    val qrScanPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                realCameraState = PermissionManager.checkPermissionState(context, AppPermission.CAMERA, activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasBeenRequested = true
        realCameraState = PermissionManager.checkPermissionState(context, AppPermission.CAMERA, activity)
    }

    val isCameraGranted = cameraGranted ?: (realCameraState == PermissionState.GRANTED)
    val isPermanentlyDenied = realCameraState == PermissionState.PERMANENTLY_DENIED

    val handleScanFrameClick: () -> Unit = {
        if (isCameraGranted) {
            onSuccessJoin()
        } else if (isPermanentlyDenied) {
            PermissionManager.openAppSettings(context)
        } else {
            PermissionManager.markRequested(context, AppPermission.CAMERA)
            permissionLauncher.launch(qrScanPermissions)
        }
    }

    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Vào kênh", color = TextPrimary, style = DamType.Heading18)
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
                        hasBeenRequested = hasBeenRequested,
                        isPermanentlyDenied = isPermanentlyDenied,
                        onScanFrameClick = handleScanFrameClick,
                        onOpenSettings = { PermissionManager.openAppSettings(context) },
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

            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Bạn là ban tổ chức?",
                    color = TextSecondary,
                    style = DamType.Muted,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                OutlinedCyanButton(
                    text = "Tạo sự kiện mới",
                    onClick = onNavigateToCreateEvent,
                    iconRes = R.drawable.ic_plus,
                    modifier = Modifier.fillMaxWidth(),
                    height = 54.dp
                )
            }
        }
    }
}

@Composable
private fun OutlinedCyanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
    iconRes: Int? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = ButtonShape
    val backgroundColor = if (isPressed) AccentCyan.copy(alpha = 0.15f) else TagCyanBg

    Box(
        modifier = modifier
            .heightIn(min = maxOf(height, 54.dp))
            .clip(shape)
            .background(backgroundColor)
            .border(1.5.dp, AccentCyan, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = DamType.Button,
                color = AccentCyan,
                textAlign = TextAlign.Center
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
            .background(if (selected) PrimaryBrush else Brush.linearGradient(listOf(Transparent, Transparent)))
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
    hasBeenRequested: Boolean,
    isPermanentlyDenied: Boolean,
    onScanFrameClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onQrScanned: () -> Unit
) {
    if (isCameraGranted) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Quét mã QR của bạn.\nTên, vai trò và kênh sẽ được gán tự động.",
                color = TextSecondary,
                style = DamType.Body13_5Line20,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.85f)
            )
        }
    } else {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(GlassBg)
                    .border(1.dp, GlassBorder, RoundedCornerShape(30.dp))
                    .clickable(onClick = onScanFrameClick),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Box(
                        Modifier
                            .size(98.dp)
                            .clip(CircleShape)
                            .background(CameraBadgeBg)
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
                        style = DamType.Title20,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    when {
                        isPermanentlyDenied -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Quyền camera đang bị tắt",
                                    color = TextSecondary,
                                    style = DamType.Body14Line21,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Mở cài đặt",
                                    color = AccentCyan,
                                    style = DamType.Body14Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(onClick = onOpenSettings)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                        hasBeenRequested -> {
                            Text(
                                "Cần quyền camera để quét mã. Chạm để thử lại.",
                                color = TextSecondary,
                                style = DamType.Body14Line21,
                                textAlign = TextAlign.Center
                            )
                        }
                        else -> {
                            Text(
                                "Camera chỉ dùng để đọc mã QR của bạn, app không ghi hình.\nChạm để cho phép camera",
                                color = TextSecondary,
                                style = DamType.Body14Line21,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Chạm vào khung phía trên để cấp quyền và quét mã QR.",
                color = TextSecondary,
                style = DamType.Body13_5Line20,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.85f)
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
            .background(Brush.horizontalGradient(listOf(Transparent, AccentCyan, Transparent)))
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
            Text("TẦN SỐ ẢO · PIN 6 SỐ", color = AccentCyan, style = DamType.Label)
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
                            .background(if (key.isNotEmpty()) GlassBg else Transparent)
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
                            Text(key, color = TextPrimary, style = DamType.PinKey)
                        }
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun JoinQrSmallScreenPreview() {
    DamThoaiTheme {
        JoinChannelScreenContent({}, {}, {}, {}, initialTab = 0, cameraGranted = true)
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