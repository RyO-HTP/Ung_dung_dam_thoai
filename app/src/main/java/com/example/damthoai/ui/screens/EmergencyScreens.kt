package com.example.damthoai.ui.screens

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.R
import com.example.damthoai.data.Member
import com.example.damthoai.data.MemberLevel
import com.example.damthoai.hardware.PttController
import com.example.damthoai.hardware.PttSource
import com.example.damthoai.hardware.emergency.EmergencyAlert
import com.example.damthoai.hardware.emergency.EmergencyAlertManager
import com.example.damthoai.ui.components.AvatarCircle
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.BgVariant
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.DangerButton
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.SecondaryButton
import com.example.damthoai.ui.components.SectionLabel
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.ButtonShape
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DangerBrush
import com.example.damthoai.ui.theme.DangerEnd
import com.example.damthoai.ui.theme.DangerShadow
import com.example.damthoai.ui.theme.DangerStart
import com.example.damthoai.ui.theme.DangerText
import com.example.damthoai.ui.theme.DisabledButtonEnd
import com.example.damthoai.ui.theme.DisabledButtonStart
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.TagRedBg
import com.example.damthoai.ui.theme.TagRedText
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
import com.example.damthoai.ui.theme.Transparent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

data class EmergencyCallParticipant(
    val member: Member,
    val status: String,
    val isSpeaking: Boolean = false
)

private fun Modifier.emergencyGradient() = drawBehind {
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to DangerText,
                0.6f to DangerStart,
                1f to DangerEnd
            ),
            center = Offset(size.width * 0.35f, size.height * 0.30f),
            radius = size.minDimension * 0.9f
        )
    )
}

@Composable
fun EmergencyCallSelectScreen(
    onBack: () -> Unit,
    onCall: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    recipients: List<Member> = emptyList()
) {
    val context = LocalContext.current
    var selectedIds by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() }
        )
    ) {
        mutableStateOf(setOf(recipients.firstOrNull()?.id.orEmpty()))
    }

    EmergencyCallSelectContent(
        recipients = recipients,
        selectedIds = selectedIds,
        onSelectedIdsChange = { selectedIds = it },
        onBack = onBack,
        onCall = onCall,
        onShortHold = {
            Toast.makeText(
                context,
                "Giữ 2 giây để gọi",
                Toast.LENGTH_SHORT
            ).show()
        },
        modifier = modifier
    )
}

