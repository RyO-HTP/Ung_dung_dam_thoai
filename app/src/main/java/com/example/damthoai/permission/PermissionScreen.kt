package com.example.damthoai.permission

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.damthoai.R

/**
 * =================================================================================
 * PHẦN GIAO DIỆN DEMO: Màn hình kiểm tra & cấp quyền (Permission Management Screen)
 * VAI TRÒ: Thành viên 4 (Hardware / Event Integration) - Tuần 1
 * =================================================================================
 *
 * Màn hình Compose DEMO cho phép Thành viên 4 (và nhóm) test trực tiếp toàn bộ luồng cấp quyền:
 * 1. Liệt kê danh sách nhóm quyền kèm trạng thái (Đã cấp / Chưa cấp / Bị từ chối vĩnh viễn).
 * 2. Cho phép bấm xin quyền lẻ từng nhóm hoặc bấm "CẤP TẤT CẢ QUYỀN CẦN THIẾT".
 * 3. Tự động xử lý mở Dialog hướng dẫn nếu bị từ chối vĩnh viễn (chuyển sang Cài đặt ứng dụng).
 * 4. Tích hợp nút kiểm tra & yêu cầu Tắt tối ưu hóa pin cho ứng dụng chạy nền.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    modifier: Modifier = Modifier,
    onAllPermissionsGranted: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    // Thu thập trạng thái quyền thời gian thực từ StateFlow của PermissionManager
    val permissionStates by PermissionManager.permissionStates.collectAsStateWithLifecycle()

    // Lưu trữ nhóm quyền đang được chọn để xin cấp
    var currentRequestingPermission by remember { mutableStateOf<AppPermission?>(null) }

    // Dialog thông báo khi quyền bị từ chối vĩnh viễn hoặc cần giải thích (Rationale)
    var showSettingsDialogForPermission by remember { mutableStateOf<AppPermission?>(null) }

    // Cập nhật lại trạng thái quyền mỗi khi ứng dụng Resume (người dùng quay lại từ Cài đặt)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.EVENT_ON_RESUME) {
                PermissionManager.refreshPermissionStates(context, activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        PermissionManager.refreshPermissionStates(context, activity)
    }

    // Launcher cấp quyền từ Activity Result API trong Jetpack Compose
    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Cập nhật lại trạng thái ngay sau khi người dùng bấm Chấp nhận/Từ chối trên Dialog hệ thống
        PermissionManager.refreshPermissionStates(context, activity)

        // Kiểm tra xem nhóm vừa xin có bị từ chối vĩnh viễn không
        currentRequestingPermission?.let { requestedPerm ->
            val newState = PermissionManager.checkPermissionState(context, requestedPerm, activity)
            if (newState == PermissionState.PERMANENTLY_DENIED || newState == PermissionState.DENIED) {
                showSettingsDialogForPermission = requestedPerm
            }
        }
        currentRequestingPermission = null
    }

    val isAllRequiredGranted = PermissionManager.allRequiredGranted(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Quản Lý Quyền Hệ Thống",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Card tổng quan trạng thái ứng dụng
            StatusBannerCard(isAllRequiredGranted = isAllRequiredGranted)

            Spacer(modifier = Modifier.height(16.dp))

            // Danh sách các nhóm quyền
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(AppPermission.entries) { appPerm ->
                    val state = permissionStates[appPerm] ?: PermissionState.DENIED
                    PermissionItemCard(
                        appPermission = appPerm,
                        permissionState = state,
                        onRequestPermission = {
                            if (appPerm == AppPermission.BACKGROUND) {
                                // Với nhóm Background, mở Cài đặt Tắt tối ưu pin
                                PermissionManager.requestIgnoreBatteryOptimizations(context)
                            } else if (appPerm == AppPermission.NETWORK) {
                                // Nhóm Network là Normal Permission, không cần xin
                                PermissionManager.refreshPermissionStates(context, activity)
                            } else {
                                val manifestPerms = appPerm.getManifestPermissions()
                                if (manifestPerms.isNotEmpty()) {
                                    currentRequestingPermission = appPerm
                                    multiplePermissionLauncher.launch(manifestPerms)
                                }
                            }
                        }
                    )
                }

                // Ghi chú dành cho dòng máy Xiaomi/OPPO/Samsung...
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.perm_battery_note_xiaomi_oppo),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Nút "CẤP TẤT CẢ QUYỀN CẦN THIẾT"
            Button(
                onClick = {
                    val allManifestPerms = AppPermission.entries
                        .flatMap { it.getManifestPermissions().toList() }
                        .distinct()
                        .toTypedArray()

                    if (allManifestPerms.isNotEmpty()) {
                        multiplePermissionLauncher.launch(allManifestPerms)
                    }
                    if (!PermissionManager.isIgnoringBatteryOptimizations(context)) {
                        PermissionManager.requestIgnoreBatteryOptimizations(context)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAllRequiredGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isAllRequiredGranted) "ĐÃ ĐỦ QUYỀN BẮT BUỘC - VÀO BỘ ĐÀM" else stringResource(id = R.string.perm_btn_grant_all),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }

    // Dialog thông báo hướng dẫn mở Cài đặt khi bị từ chối vĩnh viễn
    showSettingsDialogForPermission?.let { perm ->
        AlertDialog(
            onDismissRequest = { showSettingsDialogForPermission = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(text = stringResource(id = R.string.perm_permanently_denied_title))
            },
            text = {
                Text(
                    text = stringResource(
                        id = R.string.perm_permanently_denied_message,
                        stringResource(id = perm.titleRes)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsDialogForPermission = null
                        PermissionManager.openAppSettings(context)
                    }
                ) {
                    Text(text = stringResource(id = R.string.perm_btn_open_settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSettingsDialogForPermission = null }
                ) {
                    Text(text = stringResource(id = R.string.btn_cancel))
                }
            }
        )
    }
}

/**
 * Card hiển thị tổng quan trạng thái sẵn sàng của ứng dụng.
 */
