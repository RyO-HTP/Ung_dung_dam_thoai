/**
 * Path: app/src/main/java/com/example/damthoai/ui/channels/ChannelListScreen.kt
 *
 * Khối KDoc tiếng Việt:
 * Màn hình Danh sách Kênh (Tab "Kênh" chính) chuẩn thiết kế Đàm Thoại HTML/PDF.
 * Hiển thị Tiêu đề + Nút +, Tên sự kiện + Mạng, Thẻ Live Cue, Các Thẻ Kênh GlassCard
 * kèm Slider âm lượng tương tác, Nhãn HẠ ÂM 80%, Nút "•••", Frame thêm kênh nét đứt và Banner Đổi chức vụ.
 */
package com.example.damthoai.ui.channels

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.SectionLabel
import com.example.damthoai.ui.components.StatusPill
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.PrimaryEnd
import com.example.damthoai.ui.theme.PrimaryStart
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun ChannelListScreen(
    onNavigateToActiveCall: () -> Unit,
    onNavigateToAddChannel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    DamThoaiBackground(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Header: Tiêu đề lớn "Kênh" (Trái) + Nút "+" Vuông Gradient (Phải)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kênh",
                        color = TextPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    // Nút vuông gradient "+"
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(PrimaryStart, PrimaryEnd)))
                            .clickable { onNavigateToAddChannel() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Thêm kênh",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Dòng phụ: Tên sự kiện + StatusPill Mạng Internet 42ms
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.eventName,
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    StatusPill(
                        text = uiState.networkStatus,
                        statusColor = AccentMint
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Thẻ LIVE CUE (Viền & Nền Vàng nhạt, Icon Tia chớp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x22FFC857))
                        .border(1.5.dp, AccentAmber, RoundedCornerShape(20.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AccentAmber),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = Color(0xFF3E2200),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            SectionLabel(
                                text = uiState.liveCueHeader,
                                color = AccentAmber
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = uiState.liveCueTitle,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = uiState.liveCueSubtitle,
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                SectionLabel(text = "DANH SÁCH KÊNH CỦA BẠN")
                Spacer(modifier = Modifier.height(10.dp))

                // Danh sách Thẻ Kênh GlassCard
                uiState.channels.forEach { item ->
                    val isSelectedMic = item.channel.id == uiState.activeMicChannelId

                    ChannelCardItem(
                        item = item,
                        isSelectedMic = isSelectedMic,
                        onCardClick = { onNavigateToActiveCall() },
                        onMicClick = { viewModel.selectMicChannel(item.channel.id) },
                        onMenuClick = { viewModel.openChannelMenu(item) },
                        onVolumeChange = { vol -> viewModel.updateChannelVolume(item.channel.id, vol) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Khung nét đứt cuối danh sách: "+ Vào thêm kênh bằng QR hoặc PIN"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(GlassBg)
                        .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                        .clickable { onNavigateToAddChannel() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ Vào thêm kênh bằng QR hoặc PIN",
                        color = AccentCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(100.dp)) // Tránh bị BottomBar đè
            }

            // Banner đổi chức vụ nổi trượt từ trên xuống
            RoleChangedBanner(
                visible = uiState.roleChangedNotification != null,
                title = uiState.roleChangedNotification?.title ?: "",
                subtitle = uiState.roleChangedNotification?.subtitle ?: "",
                onDismiss = { viewModel.dismissRoleChangedBanner() }
            )

            // Bottom Sheet Menu Kênh khi bấm "•••"
            uiState.selectedMenuChannel?.let { selected ->
                ChannelMenuSheet(
                    channelItem = selected,
                    onDismiss = { viewModel.closeChannelMenu() },
                    onToggleMute = { isMuted ->
                        viewModel.toggleMuteChannel(selected.channel.id, isMuted)
                        viewModel.closeChannelMenu()
                    },
                    onLeaveChannel = { viewModel.closeChannelMenu() }
                )
            }
        }
    }
}

/**
 * Thẻ Kênh đơn vị (GlassCard)
 */
@Composable
private fun ChannelCardItem(
    item: ChannelItemUiState,
    isSelectedMic: Boolean,
    onCardClick: () -> Unit,
    onMicClick: () -> Unit,
    onMenuClick: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    val borderColor = if (isSelectedMic) AccentCyan else GlassBorder
    val borderWidth = if (isSelectedMic) 2.dp else 1.dp

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        borderColor = borderColor,
        borderWidth = borderWidth
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ô Icon vuông bên trái
                val icon = when (item.channel.id) {
                    "ch_01" -> Icons.Default.WorkspacePremium
                    "ch_02" -> Icons.Default.Home
                    else -> Icons.Default.Security
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GlassBg)
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Tên kênh + Dòng trạng thái (Ví dụ "Lan Phương đang nói" cyan + sóng âm)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.channel.name,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (item.channel.isPriority) {
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusPill(text = "ƯU TIÊN", statusColor = AccentAmber)
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    if (item.channel.currentSpeaker != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${item.channel.currentSpeaker} đang nói",
                                color = AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = "Không ai đang nói",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Nút Mic nhỏ
                val micBg = if (isSelectedMic) Brush.linearGradient(listOf(PrimaryStart, PrimaryEnd)) else Brush.linearGradient(listOf(GlassBg, GlassBg))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(micBg)
                        .border(1.dp, if (isSelectedMic) AccentCyan else GlassBorder, CircleShape)
                        .clickable { onMicClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Mic phát",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Nút "•••" Mở Menu Sheet
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(GlassBg)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { onMenuClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Menu",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Slider Âm lượng
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = item.volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = GlassBg
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Nhãn Vàng HẠ ÂM 80% hoặc Số %
                if (item.isDucked80) {
                    StatusPill(text = "HẠ ÂM 80%", statusColor = AccentAmber)
                } else {
                    Text(
                        text = "${(item.volume * 100).toInt()}%",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ChannelListScreenPreview() {
    DamThoaiTheme {
        ChannelListScreen(onNavigateToActiveCall = {}, onNavigateToAddChannel = {})
    }
}