@Composable
private fun EmergencyCallSelectContent(
    recipients: List<Member>,
    selectedIds: Set<String>,
    onSelectedIdsChange: (Set<String>) -> Unit,
    onBack: () -> Unit,
    onCall: (Set<String>) -> Unit,
    onShortHold: () -> Unit,
    modifier: Modifier = Modifier,
    previewProgress: Float? = null
) {
    val quickTargets = listOf(
        "Tổng đạo diễn" to recipients
            .filter { it.level == MemberLevel.LEVEL_1 }
            .map { it.id }
            .toSet(),
        "Cả kênh Hậu cần" to recipients
            .filter { it.roleTitle.contains("Hậu cần", ignoreCase = true) }
            .map { it.id }
            .toSet(),
        "Toàn sự kiện" to recipients.map { it.id }.toSet()
    )

    DamThoaiBackground(modifier = modifier, variant = BgVariant.Emergency) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 14.dp, bottom = 26.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Gọi khẩn cấp",
                        color = TextPrimary,
                        style = DamType.Title22
                    )
                    Text(
                        text = "Chỉ người được chọn nhận cảnh báo",
                        color = TextSecondary,
                        style = DamType.Muted
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel(text = "Gửi nhanh tới", color = DangerText)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickTargets.forEach { (target, targetIds) ->
                    QuickTargetChip(
                        text = target,
                        selected = selectedIds == targetIds,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectedIdsChange(targetIds) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel(text = "Hoặc chọn người", color = DangerText)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recipients.forEach { member ->
                    EmergencyRecipientCard(
                        member = member,
                        selected = member.id in selectedIds,
                        onClick = {
                            onSelectedIdsChange(if (member.id in selectedIds) {
                                selectedIds - member.id
                            } else {
                                selectedIds + member.id
                            })
                        }
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                text = "Máy người nhận sẽ rung, nháy đèn rồi hai bên nói chuyện qua lại ngay trong app.",
                color = TextSecondary,
                style = DamType.Small,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            )
            HoldToCallButton(
                selectedIds = selectedIds,
                onCall = onCall,
                onShortHold = onShortHold,
                previewProgress = previewProgress
            )
        }
    }
}

@Composable
private fun QuickTargetChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val fillColor = if (selected) AccentRed else GlassBg
    val borderColor = if (selected) AccentRed else GlassBorder
    val textColor = if (selected) TextPrimary else TextSecondary
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(fillColor)
                .border(1.dp, borderColor, RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = textColor,
                style = DamType.Tag11.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmergencyRecipientCard(
    member: Member,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GlassBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarCircle(nameOrInitials = member.name, size = 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.name,
                color = TextPrimary,
                style = DamType.Body.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = "${member.roleTitle} · Cấp ${member.level.code}",
                color = TextSecondary,
                style = DamType.Small
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) AccentRed else GlassBg.copy(alpha = 0f))
                .border(
                    width = 2.dp,
                    color = if (selected) AccentRed else TextPrimary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = "Đã chọn",
                    tint = TextPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun HoldToCallButton(
    selectedIds: Set<String>,
    onCall: (Set<String>) -> Unit,
    onShortHold: () -> Unit,
    previewProgress: Float? = null
) {
    val enabled = selectedIds.isNotEmpty()
    var isHolding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val currentSelectedIds by rememberUpdatedState(selectedIds)
    val currentOnCall by rememberUpdatedState(onCall)
    val currentOnShortHold by rememberUpdatedState(onShortHold)
    val haptic = LocalHapticFeedback.current
    val displayedProgress = (previewProgress ?: progress).coerceIn(0f, 1f)

    LaunchedEffect(isHolding) {
        if (!isHolding) {
            progress = 0f
            return@LaunchedEffect
        }

        val startNanos = withFrameNanos { it }
        while (isHolding && progress < 1f) {
            val elapsedNanos = withFrameNanos { it } - startNanos
            progress = (elapsedNanos / 2_000_000_000f).coerceIn(0f, 1f)
        }
        if (isHolding && progress >= 1f) {
            progress = 1f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 10.dp)
                .shadow(30.dp, ButtonShape, spotColor = DangerShadow)
                .background(Transparent, ButtonShape)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (previewProgress == null && enabled) {
                        Modifier.pointerInput(enabled) {
                            detectTapGestures(
                                onPress = {
                                    isHolding = true
                                    try {
                                        val holdStartedAt = SystemClock.uptimeMillis()
                                        val releasedBeforeTimeout =
                                            withTimeoutOrNull(2_000L) {
                                                tryAwaitRelease()
                                            }
                                        val heldDuration =
                                            SystemClock.uptimeMillis() - holdStartedAt
                                        when {
                                            releasedBeforeTimeout == null ||
                                                    (releasedBeforeTimeout == true &&
                                                            heldDuration >= 2_000L) -> {
                                                progress = 1f
                                                haptic.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                                currentOnCall(currentSelectedIds)
                                            }
                                            releasedBeforeTimeout == true ->
                                                currentOnShortHold()
                                        }
                                    } finally {
                                        isHolding = false
                                    }
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .alpha(if (enabled) 1f else 0.62f)
                .clip(ButtonShape)
                .background(
                    if (enabled) {
                        DangerBrush
                    } else {
                        Brush.horizontalGradient(
                            listOf(DisabledButtonStart, DisabledButtonEnd)
                        )
                    }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(displayedProgress)
                    .background(DangerEnd)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_alert),
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Giữ 2 giây để gọi",
                    color = TextPrimary,
                    style = DamType.Button,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun EmergencyAlertIncomingScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    alertFlow: StateFlow<EmergencyAlert?> = EmergencyAlertManager.currentAlert,
    isVibratingFlow: StateFlow<Boolean> = EmergencyAlertManager.isVibrating,
    isFlashingFlow: StateFlow<Boolean> = EmergencyAlertManager.isFlashing
) {
    val alert by alertFlow.collectAsState()
    val isVibrating by isVibratingFlow.collectAsState()
    val isFlashing by isFlashingFlow.collectAsState()

    DamThoaiBackground(modifier = modifier, variant = BgVariant.Emergency) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 18.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "CẢNH BÁO KHẨN CẤP",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(TagRedBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = TagRedText,
                style = DamType.Tag12
            )
            Spacer(Modifier.height(30.dp))
            EmergencyAlertOrb()
            Spacer(Modifier.height(44.dp))
            Text(
                text = alert?.let { "${it.fromName} gọi khẩn cấp" }.orEmpty(),
                color = TextPrimary,
                style = DamType.Title26,
                textAlign = TextAlign.Center
            )
            Text(
                text = alert?.let {
                    "${it.fromRole} · ${if (it.isDirectCall) "gọi riêng bạn" else "gọi toàn kênh"}"
                }.orEmpty(),
                color = TextSecondary,
                style = DamType.Body,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(18.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlertStatusChip(
                    iconRes = R.drawable.ic_zap,
                    text = "Đang rung",
                    active = isVibrating
                )
                AlertStatusChip(
                    iconRes = R.drawable.ic_light,
                    text = "Đèn flash đang nháy",
                    active = isFlashing
                )
            }
            Spacer(Modifier.weight(1f))
            DangerButton(
                text = "Trả lời",
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
                height = 60.dp
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Để sau",
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EmergencyAlertOrb() {
    val transition = rememberInfiniteTransition(label = "emergency_alert_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.84f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emergency_alert_alpha"
    )

    Box(
        modifier = Modifier.size(210.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = AccentRed.copy(alpha = 0.08f),
                radius = size.minDimension / 2f - 20.dp.toPx(),
                style = Stroke(width = 40.dp.toPx())
            )
            drawCircle(
                color = AccentRed.copy(alpha = 0.16f),
                radius = size.minDimension / 2f - 29.dp.toPx(),
                style = Stroke(width = 18.dp.toPx())
            )
        }
        Box(
            modifier = Modifier
                .size(130.dp)
                .shadow(28.dp, CircleShape, spotColor = DangerShadow)
                .clip(CircleShape)
                .emergencyGradient()
                .alpha(pulse),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_alert),
                contentDescription = "Cảnh báo khẩn cấp",
                tint = TextPrimary,
                modifier = Modifier.size(58.dp)
            )
        }
    }
}

@Composable
private fun AlertStatusChip(
    iconRes: Int,
    text: String,
    active: Boolean
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(GlassBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(50))
            .alpha(if (active) 1f else 0.38f)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = DangerText,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = text, color = TextPrimary, style = DamType.Small)
    }
}

@Composable
fun EmergencyActiveCallScreen(
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier,
    caller: EmergencyAlert? = null,
    activeParticipants: List<EmergencyCallParticipant> = emptyList()
) {
    val displayParticipants = caller?.let { alert ->
        val callerTemplate = activeParticipants.firstOrNull()
        val callerParticipant = callerTemplate?.copy(
            member = callerTemplate.member.copy(
                name = alert.fromName,
                roleTitle = alert.fromRole
            ),
            status = "${alert.fromRole} · đang nói",
            isSpeaking = true
        )
        listOfNotNull(callerParticipant) + activeParticipants.filterNot {
            it.member.name == alert.fromName
        }
    } ?: activeParticipants
    val isTalking by PttController.isPttActive.collectAsState()
    val view = LocalView.current
    val latestOnEndCall by rememberUpdatedState(onEndCall)
    val finishCall = {
        PttController.forceReleasePtt()
        latestOnEndCall()
    }

    DisposableEffect(view) {
        val observer = view.viewTreeObserver
        val focusListener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (!hasFocus) PttController.forceReleasePtt()
        }
        observer.addOnWindowFocusChangeListener(focusListener)
        onDispose {
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focusListener)
            PttController.forceReleasePtt()
        }
    }

    EmergencyActiveCallContent(
        participants = displayParticipants,
        isTalking = isTalking,
        onEndCall = finishCall,
        modifier = modifier
    )
}

@Composable
private fun EmergencyActiveCallContent(
    participants: List<EmergencyCallParticipant>,
    isTalking: Boolean,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    DamThoaiBackground(modifier = modifier, variant = BgVariant.Emergency) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 14.dp, bottom = 26.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                BackButton(onClick = onEndCall, modifier = Modifier.align(Alignment.CenterStart))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 52.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Cuộc gọi khẩn cấp",
                        color = TextPrimary,
                        style = DamType.Heading16
                    )
                    Text(
                        text = "Các kênh khác đã hạ âm",
                        color = TextSecondary,
                        style = DamType.Muted
                    )
                }
                Spacer(Modifier.size(40.dp).align(Alignment.CenterEnd))
            }

            Spacer(Modifier.height(16.dp))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                borderColor = AccentRed.copy(alpha = 0.5f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    participants.forEach { participant ->
                        EmergencyParticipantRow(
                            participant = participant,
                            isUserTalking = isTalking
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp),
                contentAlignment = Alignment.Center
            ) {
                EmergencyPttButton(isTalking = isTalking)
            }

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                text = "Kết thúc cuộc gọi",
                onClick = onEndCall,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp,
                textColor = DangerText
            )
        }
    }
}

