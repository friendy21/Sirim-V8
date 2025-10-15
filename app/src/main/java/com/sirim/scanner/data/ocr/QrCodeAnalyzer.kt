package com.sirim.scanner.data.ocr

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.tasks.await
import android.graphics.Bitmap

class QrCodeAnalyzer {

    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    private val zxingReader = MultiFormatReader().apply {
        setHints(mapOf(com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    suspend fun analyze(imageProxy: ImageProxy): QrDetection? {
        val mediaImage = imageProxy.image ?: return null
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val results = runCatching { barcodeScanner.process(inputImage).await() }.getOrNull().orEmpty()
        val mlKitResult = results.firstOrNull { result ->
            val payload = result.rawValue
            payload != null && payload.isNotBlank()
        }
        if (mlKitResult != null) {
            return QrDetection(mlKitResult.rawValue.orEmpty())
        }

        val bitmap = imageProxy.toBitmap() ?: return null
        return try {
            decodeWithZxing(bitmap)
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun decodeWithZxing(bitmap: Bitmap): QrDetection? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        return try {
            val result = zxingReader.decodeWithState(binary)
            if (result.barcodeFormat == BarcodeFormat.QR_CODE) {
                QrDetection(result.text)
            } else {
                null
            }
        } catch (notFound: NotFoundException) {
            null
        } finally {
            zxingReader.reset()
        }
    }
}
 
data class QrDetection(val payload: String)
