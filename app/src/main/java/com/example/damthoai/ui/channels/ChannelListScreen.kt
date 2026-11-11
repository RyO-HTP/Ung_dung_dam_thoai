package com.example.damthoai.ui.channels

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.damthoai.ui.theme.CallVolumeInactive
import com.example.damthoai.ui.theme.CallDuckedFill
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.damthoai.R
import com.example.damthoai.data.fake.FakeNetworkStatus
import com.example.damthoai.data.fake.FakeRemainingUiData
import com.example.damthoai.data.fake.FakeSpeakingState
import com.example.damthoai.data.fake.FakeRepositories
import com.example.damthoai.data.fake.NetworkStatus
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.LiveCueCard
import com.example.damthoai.ui.components.TagLabel
import com.example.damthoai.ui.components.dashedBorder
import com.example.damthoai.ui.components.outerGlow
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.ChannelAddBorder
import com.example.damthoai.ui.theme.CueBorder
import com.example.damthoai.ui.theme.CueGradientEnd
import com.example.damthoai.ui.theme.CueGradientStart
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.PrimaryBrush
import com.example.damthoai.ui.theme.PrimaryStart
import com.example.damthoai.ui.theme.SelectedChannelBg
import com.example.damthoai.ui.theme.TagAmberBg
import com.example.damthoai.ui.theme.TagRedBg
import com.example.damthoai.ui.theme.TrackBg
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.PureWhite
import com.example.damthoai.ui.theme.TextSecondary
import com.example.damthoai.ui.theme.cssGradient
import kotlin.math.sin

