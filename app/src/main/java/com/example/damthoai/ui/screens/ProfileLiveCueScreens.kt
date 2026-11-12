package com.example.damthoai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import com.example.damthoai.data.LiveCue
import com.example.damthoai.ui.components.AvatarCircle
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.GlassTextField
import com.example.damthoai.ui.components.LiveCueCard
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.components.SectionLabel
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun LiveCueDraftScreen(
    onBack: () -> Unit,
    onSendCue: (LiveCue) -> Unit,
    modifier: Modifier = Modifier,
    initialCue: LiveCue = LiveCue(title = "Tiết mục tiếp theo", message = "Chuẩn bị ra sân khấu")
) {
    var title by remember { mutableStateOf(initialCue.title) }
    var message by remember { mutableStateOf(initialCue.message) }
    val previewCue = remember(title, message) {
        initialCue.copy(title = title, message = message)
    }

    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 26.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Live Cue",
                        color = TextPrimary,
                        style = DamType.Title21
                    )
                    Text(
                        text = "Hiển thị trên mọi thiết bị",
                        color = TextSecondary,
                        style = DamType.Muted
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionLabel("XEM TRƯỚC", color = TextSecondary)
                LiveCueCard(cue = previewCue)
                GlassTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Tiết mục đang diễn",
                    autoFocus = true
                )
                GlassTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = "Nhắc việc cho các ban"
                )
            }
            PrimaryButton(
                text = "Đẩy lên mọi máy",
                onClick = {
                    // Truyền trực tiếp đối tượng previewCue đã được cập nhật nội dung chuẩn xác
                    onSendCue(previewCue)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
        }
    }
}

@Composable
fun UserProfileScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayName by remember { mutableStateOf("Lan Phương") }
    var role by remember { mutableStateOf("Tổng đạo diễn") }

    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 26.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Hồ sơ của bạn",
                    color = TextPrimary,
                    style = DamType.Title21
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarCircle("LP", size = 72.dp)
                }
                GlassTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = "Tên hiển thị",
                    autoFocus = true
                )
                GlassTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = "Chức vụ"
                )
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_crown),
                                contentDescription = null,
                                tint = AccentAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Cấp của bạn",
                                color = TextSecondary,
                                style = DamType.Muted.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Cấp 1 · Người tạo sự kiện",
                                color = TextPrimary,
                                style = DamType.Heading
                            )
                            Text(
                                text = "Cấp cao nhất, không thể tự đổi. Thành viên khác tự chọn cấp của mình, bạn có thể sửa lại cho họ trong mục Thành viên.",
                                color = TextSecondary,
                                style = DamType.SmallLine17
                            )
                        }
                    }
                }
            }
            PrimaryButton(
                text = "Lưu",
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LiveCueDraftScreenPreview() {
    DamThoaiTheme {
        LiveCueDraftScreen({}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun UserProfileScreenPreview() {
    DamThoaiTheme {
        UserProfileScreen({}, {})
    }
}