package com.example.damthoai.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.R
import com.example.damthoai.data.fake.FakeRepositories
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.KitSwitch
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DangerText
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.GlassSheetShape
import com.example.damthoai.ui.theme.IconBoxBg
import com.example.damthoai.ui.theme.Scrim
import com.example.damthoai.ui.theme.SheetBg
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun ChannelMenuSheet(
    channelItem: ChannelItemUiState,
    onDismiss: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onLeaveChannel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val channelIcon = when (channelItem.channel.id) {
        "ch_01" -> R.drawable.ic_crown
        "ch_02" -> R.drawable.ic_stage
        else -> R.drawable.ic_shield
    }
    val channelIconTint = if (channelItem.channel.id == "ch_01") AccentAmber else TextPrimary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassSheetShape)
                .background(SheetBg)
                .border(1.dp, GlassBorder, GlassSheetShape)
                .clickable {}
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 30.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 44.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(TextSecondary.copy(alpha = 0.5f))
            )
            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(IconBoxBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(channelIcon),
                        contentDescription = null,
                        tint = channelIconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        channelItem.channel.name,
                        color = TextPrimary,
                        style = DamType.Heading.copy(fontSize = 18.sp)
                    )
                    Text(
                        "MC · kênh chuyên trách",
                        color = TextSecondary,
                        style = DamType.Body
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_vol),
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Tắt nghe kênh này",
                        color = TextPrimary,
                        style = DamType.Heading.copy(fontSize = 16.sp)
                    )
                    Text(
                        "Vẫn ở trong kênh nhưng không nghe âm thanh",
                        color = TextSecondary,
                        style = DamType.Body
                    )
                }
                Spacer(Modifier.width(8.dp))
                KitSwitch(
                    checked = channelItem.isMuted,
                    onCheckedChange = onToggleMute
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLeaveChannel),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_x),
                    contentDescription = null,
                    tint = DangerText,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Rời kênh",
                        color = DangerText,
                        style = DamType.Heading.copy(fontSize = 16.sp)
                    )
                    Text(
                        "Muốn vào lại phải quét QR hoặc nhập PIN",
                        color = TextSecondary,
                        style = DamType.Body
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ChannelMenuSheetPreview() {
    val channel = FakeRepositories.sampleChannels[1]
    DamThoaiTheme {
        DamThoaiBackground {
            ChannelMenuSheet(
                channelItem = ChannelItemUiState(channel = channel),
                onDismiss = {},
                onToggleMute = {},
                onLeaveChannel = {}
            )
        }
    }
}
