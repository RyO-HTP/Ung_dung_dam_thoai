package com.example.damthoai.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.damthoai.ui.theme.IconBoxBg
import com.example.damthoai.ui.theme.SheetBg
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelMenuSheet(
    channelItem: ChannelItemUiState,
    onDismiss: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onLeaveChannel: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = SheetBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 16.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(TextSecondary.copy(alpha = 0.5f), CircleShape)
            )
        },
        contentWindowInsets = { WindowInsets(0) }
    ) {
        ChannelMenuSheetContent(
            channelItem = channelItem,
            onToggleMute = onToggleMute,
            onLeaveChannel = onLeaveChannel
        )
    }
}

@Composable
fun ChannelMenuSheetContent(
    channelItem: ChannelItemUiState,
    onToggleMute: (Boolean) -> Unit,
    onLeaveChannel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val channelIcon = getChannelIcon(channelItem.channel)
    val channelIconTint = if (channelIcon == R.drawable.ic_crown) AccentAmber else TextPrimary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
    ) {
        // Phần đầu
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(IconBoxBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(channelIcon),
                    contentDescription = null,
                    tint = channelIconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = channelItem.channel.name,
                    color = TextPrimary,
                    style = DamType.Heading.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "MC · kênh chuyên trách",
                    color = TextSecondary,
                    style = DamType.Body.copy(fontSize = 14.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Hàng 1: Tắt nghe kênh này
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { onToggleMute(!channelItem.isMuted) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_vol),
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Tắt nghe kênh này",
                    color = TextPrimary,
                    style = DamType.Heading16.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Vẫn ở trong kênh nhưng không nghe âm\u00A0thanh",
                    color = TextSecondary,
                    style = DamType.Body.copy(fontSize = 14.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(16.dp))
            KitSwitch(
                checked = channelItem.isMuted,
                onCheckedChange = onToggleMute
            )
        }

        Spacer(Modifier.height(20.dp))

        // Hàng 2: Rời kênh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onLeaveChannel),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_x),
                    contentDescription = null,
                    tint = DangerText,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Rời kênh",
                    color = DangerText,
                    style = DamType.Heading16.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Muốn vào lại phải quét QR hoặc nhập PIN",
                    color = TextSecondary,
                    style = DamType.Body.copy(fontSize = 14.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
