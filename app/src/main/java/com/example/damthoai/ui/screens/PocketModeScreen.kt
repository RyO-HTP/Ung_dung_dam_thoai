package com.example.damthoai.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import com.example.damthoai.R
import com.example.damthoai.hardware.PttController
import com.example.damthoai.hardware.PttSource
import com.example.damthoai.ui.components.BgVariant
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.PttButton
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.CallUnlockTrack
import com.example.damthoai.ui.theme.CallUnlockInk
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun PocketModeScreen(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var locked by remember { mutableStateOf(true) }
    var pttBounds by remember { mutableStateOf<Rect?>(null) }
    var unlockBounds by remember { mutableStateOf<Rect?>(null) }
    // Tuần 3: sửa - đổi tọa độ chạm cục bộ sang root trước khi kiểm tra hitbox.
    var pointerInputOrigin by remember { mutableStateOf(Offset.Zero) }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val pttHitRadiusPx = with(LocalDensity.current) { 125.dp.toPx() }

    BackHandler(enabled = locked) {}

    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DamThoaiBackground(variant = BgVariant.Pocket) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(locked, pttBounds, unlockBounds, pointerInputOrigin) {
                    if (locked) {
                        awaitPointerEventScope {
                            while (true) {
                                // Tuần 3: sửa - để các nút con xử lý chạm trước lớp chặn.
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                event.changes.forEach { change ->
                                    val positionInRoot = change.position + pointerInputOrigin
                                    val pttCenter = pttBounds?.center
                                    val dx = pttCenter?.let { positionInRoot.x - it.x }
                                    val dy = pttCenter?.let { positionInRoot.y - it.y }
                                    val hitsPtt = dx != null && dy != null &&
                                        dx * dx + dy * dy <= pttHitRadiusPx * pttHitRadiusPx
                                    val isAllowedTouch = hitsPtt ||
                                        unlockBounds?.contains(positionInRoot) == true
                                    if (change.pressed && !isAllowedTouch) change.consume()
                                }
                            }
                        }
                    }
                }
                .onGloballyPositioned { pointerInputOrigin = it.positionInRoot() }
        ) {
            PocketModeContent(
                locked = locked,
                onUnlockDragComplete = {
                    locked = false
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUnlock()
                },
                modifier = Modifier.fillMaxSize(),
                onPttBoundsChanged = { pttBounds = it },
                onUnlockBoundsChanged = { unlockBounds = it }
            )
        }
    }
}

@Composable
private fun PocketModeContent(
    locked: Boolean,
    onUnlockDragComplete: () -> Unit,
    modifier: Modifier = Modifier,
    onPttBoundsChanged: (Rect) -> Unit = {},
    onUnlockBoundsChanged: (Rect) -> Unit = {}
) {
    var isTalking by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassBg)
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (locked) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(7.dp))
                }
                Text(
                    if (locked) "Đã khóa chống chạm" else "Đã mở khóa",
                    color = TextPrimary,
                    style = DamType.Small
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Hậu cần – An ninh",
                color = TextPrimary,
                style = DamType.Heading.copy(fontSize = 20.sp)
            )
        }
        PttButton(
            size = 250.dp,
            state = if (isTalking) CallUiState.CallTalk else CallUiState.CallIdle,
            modifier = Modifier.onGloballyPositioned {
                onPttBoundsChanged(it.boundsInRoot())
            },
            onPress = {
                PttController.onKeyPressed(PttSource.SCREEN_BUTTON)
                isTalking = PttController.activeSource.value == PttSource.SCREEN_BUTTON
            },
            onRelease = {
                PttController.onKeyReleased(PttSource.SCREEN_BUTTON)
                isTalking = false
            }
        )
        if (locked) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Rung nhẹ và bíp khi mở mic",
                    color = TextSecondary,
                    style = DamType.Muted,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                PocketUnlockSlider(
                    locked = true,
                    onUnlock = onUnlockDragComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned {
                            onUnlockBoundsChanged(it.boundsInRoot())
                        }
                )
            }
        } else {
            Text(
                "Đã mở khóa",
                color = AccentCyan,
                style = DamType.Chip,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PocketUnlockSlider(
    locked: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableStateOf(0f) }
    var trackWidth by remember { mutableStateOf(0) }
    // Tuần 3: sửa - giữ núm sát ngón tay lúc kéo, dùng animation khi trả về.
    var isDragging by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "unlockProgress")
    val knobProgress = if (isDragging) progress else animatedProgress
    val knobSize = 48.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val knobWidthPx = with(density) { knobSize.roundToPx() }

    Box(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(CallUnlockTrack)
            .onSizeChanged { trackWidth = it.width }
            .pointerInput(locked, trackWidth) {
                detectHorizontalDragGestures(
                    // Tuần 3: sửa - đồng bộ núm với ngón tay trong lúc gesture đang kéo.
                    onDragStart = {
                        isDragging = true
                        progress = animatedProgress
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        val travel = (trackWidth - knobWidthPx).coerceAtLeast(1)
                        progress = (progress + dragAmount / travel).coerceIn(0f, 1f)
                        change.consume()
                    },
                    onDragEnd = {
                        isDragging = false
                        // Tuần 3: sửa - hoàn tất vuốt khi đạt ít nhất 80% hành trình.
                        if (progress >= 0.8f) {
                            progress = 1f
                            onUnlock()
                        } else {
                            progress = 0f
                        }
                    },
                    onDragCancel = {
                        // Tuần 3: sửa - khôi phục trạng thái nếu gesture bị hủy.
                        isDragging = false
                        progress = 0f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Vuốt để mở khóa",
            color = TextPrimary,
            style = DamType.Chip,
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    IntOffset(
                        x = (knobProgress * (trackWidth - knobWidthPx).coerceAtLeast(0)).roundToInt(),
                        y = 0
                    )
                }
                .size(knobSize)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(AccentCyan, Color.White))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chev),
                contentDescription = null,
                tint = CallUnlockInk,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Tuần 3: sửa - preview trực tiếp màn hình Bỏ túi cùng trạng thái khóa.
@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PocketModeScreenPreview() {
    DamThoaiTheme {
        PocketModeScreen(onUnlock = {})
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PocketModeLockedPreview() {
    DamThoaiTheme {
        PocketModeContent(locked = true, onUnlockDragComplete = {})
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PocketModeUnlockedPreview() {
    DamThoaiTheme {
        PocketModeContent(locked = false, onUnlockDragComplete = {})
    }
}
