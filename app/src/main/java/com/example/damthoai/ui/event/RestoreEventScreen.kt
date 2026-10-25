package com.example.damthoai.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.R
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun RestoreEventScreen(
    onBack: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    var adminCode by rememberSaveable { mutableStateOf("K7M2-93QX") }

    DamThoaiBackground(modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 26.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Text(
                    "Khôi phục sự kiện",
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    style = DamType.Heading.copy(fontSize = 16.sp),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.size(40.dp))
            }

            Spacer(Modifier.height(22.dp))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(AccentCyan.copy(alpha = 0.1f))
                    .border(1.5.dp, AccentCyan.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lock),
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Lấy lại quyền quản trị",
                modifier = Modifier.fillMaxWidth(),
                color = TextPrimary,
                style = DamType.Title.copy(fontSize = 24.sp, lineHeight = 29.sp),
                textAlign = TextAlign.Center
            )
            Text(
                "Nhập mã quản trị đã lưu khi tạo sự kiện để quản lý lại trên máy này. Máy cũ sẽ mất quyền quản trị.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = TextSecondary,
                style = DamType.Body.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(26.dp))
            Text(
                "Mã quản trị",
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                color = TextSecondary,
                style = DamType.Body.copy(fontWeight = FontWeight.SemiBold)
            )
            val shape = RoundedCornerShape(18.dp)
            BasicTextField(
                value = adminCode,
                onValueChange = { adminCode = it.uppercase() },
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, shape, spotColor = AccentCyan.copy(alpha = 0.18f))
                    .clip(shape)
                    .background(GlassBg)
                    .border(1.5.dp, AccentCyan, shape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                singleLine = true,
                textStyle = DamType.Mono.copy(
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrect = false
                ),
                cursorBrush = SolidColor(AccentCyan)
            )

            Spacer(Modifier.weight(1f))
            PrimaryButton(
                text = "Khôi phục",
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RestoreEventScreenPreview() {
    DamThoaiTheme {
        RestoreEventScreen(onBack = {}, onRestore = {})
    }
}
