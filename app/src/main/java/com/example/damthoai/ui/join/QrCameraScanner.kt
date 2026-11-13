/**
 * Path: app/src/main/java/com/example/damthoai/ui/join/QrCameraScanner.kt
 *
 * Khối KDoc tiếng Việt:
 * Thành phần quét mã QR trực tiếp qua CameraX và Google ML Kit Barcode Scanning.
 */
package com.example.damthoai.ui.join

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun QrCameraScanner(
    onQrDetected: (String) -> Unit,
    onError: (String) -> Unit,
    torchEnabled: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isInspection = LocalInspectionMode.current

    if (isInspection) {
        // Preview placeholder
        Box(
            modifier = modifier.background(androidx.compose.ui.graphics.Color.Black),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text("Camera Preview", color = androidx.compose.ui.graphics.Color.White)
        }
        return
    }

    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(context, lifecycleOwner, torchEnabled) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
                val scanner = BarcodeScanning.getClient(options)

                var lastDetectedValue: String? = null
                var lastDetectedTime = 0L
                var isDetected = false

                imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (isDetected) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val inputImage = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        scanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                val qrCode = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                                val rawValue = qrCode?.rawValue
                                if (!rawValue.isNullOrBlank()) {
                                    val now = System.currentTimeMillis()
                                    if (rawValue != lastDetectedValue || (now - lastDetectedTime) > 2000) {
                                        lastDetectedValue = rawValue
                                        lastDetectedTime = now
                                        isDetected = true
                                        onQrDetected(rawValue)
                                    }
                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.e("QrCameraScanner", "Lỗi phân tích hình ảnh camera: ${exception.message}", exception)
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                cameraInstance = camera

                val hasFlash = camera.cameraInfo.hasFlashUnit()
                onTorchAvailable(hasFlash)
                if (hasFlash) {
                    try {
                        camera.cameraControl.enableTorch(torchEnabled)
                    } catch (e: Exception) {
                        Log.e("QrCameraScanner", "Không bật được đèn pin: ${e.message}")
                    }
                }
            } catch (exception: Exception) {
                Log.e("QrCameraScanner", "Không mở được camera: ${exception.message}", exception)
                onError("Không mở được camera. Bạn có thể chọn ảnh QR từ thư viện.")
            }
        }

        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                cameraExecutor.shutdown()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier,
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
            } catch (e: Exception) {
                Log.e("QrCameraScanner", "Lỗi update PreviewView: ${e.message}", e)
            }
        }
    )
}
