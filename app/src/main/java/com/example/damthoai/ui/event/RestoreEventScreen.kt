package com.example.damthoai.ui.event

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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.R
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.outerGlow
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.components.SecondaryButton
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.TextPlaceholder
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun RestoreEventScreen(
    onBack: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var adminCode by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    DamThoaiBackground(modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 26.dp)
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
                    style = DamType.Heading16,
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
                style = DamType.Title24,
                textAlign = TextAlign.Center
            )
            Text(
                "Nhập mã quản trị đã lưu khi tạo sự kiện để quản lý lại trên máy này. Máy cũ sẽ mất quyền quản trị.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = TextSecondary,
                style = DamType.Body13_5Line20,
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
            val hasError = errorMessage != null
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (hasError) {
                            Modifier.outerGlow(AccentRed, corner = 18.dp, spread = 14.dp, maxAlpha = 0.25f)
                        } else {
                            Modifier.outerGlow(AccentCyan, corner = 18.dp, spread = 14.dp, maxAlpha = 0.18f)
                        }
                    )
                    .clip(shape)
                    .background(GlassBg)
                    .border(1.5.dp, if (hasError) AccentRed else AccentCyan, shape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = adminCode,
                    onValueChange = {
                        adminCode = it.uppercase()
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 40.dp),
                    singleLine = true,
                    textStyle = DamType.MonoInput.copy(color = TextPrimary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrect = false
                    ),
                    cursorBrush = SolidColor(AccentCyan),
                    decorationBox = { innerTextField ->
                        Box {
                            if (adminCode.isEmpty()) {
                                Text(
                                    text = "VD: ADM-7788",
                                    color = TextPlaceholder,
                                    style = DamType.MonoInput
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.CenterEnd)
                        .clip(CircleShape)
                        .clickable {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                adminCode = clipText.toString().trim().uppercase()
                                errorMessage = null
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_paste),
                        contentDescription = "Dán mã",
                        tint = if (hasError) AccentRed else AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            errorMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    color = AccentRed,
                    style = DamType.Small
                )
            }

            Spacer(Modifier.weight(1f))
            PrimaryButton(
                text = "Khôi phục",
                onClick = {
                    // Kiểm tra mã quản trị đơn giản tại local thay vì gọi Repository giả lập cũ
                    if (adminCode.isBlank()) {
                        errorMessage = "Mã quản trị không được để trống."
                    } else {
                        errorMessage = null
                        onRestore()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = adminCode.isNotBlank(),
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
