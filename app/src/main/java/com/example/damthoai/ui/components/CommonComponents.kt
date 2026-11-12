package com.example.damthoai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.LaunchedEffect
import com.example.damthoai.R
import com.example.damthoai.data.MemberLevel
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.AvatarGradients
import com.example.damthoai.ui.theme.AvatarInk
import com.example.damthoai.ui.theme.ButtonShape
import com.example.damthoai.ui.theme.CardShape
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DangerBrush
import com.example.damthoai.ui.theme.DangerShadow
import com.example.damthoai.ui.theme.DisabledButtonEnd
import com.example.damthoai.ui.theme.DisabledButtonStart
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.PillShape
import com.example.damthoai.ui.theme.PrimaryBrush
import com.example.damthoai.ui.theme.PrimaryShadow
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
import com.example.damthoai.ui.theme.TagShape
import com.example.damthoai.ui.theme.SwitchOnBrush
import com.example.damthoai.ui.theme.SwitchOff
import com.example.damthoai.ui.theme.GhostBtnBg
import com.example.damthoai.ui.theme.GhostBtnBorder
import com.example.damthoai.ui.theme.IconBoxBg
import com.example.damthoai.ui.theme.IconBoxBorder
import com.example.damthoai.ui.theme.IconBoxShape
import com.example.damthoai.ui.theme.TagAmberBg
import com.example.damthoai.ui.theme.TagCyanBg
import com.example.damthoai.ui.theme.TagNeutralBg
import com.example.damthoai.ui.theme.TagRedBg
import com.example.damthoai.ui.theme.TagRedText
import com.example.damthoai.ui.theme.TextPlaceholder
import com.example.damthoai.ui.theme.PureWhite
import com.example.damthoai.ui.theme.Transparent

fun Modifier.outerGlow(
    color: Color,
    corner: Dp,
    spread: Dp = 18.dp,
    maxAlpha: Float = 0.25f
): Modifier = drawBehind {
    val steps = 10
    val spreadPx = spread.toPx()
    val strokeWidth = spreadPx / steps * 1.2f
    val alphaLimit = maxAlpha.coerceIn(0f, 1f)

    for (step in 1..steps) {
        val fraction = step.toFloat() / steps
        val grow = spreadPx * fraction
        val fade = (1f - fraction) * (1f - fraction)
        drawRoundRect(
            color = color,
            topLeft = Offset(-grow, -grow),
            size = Size(size.width + 2f * grow, size.height + 2f * grow),
            cornerRadius = CornerRadius(corner.toPx() + grow),
            alpha = alphaLimit * fade,
            style = Stroke(width = strokeWidth)
        )
    }
}

@Composable
fun AvatarCircle(nameOrInitials: String, size: Dp) {
    val initials = remember(nameOrInitials) {
        val words = nameOrInitials.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        if (words.size > 1) {
            words.take(2).joinToString("") { it.take(1) }.uppercase()
        } else {
            nameOrInitials.trim().take(2).uppercase()
        }
    }
    val colors = AvatarGradients[(nameOrInitials.hashCode().ushr(1)) % AvatarGradients.size]

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(colors.first, colors.second))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = AvatarInk,
            style = DamType.avatar(size)
        )
    }
}

