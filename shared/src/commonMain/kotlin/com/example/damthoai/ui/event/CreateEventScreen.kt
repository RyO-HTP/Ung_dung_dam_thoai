package com.example.damthoai.ui.event

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.resources.*
import com.example.damthoai.data.AppStartPrefs
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.KitBottomSheet
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.components.SecondaryButton
import com.example.damthoai.ui.components.outerGlow
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.CueBorder
import com.example.damthoai.ui.theme.CueFill
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.InputShape
import com.example.damthoai.ui.theme.TagCyanBg
import com.example.damthoai.ui.theme.TextPlaceholder
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

private fun generateSuggestedAdminCode(): String {
    val chars = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    val randomPart = (1..6).map { chars.random() }.joinToString("")
    return "ADM-$randomPart"
}

private fun generateSuggestedPinCode(): String {
    return (100000..999999).random().toString()
}

@Composable
fun CreateEventScreen(
    viewModel: CreateEventViewModel = androidx.lifecycle.viewmodel.compose.viewModel { CreateEventViewModel() },
    onBack: () -> Unit,
    onEventCreated: () -> Unit,
    onNavigateToRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    var eventName by rememberSaveable { mutableStateOf("") }
    val creator by viewModel.creator.collectAsState()
    var creatorName by rememberSaveable { mutableStateOf(creator.name) }
    var creatorRoleTitle by rememberSaveable { mutableStateOf(creator.roleTitle) }
    var adminCode by rememberSaveable { mutableStateOf("") }
    var eventPinCode by rememberSaveable { mutableStateOf("") }
    var showSuccessSheet by rememberSaveable { mutableStateOf(false) }

    val isFormValid = eventName.isNotBlank()

    DamThoaiBackground(modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Text(
                    "Tạo sự kiện",
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    style = DamType.Heading16,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.size(40.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(4.dp))
                EventFieldLabel("Tên sự kiện")
                EventInputField(
                    value = eventName,
                    onValueChange = { eventName = it },
                    placeholder = "Nhập tên sự kiện..."
                )

                EventFieldLabel("Tên của bạn")
                EventInputField(
                    value = creatorName,
                    onValueChange = { creatorName = it },
                    placeholder = "Nhập tên của bạn..."
                )

                EventFieldLabel("Chức vụ")
                EventInputField(
                    value = creatorRoleTitle,
                    onValueChange = { creatorRoleTitle = it },
                    placeholder = "Nhập chức vụ (Tổng đạo diễn, Hậu cần...)"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CueFill)
                        .border(1.dp, CueBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_crown),
                        contentDescription = null,
                        tint = AccentAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Bạn là Cấp 1 · quyền cao nhất",
                            color = TextPrimary,
                            style = DamType.Heading14
                        )
                        Text(
                            "Quản lý kênh, thành viên, phát khẩn cấp.",
                            modifier = Modifier.padding(top = 2.dp),
                            color = TextSecondary,
                            style = DamType.Small
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = "Tạo sự kiện",
                onClick = {
                    viewModel.updateCreator(creatorName, creatorRoleTitle)
                    val code = generateSuggestedAdminCode()
                    val pin = generateSuggestedPinCode()
                    adminCode = code
                    eventPinCode = pin
                    AppStartPrefs.setEventPin(pin)
                    AppStartPrefs.setEventName(eventName)
                    showSuccessSheet = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isFormValid,
                height = 54.dp
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Khôi phục bằng mã quản trị",
                onClick = onNavigateToRestore,
                iconRes = Res.drawable.ic_lock,
                modifier = Modifier.fillMaxWidth(),
                height = 48.dp
            )
        }
    }

    if (showSuccessSheet) {
        KitBottomSheet(onDismissRequest = { }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .padding(top = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Đã tạo sự kiện",
                    color = TextPrimary,
                    style = DamType.Title24,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                
                // MÃ PIN 6 SỐ VÀO KÊNH
                Text(
                    text = "MÃ PIN TẦN SỐ VÀO KÊNH (6 SỐ)",
                    color = AccentCyan,
                    style = DamType.Tag12,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(TagCyanBg)
                            .border(1.5.dp, AccentCyan, RoundedCornerShape(18.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = eventPinCode,
                            color = AccentCyan,
                            style = DamType.PinBig,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // MÃ QUẢN TRỊ KHÔI PHỤC
                Text(
                    text = "MÃ QUẢN TRỊ (KHÔI PHỤC QUYỀN)",
                    color = TextSecondary.copy(alpha = 0.7f),
                    style = DamType.Small,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))

                var copied by remember { mutableStateOf(false) }
                LaunchedEffect(copied) {
                    if (copied) {
                        kotlinx.coroutines.delay(2000)
                        copied = false
                    }
                }

                SelectionContainer {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(GlassBg)
                            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = adminCode,
                            color = TextPrimary,
                            style = DamType.MonoInput.copy(
                                fontSize = 15.sp,
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(end = 36.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .align(Alignment.CenterEnd)
                                .clip(CircleShape)
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(adminCode))
                                    copied = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(if (copied) Res.drawable.ic_check else Res.drawable.ic_copy),
                                contentDescription = "Sao chép mã",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Hãy chia sẻ Mã PIN 6 số cho ekip để họ vào kênh thoại, và lưu Mã quản trị để khôi phục quyền.",
                    color = TextSecondary,
                    style = DamType.Small,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.9f)
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = "Tiếp tục vào kênh",
                    onClick = {
                        // Ban tổ chức đăng ký sự kiện (PIN + tên) lên server để người vào sau nhận đúng tên
                        val callManager = com.example.damthoai.service.DamThoaiCallManager.getInstance()
                        callManager.updateProfile(creatorName, creatorRoleTitle.ifBlank { "Ban tổ chức" })
                        callManager.joinEvent(
                            pin = eventPinCode,
                            eventNameHint = eventName.ifBlank { null },
                            asOrganizer = true
                        )
                        showSuccessSheet = false
                        onEventCreated()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 54.dp
                )
            }
        }
    }
}

@Composable
private fun EventFieldLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        color = TextSecondary,
        style = DamType.Body.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
private fun EventInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .outerGlow(AccentCyan, corner = 18.dp, spread = 16.dp, maxAlpha = 0.18f)
            .clip(shape)
            .background(GlassBg)
            .border(1.5.dp, AccentCyan, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        singleLine = true,
        textStyle = DamType.Input.copy(color = TextPrimary),
        cursorBrush = SolidColor(AccentCyan),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, color = TextPlaceholder, style = DamType.Input)
                }
                innerTextField()
            }
        }
    )
}

@Preview
@Composable
private fun CreateEventScreenPreview() {
    DamThoaiTheme {
        CreateEventScreen(onBack = {}, onEventCreated = {}, onNavigateToRestore = {})
    }
}