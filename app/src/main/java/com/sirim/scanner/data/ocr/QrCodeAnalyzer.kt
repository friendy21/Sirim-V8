package com.sirim.scanner.data.ocr

import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class QrCodeAnalyzer {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun analyze(imageProxy: ImageProxy): QrDetection? {
        val mediaImage = imageProxy.image ?: return null
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val result = runCatching { textRecognizer.process(inputImage).await() }.getOrNull()
            ?: return null

        val detection = selectPayload(result)
        val payload = detection?.first?.takeIf { it.isNotBlank() }
        return payload?.let { text ->
            QrDetection(payload = text, boundingBox = detection.second)
        }
    }

    private fun selectPayload(result: Text): Pair<String, Rect?>? {
        val blocks = result.textBlocks
        if (blocks.isEmpty()) {
            val fallback = result.text.trim().ifEmpty { null }
            return fallback?.let { it to null }
        }

        val bestBlock = blocks
            .mapNotNull { block ->
                val boundingBox = block.boundingBox ?: return@mapNotNull null
                val text = block.text.trim()
                if (text.isEmpty()) {
                    null
                } else {
                    block to boundingBox.bottom
                }
            }
            .maxByOrNull { (_, bottom) -> bottom }
            ?.first

        val candidateText = bestBlock?.lines
            ?.map { line -> line.text.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(separator = "\n")

        if (!candidateText.isNullOrBlank()) {
            return candidateText to bestBlock?.boundingBox
        }

        val fallback = blocks
            .flatMap { block -> block.lines }
            .sortedBy { line -> line.boundingBox?.bottom ?: Int.MIN_VALUE }
            .lastOrNull { line -> line.text.isNotBlank() }
            ?.let { it.text to it.boundingBox }

        if (fallback != null) {
            val trimmed = fallback.first.trim().ifEmpty { null }
            if (trimmed != null) {
                return trimmed to fallback.second
            }
        }

        val rawText = result.text.trim().ifEmpty { null }
        return rawText?.let { it to null }
    }
}

data class QrDetection(val payload: String, val boundingBox: Rect?)
