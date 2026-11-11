/**
 * Path: app/src/main/java/com/example/damthoai/ui/components/QrCodeImage.kt
 *
 * Khối KDoc tiếng Việt:
 * Thành phần hiển thị mã QR thật được tạo động từ chuỗi nội dung sử dụng ZXing core.
 */
package com.example.damthoai.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.EnumMap

@Composable
fun QrCodeImage(
    content: String,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Mã QR"
) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, content, size) {
        value = withContext(Dispatchers.Default) {
            try {
                val px = 512 // 512x512 bitmap for high clarity
                val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                    put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                    put(EncodeHintType.MARGIN, 1)
                    put(EncodeHintType.CHARACTER_SET, "UTF-8")
                }
                val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, px, px, hints)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                    }
                }
                bmp
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(12.dp, Color.White, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmapState.value
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.LightGray)
            )
        }
    }
}
