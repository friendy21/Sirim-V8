package com.sirim.scanner.data.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.tasks.await

class BarcodeAnalyzer {

    private val supportedFormats = setOf(
        Barcode.FORMAT_EAN_8,
        Barcode.FORMAT_EAN_13,
        Barcode.FORMAT_UPC_A,
        Barcode.FORMAT_UPC_E,
        Barcode.FORMAT_CODE_128
    )

    private val supportedZxingFormats = setOf(
        BarcodeFormat.EAN_8,
        BarcodeFormat.EAN_13,
        BarcodeFormat.UPC_A,
        BarcodeFormat.UPC_E,
        BarcodeFormat.CODE_128
    )

    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128
            )
            .build()
    )

    private val zxingReader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.EAN_8,
                BarcodeFormat.EAN_13,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128
            ),
            DecodeHintType.TRY_HARDER to true
        )
        setHints(hints)
    }

    suspend fun analyze(imageProxy: ImageProxy): BarcodeDetection? {
        val mediaImage = imageProxy.image ?: return null
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detectWithMlKit(inputImage)?.let { return it }

        val bitmap = imageProxy.toBitmap() ?: return null
        return try {
            decodeWithZxing(bitmap)
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    suspend fun analyze(bytes: ByteArray): BarcodeDetection? {
        if (bytes.isEmpty()) return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return analyze(bitmap)
    }

    suspend fun analyze(bitmap: Bitmap): BarcodeDetection? {
        detectWithMlKit(InputImage.fromBitmap(bitmap, 0))?.let { return it }
        return decodeWithZxing(bitmap)
    }

    private suspend fun detectWithMlKit(image: InputImage): BarcodeDetection? {
        val results = runCatching { barcodeScanner.process(image).await() }.getOrNull().orEmpty()
        val mlKitResult = results.firstOrNull { result ->
            val payload = result.rawValue
            payload != null && payload.isNotBlank() && result.format in supportedFormats
        }
        return mlKitResult?.let { result ->
            BarcodeDetection(
                value = result.rawValue.orEmpty(),
                format = formatLabel(result.format)
            )
        }
    }

    private fun decodeWithZxing(bitmap: Bitmap): BarcodeDetection? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))
        return try {
            val result = zxingReader.decodeWithState(binary)
            BarcodeDetection(result.text, formatLabel(result.barcodeFormat))
        } catch (notFound: NotFoundException) {
            null
        } finally {
            zxingReader.reset()
        }
    }

    private fun formatLabel(format: Int): String {
        if (format !in supportedFormats) return "Barcode"
        return when (format) {
            Barcode.FORMAT_EAN_8 -> "EAN-8"
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            Barcode.FORMAT_UPC_A -> "UPC-A"
            Barcode.FORMAT_UPC_E -> "UPC-E"
            Barcode.FORMAT_CODE_128 -> "Code 128"
            else -> "Barcode"
        }
    }

    private fun formatLabel(format: BarcodeFormat): String {
        if (format !in supportedZxingFormats) return "Barcode"
        return when (format) {
            BarcodeFormat.EAN_8 -> "EAN-8"
            BarcodeFormat.EAN_13 -> "EAN-13"
            BarcodeFormat.UPC_A -> "UPC-A"
            BarcodeFormat.UPC_E -> "UPC-E"
            BarcodeFormat.CODE_128 -> "Code 128"
            else -> "Barcode"
        }
    }
}

data class BarcodeDetection(
    val value: String,
    val format: String
)
