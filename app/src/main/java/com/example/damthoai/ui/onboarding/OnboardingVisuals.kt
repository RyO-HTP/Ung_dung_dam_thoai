/**
 * Path: app/src/main/java/com/example/damthoai/ui/onboarding/OnboardingVisuals.kt
 *
 * Khối KDoc tiếng Việt:
 * Chứa các Composable minh họa giao diện thuần Compose (Canvas/Box) cho 3 slide Onboarding.
 * Không dùng ảnh bitmap, hoàn toàn tự vẽ bằng đồ họa vector và hiệu ứng kính mờ Đàm Thoại.
 */
package com.example.damthoai.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

/**
 * Minh họa Slide 1: Điện thoại có khung QR + Viên thuốc "Sân khấu" (dấu tick) + Viên thuốc "PIN 240826"
 */
@Composable
fun Slide1Visual(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(240.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(GlassBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Khung điện thoại giả lập
        Box(
            modifier = Modifier
                .size(160.dp, 200.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, GlassBorder, RoundedCornerShape(24.dp))
                .background(Color(0x22000000)),
            contentAlignment = Alignment.Center
        ) {
            // Khung quét QR góc cyan
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .border(2.dp, AccentCyan, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = AccentCyan.copy(alpha = 0.6f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Viên thuốc 1: "Sân khấu" + dấu tick xanh
        Row(
            modifier = Modifier
                .offset(x = (-40).dp, y = (-70).dp)
                .clip(CircleShape)
                .background(Color(0xFF142454))
                .border(1.dp, AccentMint, CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = AccentMint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Sân khấu",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Viên thuốc 2: "PIN 240826" font mono
        Box(
            modifier = Modifier
                .offset(x = 40.dp, y = 70.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1038))
                .border(1.dp, AccentCyan, CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "PIN 240826",
                color = AccentCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Minh họa Slide 2: Vòng tròn cam vương miện Tổng đạo diễn -> đường nét đứt -> 3 kênh hạ âm -80%
 */
@Composable
fun Slide2Visual(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // Nối nét đứt Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startX = size.width / 2
            val startY = 48.dp.toPx()
            val stroke = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))

            val targets = listOf(
                Offset(size.width * 0.20f, size.height * 0.75f),
                Offset(size.width * 0.50f, size.height * 0.75f),
                Offset(size.width * 0.80f, size.height * 0.75f)
            )

            targets.forEach { target ->
                drawLine(
                    color = AccentCyan.copy(alpha = 0.5f),
                    start = Offset(startX, startY),
                    end = target,
                    strokeWidth = stroke.width,
                    pathEffect = stroke.pathEffect
                )
            }
        }

        // Vòng tròn Cam-Vàng Tổng đạo diễn (Vương miện)
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(AccentAmber)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = Color(0xFF3D2000),
                modifier = Modifier.size(28.dp)
            )
        }

        // 3 Vòng tròn kênh phụ dưới
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 130.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val channels = listOf("Sân khấu", "Kỹ thuật", "Hậu cần")
            channels.forEach { channelName ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(GlassBg)
                            .border(1.5.dp, AccentCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        // Nhãn -80% màu vàng
                        Box(
                            modifier = Modifier
                                .offset(y = (-18).dp)
                                .clip(CircleShape)
                                .background(AccentAmber)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text("-80%", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(channelName, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Minh họa Slide 3: Quả cầu radio ở giữa, các quỹ đạo tròn nét đứt/liền và các nút mạng quanh quỹ đạo
 */
@Composable
fun Slide3Visual(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        // Vẽ quỹ đạo tròn bằng Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = AccentCyan.copy(alpha = 0.3f),
                radius = 70.dp.toPx(),
                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f))
            )
            drawCircle(
                color = AccentCyan.copy(alpha = 0.15f),
                radius = 105.dp.toPx(),
                style = Stroke(width = 2f)
            )
        }

        // Quả cầu radio ở trung tâm
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color(0xFF3366FF))
                .border(2.dp, AccentCyan, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Radio,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }

        // Các nút vệ tinh xung quanh quỹ đạo (SK, HC, KT, ĐĐ, MC, AN)
        val nodeLabels = listOf(
            Triple("SK", "Sân khấu", Offset(-80f, -50f)),
            Triple("HC", "Hậu cần", Offset(80f, -50f)),
            Triple("KT", null, Offset(-95f, 40f)),
            Triple("ĐĐ", null, Offset(95f, 40f)),
            Triple("MC", null, Offset(0f, -100f)),
            Triple("AN", null, Offset(0f, 100f))
        )

        nodeLabels.forEach { (shortCode, fullLabel, offset) ->
            Box(
                modifier = Modifier
                    .offset(x = offset.x.dp, y = offset.y.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF142454))
                    .border(1.dp, AccentCyan, CircleShape)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(shortCode, color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (fullLabel != null) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(fullLabel, color = TextPrimary, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

// Previews
@Preview(showBackground = true, backgroundColor = 0xFF060A1F)
@Composable
private fun SlidesPreview() {
    DamThoaiTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Slide1Visual()
            Slide2Visual()
            Slide3Visual()
        }
    }
}
