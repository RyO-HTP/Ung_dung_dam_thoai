package com.example.damthoai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.damthoai.R
import com.example.damthoai.data.fake.MemberLevel
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
            style = DamType.Heading.copy(
                fontSize = (size.value * 0.375f).sp,
                fontWeight = FontWeight.ExtraBold
            )
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
    autoCapitalize: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(18.dp)

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
                .shadow(
                    elevation = if (isFocused) 24.dp else 0.dp,
                    shape = shape,
                    spotColor = AccentCyan.copy(alpha = 0.45f)
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
                value = value,
                onValueChange = { input ->
                    onValueChange(if (autoCapitalize) input.uppercase() else input)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                interactionSource = interactionSource,
                keyboardOptions = KeyboardOptions(
                    capitalization = if (autoCapitalize) {
                        KeyboardCapitalization.Characters
                    } else {
                        KeyboardCapitalization.None
                    }
                ),
                textStyle = DamType.Body.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                ),
                cursorBrush = SolidColor(AccentCyan),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                color = TextPlaceholder,
                                style = DamType.Body.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
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
    height: Dp = 54.dp
) {
    val gradient = if (enabled) {
        PrimaryBrush
    } else {
        Brush.horizontalGradient(listOf(Color.Gray, Color.DarkGray))
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
                .background(Color.Transparent, ButtonShape)
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
            Text(text, style = DamType.Button, color = TextPrimary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp
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
        Text(text, style = DamType.Button, color = TextPrimary, textAlign = TextAlign.Center)
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
                .background(Color.Transparent, ButtonShape)
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
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(IconBoxShape)
            .background(IconBoxBg)
            .border(1.dp, IconBoxBorder, IconBoxShape)
            .then(clickModifier),
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

@Composable
fun KitSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
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
                .background(Color.White)
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    statusColor: Color = AccentMint,
    backgroundColor: Color = GlassBg
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(backgroundColor)
            .border(1.dp, GlassBorder, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
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
            color = TextPrimary,
            style = DamType.Small.copy(fontWeight = FontWeight.SemiBold)
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
@Preview(name = "Common Components Preview", showBackground = true, backgroundColor = 0xFF060A1F)
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
