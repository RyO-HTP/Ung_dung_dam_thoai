package com.example.damthoai.ui.screens

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun CreateEventScreen(
    onEventCreated: () -> Unit,
    modifier: Modifier = Modifier
) {
    var eventName by remember { mutableStateOf("Đêm nhạc Chào Tân sinh viên") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "TRƯỞNG BAN TỔ CHỨC", color = AccentCyan, style = DamType.Label)
        Text(text = "Tạo sự kiện", color = TextPrimary, style = DamType.Title22)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = eventName,
            onValueChange = { eventName = it },
            label = { Text("Tên sự kiện", color = TextSecondary, style = DamType.Small) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "KÊNH VÀ MÃ VÀO NHANH", color = TextSecondary, style = DamType.Heading14)
        Spacer(modifier = Modifier.height(8.dp))

        val channelsList = listOf(
            Triple("Toàn sự kiện", "PIN 240826", "Cấp 1"),
            Triple("Sân khấu", "PIN 240827", "Cấp 2"),
            Triple("Kỹ thuật", "PIN 240828", "Cấp 2"),
            Triple("Hậu cần – An ninh", "PIN 240829", "Cấp 2"),
            Triple("Đón tiếp", "PIN 240830", "Cấp 3")
        )

        channelsList.forEach { (name, pin, level) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = GlassBg)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = name, color = TextPrimary, style = DamType.Heading)
                        Text(text = "$pin · $level", color = TextSecondary, style = DamType.Small12)
                    }
                    Icon(painter = painterResource(R.drawable.ic_qr), contentDescription = "Mã QR")
                }
            }

        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onEventCreated,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(text = "Tạo sự kiện & Sinh mã QR", style = DamType.Button)
        }
    }

}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CreateEventScreenPreview() {
    DamThoaiTheme {
        CreateEventScreen(onEventCreated = {})
    }
}