@Composable
private fun EmergencyParticipantRow(
    participant: EmergencyCallParticipant,
    isUserTalking: Boolean
) {
    val isSpeaking = participant.isSpeaking ||
            (participant.member.name == "Minh Hậu" && isUserTalking)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarCircle(nameOrInitials = participant.member.name, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (participant.member.name == "Minh Hậu") {
                    "${participant.member.name} (bạn)"
                } else {
                    participant.member.name
                },
                color = TextPrimary,
                style = DamType.Body.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = if (participant.member.name == "Minh Hậu" && isUserTalking) {
                    "đang nói"
                } else {
                    participant.status
                },
                color = if (isSpeaking) DangerText else TextSecondary,
                style = DamType.Small
            )
        }
        if (isSpeaking) {
            SpeakingWaveform()
        }
    }
}

@Composable
private fun SpeakingWaveform() {
    Canvas(Modifier.size(width = 26.dp, height = 24.dp)) {
        val centerY = size.height / 2f
        val heights = listOf(7f, 14f, 20f, 13f, 8f)
        val gap = 5.dp.toPx()
        val strokeWidth = 2.dp.toPx()
        heights.forEachIndexed { index, height ->
            val x = index * gap + strokeWidth
            drawLine(
                color = DangerText,
                start = Offset(x, centerY - height.dp.toPx() / 2f),
                end = Offset(x, centerY + height.dp.toPx() / 2f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun EmergencyPttButton(isTalking: Boolean) {
    Box(
        modifier = Modifier.size(214.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(214.dp)
                .background(DangerStart.copy(alpha = if (isTalking) 0.16f else 0.08f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(178.dp)
                .background(DangerStart.copy(alpha = if (isTalking) 0.30f else 0.18f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            PttController.onKeyPressed(PttSource.SCREEN_BUTTON)
                            try {
                                tryAwaitRelease()
                            } finally {
                                PttController.onKeyReleased(PttSource.SCREEN_BUTTON)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .size(150.dp)
                    .shadow(28.dp, CircleShape, spotColor = DangerShadow)
                    .clip(CircleShape)
                    .emergencyGradient()
                    .graphicsLayer {
                        val scale = if (isTalking) 0.97f else 1f
                        scaleX = scale
                        scaleY = scale
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = "Giữ để nói",
                    tint = TextPrimary,
                    modifier = Modifier.size(38.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "GIỮ ĐỂ NÓI",
                    color = TextPrimary,
                    style = DamType.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}