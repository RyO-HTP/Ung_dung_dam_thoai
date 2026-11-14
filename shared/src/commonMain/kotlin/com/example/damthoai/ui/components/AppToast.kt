package com.example.damthoai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thông báo ngắn kiểu Toast, chạy giống nhau trên Android và iOS (iOS không có Toast hệ thống).
 * Gọi [AppToast.show] ở bất kỳ đâu; [AppToastHost] đặt một lần ở gốc giao diện.
 */
object AppToast {
    private data class Message(val text: String, val id: Long)

    private val _current = MutableStateFlow<Message?>(null)
    private var counter = 0L

    fun show(text: String) {
        _current.value = Message(text, ++counter)
    }

    @Composable
    fun Host() {
        val message by _current.asStateFlow().collectAsState()
        LaunchedEffect(message?.id) {
            if (message != null) {
                delay(2000)
                _current.value = null
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(visible = message != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = message?.text.orEmpty(),
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 64.dp, start = 24.dp, end = 24.dp)
                        .widthIn(max = 360.dp)
                        .background(Color(0xE6222233), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
fun AppToastHost() = AppToast.Host()
