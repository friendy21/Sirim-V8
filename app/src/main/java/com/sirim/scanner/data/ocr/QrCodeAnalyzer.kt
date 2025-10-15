package com.sirim.scanner.data.ocr

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

        val payload = selectPayload(result)
        return payload?.takeIf { it.isNotBlank() }?.let { QrDetection(it) }
    }

    private fun selectPayload(result: Text): String? {
        val blocks = result.textBlocks
        if (blocks.isEmpty()) {
            return result.text.trim().ifEmpty { null }
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

        val candidate = bestBlock?.lines
            ?.map { line -> line.text.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(separator = "\n")

        if (!candidate.isNullOrBlank()) {
            return candidate
        }

        val fallback = blocks
            .flatMap { block -> block.lines }
            .sortedBy { line -> line.boundingBox?.bottom ?: Int.MIN_VALUE }
            .lastOrNull { line -> line.text.isNotBlank() }
            ?.text

        return fallback?.trim()?.ifEmpty { null } ?: result.text.trim().ifEmpty { null }
    }
}

data class QrDetection(val payload: String)
