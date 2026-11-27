package com.example.damthoai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.damthoai.network.SignalingConnectionState
import com.example.damthoai.service.CallState
import com.example.damthoai.service.ConnectionMode
import com.example.damthoai.service.DamThoaiCallManager
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
import kotlin.random.Random

/**
 * TV1 - Màn hình "Kiểm tra mạng": dùng tầng mạng THẬT (không dùng dữ liệu giả) để thử
 * gọi PTT giữa 2 điện thoại:
 *  - Kết nối Signaling Server (nhập địa chỉ hoặc tự tìm qua mDNS) -> nhận STUN/TURN (Tuần 1)
 *  - Thấy ai đang online, gọi P2P và giữ nút để nói (Tuần 2)
 *  - Theo dõi đổi mạng / ICE restart (Tuần 3-4), chế độ LAN Fallback (Tuần 5), chỉ số getStats (Tuần 6)
 */
@Composable
fun NetworkTestScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { DamThoaiCallManager.getInstance(context.applicationContext) }

    val serverState by manager.serverConnectionState.collectAsState()
    val iceSummary by manager.iceServerSummary.collectAsState()
    val onlinePeers by manager.onlinePeers.collectAsState()
    val lanPeers by manager.lanPeers.collectAsState()
    val mode by manager.connectionMode.collectAsState()
    val callState by manager.callState.collectAsState()
    val pttPressed by manager.isPttPressed.collectAsState()
    val stats by manager.callStats.collectAsState()

    val initialSession = remember { com.example.damthoai.data.remote.EventSession.getSession() }
    var serverUrl by rememberSaveable { mutableStateOf(initialSession?.signalingUrl ?: "wss://damthoai-signaling.onrender.com") }
    var accessToken by rememberSaveable { mutableStateOf(initialSession?.signalingToken.orEmpty()) }
    var userId by rememberSaveable {
        mutableStateOf(initialSession?.userId ?: "${Build.MODEL.filter { it.isLetterOrDigit() }.take(10)}_${Random.nextInt(100, 999)}")
    }
    var hasMic by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    // Người vừa bấm "Gọi" khi chưa có quyền micro: chỉ gọi SAU KHI người dùng cấp quyền,
    // nếu gọi ngay thì micro chưa mở được và bên kia sẽ không nghe thấy gì.
    var pendingCallPeer by rememberSaveable { mutableStateOf<String?>(null) }
    var micDenied by rememberSaveable { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
        micDenied = !granted
        val peer = pendingCallPeer
        pendingCallPeer = null
        if (granted && peer != null) manager.startCall(peer)
    }

    val peers = if (mode == ConnectionMode.LAN_P2P) lanPeers.toList() else onlinePeers

    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackButton(onClick = onBack)
                Spacer(Modifier.width(10.dp))
                Text("Kiểm tra mạng", color = TextPrimary, style = DamType.Title21)
            }
            Text(
                "Mở màn này trên 2 điện thoại cùng nối 1 máy chủ, chọn tên của máy kia để gọi rồi giữ nút để nói.",
                color = TextSecondary,
                style = DamType.Body13Line19_5
            )

            // ---------------- 1. Máy chủ
            Section("1. Máy chủ Signaling") {
                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it.trim() },
                    label = { Text("Tên máy này") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it.trim() },
                    label = { Text("Địa chỉ máy chủ (ws://IP:8765 hoặc wss://...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accessToken,
                    onValueChange = { accessToken = it.trim() },
                    label = { Text("Mã truy cập (để trống nếu máy chủ không đặt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { manager.connect(serverUrl, userId, accessToken) },
                        enabled = userId.isNotBlank() && serverUrl.startsWith("ws")
                    ) { Text("Kết nối") }
                    OutlinedButton(
                        onClick = { manager.autoDiscoverAndConnect(userId, accessToken) },
                        enabled = userId.isNotBlank()
                    ) { Text("Tự tìm (mDNS)") }
                }
                InfoLine("Trạng thái", describe(serverState))
                InfoLine("Đường truyền", if (mode == ConnectionMode.LAN_P2P) "LAN trực tiếp (không có server)" else "Qua máy chủ")
                InfoLine("ICE", iceSummary)
            }

            // ---------------- 2. Người đang online
            Section("2. Đang online (${peers.size})") {
                if (peers.isEmpty()) {
                    Text("Chưa thấy máy nào khác.", color = TextSecondary, style = DamType.Body13)
                }
                peers.forEach { peer ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(peer, color = TextPrimary, style = DamType.Body14SemiBold, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = {
                                if (hasMic) {
                                    manager.startCall(peer)
                                } else {
                                    pendingCallPeer = peer
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = callState is CallState.Idle || callState is CallState.CallEnded
                        ) { Text("Gọi") }
                    }
                }
            }

            // ---------------- 3. Cuộc gọi + PTT
            Section("3. Cuộc gọi") {
                InfoLine("Trạng thái", describe(callState))
                if (micDenied) {
                    Text(
                        "Chưa có quyền micro nên không gọi được. Nếu không thấy hỏi lại, vào Cài đặt điện thoại > Ứng dụng > Đàm Thoại > Quyền để bật Micro.",
                        color = AccentRed,
                        style = DamType.Body13
                    )
                }
                if (!hasMic) {
                    OutlinedButton(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("Cấp quyền micro")
                    }
                }
                val inCall = callState is CallState.InCall || callState is CallState.Reconnecting
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .background(
                                when {
                                    !inCall -> Color.Gray.copy(alpha = 0.3f)
                                    pttPressed -> AccentRed
                                    else -> AccentCyan.copy(alpha = 0.8f)
                                },
                                CircleShape
                            )
                            .pointerInput(inCall, hasMic) {
                                if (!inCall || !hasMic) return@pointerInput
                                detectTapGestures(onPress = {
                                    manager.setPushToTalkPressed(true)
                                    try { tryAwaitRelease() } finally { manager.setPushToTalkPressed(false) }
                                })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (pttPressed) "ĐANG NÓI" else "GIỮ ĐỂ NÓI",
                            color = Color.White,
                            style = DamType.Body14Bold
                        )
                    }
                }
                if (inCall || callState is CallState.Calling) {
                    OutlinedButton(onClick = { manager.endCall() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Kết thúc", color = AccentRed)
                    }
                }
            }

            // ---------------- 4. Chất lượng đường truyền
            Section("4. Chất lượng (getStats)") {
                val s = stats
                if (s == null) {
                    Text("Có số liệu khi đang gọi.", color = TextSecondary, style = DamType.Body13)
                } else {
                    InfoLine("Đường đi", if (s.isRelayed) "Qua TURN relay" else "Trực tiếp (${s.localCandidateType ?: "?"})")
                    InfoLine("Mạng", s.localNetworkType ?: "?")
                    InfoLine("RTT", s.rttMs?.let { "$it ms" } ?: "?")
                    InfoLine("Mất gói", "%.1f%% (tổng %d)".format(s.intervalLossRate * 100, s.packetsLost))
                    InfoLine("Jitter", "%.1f ms".format(s.jitterMs))
                    InfoLine("Âm thanh bị vá", "%.1f%%".format(s.concealmentRatio * 100))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = TextPrimary, style = DamType.Body15.copy(fontWeight = FontWeight.Bold))
            content()
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = TextSecondary, style = DamType.Body13, modifier = Modifier.width(110.dp))
        Text(value, color = TextPrimary, style = DamType.Body13)
    }
}

private fun describe(state: SignalingConnectionState): String = when (state) {
    SignalingConnectionState.Disconnected -> "Chưa kết nối"
    SignalingConnectionState.Connecting -> "Đang kết nối..."
    SignalingConnectionState.Connected -> "Đã nối, đang đăng ký"
    is SignalingConnectionState.Registered -> "Đã đăng ký: ${state.userId}"
    is SignalingConnectionState.Error -> "Lỗi: ${state.message}"
}

private fun describe(state: CallState): String = when (state) {
    CallState.Idle -> "Rảnh"
    is CallState.Calling -> "Đang gọi ${state.targetUserId}..."
    is CallState.InCall -> "Đang nói chuyện với ${state.peerUserId}"
    is CallState.Reconnecting -> "Đổi mạng, đang nối lại (lần ${state.attempt})"
    is CallState.CallEnded -> "Đã kết thúc${state.reason?.let { ": $it" } ?: ""}"
}