@Composable
fun LevelTag(level: MemberLevel) {
    val (background, foreground) = when (level) {
        MemberLevel.LEVEL_1 -> TagAmberBg to AccentAmber
        MemberLevel.LEVEL_2 -> TagCyanBg to AccentCyan
        MemberLevel.LEVEL_3 -> TagNeutralBg to TextPrimary
    }

    Box(
        modifier = Modifier
            .clip(TagShape)
            .background(background)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(level.shortLabel, color = foreground, style = DamType.Tag)
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    autoCapitalize: Boolean = false,
    autoFocus: Boolean = false,
    textStyle: TextStyle = DamType.Input.copy(color = TextPrimary),
    singleLine: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(18.dp)

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = TextFieldValue(
                text = value,
                selection = TextRange(value.length)
            )
        }
    }

    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(isFocused) {
        if (isFocused && !wasFocused) {
            textFieldValue = textFieldValue.copy(
                selection = TextRange(textFieldValue.text.length)
            )
        }
        wasFocused = isFocused
    }

    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                color = TextSecondary,
                style = DamType.Muted.copy(fontWeight = FontWeight.SemiBold)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFocused) {
                        Modifier.outerGlow(
                            color = AccentCyan,
                            corner = 18.dp,
                            spread = 24.dp,
                            maxAlpha = 0.45f
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(shape)
                .background(GlassBg)
                .border(
                    width = if (isFocused) 1.5.dp else 1.dp,
                    color = if (isFocused) AccentCyan else GlassBorder,
                    shape = shape
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    val transformedText = if (autoCapitalize) newValue.text.uppercase() else newValue.text
                    val adjustedSelection = if (autoCapitalize && transformedText != newValue.text) {
                        newValue.selection
                    } else {
                        newValue.selection
                    }
                    textFieldValue = newValue.copy(text = transformedText, selection = adjustedSelection)
                    if (transformedText != value) {
                        onValueChange(transformedText)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = singleLine,
                interactionSource = interactionSource,
                keyboardOptions = KeyboardOptions(
                    capitalization = if (autoCapitalize) {
                        KeyboardCapitalization.Characters
                    } else {
                        KeyboardCapitalization.None
                    }
                ),
                textStyle = textStyle,
                cursorBrush = SolidColor(AccentCyan),
                decorationBox = { innerTextField ->
                    Box {
                        if (textFieldValue.text.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                color = TextPlaceholder,
                                style = textStyle.copy(color = TextPlaceholder)
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}

@Composable
fun rememberEndCursorState(value: String): androidx.compose.runtime.MutableState<TextFieldValue> {
    val state = remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (state.value.text != value) {
            state.value = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }
    return state
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = CardShape,
    backgroundColor: Color = GlassBg,
    borderColor: Color = GlassBorder,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(borderWidth, borderColor, shape)
            .padding(contentPadding)
    ) {
        content()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    iconRes: Int? = null
) {
    val gradient = if (enabled) {
        PrimaryBrush
    } else {
        Brush.horizontalGradient(listOf(DisabledButtonStart, DisabledButtonEnd))
    }
    Box(
        modifier = modifier.heightIn(min = maxOf(height, 54.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 10.dp)
                .shadow(30.dp, ButtonShape, spotColor = PrimaryShadow)
                .background(Transparent, ButtonShape)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxOf(height, 54.dp))
                .clip(ButtonShape)
                .background(gradient)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, style = DamType.Button, color = TextPrimary, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    textColor: Color = TextPrimary,
    iconRes: Int? = null
) {
    Box(
        modifier = modifier
            .heightIn(min = maxOf(height, 54.dp))
            .clip(ButtonShape)
            .background(GhostBtnBg)
            .border(1.dp, GhostBtnBorder, ButtonShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = DamType.Button, color = textColor, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp
) {
    Box(
        modifier = modifier.heightIn(min = maxOf(height, 54.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 10.dp)
                .shadow(30.dp, ButtonShape, spotColor = DangerShadow)
                .background(Transparent, ButtonShape)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxOf(height, 54.dp))
                .clip(ButtonShape)
                .background(DangerBrush)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, style = DamType.Button, color = TextPrimary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextSecondary
) {
    Text(text = text.uppercase(), modifier = modifier, style = DamType.Label, color = color)
}

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconBox(
        iconRes = R.drawable.ic_back,
        contentDescription = "Quay lại",
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun IconBox(
    iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(role = Role.Button, onClick = onClick)
            } else {
                Modifier
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(IconBoxShape)
                .background(IconBoxBg)
                .border(1.dp, IconBoxBorder, IconBoxShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun KitSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (checked) Modifier.background(SwitchOnBrush)
                    else Modifier.background(SwitchOff)
                )
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 3.dp)
                    .size(22.dp)
                    .offset(x = if (checked) 18.dp else 0.dp)
                    .clip(CircleShape)
                    .background(PureWhite)
            )
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    statusColor: Color = AccentMint,
    backgroundColor: Color = GlassBg,
    textColor: Color = TextPrimary,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 4.dp,
    textStyle: TextStyle = DamType.Small.copy(fontWeight = FontWeight.SemiBold)
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(backgroundColor)
            .border(1.dp, GlassBorder, CircleShape)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = textColor,
            style = textStyle
        )
    }
}

@Composable
fun TagLabel(
    text: String,
    color: Color = AccentAmber,
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null
) {
    val resolvedBackgroundColor = backgroundColor ?: when (color) {
        AccentAmber -> TagAmberBg
        AccentCyan -> TagCyanBg
        AccentRed, TagRedText -> TagRedBg
        else -> color.copy(alpha = 0.18f)
    }
    val textColor = if (color == AccentRed) TagRedText else color
    Text(
        text = text,
        modifier = modifier
            .clip(TagShape)
            .background(resolvedBackgroundColor)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        color = textColor,
        style = DamType.Tag
    )
}

@Composable
fun TagLabel(
    text: String,
    layoutModifier: Modifier,
    color: Color,
    backgroundColor: Color = color.copy(alpha = 0.16f)
) {
    TagLabel(text, color, layoutModifier, backgroundColor)
}

fun Modifier.dashedBorder(
    color: Color,
    width: Dp = 1.dp,
    cornerRadius: Dp = 0.dp,
    strokeWidth: Dp = width
): Modifier = drawWithContent {
    drawContent()
    val strokePx = strokeWidth.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(strokePx / 2, strokePx / 2),
        size = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = Stroke(
            width = strokePx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        )
    )
}

// ================= PREVIEWS =================
@Preview(name = "Common Components Preview", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CommonComponentsPreview() {
    DamThoaiTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BackButton(onClick = {})
            SectionLabel(text = "TẦN SỐ ẢO · PIN 6 SỐ")
            StatusPill(text = "Internet · 42 ms", statusColor = AccentMint)
            TagLabel(text = "ƯU TIÊN", color = AccentAmber)
            TagLabel(text = "CẤP 1", color = AccentCyan)
            TagLabel(text = "CẢNH BÁO", color = AccentRed)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Thẻ kính mờ GlassCard chuẩn thiết kế Đàm Thoại", color = TextPrimary)
            }
            PrimaryButton(text = "Vào sự kiện ngay", onClick = {}, modifier = Modifier.fillMaxWidth())
            SecondaryButton(text = "Quét mã QR", onClick = {}, modifier = Modifier.fillMaxWidth())
            DangerButton(text = "Cuộc gọi khẩn cấp", onClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}
