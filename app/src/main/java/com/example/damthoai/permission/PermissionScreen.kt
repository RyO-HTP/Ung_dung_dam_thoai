package com.example.damthoai.permission
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.damthoai.R
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.KitSwitch
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.components.TagLabel
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.MicDeniedBg
import com.example.damthoai.ui.theme.MicDeniedBorder
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun PermissionScreen(
    modifier: Modifier = Modifier,
    onAllPermissionsGranted: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionStates by PermissionManager.permissionStates.collectAsStateWithLifecycle()
    var currentRequestingPermission by remember { mutableStateOf<AppPermission?>(null) }
    var showSettingsDialogForPermission by remember { mutableStateOf<AppPermission?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PermissionManager.refreshPermissionStates(context, activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        PermissionManager.refreshPermissionStates(context, activity)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        PermissionManager.refreshPermissionStates(context, activity)
        currentRequestingPermission?.let { requested ->
            val newState = PermissionManager.checkPermissionState(context, requested, activity)
            if (newState == PermissionState.PERMANENTLY_DENIED || newState == PermissionState.DENIED) {
                showSettingsDialogForPermission = requested
            }
        }
        currentRequestingPermission = null
    }

    fun requestPermission(permission: AppPermission) {
        if (permission == AppPermission.BACKGROUND) {
            PermissionManager.requestIgnoreBatteryOptimizations(context)
        } else {
            val manifestPermissions = permission.getManifestPermissions()
            if (manifestPermissions.isNotEmpty()) {
                currentRequestingPermission = permission
                permissionLauncher.launch(manifestPermissions)
            }
        }
    }

    val allGranted = PermissionManager.allRequiredGranted(context)
    PermissionScreenContent(
        modifier = modifier,
        states = permissionStates,
        onRequest = ::requestPermission,
        onContinue = {
            if (allGranted) {
                onAllPermissionsGranted()
            } else {
                val allManifestPermissions = AppPermission.entries
                    .flatMap { it.getManifestPermissions().toList() }
                    .distinct()
                    .toTypedArray()
                if (allManifestPermissions.isNotEmpty()) {
                    permissionLauncher.launch(allManifestPermissions)
                }
                if (!PermissionManager.isIgnoringBatteryOptimizations(context)) {
                    PermissionManager.requestIgnoreBatteryOptimizations(context)
                }
            }
        }
    )

    showSettingsDialogForPermission?.let { permission ->
        AlertDialog(
            onDismissRequest = { showSettingsDialogForPermission = null },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_alert),
                    contentDescription = null,
                    tint = AccentRed
                )
            },
            title = { Text(text = androidx.compose.ui.res.stringResource(permission.titleRes)) },
            text = {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        R.string.perm_permanently_denied_message,
                        androidx.compose.ui.res.stringResource(permission.titleRes)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialogForPermission = null
                    PermissionManager.openAppSettings(context)
                }) {
                    Text(androidx.compose.ui.res.stringResource(R.string.perm_btn_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialogForPermission = null }) {
                    Text(androidx.compose.ui.res.stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun PermissionScreenContent(
    modifier: Modifier = Modifier,
    states: Map<AppPermission, PermissionState>,
    onRequest: (AppPermission) -> Unit,
    onContinue: () -> Unit
) {
    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("9:41", style = DamType.Small.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_wifi),
                        contentDescription = "Wi-Fi",
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("92%", style = DamType.Small.copy(fontSize = 12.sp), color = TextPrimary)
                }
            }

            Column(modifier = Modifier.padding(top = 10.dp)) {
                Text("Bước cuối cùng", style = DamType.Title, color = TextPrimary)
                Text(
                    "Cấp quyền để bộ đàm hoạt động trơn tru.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = DamType.Body,
                    color = TextSecondary
                )
            }

            PermissionRow(
                icon = R.drawable.ic_mic,
                title = "Micro",
                description = "Để mọi người nghe bạn nói",
                required = true,
                granted = states[AppPermission.AUDIO] == PermissionState.GRANTED
            ) { onRequest(AppPermission.AUDIO) }

            PermissionRow(
                icon = R.drawable.ic_bell,
                title = "Thông báo",
                description = "Nhận cảnh báo khẩn cấp",
                granted = states[AppPermission.NOTIFICATION] == PermissionState.GRANTED
            ) { onRequest(AppPermission.NOTIFICATION) }

            PermissionRow(
                icon = R.drawable.ic_bt,
                title = "Bluetooth và thiết bị ở gần",
                description = "Tai nghe, kết nối ngang hàng",
                granted = states[AppPermission.BLUETOOTH] == PermissionState.GRANTED
            ) { onRequest(AppPermission.BLUETOOTH) }

            PermissionRow(
                icon = R.drawable.ic_zap,
                title = "Chạy nền",
                description = "Giữ app hoạt động khi tắt màn hình",
                granted = states[AppPermission.BACKGROUND] == PermissionState.GRANTED
            ) { onRequest(AppPermission.BACKGROUND) }

            Text(
                text = "Camera sẽ được hỏi khi bạn quét mã QR lần đầu.",
                style = DamType.Small,
                color = TextSecondary
            )

            Spacer(Modifier.weight(1f))
            PrimaryButton(
                text = "Tiếp tục",
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 26.dp)
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: Int,
    title: String,
    description: String,
    granted: Boolean,
    required: Boolean = false,
    onRequest: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.09f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = DamType.Heading, color = TextPrimary)
                    if (required) {
                        Spacer(Modifier.width(6.dp))
                        TagLabel("BẮT BUỘC", AccentRed)
                    }
                }
                Text(
                    description,
                    modifier = Modifier.padding(top = 2.dp),
                    style = DamType.Muted,
                    color = TextSecondary
                )
            }
            KitSwitch(
                checked = granted,
                onCheckedChange = { if (it) onRequest() },
                enabled = !granted
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun PermissionScreenPreview() {
    DamThoaiTheme {
        PermissionScreenContent(
            states = mapOf(
                AppPermission.AUDIO to PermissionState.GRANTED,
                AppPermission.NOTIFICATION to PermissionState.DENIED,
                AppPermission.BLUETOOTH to PermissionState.DENIED,
                AppPermission.BACKGROUND to PermissionState.DENIED
            ),
            onRequest = {},
            onContinue = {}
        )
    }
}

@Composable
fun MicDeniedScreen(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PermissionManager.refreshPermissionStates(context, activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { PermissionManager.refreshPermissionStates(context, activity) }

    MicDeniedContent(
        modifier = modifier,
        onOpenSettings = { PermissionManager.openAppSettings(context) },
        onRetry = {
            PermissionManager.refreshPermissionStates(context, activity)
            if (PermissionManager.checkPermissionState(context, AppPermission.AUDIO, activity) == PermissionState.GRANTED) {
                onRetry()
            }
        }
    )
}

@Composable
private fun MicDeniedContent(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit
) {
    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("9:41", style = DamType.Small.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_wifi),
                        contentDescription = "Wi-Fi",
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("92%", style = DamType.Small.copy(fontSize = 12.sp), color = TextPrimary)
                }
            }
            Spacer(Modifier.weight(0.9f))
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(MicDeniedBg)
                    .border(1.5.dp, MicDeniedBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = null,
                    tint = com.example.damthoai.ui.theme.TagRedText,
                    modifier = Modifier.size(54.dp)
                )
            }
            Column(
                modifier = Modifier.padding(top = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Cần quyền Micro", style = DamType.Title, color = TextPrimary, textAlign = TextAlign.Center)
                Text(
                    "Không có micro thì bạn không nói được. Hãy bật quyền Micro cho Đàm Thoại trong cài đặt điện thoại.",
                    modifier = Modifier.padding(top = 10.dp, start = 12.dp, end = 12.dp),
                    style = DamType.Body,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp
                )
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                text = "Mở cài đặt điện thoại",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            com.example.damthoai.ui.components.SecondaryButton(
                text = "Tôi đã bật, thử lại",
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 26.dp)
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun MicDeniedScreenPreview() {
    DamThoaiTheme {
        MicDeniedContent(onOpenSettings = {}, onRetry = {})
    }
}
