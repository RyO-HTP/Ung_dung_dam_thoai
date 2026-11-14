package com.example.damthoai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.damthoai.platform.LocalPlatformUi

/** Ảnh mã QR (Android: ZXing, iOS: CoreImage CIQRCodeGenerator). */
@Composable
fun QrCodeImage(
    content: String,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Mã QR"
) {
    LocalPlatformUi.current.QrCodeImage(content, size, modifier, contentDescription)
}
