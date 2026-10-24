package com.example.damthoai.ui.channels

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.damthoai.R
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.TagLabel
import com.example.damthoai.ui.components.dashedBorder
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentMint
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
import com.example.damthoai.ui.theme.TrackBg
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
import com.example.damthoai.ui.theme.cssGradient

@Composable
fun ChannelListScreen(
    onNavigateToActiveCall: () -> Unit,
    onNavigateToAddChannel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PrimaryBrush)
                            .clickable(onClick = onNavigateToAddChannel),
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
                            .background(GlassBg)
                            .border(1.dp, GlassBorder, CircleShape)
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_wifi),
                            contentDescription = null,
                            tint = AccentMint,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            uiState.networkStatus,
                            color = AccentMint,
                            style = DamType.Small.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cssGradient(135f, CueGradientStart, CueGradientEnd))
                        .border(1.dp, CueBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_zap),
                        contentDescription = null,
                        tint = AccentAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            uiState.liveCueHeader,
                            color = AccentAmber,
                            style = DamType.Tag
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            uiState.liveCueTitle,
                            color = TextPrimary,
                            style = DamType.Heading.copy(fontSize = 13.5.sp)
                        )
                        Text(
                            uiState.liveCueSubtitle,
                            color = TextSecondary,
                            style = DamType.Small
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                uiState.channels.forEach { item ->
                    val selected = item.channel.id == uiState.activeMicChannelId
                    ChannelCardItem(
                        item = item,
                        isSelectedMic = selected,
                        onCardClick = onNavigateToActiveCall,
                        onMicClick = { viewModel.selectMicChannel(item.channel.id) },
                        onMenuClick = { viewModel.openChannelMenu(item) },
                        onVolumeChange = { viewModel.updateChannelVolume(item.channel.id, it) }
                    )
                    Spacer(Modifier.height(12.dp))
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
                        style = DamType.Body.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
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
                    onLeaveChannel = viewModel::closeChannelMenu
                )
            }
        }
    }
}

@Composable
private fun ChannelCardItem(
    item: ChannelItemUiState,
    isSelectedMic: Boolean,
    onCardClick: () -> Unit,
    onMicClick: () -> Unit,
    onMenuClick: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val icon = when (item.channel.id) {
        "ch_01" -> R.drawable.ic_crown
        "ch_02" -> R.drawable.ic_stage
        else -> R.drawable.ic_shield
    }
    val iconTint = if (item.channel.id == "ch_01") AccentAmber else AccentCyan
    val cardModifier = Modifier
        .fillMaxWidth()
        .then(
            if (isSelectedMic) {
                Modifier.shadow(18.dp, shape, spotColor = AccentCyan)
            } else {
                Modifier
            }
        )
        .clickable(onClick = onCardClick)

    GlassCard(
        modifier = cardModifier,
        shape = shape,
        backgroundColor = if (isSelectedMic) SelectedChannelBg else GlassBg,
        borderColor = if (isSelectedMic) AccentCyan else GlassBorder,
        borderWidth = if (isSelectedMic) 1.5.dp else 1.dp,
        contentPadding = PaddingValues(
            horizontal = 14.dp,
            vertical = 12.dp
        )
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GlassBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.channel.name,
                            color = TextPrimary,
                            style = DamType.Heading.copy(fontSize = 16.sp)
                        )
                        if (item.channel.isPriority) {
                            Spacer(Modifier.width(6.dp))
                            TagLabel(
                                "ƯU TIÊN",
                                color = AccentAmber,
                                backgroundColor = TagAmberBg
                            )
                        }
                    }
                    if (item.channel.currentSpeaker != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_vol),
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${item.channel.currentSpeaker} đang nói",
                                color = AccentCyan,
                                style = DamType.Small.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    } else {
                        Text(
                            "Không ai đang nói",
                            color = TextSecondary,
                            style = DamType.Small
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(38.dp)
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
                                Modifier.shadow(10.dp, CircleShape, spotColor = AccentCyan)
                            } else {
                                Modifier.border(1.dp, GlassBorder, CircleShape)
                            }
                        )
                        .clickable(onClick = onMicClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = "Mic phát",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(
                    Modifier
                        .size(width = 28.dp, height = 38.dp)
                        .clickable(onClick = onMenuClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_dots),
                        contentDescription = "Menu",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_vol),
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(10.dp))
                Slider(
                    value = item.volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f),
                    thumb = {
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    },
                    track = { sliderState ->
                        Canvas(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        ) {
                            val radius = CornerRadius(size.height / 2)
                            drawRoundRect(
                                color = TrackBg,
                                cornerRadius = radius
                            )
                            val fraction = sliderState.value.coerceIn(0f, 1f)
                            if (fraction > 0f) {
                                drawRoundRect(
                                    brush = cssGradient(90f, PrimaryStart, AccentCyan),
                                    size = Size(size.width * fraction, size.height),
                                    cornerRadius = radius
                                )
                            }
                        }
                    }
                )
                Spacer(Modifier.width(10.dp))
                if (item.isDucked80) {
                    TagLabel(
                        "HẠ ÂM 80%",
                        color = AccentAmber,
                        backgroundColor = TagAmberBg
                    )
                } else {
                    Text(
                        "${(item.volume * 100).toInt()}%",
                        modifier = Modifier.width(36.dp),
                        color = TextSecondary,
                        style = DamType.Muted.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.End
                    )
                }
            }
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
