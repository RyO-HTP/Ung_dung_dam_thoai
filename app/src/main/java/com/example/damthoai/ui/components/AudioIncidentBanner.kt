package com.example.damthoai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.damthoai.R
import com.example.damthoai.hardware.audio.AudioIncident
import com.example.damthoai.hardware.audio.AudioIncidentBus
import com.example.damthoai.permission.PermissionManager
import com.example.damthoai.ui.components.outerGlow
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

/**
 * Banner hiển thị thông báo sự cố âm thanh hệ thống.
 * Tự động chọn màu sắc và icon theo loại sự cố, có nút đóng và nút hành động khi bị thu hồi quyền.
 */
@Composable
fun AudioIncidentBanner(
    modifier: Modifier = Modifier,
    incident: AudioIncident? = null,
    compactSingleLine: Boolean = false,
    showDismiss: Boolean = true,
    onDismiss: () -> Unit = { AudioIncidentBus.dismiss() },
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = incident != null,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = modifier
    ) {
        if (incident == null) return@AnimatedVisibility

        val (color, iconRes) = when (incident) {
            is AudioIncident.HeadsetReconnected,
            is AudioIncident.AudioFocusRegained -> Pair(AccentMint, R.drawable.ic_check)

            is AudioIncident.MicUnavailable,
            is AudioIncident.AudioPermissionRevoked,
            is AudioIncident.Unknown -> Pair(AccentRed, R.drawable.ic_alert)

            else -> Pair(AccentAmber, R.drawable.ic_alert)
        }

        val shape = RoundedCornerShape(20.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .outerGlow(color, corner = 20.dp, spread = 12.dp, maxAlpha = 0.18f)
                .clip(shape)
                .background(GlassBg)
                .border(1.dp, color.copy(alpha = 0.5f), shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = incident.userMessage,
                    color = TextPrimary,
                    style = if (compactSingleLine) DamType.Small else DamType.Body14SemiBold
                )
                if (!compactSingleLine && incident is AudioIncident.AudioPermissionRevoked) {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color.copy(alpha = 0.16f))
                            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable { PermissionManager.openAppSettings(context) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Mở cài đặt",
                            color = color,
                            style = DamType.Small11Bold
                        )
                    }
                }
            }
            if (showDismiss) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_del),
                        contentDescription = "Đóng",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AudioIncidentBannerAmberPreview() {
    DamThoaiTheme {
        DamThoaiBackground {
            Box(Modifier.padding(20.dp)) {
                AudioIncidentBanner(
                    incident = AudioIncident.HeadsetUnplugged(deviceType = 1)
                )
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AudioIncidentBannerRedPreview() {
    DamThoaiTheme {
        DamThoaiBackground {
            Box(Modifier.padding(20.dp)) {
                AudioIncidentBanner(
                    incident = AudioIncident.AudioPermissionRevoked(permission = "RECORD_AUDIO")
                )
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AudioIncidentBannerGreenPreview() {
    DamThoaiTheme {
        DamThoaiBackground {
            Box(Modifier.padding(20.dp)) {
                AudioIncidentBanner(
                    incident = AudioIncident.HeadsetReconnected(deviceType = 1)
                )
            }
        }
    }
}
