package com.example.damthoai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.damthoai.service.DamThoaiCallManager
import com.example.damthoai.service.ServerStatus
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

/**
 * Thanh cảnh báo khi thiết bị CHƯA kết nối được máy chủ. Ẩn khi đã kết nối.
 * Mục đích: người dùng biết ngay vì sao danh sách thành viên trống, thay vì tưởng chỉ có mình trong sự kiện.
 */
@Composable
fun ServerStatusBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val callManager = remember { DamThoaiCallManager.getInstance(context) }
    val status by callManager.serverStatus.collectAsStateWithLifecycle()

    val (color, title, detail) = when (val s = status) {
        is ServerStatus.Idle, is ServerStatus.Connected -> return
        is ServerStatus.Reconnecting -> Triple(
            AccentAmber, "Mất kết nối máy chủ, đang nối lại…", "Danh sách thành viên có thể chưa cập nhật."
        )
        is ServerStatus.Searching -> Triple(
            AccentAmber, "Đang kết nối máy chủ…", "Máy chủ đám mây có thể mất tới 1 phút để khởi động lần đầu."
        )
        is ServerStatus.NotFound -> Triple(
            AccentRed,
            "Không tìm thấy máy chủ",
            "Kiểm tra kết nối Internet (Wi-Fi hoặc 4G). App sẽ tự thử lại." +
                (if (s.lastUrl.isNotBlank()) " Địa chỉ đã thử: ${s.lastUrl}" else "")
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = DamType.Body14SemiBold)
            Text(detail, color = TextSecondary, style = DamType.Small)
        }
        if (status is ServerStatus.NotFound) {
            Spacer(Modifier.width(8.dp))
            Text(
                "Thử lại",
                color = Color.Black,
                style = DamType.Body14SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
                    .clickable { callManager.retryServerConnection() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
