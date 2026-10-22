/**
 * Path: app/src/main/java/com/example/damthoai/ui/join/JoinChannelScreen.kt
 *
 * Khối KDoc tiếng Việt:
 * Màn hình Vào Kênh với 2 Tab: Quét mã QR & Nhập mã PIN 6 số.
 * Bao gồm:
 * - Tab Quét QR: Khung xem camera, 4 góc ngắm cyan, vạch quét chạy ngang, xử lý chưa có quyền camera.
 * - Tab Nhập PIN: Bàn phím kính 3x4 tự vẽ, mã PIN định dạng 3-3 cyan mono nhấp nháy cursor.
 * - Màn lỗi Không vào được kênh với 3 gạch đầu dòng lý do.
 */
package com.example.damthoai.ui.join

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.MonospacePinStyle
import com.example.damthoai.ui.theme.PillShape
import com.example.damthoai.ui.theme.PrimaryEnd
import com.example.damthoai.ui.theme.PrimaryStart
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

/**
 * Màn hình Chính Vào Kênh (Chứa 2 Tab)
 */
@Composable
fun JoinChannelScreen(
    onBack: () -> Unit,
    onSuccessJoin: () -> Unit,
    onNavigateToCreateEvent: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Quét QR, 1: Nhập PIN
    var isCameraGranted by remember { mutableStateOf(true) } // // TODO(TV4): Lấy quyền camera thực
    var showJoinError by remember { mutableStateOf(false) }

    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Nút back + Tiêu đề
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Vào kênh",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Thanh chuyển Tab dạng viên thuốc "Quét QR" | "Nhập tần số"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(PillShape)
                    .background(GlassBg)
                    .border(1.dp, GlassBorder, PillShape)
                    .padding(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    val tabGradient = Brush.horizontalGradient(listOf(PrimaryStart, PrimaryEnd))

                    // Tab Quét QR
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(PillShape)
                            .background(if (selectedTab == 0) tabGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                            .clickable { selectedTab = 0 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Quét QR",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Tab Nhập tần số
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(PillShape)
                            .background(if (selectedTab == 1) tabGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                            .clickable { selectedTab = 1 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nhập tần số",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Hiển thị nội dung Tab tương ứng
            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == 0) {
                    QrScanTab(
                        isCameraGranted = isCameraGranted,
                        onRequestCamera = { isCameraGranted = true },
                        onSwitchToPin = { selectedTab = 1 },
                        onQrScanned = { onSuccessJoin() },
                        onTriggerMockError = { showJoinError = true }
                    )
                } else {
                    PinInputTab(
                        onPinSubmit = { pin ->
                            if (pin == "000000") showJoinError = true else onSuccessJoin()
                        }
                    )
                }
            }

            // Link tạo sự kiện ở cuối
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Muốn tạo sự kiện mới? Tạo sự kiện ›",
                color = AccentCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onNavigateToCreateEvent() }
                    .padding(8.dp)
            )
        }

        // Màn hình/Hộp thoại Báo Lỗi "Không vào được kênh"
        if (showJoinError) {
            JoinErrorDialog(
                onRetryQr = {
                    showJoinError = false
                    selectedTab = 0
                },
                onSwitchPin = {
                    showJoinError = false
                    selectedTab = 1
                }
            )
        }
    }
}

/**
 * Nội dung Tab Quét QR (Có hiệu ứng tia quét chạy animation)
 */
