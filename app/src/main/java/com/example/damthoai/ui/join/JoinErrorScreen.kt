package com.example.damthoai.ui.join

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.damthoai.R
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.components.SecondaryButton
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.TextPrimary

@Composable
fun JoinErrorScreen(
    onRetryQr: () -> Unit,
    onSwitchPin: () -> Unit,
    onNavigateToCreateEvent: () -> Unit,
    modifier: Modifier = Modifier,
    reason: com.example.damthoai.data.qr.QrJoinFailure? = null,
    customMessage: String? = null
) {
    val errorSubtitle = when (reason) {
        com.example.damthoai.data.qr.QrJoinFailure.EXPIRED -> "Mã QR đã hết hạn"
        com.example.damthoai.data.qr.QrJoinFailure.MALFORMED -> "Mã QR không đúng định dạng"
        com.example.damthoai.data.qr.QrJoinFailure.NOT_DAM_THOAI -> "Đây không phải mã của Đàm Thoại"
        com.example.damthoai.data.qr.QrJoinFailure.SERVER_REJECTED -> customMessage ?: "Máy chủ từ chối mã"
        com.example.damthoai.data.qr.QrJoinFailure.NETWORK -> "Không kết nối được máy chủ"
        null -> "Hãy kiểm tra lại các nguyên nhân thường gặp"
    }
    DamThoaiBackground(modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(110.dp).clip(CircleShape)
                    .background(AccentAmber.copy(alpha = 0.14f))
                    .border(1.5.dp, AccentAmber.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_alert),
                    contentDescription = null,
                    tint = AccentAmber,
                    modifier = Modifier.size(54.dp)
                )
            }
            Spacer(Modifier.height(30.dp))
            Text(
                "Không vào được kênh",
                color = TextPrimary,
                style = DamType.Title,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorSubtitle,
                color = AccentAmber,
                style = DamType.Body,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(26.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                    .background(GlassBg)
                    .border(1.dp, GlassBorder, RoundedCornerShape(30.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ErrorReason("Mã QR hoặc PIN sai, hoặc đã bị Trưởng ban đặt lại")
                ErrorReason("Sự kiện đã kết thúc")
                ErrorReason("Điện thoại chưa có kết nối mạng")
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                text = "Quét lại",
                onClick = onRetryQr,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp
            )
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                text = "Nhập PIN",
                onClick = onSwitchPin,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Tạo sự kiện mới",
                onClick = onNavigateToCreateEvent,
                iconRes = R.drawable.ic_plus,
                modifier = Modifier.fillMaxWidth(),
                height = 48.dp
            )
        }
    }
}

@Composable
private fun ErrorReason(text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(AccentAmber)
        )
        Spacer(Modifier.size(10.dp))
        Text(text, color = TextPrimary, style = DamType.Body14Line21)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun JoinErrorScreenPreview() {
    DamThoaiTheme {
        JoinErrorScreen(
            onRetryQr = {},
            onSwitchPin = {},
            onNavigateToCreateEvent = {}
        )
    }
}
