package com.example.damthoai.hardware
import android.content.pm.ApplicationInfo
import android.media.AudioDeviceInfo
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.damthoai.R
import com.example.damthoai.hardware.diagnostics.FieldTestLogger
import com.example.damthoai.hardware.audio.AudioIncident
import com.example.damthoai.hardware.audio.AudioIncidentBus
import com.example.damthoai.hardware.emergency.EmergencyAlert
import com.example.damthoai.hardware.emergency.EmergencyAlertManager
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.KitSwitch
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.tooling.preview.Preview

/**
 * =================================================================================
 * PHẦN GIAO DIỆN DEMO: Màn hình test ánh xạ phím cứng PTT (Hardware Key Demo)
 * VAI TRÒ: Thành viên 4 (Hardware / Event Integration) - Tuần 2
 * =================================================================================
 *
 * Màn hình Compose DEMO cho phép Thành viên 4 (và nhóm) test trực tiếp việc bấm phím cứng Volume:
 * 1. Đổi giao diện lớn thành "ĐANG NÓI (MIC BẬT)" hoặc "ĐANG NGHE (MIC TẮT)".
 * 2. Hiển thị nguồn phím bấm chính xác ([PttSource.VOLUME_UP], [PttSource.VOLUME_DOWN], etc.).
 * 3. Cho phép bật/tắt chế độ bắt phím và chuyển đổi cấu hình (Cả 2 phím / Chỉ Vol- / Chỉ Vol+).
 * 4. Tích hợp nút PTT cảm ứng trên màn hình để thử nghiệm cả 2 cách kích hoạt.
 *
 * KIỂM CHỨNG TRÊN MÁY THẬT:
 * Bạn bấm và giữ phím Volume trên cạnh điện thoại -> Màn hình đổi sang màu đỏ "ĐANG NÓI"
 * và ÂM LƯỢNG MÁY KHÔNG BỊ THAY ĐỔI. Thả phím ra -> Đổi về màu xám/xanh "ĐANG NGHE".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PttKeyDemoScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    DisposableEffect(Unit) {
        PttController.acquireCapture("ptt_demo")
        onDispose { PttController.releaseCapture("ptt_demo") }
    }

    val isPttActive by PttController.isPttActive.collectAsStateWithLifecycle()
    val activeSource by PttController.activeSource.collectAsStateWithLifecycle()
    val isPttModeEnabled by PttController.isPttModeEnabled.collectAsStateWithLifecycle()
    val volumeKeySetting by PttController.volumeKeySetting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val isEmergencyVibrating by EmergencyAlertManager.isVibrating.collectAsStateWithLifecycle()
    val isEmergencyFlashing by EmergencyAlertManager.isFlashing.collectAsStateWithLifecycle()
    val flashUnavailableReason by EmergencyAlertManager.flashUnavailableReason.collectAsStateWithLifecycle()
    val vibrationUnavailableReason by EmergencyAlertManager.vibrationUnavailableReason.collectAsStateWithLifecycle()
    val recentAudioIncidents by AudioIncidentBus.recentIncidents.collectAsStateWithLifecycle()
    val fieldLogActive by FieldTestLogger.loggingActive.collectAsStateWithLifecycle()
    val fieldLogDirectory by FieldTestLogger.outputDirectoryPath.collectAsStateWithLifecycle()
    val fieldLogFile by FieldTestLogger.currentCsvPath.collectAsStateWithLifecycle()
    val fieldPressCounts by FieldTestLogger.pressCounts.collectAsStateWithLifecycle()
    val duplicateKeyCount by FieldTestLogger.duplicateKeyCount.collectAsStateWithLifecycle()
    val ignoredKeyCount by FieldTestLogger.ignoredKeyCount.collectAsStateWithLifecycle()
    val bluetoothLatencyEstimateMs by FieldTestLogger.bluetoothLatencyEstimateMs.collectAsStateWithLifecycle()

    val animatedBgColor by animateColorAsState(
        targetValue = if (isPttActive) AccentRed else AccentMint,
        animationSpec = tween(durationMillis = 150),
        label = "PttBgColor"
    )

    DamThoaiBackground(modifier = modifier) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Demo Phím Cứng PTT (Tuần 2)",
                            color = TextPrimary,
                            style = DamType.Heading.copy(fontSize = 18.sp)
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) BackButton(onClick = onBack)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = TextPrimary
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // HỘP HIỂN THỊ TRẠNG THÁI PTT LỚN (ĐANG NÓI / ĐANG NGHE)
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(20.dp),
                backgroundColor = animatedBgColor,
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(if (isPttActive) R.drawable.ic_mic else R.drawable.ic_mute),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isPttActive)
                            stringResource(id = R.string.ptt_status_speaking)
                        else
                            stringResource(id = R.string.ptt_status_listening),
                        color = Color.White,
                        style = DamType.Title.copy(fontSize = 20.sp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (activeSource != null)
                            stringResource(id = R.string.ptt_source_label, activeSource!!.displayName)
                        else
                            stringResource(id = R.string.ptt_source_none),
                        color = Color.White.copy(alpha = 0.85f),
                        style = DamType.Body
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isDebuggable) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.emergency_demo_title),
                            style = DamType.Heading
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                if (isEmergencyVibrating) {
                                    R.string.emergency_demo_vibrating
                                } else {
                                    R.string.emergency_demo_not_vibrating
                                }
                            ),
                            style = DamType.Body
                        )
                        Text(
                            text = stringResource(
                                if (isEmergencyFlashing) {
                                    R.string.emergency_demo_flashing
                                } else {
                                    R.string.emergency_demo_not_flashing
                                }
                            ),
                            style = DamType.Body
                        )
                        flashUnavailableReason?.let { reason ->
                            Text(text = reason, style = DamType.Small)
                        }
                        vibrationUnavailableReason?.let { reason ->
                            Text(text = reason, style = DamType.Small)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                EmergencyAlertManager.trigger(
                                    context,
                                    EmergencyAlert(
                                        id = "debug-${System.currentTimeMillis()}",
                                        fromName = context.getString(R.string.emergency_demo_sender_name),
                                        fromRole = context.getString(R.string.emergency_demo_sender_role),
                                        startedAtMs = System.currentTimeMillis(),
                                        isDirectCall = true
                                    )
                                )
                            }
                        ) {
                            Text(text = stringResource(R.string.emergency_demo_activate))
                        }
                        Button(onClick = { EmergencyAlertManager.stop() }) {
                            Text(text = stringResource(R.string.emergency_demo_stop))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (isDebuggable) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.audio_incident_demo_title),
                            style = DamType.Heading
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                AudioIncidentBus.simulate(
                                    AudioIncident.HeadsetUnplugged(
                                        AudioDeviceInfo.TYPE_WIRED_HEADSET
                                    )
                                )
                            }
                        ) {
                            Text(text = stringResource(R.string.audio_incident_demo_headset))
                        }
                        Button(
                            onClick = {
                                AudioIncidentBus.simulate(
                                    AudioIncident.AudioFocusLost(
                                        context.getString(R.string.audio_incident_demo_focus_reason)
                                    )
                                )
                            }
                        ) {
                            Text(text = stringResource(R.string.audio_incident_demo_focus))
                        }
                        Button(
                            onClick = {
                                AudioIncidentBus.simulate(
                                    AudioIncident.MicUnavailable(
                                        context.getString(R.string.audio_incident_demo_mic_reason)
                                    )
                                )
                            }
                        ) {
                            Text(text = stringResource(R.string.audio_incident_demo_mic))
                        }
                        Text(
                            text = stringResource(R.string.audio_incident_demo_recent),
                            style = DamType.Body
                        )
                        recentAudioIncidents.forEach { incident ->
                            Text(
                                text = incident.toDisplayText(context),
                                style = DamType.Small
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (isDebuggable) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.field_test_title),
                            style = DamType.Heading
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (fieldLogActive) {
                                    FieldTestLogger.stop()
                                } else {
                                    FieldTestLogger.start(context)
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(
                                    if (fieldLogActive) {
                                        R.string.field_test_stop_logging
                                    } else {
                                        R.string.field_test_start_logging
                                    }
                                )
                            )
                        }
                        fieldLogDirectory?.let { directory ->
                            Text(
                                text = stringResource(R.string.field_test_directory, directory),
                                style = DamType.Small
                            )
                        }
                        fieldLogFile?.let { path ->
                            Text(
                                text = stringResource(R.string.field_test_csv_file, path),
                                style = DamType.Small
                            )
                        }
                        Button(
                            onClick = { FieldTestLogger.playBluetoothLatencyTick() },
                            enabled = fieldLogActive
                        ) {
                            Text(text = stringResource(R.string.field_test_play_tick))
                        }
                        bluetoothLatencyEstimateMs?.let { estimate ->
                            Text(
                                text = stringResource(
                                    R.string.field_test_bluetooth_estimate,
                                    estimate
                                ),
                                style = DamType.Body
                            )
                        }
                        Text(
                            text = stringResource(R.string.field_test_press_counts),
                            style = DamType.Body
                        )
                        Text(
                            text = stringResource(
                                R.string.field_test_press_count_volume_up,
                                fieldPressCounts[PttSource.VOLUME_UP.name] ?: 0
                            ),
                            style = DamType.Small
                        )
                        Text(
                            text = stringResource(
                                R.string.field_test_press_count_volume_down,
                                fieldPressCounts[PttSource.VOLUME_DOWN.name] ?: 0
                            ),
                            style = DamType.Small
                        )
                        Text(
                            text = stringResource(
                                R.string.field_test_press_count_headset,
                                fieldPressCounts[PttSource.HEADSET.name] ?: 0
                            ),
                            style = DamType.Small
                        )
                        Text(
                            text = stringResource(
                                R.string.field_test_press_count_screen,
                                fieldPressCounts[PttSource.SCREEN_BUTTON.name] ?: 0
                            ),
                            style = DamType.Small
                        )
                        Text(
                            text = stringResource(
                                R.string.field_test_duplicate_count,
                                duplicateKeyCount
                            ),
                            style = DamType.Small
                        )
                        Text(
                            text = stringResource(
                                R.string.field_test_ignored_count,
                                ignoredKeyCount
                            ),
                            style = DamType.Small
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // NÚT PTT TRÊN MÀN HÌNH (SỬ DỤNG POINTER INPUT)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Thử nghiệm Nút PTT Cảm Ứng (Màn hình)",
                        style = DamType.Heading
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(
                                color = if (isPttActive && activeSource == PttSource.SCREEN_BUTTON)
                                    AccentRed
                                else
                                    MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        PttController.onKeyPressed(PttSource.SCREEN_BUTTON)
                                        tryAwaitRelease()
                                        PttController.onKeyReleased(PttSource.SCREEN_BUTTON)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "GIỮ\nNÓI",
                            color = Color.White,
                            style = DamType.Button.copy(fontSize = 15.sp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // PHẦN CÀI ĐẶT PHÍM CỨNG VOLUME
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = stringResource(id = R.string.ptt_setting_title),
                        style = DamType.Heading
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Switch Bật/Tắt chế độ bắt phím
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.ptt_setting_mode_enabled),
                            style = DamType.Body
                        )
                        KitSwitch(
                            checked = isPttModeEnabled,
                            onCheckedChange = { PttController.setPttModeEnabled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Radio Button chọn phím Volume
                    VolumeKeyOptionRow(
                        title = stringResource(id = R.string.ptt_setting_both),
                        selected = volumeKeySetting == VolumeKeySetting.BOTH,
                        enabled = isPttModeEnabled,
                        onSelect = { PttController.setVolumeKeySetting(VolumeKeySetting.BOTH) }
                    )

                    VolumeKeyOptionRow(
                        title = stringResource(id = R.string.ptt_setting_vol_down_only),
                        selected = volumeKeySetting == VolumeKeySetting.VOLUME_DOWN_ONLY,
                        enabled = isPttModeEnabled,
                        onSelect = { PttController.setVolumeKeySetting(VolumeKeySetting.VOLUME_DOWN_ONLY) }
                    )

                    VolumeKeyOptionRow(
                        title = stringResource(id = R.string.ptt_setting_vol_up_only),
                        selected = volumeKeySetting == VolumeKeySetting.VOLUME_UP_ONLY,
                        enabled = isPttModeEnabled,
                        onSelect = { PttController.setVolumeKeySetting(VolumeKeySetting.VOLUME_UP_ONLY) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // GHI CHÚ GIỚI HẠN FOREGROUND
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.Top
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.ptt_note_foreground_limit),
                        style = DamType.Small,
                        color = TextSecondary
                    )
                }
            }
        }
    }
    }
}

private fun AudioIncident.toDisplayText(context: android.content.Context): String =
    when (this) {
        is AudioIncident.HeadsetUnplugged ->
            context.getString(R.string.audio_incident_headset_unplugged, deviceType)
        AudioIncident.HeadsetReconnected ->
            context.getString(R.string.audio_incident_headset_reconnected)
        AudioIncident.BluetoothDisconnected ->
            context.getString(R.string.audio_incident_bluetooth_disconnected)
        is AudioIncident.AudioFocusLost ->
            context.getString(R.string.audio_incident_focus_lost, reason)
        AudioIncident.AudioFocusRegained ->
            context.getString(R.string.audio_incident_focus_regained)
        is AudioIncident.MicUnavailable ->
            context.getString(R.string.audio_incident_mic_unavailable, reason)
        is AudioIncident.AudioPermissionRevoked ->
            context.getString(R.string.audio_incident_permission_revoked, permission)
        is AudioIncident.AudioRouteFailed ->
            context.getString(R.string.audio_incident_route_failed, reason)
        is AudioIncident.Unknown ->
            context.getString(
                R.string.audio_incident_unknown,
                throwable.message ?: throwable.javaClass.simpleName
            )
    }

@Composable
private fun VolumeKeyOptionRow(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = if (enabled) onSelect else null,
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = DamType.Body,
            color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.55f)
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PttKeyDemoScreenPreview() {
    com.example.damthoai.ui.theme.DamThoaiTheme {
        PttKeyDemoScreen()
    }
}