@Composable
private fun QrScanTab(
    isCameraGranted: Boolean,
    onRequestCamera: () -> Unit,
    onSwitchToPin: () -> Unit,
    onQrScanned: () -> Unit,
    onTriggerMockError: () -> Unit
) {
    if (!isCameraGranted) {
        // Trạng thái CHƯA cấp quyền Camera
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(GlassBg)
                    .border(1.dp, GlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Cần quyền Camera để quét QR",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Camera chỉ dùng để đọc mã QR của ban, app không ghi hình.",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(28.dp))
            PrimaryButton(
                text = "Cấp quyền Camera",
                onClick = onRequestCamera,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            SecondaryButton(
                text = "Nhập PIN thay thế",
                onClick = onSwitchToPin,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        // Trạng thái ĐÃ CÓ CAMERA PREVIEW
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Khung Camera Viewfinder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF030614))
                    .border(1.5.dp, GlassBorder, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Composable QrScannerView
                QrScannerView(onQrScanned = { _ -> onQrScanned() })

                // 4 góc ngắm cyan
                Canvas(modifier = Modifier.size(200.dp)) {
                    val cornerLen = 28.dp.toPx()
                    val strokeWidth = 4.dp.toPx()
                    val w = size.width
                    val h = size.height

                    // Góc trên trái
                    drawLine(AccentCyan, Offset(0f, 0f), Offset(cornerLen, 0f), strokeWidth)
                    drawLine(AccentCyan, Offset(0f, 0f), Offset(0f, cornerLen), strokeWidth)
                    // Góc trên phải
                    drawLine(AccentCyan, Offset(w, 0f), Offset(w - cornerLen, 0f), strokeWidth)
                    drawLine(AccentCyan, Offset(w, 0f), Offset(w, cornerLen), strokeWidth)
                    // Góc dưới trái
                    drawLine(AccentCyan, Offset(0f, h), Offset(cornerLen, h), strokeWidth)
                    drawLine(AccentCyan, Offset(0f, h), Offset(0f, h - cornerLen), strokeWidth)
                    // Góc dưới phải
                    drawLine(AccentCyan, Offset(w, h), Offset(w - cornerLen, h), strokeWidth)
                    drawLine(AccentCyan, Offset(w, h), Offset(w, h - cornerLen), strokeWidth)
                }

                // Animation vạch quét ngang màu Cyan chạy lên xuống
                val infiniteTransition = rememberInfiniteTransition(label = "scanLine")
                val scanY by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 200f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scanY"
                )

                Box(
                    modifier = Modifier
                        .size(190.dp, 2.dp)
                        .offset(y = (scanY - 100).dp)
                        .background(AccentCyan)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Quét mã QR của ban. Tên, vai trò và kênh sẽ được gán tự động.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
            SecondaryButton(
                text = "Mô phỏng Quét QR thất bại (Debug)",
                onClick = onTriggerMockError,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Composable QrScannerView tách riêng để dễ thay CameraX / ZXing
 */
@Composable
fun QrScannerView(
    onQrScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // // TODO(TV4): Tích hợp CameraX ImageAnalysis + ML Kit QR Barcode Scanner thực sự
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { onQrScanned("MOCK_QR_EVENT_889900") },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.QrCodeScanner,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(120.dp)
        )
        Text(
            text = "Chạm vào ô camera để mô phỏng Quét QR thành công",
            color = AccentCyan.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        )
    }
}

/**
 * Nội dung Tab Nhập Tần Số (PIN 6 Số + Bàn phím số tự vẽ 3x4)
 */
@Composable
private fun PinInputTab(
    onPinSubmit: (String) -> Unit
) {
    var pinDigits by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Thẻ hiển thị Mã PIN cỡ lớn font Mono
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SectionLabel(text = "TẦN SỐ ẢO · PIN 6 SỐ")
                Spacer(modifier = Modifier.height(12.dp))

                // Định dạng hiển thị "240 82_"
                val formattedPin = buildString {
                    for (i in 0 until 6) {
                        if (i == 3) append(" ")
                        if (i < pinDigits.length) {
                            append(pinDigits[i])
                        } else if (i == pinDigits.length) {
                            append("_") // Cursor nhấp nháy
                        } else {
                            append("•")
                        }
                    }
                }

                Text(
                    text = formattedPin,
                    style = MonospacePinStyle,
                    fontSize = 32.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bàn phím số tự vẽ 3x4
        GlassKeypad(
            onDigitClick = { digit ->
                if (pinDigits.length < 6) pinDigits += digit
            },
            onDeleteClick = {
                if (pinDigits.isNotEmpty()) pinDigits = pinDigits.dropLast(1)
            },
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Nút Vào Kênh (Chỉ bật khi đủ 6 số)
        PrimaryButton(
            text = "Vào kênh",
            enabled = pinDigits.length == 6,
            onClick = { onPinSubmit(pinDigits) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Bàn phím kính 3x4 tự vẽ (1-9, trống - 0 - ⌫)
 */
@Composable
private fun GlassKeypad(
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (key.isNotEmpty()) GlassBg else Color.Transparent)
                            .border(if (key.isNotEmpty()) 1.dp else 0.dp, GlassBorder, RoundedCornerShape(16.dp))
                            .clickable(enabled = key.isNotEmpty()) {
                                if (key == "⌫") onDeleteClick() else if (key.isNotEmpty()) onDigitClick(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

/**
 * Màn hình Báo Lỗi "Không vào được kênh"
 */
@Composable
fun JoinErrorDialog(
    onRetryQr: () -> Unit,
    onSwitchPin: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon Cảnh báo Vàng
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(AccentAmber.copy(alpha = 0.2f))
                        .border(1.5.dp, AccentAmber, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AccentAmber,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Không vào được kênh",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hãy kiểm tra lại các nguyên nhân thường gặp",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Thẻ Danh sách 3 gạch đầu dòng chấm vàng
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x11FFFFFF))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BulletPointText("Mã QR hoặc PIN sai, hoặc đã bị Trưởng ban đặt lại")
                    BulletPointText("Sự kiện đã kết thúc")
                    BulletPointText("Điện thoại chưa có kết nối mạng")
                }

                Spacer(modifier = Modifier.height(20.dp))

                PrimaryButton(
                    text = "Quét lại",
                    onClick = onRetryQr,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                SecondaryButton(
                    text = "Nhập PIN",
                    onClick = onSwitchPin,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BulletPointText(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("• ", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text, color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Preview
@Composable
private fun JoinChannelScreenPreview() {
    DamThoaiTheme {
        JoinChannelScreen(onBack = {}, onSuccessJoin = {}, onNavigateToCreateEvent = {})
    }
}
