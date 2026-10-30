package com.example.damthoai.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.hardware.HandsFreeSettings
import com.example.damthoai.permission.PermissionManager

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var mdnsEnabled by remember { mutableStateOf(true) }
    val headsetEnabled by HandsFreeSettings.headsetButtonEnabled.collectAsState()
    val duckingEnabled by HandsFreeSettings.duckSecondaryEnabled.collectAsState()

    val hasAudioCamera = remember { PermissionManager.hasAudioAndCameraPermissions(context) }
    val hasNetworkBt = remember { PermissionManager.hasNetworkAndBluetoothPermissions(context) }
    val hasBackground = remember { PermissionManager.hasBackgroundExecutionPermissions(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "Cài đặt", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Permission Management (TV4 - Week 1)
        Text(text = "QUẢN LÝ QUYỀN HỆ THỐNG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "Trạng thái phân quyền Android", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "• Micro & Camera: ${if (hasAudioCamera) "Đã cấp" else "Chưa cấp"}", fontSize = 12.sp)
                Text(text = "• Mạng & Bluetooth: ${if (hasNetworkBt) "Đã cấp" else "Chưa cấp"}", fontSize = 12.sp)
                Text(text = "• Chạy nền & Tối ưu pin: ${if (hasBackground) "Đã cấp" else "Chưa cấp"}", fontSize = 12.sp)
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        activity?.let {
                            PermissionManager.requestPermissions(it, 1001)
                            if (!PermissionManager.isIgnoringBatteryOptimizations(it)) {
                                PermissionManager.requestIgnoreBatteryOptimizations(it)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Yêu cầu cấp lại quyền hệ thống")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 1
        Text(text = "KẾT NỐI ĐA TẦNG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "Internet (4G/5G, Wi-Fi)", fontWeight = FontWeight.Bold)
                Text(text = "Máy chủ SFU · đang dùng", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Mạng nội bộ (mDNS)", fontWeight = FontWeight.Bold)
                        Text(text = "Tự chuyển khi nghẽn mạng", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = mdnsEnabled, onCheckedChange = { mdnsEnabled = it })
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 2
        Text(text = "RẢNH TAY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Nút Play/Pause tai nghe", fontWeight = FontWeight.Bold)
                        Text(text = "Bấm để mở/tắt mic, có tiếng bíp", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = headsetEnabled,
                        onCheckedChange = HandsFreeSettings::setHeadsetButtonEnabled
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 3
        Text(text = "ÂM THANH", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Hạ âm kênh phụ (Audio Ducking)", fontWeight = FontWeight.Bold)
                        Text(text = "Khi kênh ưu tiên có người nói", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = duckingEnabled,
                        onCheckedChange = HandsFreeSettings::setDuckSecondaryEnabled
                    )
                }
            }
        }
    }
}
