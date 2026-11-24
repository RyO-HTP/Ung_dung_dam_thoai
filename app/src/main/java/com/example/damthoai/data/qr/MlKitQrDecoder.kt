/**
 * Path: app/src/main/java/com/example/damthoai/data/qr/MlKitQrDecoder.kt
 *
 * Khối KDoc tiếng Việt:
 * Bộ giải mã mã QR từ tệp ảnh trong thư viện thiết bị sử dụng Google ML Kit Barcode Scanning.
 */
package com.example.damthoai.data.qr

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.damthoai.ui.join.QrDecoder
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume

class MlKitQrDecoder(private val context: Context) : QrDecoder {

    companion object {
        private const val TAG = "MlKitQrDecoder"
    }

    override suspend fun decode(uri: Uri): String? = withContext(Dispatchers.IO) {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        try {
            val image = InputImage.fromFilePath(context, uri)
            suspendCancellableCoroutine { continuation ->
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val rawValue = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
                        if (continuation.isActive) {
                            continuation.resume(rawValue)
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Lỗi quét QR từ ảnh: ${exception.message}", exception)
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                    .addOnCanceledListener {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
            }
        } catch (ioException: IOException) {
            Log.e(TAG, "Không đọc được file ảnh QR: ${ioException.message}", ioException)
            null
        } catch (exception: Exception) {
            Log.e(TAG, "Lỗi không xác định khi decode QR: ${exception.message}", exception)
            null
        } finally {
            try {
                scanner.close()
            } catch (e: Exception) {
                // Ignore close error
            }
        }
    }
}
