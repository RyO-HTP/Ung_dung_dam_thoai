package com.example.damthoai.ui.join
import com.example.damthoai.ui.theme.AvatarInk
import com.example.damthoai.ui.theme.ChipInk


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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.BlueAvatarA
import com.example.damthoai.ui.theme.BlueAvatarB
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.InputShape
import com.example.damthoai.ui.theme.cssGradient
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun UserInfoScreen(
    onBack: () -> Unit,
    onEnterChannel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayName by remember { mutableStateOf("Minh Hậu") }
    var roleTitle by remember { mutableStateOf("Trưởng ban Hậu cần") }
    var selectedLevel by remember { mutableStateOf(2) }
    var nameFocused by remember { mutableStateOf(true) }

    val initials = remember(displayName) {
        val parts = displayName.trim().split(" ").filter(String::isNotEmpty)
        when {
            parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
            parts.isNotEmpty() -> parts.first().take(2).uppercase()
            else -> "DT"
        }
    }

    DamThoaiBackground(modifier = modifier) {
        Column(
            Modifier.fillMaxSize()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClick = onBack)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Thông tin của bạn", color = TextPrimary, style = DamType.Heading.copy(fontSize = 16.sp))
                    Text("Điền đúng để mọi người điều phối dễ hơn", color = TextSecondary, style = DamType.Muted)
                }
                Spacer(Modifier.size(40.dp))
            }

            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(cssGradient(135f, BlueAvatarA, BlueAvatarB)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials,
                    color = AvatarInk,
                    style = DamType.Title.copy(fontSize = 28.sp, lineHeight = 34.sp)
                )
            }
            Spacer(Modifier.height(20.dp))

            ProfileField(
                label = "Tên hiển thị",
                value = displayName,
                onValueChange = { displayName = it },
                focused = nameFocused,
                onFocusChanged = { nameFocused = it },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            Spacer(Modifier.height(14.dp))
            ProfileField(
                label = "Chức vụ",
                value = roleTitle,
                onValueChange = { roleTitle = it },
                focused = false,
                onFocusChanged = {},
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth()) {
                Text("Cấp của bạn", color = TextSecondary, style = DamType.Muted.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LevelOption(
                        text = "Cấp 2 · Trưởng ban",
                        selected = selectedLevel == 2,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedLevel = 2 }
                    )
                    LevelOption(
                        text = "Cấp 3 · CTV",
                        selected = selectedLevel == 3,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedLevel = 3 }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Chọn đúng cấp của bạn. Nếu chọn sai, Tổng đạo diễn sẽ chỉnh lại. Cấp 1 chỉ do người tạo sự kiện cấp.",
                    color = TextSecondary,
                    style = DamType.Small.copy(lineHeight = 16.sp)
                )
            }

            Spacer(Modifier.weight(1f))
            PrimaryButton(
                text = "Vào kênh",
                onClick = onEnterChannel,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp
            )
        }
    }
}

@Composable
private fun ProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    focused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    keyboardOptions: KeyboardOptions
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = TextSecondary, style = DamType.Muted.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth()
                .height(52.dp)
                .shadow(if (focused) 14.dp else 0.dp, InputShape, spotColor = AccentCyan.copy(alpha = 0.18f))
                .clip(InputShape)
                .background(GlassBg)
                .border(if (focused) 1.5.dp else 1.dp, if (focused) AccentCyan else GlassBorder, InputShape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChanged(it.isFocused) },
                singleLine = true,
                textStyle = DamType.Body.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary),
                cursorBrush = Brush.horizontalGradient(listOf(AccentCyan, AccentCyan)),
                keyboardOptions = keyboardOptions
            )
        }
    }
}

@Composable
private fun LevelOption(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp)
            .clip(CircleShape)
            .background(if (selected) Color.White else GlassBg)
            .border(1.dp, if (selected) Color.White else GlassBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) ChipInk else TextPrimary,
            style = DamType.Chip,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun UserInfoScreenPreview() {
    DamThoaiTheme { UserInfoScreen(onBack = {}, onEnterChannel = {}) }
}
