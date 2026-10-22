/**
 * Path: app/src/main/java/com/example/damthoai/ui/onboarding/OnboardingScreen.kt
 *
 * Khối KDoc tiếng Việt:
 * Màn hình Giới thiệu ứng dụng (Onboarding Screen) gồm 3 slide minh họa.
 * Tích hợp HorizontalPager, nút Bỏ qua góc phải trên, chỉ báo chấm trang và nút Tiếp tục/Bắt đầu.
 */
package com.example.damthoai.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pageCount = 3
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()

    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            // Thanh tiêu đề trên: Nút "Bỏ qua" ở góc phải
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GlassBg)
                        .clickable { onFinishOnboarding() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Bỏ qua",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Thân Pager 3 slide
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Minh họa hình vẽ Compose tương ứng từng slide
                    when (page) {
                        0 -> Slide1Visual()
                        1 -> Slide2Visual()
                        else -> Slide3Visual()
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    // Tiêu đề
                    val title = when (page) {
                        0 -> "Vào kênh trong 3 giây"
                        1 -> "Nghe nhiều kênh, không lỡ lệnh"
                        else -> "Không bao giờ mất liên lạc"
                    }
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Mô tả
                    val description = when (page) {
                        0 -> "Quét mã QR của ban hoặc nhập tần số PIN 6 số. Tên và vai trò được gán sẵn."
                        1 -> "Nghe cùng lúc nhiều kênh. Khi Tổng đạo diễn nói, các kênh phụ tự hạ âm 80%."
                        else -> "Tự chuyển giữa Internet, Wi-Fi nội bộ và Bluetooth khi mạng hội trường bị nghẽn."
                    }
                    Text(
                        text = description,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chỉ báo chấm trang (Page Indicator Dots)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(if (isSelected) 24.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) AccentCyan else TextSecondary.copy(alpha = 0.3f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Nút chuyển slide hoặc Hoàn tất
            val isLastPage = pagerState.currentPage == pageCount - 1
            PrimaryButton(
                text = if (isLastPage) "Bắt đầu" else "Tiếp tục",
                onClick = {
                    if (isLastPage) {
                        onFinishOnboarding()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview
@Composable
private fun OnboardingScreenPreview() {
    DamThoaiTheme {
        OnboardingScreen(onFinishOnboarding = {})
    }
}
