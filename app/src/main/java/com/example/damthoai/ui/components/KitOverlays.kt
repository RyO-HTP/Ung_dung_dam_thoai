package com.example.damthoai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DialogBg
import com.example.damthoai.ui.theme.GhostBtnBg
import com.example.damthoai.ui.theme.GhostBtnBorder
import com.example.damthoai.ui.theme.PrimaryBrush
import com.example.damthoai.ui.theme.Scrim
import com.example.damthoai.ui.theme.SheetBg
import com.example.damthoai.ui.theme.SheetShape
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
import com.example.damthoai.ui.theme.DangerBrush
import com.example.damthoai.ui.theme.DialogShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = SheetShape,
        containerColor = SheetBg,
        scrimColor = Scrim,
        dragHandle = {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(TextSecondary.copy(alpha = 0.5f))
            )
        },
        content = content
    )
}

@Composable
fun KitDialog(
    title: String,
    description: String,
    primaryButtonText: String,
    secondaryButtonText: String,
    onPrimaryClick: () -> Unit,
    onDismissRequest: () -> Unit,
    primaryBrush: Brush = PrimaryBrush
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DialogShape)
                .background(DialogBg)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                style = DamType.Heading.copy(fontSize = 16.sp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = description,
                color = TextSecondary,
                style = DamType.Muted
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DialogActionButton(
                    text = secondaryButtonText,
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                    isPrimary = false
                )
                DialogActionButton(
                    text = primaryButtonText,
                    onClick = onPrimaryClick,
                    modifier = Modifier.weight(1f),
                    isPrimary = true,
                    primaryBrush = primaryBrush
                )
            }
        }
    }
}

@Composable
private fun DialogActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    isPrimary: Boolean,
    primaryBrush: Brush = PrimaryBrush
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (isPrimary) {
                    Modifier.background(primaryBrush)
                } else {
                    Modifier
                        .background(GhostBtnBg)
                        .border(1.dp, GhostBtnBorder, RoundedCornerShape(14.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextPrimary,
            style = DamType.Small.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun KitBottomSheetPreview() {
    DamThoaiTheme {
        KitBottomSheet(onDismissRequest = {}) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Tùy chọn kênh", color = TextPrimary, style = DamType.Heading)
                Text("Nội dung bottom sheet", color = TextSecondary, style = DamType.Body)
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun KitDialogPrimaryPreview() {
    DamThoaiTheme {
        KitDialog(
            title = "Xác nhận thao tác",
            description = "Bạn có chắc chắn muốn tiếp tục?",
            primaryButtonText = "Tiếp tục",
            secondaryButtonText = "Hủy",
            onPrimaryClick = {},
            onDismissRequest = {}
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun KitDialogDangerPreview() {
    DamThoaiTheme {
        KitDialog(
            title = "Xóa thành viên",
            description = "Thành viên sẽ bị xóa khỏi sự kiện.",
            primaryButtonText = "Xóa",
            secondaryButtonText = "Hủy",
            onPrimaryClick = {},
            onDismissRequest = {},
            primaryBrush = DangerBrush
        )
    }
}
