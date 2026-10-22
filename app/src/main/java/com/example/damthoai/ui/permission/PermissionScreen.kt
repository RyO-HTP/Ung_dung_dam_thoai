/**
 * Path: app/src/main/java/com/example/damthoai/ui/permission/PermissionScreen.kt
 *
 * Khối KDoc tiếng Việt:
 * Các màn hình Yêu cầu Cấp Quyền:
 * 1. Màn "Bước cuối cùng" (4 công tắc Micro, Thông báo, Bluetooth, Chạy nền)
 * 2. Màn "Cần quyền Micro" (Cảnh báo khi từ chối Micro)
 */
package com.example.damthoai.ui.permission

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.components.SecondaryButton
import com.example.damthoai.ui.components.SectionLabel
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DangerStart
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

/**
 * Màn hình "Bước cuối cùng" - Yêu cầu cấp 4 quyền ứng dụng cơ bản
 */
@Composable
fun GrantPermissionScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    uiState: PermissionUiState = PermissionUiState()
) {
    // // TODO(TV4): Lấy trạng thái công tắc từ PermissionManager của Người 4
    var micState by remember { mutableStateOf(uiState.isMicrophoneGranted) }
    var notiState by remember { mutableStateOf(uiState.isNotificationGranted) }
    var btState by remember { mutableStateOf(uiState.isBluetoothGranted) }
    var bgState by remember { mutableStateOf(uiState.isBackgroundGranted) }

    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Spacer(modifier = Modifier.width(16.dp))
                SectionLabel(text = "THIẾT LẬP BAN ĐẦU")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Bước cuối cùng",
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Cấp quyền để bộ đàm hoạt động trơn tru.",
                color = TextSecondary,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Danh sách 4 thẻ quyền
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Thẻ Micro [BẮT BUỘC]
                PermissionRowCard(
                    title = "Micro",
                    description = "Để mọi người nghe bạn nói",
                    isGranted = micState,
                    isRequired = true,
                    onToggle = { micState = it }
                )

                // Thẻ Thông báo
                PermissionRowCard(
                    title = "Thông báo",
                    description = "Nhận cảnh báo khẩn cấp",
                    isGranted = notiState,
                    onToggle = { notiState = it }
                )

                // Thẻ Bluetooth
                PermissionRowCard(
                    title = "Bluetooth và thiết bị ở gần",
                    description = "Tai nghe, kết nối ngang hàng",
                    isGranted = btState,
                    onToggle = { btState = it }
                )

                // Thẻ Chạy nền
                PermissionRowCard(
                    title = "Chạy nền",
                    description = "Giữ app hoạt động khi tắt màn hình",
                    isGranted = bgState,
                    onToggle = { bgState = it }
                )
            }

            // Ghi chú Camera bên dưới
            Text(
                text = "Camera sẽ được hỏi khi bạn quét mã QR lần đầu.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            PrimaryButton(
                text = "Tiếp tục",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Thẻ Hàng Ngang hiển thị 1 Quyền + công tắc Switch
 */
@Composable
private fun PermissionRowCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onToggle: (Boolean) -> Unit,
    isRequired: Boolean = false
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isRequired) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AccentRed.copy(alpha = 0.2f))
                                .border(1.dp, AccentRed, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "BẮT BUỘC",
                                color = AccentRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = isGranted,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TextPrimary,
                    checkedTrackColor = AccentCyan,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = GlassBg
                )
            )
        }
    }
}

/**
 * Màn hình "Cần quyền Micro" khi người dùng từ chối quyền Micro
 */
@Composable
fun MicrophonePermissionScreen(
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon Mic gạch chéo trong vòng tròn đỏ
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(DangerStart.copy(alpha = 0.2f))
                    .border(2.dp, DangerStart, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = null,
                    tint = DangerStart,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Cần quyền Micro",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Không có micro thì bạn không nói được. Hãy bật quyền Micro cho Đàm Thoại trong cài đặt điện thoại.",
                color = TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            PrimaryButton(
                text = "Mở cài đặt điện thoại",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            SecondaryButton(
                text = "Tôi đã bật, thử lại",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview
@Composable
private fun GrantPermissionScreenPreview() {
    DamThoaiTheme {
        GrantPermissionScreen(onContinue = {}, onBack = {})
    }
}

@Preview
@Composable
private fun MicrophonePermissionScreenPreview() {
    DamThoaiTheme {
        MicrophonePermissionScreen(onOpenSettings = {}, onRetry = {})
    }
}