@Composable
private fun StatusBannerCard(isAllRequiredGranted: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAllRequiredGranted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isAllRequiredGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isAllRequiredGranted) Color(0xFF2E7D32) else Color(0xFFE65100)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isAllRequiredGranted) "Sẵn sàng hoạt động" else "Cần cấp thêm quyền",
                    fontWeight = FontWeight.Bold,
                    color = if (isAllRequiredGranted) Color(0xFF1B5E20) else Color(0xFFBF360C),
                    fontSize = 16.sp
                )
                Text(
                    text = if (isAllRequiredGranted)
                        "Ứng dụng đã có đầy đủ quyền bắt buộc để truyền nhận tín hiệu bộ đàm."
                    else
                        "Bộ đàm cần quyền Micro và Chạy nền để không bị ngắt kết nối.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.DarkGray
                )
            }
        }
    }
}

/**
 * Card hiển thị thông tin và nút điều khiển của từng nhóm [AppPermission].
 */
@Composable
private fun PermissionItemCard(
    appPermission: AppPermission,
    permissionState: PermissionState,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(id = appPermission.titleRes),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // Badge Bắt buộc / Tùy chọn
                    BadgeText(
                        text = if (appPermission.isRequired) "Bắt buộc" else "Tùy chọn",
                        backgroundColor = if (appPermission.isRequired) Color(0xFFFFEBEE) else Color(0xFFE1F5FE),
                        textColor = if (appPermission.isRequired) Color(0xFFC62828) else Color(0xFF0277BD)
                    )
                }

                // Badge trạng thái hiện tại (Đã cấp / Chưa cấp / Bị chặn)
                val statusBgColor = when (permissionState) {
                    PermissionState.GRANTED -> Color(0xFFE8F5E9)
                    PermissionState.DENIED -> Color(0xFFFFF8E1)
                    PermissionState.PERMANENTLY_DENIED -> Color(0xFFFFEBEE)
                }
                val statusTextColor = when (permissionState) {
                    PermissionState.GRANTED -> Color(0xFF2E7D32)
                    PermissionState.DENIED -> Color(0xFFF57F17)
                    PermissionState.PERMANENTLY_DENIED -> Color(0xFFC62828)
                }

                BadgeText(
                    text = stringResource(id = permissionState.statusTextRes),
                    backgroundColor = statusBgColor,
                    textColor = statusTextColor
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(id = appPermission.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (permissionState != PermissionState.GRANTED && appPermission != AppPermission.NETWORK) {
                if (appPermission == AppPermission.BACKGROUND) {
                    OutlinedButton(
                        onClick = onRequestPermission,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(id = R.string.perm_btn_ignore_battery))
                    }
                } else {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(text = stringResource(id = R.string.perm_btn_grant))
                    }
                }
            }
        }
    }
}

/**
 * Composable vẽ nhãn Badge màu sắc nhỏ gọn.
 */
@Composable
private fun BadgeText(
    text: String,
    backgroundColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .background(color = backgroundColor, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
