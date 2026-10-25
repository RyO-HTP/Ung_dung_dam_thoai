package com.example.damthoai.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.R
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.CueBorder
import com.example.damthoai.ui.theme.CueFill
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.InputShape
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun CreateEventScreen(
    onBack: () -> Unit,
    onEventCreated: () -> Unit,
    onNavigateToRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    var eventName by rememberSaveable { mutableStateOf("Đêm nhạc Chào Tân sinh viên") }

    DamThoaiBackground(modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Text(
                    "Tạo sự kiện",
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    style = DamType.Heading.copy(fontSize = 16.sp),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.size(40.dp))
            }

            Spacer(Modifier.height(12.dp))
            EventFieldLabel("Tên sự kiện")
            EventNameInput(
                value = eventName,
                onValueChange = { eventName = it }
            )

            Spacer(Modifier.height(12.dp))
            EventFieldLabel("Tên của bạn")
            StaticEventField("Lan Phương")

            Spacer(Modifier.height(12.dp))
            EventFieldLabel("Chức vụ")
            StaticEventField("Tổng đạo diễn")

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CueFill)
                    .border(1.dp, CueBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_crown),
                    contentDescription = null,
                    tint = AccentAmber,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Bạn là Cấp 1 · quyền cao nhất",
                        color = TextPrimary,
                        style = DamType.Heading.copy(fontSize = 14.sp)
                    )
                    Text(
                        "Sửa tên, chức vụ, cấp của mọi người, cấp quyền cao hơn, chiếm quyền phát, đẩy Live Cue, quản lý mã QR.",
                        modifier = Modifier.padding(top = 3.dp),
                        color = TextSecondary,
                        style = DamType.Small.copy(lineHeight = 17.sp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    "5 kênh tự tạo: Toàn sự kiện, Sân khấu, Kỹ thuật, Hậu cần – An ninh, Đón tiếp",
                    color = TextSecondary,
                    style = DamType.Muted
                )
            }

            Spacer(Modifier.weight(1f))
            PrimaryButton(
                text = "Tạo sự kiện",
                onClick = onEventCreated,
                modifier = Modifier.fillMaxWidth(),
                enabled = eventName.isNotBlank(),
                height = 54.dp
            )
            Text(
                "Đã tạo trước đó? Khôi phục bằng mã quản trị",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 10.dp)
                    .clickable(onClick = onNavigateToRestore),
                color = AccentCyan,
                style = DamType.Button.copy(fontSize = 13.sp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EventFieldLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        color = TextSecondary,
        style = DamType.Body.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
private fun EventNameInput(
    value: String,
    onValueChange: (String) -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, shape, spotColor = AccentCyan.copy(alpha = 0.18f))
            .clip(shape)
            .background(GlassBg)
            .border(1.5.dp, AccentCyan, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        singleLine = true,
        textStyle = DamType.Body.copy(
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        ),
        cursorBrush = SolidColor(AccentCyan)
    )
}

@Composable
private fun StaticEventField(value: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = InputShape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            value,
            color = TextPrimary,
            style = DamType.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun CreateEventScreenPreview() {
    DamThoaiTheme {
        CreateEventScreen(onBack = {}, onEventCreated = {}, onNavigateToRestore = {})
    }
}
