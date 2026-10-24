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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    OnboardingScreenContent(onFinishOnboarding, modifier)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingScreenContent(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0
) {
    val pageCount = 3
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()

    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 26.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(30.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bỏ qua",
                    color = TextMuted,
                    style = DamType.Muted.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    modifier = Modifier.clickable(onClick = onFinishOnboarding)
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (page) {
                            0 -> Slide1Visual()
                            1 -> Slide2Visual()
                            else -> Slide3Visual()
                        }
                    }

                    Text(
                        text = titles[page],
                        color = com.example.damthoai.ui.theme.TextPrimary,
                        style = DamType.TitleIntro,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = descriptions[page],
                        color = TextMuted,
                        style = DamType.Body.copy(lineHeight = 21.sp),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (pagerState.currentPage == index) 26.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(if (pagerState.currentPage == index) AccentCyan else TextMuted.copy(alpha = 0.25f))
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = if (pagerState.currentPage == pageCount - 1) "Bắt đầu" else "Tiếp tục",
                onClick = {
                    if (pagerState.currentPage == pageCount - 1) onFinishOnboarding()
                    else coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp
            )
        }
    }
}

private val titles = listOf(
    "Vào kênh trong 3 giây",
    "Nghe nhiều kênh, không lỡ lệnh",
    "Không bao giờ mất liên lạc"
)

private val descriptions = listOf(
    "Quét mã QR của ban hoặc nhập tần số PIN 6 số. Tên và vai trò được gán sẵn.",
    "Nghe cùng lúc nhiều kênh. Khi Tổng đạo diễn nói, các kênh phụ tự hạ âm 80%.",
    "Tự chuyển giữa Internet, Wi-Fi nội bộ và Bluetooth khi mạng hội trường bị nghẽn."
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingSlide1Preview() {
    DamThoaiTheme { OnboardingScreenContent(onFinishOnboarding = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingSlide2Preview() {
    DamThoaiTheme { OnboardingScreenContent(onFinishOnboarding = {}, initialPage = 1) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingSlide3Preview() {
    DamThoaiTheme { OnboardingScreenContent(onFinishOnboarding = {}, initialPage = 2) }
}
