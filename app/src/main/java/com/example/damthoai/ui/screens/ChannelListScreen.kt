package com.example.damthoai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.damthoai.R
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.SelectedChannelBg
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun ChannelListScreen(
    onNavigateToActiveCall: () -> Unit,
    onNavigateToCreateEvent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "SỰ KIỆN", color = AccentCyan, style = DamType.Label)
                Text(text = "Đêm nhạc Chào Tân sinh viên", color = TextPrimary, style = DamType.Heading18)
                Text(text = "Cloud · 42 ms", color = TextSecondary, style = DamType.Small12)
            }

            OutlinedButton(onClick = onNavigateToCreateEvent) {
                Icon(painter = painterResource(R.drawable.ic_plus), contentDescription = "Tạo sự kiện")
                Text("Tạo sự kiện", color = TextPrimary, style = DamType.Chip)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live Cue Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GlassBg)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "THANH KỊCH BẢN · ĐANG DIỄN",
                    style = DamType.Label,
                    color = TextSecondary
                )
                Text(
                    text = "Tiết mục 4 – Ban nhạc khách mời",
                    style = DamType.Body14Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Tiếp theo: chuyển cảnh, hậu cần mang đạo cụ",
                    style = DamType.Small12,
                    color = TextSecondary.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Danh sách kênh", color = TextPrimary, style = DamType.Heading16)
        Spacer(modifier = Modifier.height(8.dp))

        val channels = listOf(
            Triple("Toàn sự kiện", "Tổng đạo diễn đang nói · ƯU TIÊN", true),
            Triple("Sân khấu", "MC · 8 người", false),
            Triple("Kỹ thuật", "Âm thanh – Ánh sáng – LED · 11 người", false),
            Triple("Hậu cần – An ninh", "Đang chọn để nói · 12 người", true),
            Triple("Đón tiếp", "Check-in & VIP · 6 người", false)
        )

        channels.forEach { (name, subtitle, isHighlight) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onNavigateToActiveCall() },
                colors = CardDefaults.cardColors(
                    containerColor = if (isHighlight) SelectedChannelBg else GlassBg
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = name, color = TextPrimary, style = DamType.Heading)
                        Text(text = subtitle, color = TextSecondary, style = DamType.Small12)
                    }
                    Icon(painter = painterResource(R.drawable.ic_vol), contentDescription = "Sound")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GlassBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Mic đang tắt · nghe 3 kênh", color = TextPrimary, style = DamType.Body13)
                Button(onClick = onNavigateToActiveCall) {
                    Text("Mở mic", style = DamType.Chip)
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ChannelListScreenPreview() {
    DamThoaiTheme {
        ChannelListScreen(onNavigateToActiveCall = {}, onNavigateToCreateEvent = {})
    }
}