@Composable
fun ChannelListScreen(
    onNavigateToActiveCall: () -> Unit,
    onNavigateToAddChannel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val networkStatus by FakeNetworkStatus.status.collectAsState()
    val speakingChannelIds by FakeSpeakingState.speakingChannelIds.collectAsState()
    val liveCue by FakeRemainingUiData.liveCueState.collectAsState()
    val (networkStatusLabel, networkStatusColor) = when (val status = networkStatus) {
        is NetworkStatus.Good -> "Internet · ${status.ms} ms" to AccentMint
        is NetworkStatus.Weak -> "Internet · ${status.ms} ms" to AccentAmber
        NetworkStatus.Offline -> "Offline" to AccentRed
    }

    DamThoaiBackground(modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kênh", color = TextPrimary, style = DamType.Title)
                    Box(
                        Modifier.size(48.dp).clickable(onClick = onNavigateToAddChannel),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(PrimaryBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_plus),
                                contentDescription = "Thêm kênh",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        uiState.eventName,
                        color = TextSecondary,
                        style = DamType.Muted
                    )
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (networkStatus == NetworkStatus.Offline) {
                                    TagRedBg
                                } else {
                                    GlassBg
                                }
                            )
                            .border(1.dp, GlassBorder, CircleShape)
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_wifi),
                            contentDescription = null,
                            tint = networkStatusColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            networkStatusLabel,
                            color = networkStatusColor,
                            style = DamType.Small11Bold
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                liveCue?.let { LiveCueCard(cue = it) }

                Spacer(Modifier.height(12.dp))
                uiState.channels.forEach { item ->
                    val selected = item.channel.id == uiState.activeMicChannelId
                    ChannelCardItem(
                        item = item,
                        isSelectedMic = selected,
                        isSpeaking = item.channel.id in speakingChannelIds,
                        onCardClick = onNavigateToActiveCall,
                        onMicClick = { viewModel.selectMicChannel(item.channel.id) },
                        onMenuClick = { viewModel.openChannelMenu(item) },
                        onVolumeChange = { viewModel.updateChannelVolume(item.channel.id, it) }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(GlassBg)
                        .dashedBorder(ChannelAddBorder, 1.dp, 22.dp)
                        .clickable(onClick = onNavigateToAddChannel),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+ Vào thêm kênh bằng QR hoặc PIN",
                        color = TextSecondary,
                        style = DamType.Body13Bold
                    )
                }
                Spacer(Modifier.height(100.dp))
            }

            RoleChangedBanner(
                visible = uiState.roleChangedNotification != null,
                title = uiState.roleChangedNotification?.title.orEmpty(),
                subtitle = uiState.roleChangedNotification?.subtitle.orEmpty(),
                onDismiss = viewModel::dismissRoleChangedBanner
            )

            uiState.selectedMenuChannel?.let { selected ->
                ChannelMenuSheet(
                    channelItem = selected,
                    onDismiss = viewModel::closeChannelMenu,
                    onToggleMute = { isMuted ->
                        viewModel.toggleMuteChannel(selected.channel.id, isMuted)
                        viewModel.closeChannelMenu()
                    },
                    onLeaveChannel = { viewModel.leaveChannel(selected.channel.id) }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChannelCardItem(
    item: ChannelItemUiState,
    isSelectedMic: Boolean,
    isSpeaking: Boolean,
    onCardClick: () -> Unit,
    onMicClick: () -> Unit,
    onMenuClick: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val speakingGlow by animateFloatAsState(
        targetValue = if (isSpeaking) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "channelSpeakingGlow"
    )
    val pulse = if (isSpeaking) {
        val pulseTransition = rememberInfiniteTransition(label = "channelSpeakingPulse")
        pulseTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "channelSpeakingPulseValue"
        ).value
    } else {
        0.6f
    }
    val glowAlpha = speakingGlow * (0.35f + pulse * 0.65f)
    val icon = when {
        item.channel.isDefault || item.channel.id == "ch_01" || item.channel.name.contains("Toàn sự kiện", ignoreCase = true) -> R.drawable.ic_crown
        item.channel.id == "ch_02" || item.channel.name.contains("Sân khấu", ignoreCase = true) -> R.drawable.ic_stage
        else -> R.drawable.ic_radio
    }
    val iconTint = if (icon == R.drawable.ic_crown) AccentAmber else AccentCyan
    val cardModifier = Modifier
        .fillMaxWidth()
        .then(
            if (isSelectedMic || speakingGlow > 0.01f) {
                Modifier.outerGlow(
                    color = AccentCyan,
                    corner = 24.dp,
                    spread = 30.dp,
                    maxAlpha = (if (isSelectedMic) 0.10f else 0f) + glowAlpha * 0.20f
                )
            } else {
                Modifier
            }
        )
        .clickable(onClick = onCardClick)
    val cardBorderColor = when {
        speakingGlow > 0.01f -> AccentCyan.copy(
            alpha = maxOf(if (isSelectedMic) 0.8f else 0f, glowAlpha)
        )
        isSelectedMic -> AccentCyan
        else -> GlassBorder
    }

    GlassCard(
        modifier = cardModifier,
        shape = shape,
        backgroundColor = if (isSelectedMic) SelectedChannelBg else GlassBg,
        borderColor = cardBorderColor,
        borderWidth = if (isSelectedMic || speakingGlow > 0.01f) 1.5.dp else 1.dp,
        contentPadding = PaddingValues(
            horizontal = 10.dp,
            vertical = 8.dp
        )
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GlassBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.channel.name,
                            color = TextPrimary,
                            style = DamType.Heading.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.channel.isPriority) {
                            Spacer(Modifier.width(4.dp))
                            TagLabel(
                                "ƯU TIÊN",
                                color = AccentAmber,
                                backgroundColor = TagAmberBg
                            )
                        }
                    }
                    if (isSpeaking) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.channel.currentSpeaker?.let { "$it đang nói" } ?: "Đang nói",
                                color = AccentCyan,
                                style = DamType.Small.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(Modifier.width(4.dp))
                            SpeakingWaveform()
                        }
                    } else {
                        Text(
                            "Không ai đang nói",
                            color = TextSecondary,
                            style = DamType.Small.copy(fontSize = 11.sp)
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.size(44.dp).clickable(onClick = onMicClick),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .then(
                                if (isSelectedMic) {
                                    Modifier.outerGlow(
                                        color = AccentCyan,
                                        corner = 16.dp,
                                        spread = 6.dp,
                                        maxAlpha = 0.20f
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clip(CircleShape)
                            .then(
                                if (isSelectedMic) {
                                    Modifier.background(PrimaryBrush, CircleShape)
                                } else {
                                    Modifier.background(GlassBg, CircleShape)
                                }
                            )
                            .then(
                                if (isSelectedMic) {
                                    Modifier
                                } else {
                                    Modifier.border(1.dp, GlassBorder, CircleShape)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mic),
                            contentDescription = "Mic phát",
                            tint = TextPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Box(
                    Modifier.size(44.dp).clickable(onClick = onMenuClick),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GlassBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_dots),
                            contentDescription = "Menu",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_vol),
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = item.volume,
                    onValueChange = onVolumeChange,
                    enabled = !item.isDucked80,
                    modifier = Modifier.weight(1f),
                    thumb = {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (item.isDucked80) PureWhite.copy(alpha = 0.75f) else PureWhite)
                        )
                    },
                    track = { sliderState ->
                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        ) {
                            val radius = CornerRadius(size.height / 2)
                            drawRoundRect(
                                color = CallVolumeInactive,
                                cornerRadius = radius
                            )
                            val fraction = sliderState.value.coerceIn(0f, 1f)
                            if (fraction > 0f) {
                                if (item.isDucked80) {
                                    drawRoundRect(
                                        color = CallDuckedFill,
                                        size = Size(size.width * fraction, size.height),
                                        cornerRadius = radius
                                    )
                                } else {
                                    val alpha = (0.55f + 0.45f * fraction).coerceIn(0.55f, 1f)
                                    drawRoundRect(
                                        brush = cssGradient(90f, PrimaryStart, AccentCyan),
                                        size = Size(size.width * fraction, size.height),
                                        cornerRadius = radius,
                                        alpha = alpha
                                    )
                                }
                            }
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                if (item.isDucked80) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lock),
                            contentDescription = "Khóa âm lượng",
                            tint = AccentAmber,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        TagLabel(
                            "HẠ ÂM 80%",
                            color = AccentAmber,
                            backgroundColor = TagAmberBg
                        )
                    }
                } else {
                    val isLowVolume = item.volume < 0.30f
                    Text(
                        "${(item.volume * 100).toInt()}%",
                        modifier = Modifier.width(38.dp),
                        color = if (isLowVolume) TextSecondary.copy(alpha = 0.6f) else TextSecondary,
                        style = DamType.Small,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeakingWaveform() {
    val transition = rememberInfiniteTransition(label = "speakingWaveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "speakingWaveformPhase"
    )
    Canvas(Modifier.width(18.dp).height(14.dp)) {
        val barWidth = size.width / 7f
        val spacing = barWidth * 0.75f
        repeat(4) { index ->
            val barHeight = size.height * (0.25f + 0.75f * ((sin(phase + index * 1.1f) + 1f) / 2f))
            drawRoundRect(
                color = AccentCyan,
                topLeft = Offset(
                    x = index * (barWidth + spacing),
                    y = (size.height - barHeight) / 2f
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ChannelListScreenPreview() {
    DamThoaiTheme {
        ChannelListScreen(onNavigateToActiveCall = {}, onNavigateToAddChannel = {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ChannelCardIdlePreview() {
    DamThoaiTheme {
        DamThoaiBackground {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                ChannelCardItem(
                    item = ChannelItemUiState(
                        channel = FakeRepositories.sampleChannels.first().copy(currentSpeaker = null)
                    ),
                    isSelectedMic = false,
                    isSpeaking = false,
                    onCardClick = {},
                    onMicClick = {},
                    onMenuClick = {},
                    onVolumeChange = {}
                )
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ChannelCardSpeakingPreview() {
    DamThoaiTheme {
        DamThoaiBackground {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                ChannelCardItem(
                    item = ChannelItemUiState(channel = FakeRepositories.sampleChannels.first()),
                    isSelectedMic = false,
                    isSpeaking = true,
                    onCardClick = {},
                    onMicClick = {},
                    onMenuClick = {},
                    onVolumeChange = {}
                )
            }
        }
    }
}
