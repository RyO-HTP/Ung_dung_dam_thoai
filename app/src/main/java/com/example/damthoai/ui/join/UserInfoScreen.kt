/**
 * Path: app/src/main/java/com/example/damthoai/ui/join/UserInfoScreen.kt
 *
 * Khối KDoc tiếng Việt:
 * Màn hình Nhập Thông Tin Cá Nhân (Tên hiển thị, Chức vụ, Cấp 2/Cấp 3) sau khi vào kênh thành công.
 * Tự động cập nhật chữ viết tắt avatar và hỗ trợ nút Chuyển Cấp độ Segmented.
 */
package com.example.damthoai.ui.join

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.components.SectionLabel
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.PillShape
import com.example.damthoai.ui.theme.PrimaryEnd
import com.example.damthoai.ui.theme.PrimaryStart
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
    var selectedLevel by remember { mutableStateOf(2) } // 2: Cấp 2, 3: Cấp 3

    // Tính chữ cái viết tắt của avatar (Ví dụ: "Minh Hậu" -> "MH")
    val avatarInitials = remember(displayName) {
        val parts = displayName.trim().split(" ").filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            "${parts.first().first()}${parts.last().first()}".uppercase()
        } else if (parts.isNotEmpty()) {
            parts.first().take(2).uppercase()
        } else {
            "DT"
        }
    }

    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Spacer(modifier = Modifier.width(16.dp))
                SectionLabel(text = "BƯỚC HOÀN TẤT")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Thông tin của bạn",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Điền đúng để mọi người điều phối dễ hơn",
                color = TextSecondary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Avatar tròn chữ viết tắt gradient
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(PrimaryStart, PrimaryEnd)))
                    .border(2.dp, AccentCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = avatarInitials,
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Ô Nhập Tên hiển thị
                    Column {
                        SectionLabel(text = "TÊN HIỂN THỊ")
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = GlassBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = GlassBg,
                                unfocusedContainerColor = GlassBg
                            )
                        )
                    }

                    // Ô Nhập Chức vụ
                    Column {
                        SectionLabel(text = "CHỨC VỤ TRONG BAN")
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = roleTitle,
                            onValueChange = { roleTitle = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = GlassBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedContainerColor = GlassBg,
                                unfocusedContainerColor = GlassBg
                            )
                        )
                    }

                    // Segmented Selector Cấp 2 / Cấp 3
                    Column {
                        SectionLabel(text = "CẤP CỦA BẠN")
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(PillShape)
                                .background(GlassBg)
                                .border(1.dp, GlassBorder, PillShape)
                                .padding(4.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Cấp 2 · Trưởng ban
                                val isLevel2Selected = selectedLevel == 2
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .clip(PillShape)
                                        .background(if (isLevel2Selected) Color.White else Color.Transparent)
                                        .clickable { selectedLevel = 2 },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Cấp 2 · Trưởng ban",
                                        color = if (isLevel2Selected) Color.Black else TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                // Cấp 3 · CTV
                                val isLevel3Selected = selectedLevel == 3
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .clip(PillShape)
                                        .background(if (isLevel3Selected) Color.White else Color.Transparent)
                                        .clickable { selectedLevel = 3 },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Cấp 3 · CTV",
                                        color = if (isLevel3Selected) Color.Black else TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Chọn đúng cấp của bạn. Nếu chọn sai, Tổng đạo diễn sẽ chỉnh lại. Cấp 1 chỉ do người tạo sự kiện cấp.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = "Vào kênh",
                onClick = onEnterChannel,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview
@Composable
private fun UserInfoScreenPreview() {
    DamThoaiTheme {
        UserInfoScreen(onBack = {}, onEnterChannel = {})
    }
}
