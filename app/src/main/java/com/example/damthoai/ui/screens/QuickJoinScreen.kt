package com.example.damthoai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun QuickJoinScreen(
    onNavigateToChannels: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pinCode by remember { mutableStateOf("240 826") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "ĐÀM THOẠI",
            style = DamType.Label,
            color = AccentCyan
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Vào kênh trong 3 giây",
            style = DamType.Title22,
            color = TextPrimary
        )

        Text(
            text = "Không cần kết bạn hay tạo tài khoản. Quét mã của ban bạn hoặc nhập tần số.",
            style = DamType.Body,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = GlassBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr),
                    contentDescription = "QR Code",
                    modifier = Modifier.height(100.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Đưa mã QR của ban vào khung", color = TextPrimary, style = DamType.Body14SemiBold)
                Text(text = "Tên và vai trò được gán tự động", color = TextSecondary, style = DamType.Small12)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = GlassBg)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "hoặc nhập tần số ảo (PIN 6 số)", color = TextPrimary, style = DamType.Body14SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pinCode,
                    onValueChange = { pinCode = it },
                    label = { Text("Mã PIN", color = TextSecondary, style = DamType.Small) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNavigateToChannels,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "Vào kênh", style = DamType.Button)
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun QuickJoinScreenPreview() {
    DamThoaiTheme {
        QuickJoinScreen(onNavigateToChannels = {})
    }
}
