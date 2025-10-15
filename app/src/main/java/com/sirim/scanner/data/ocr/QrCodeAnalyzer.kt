package com.sirim.scanner.data.ocr

import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.tasks.await

class QrCodeAnalyzer {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val serialPattern = Regex("^[A-Z0-9]{6,}\$")

    suspend fun analyze(imageProxy: ImageProxy): QrDetection? {
        val mediaImage = imageProxy.image ?: return null
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val result = runCatching { textRecognizer.process(inputImage).await() }.getOrNull()
            ?: return null

        val detection = selectPayload(result)
        val payload = detection?.first?.takeIf { it.isNotBlank() }
        return payload?.let { text ->
            val boundingBox = detection.second
            val normalized = boundingBox?.let { box ->
                createNormalizedBoundingBox(box, imageProxy)
            }
            QrDetection(
                payload = text,
                boundingBox = boundingBox,
                normalizedBoundingBox = normalized
            )
        }
    }

    private fun selectPayload(result: Text): Pair<String, Rect?>? {
        val blocks = result.textBlocks
        if (blocks.isEmpty()) {
            val fallback = result.text.trim().ifEmpty { null }
            return fallback?.let { it to null }
        }

        val prioritizedLine = blocks
            .flatMap { block -> block.lines }
            .mapNotNull { line ->
                val boundingBox = line.boundingBox ?: return@mapNotNull null
                val rawText = line.text.trim()
                if (rawText.isEmpty()) return@mapNotNull null
                LineCandidate(
                    text = rawText,
                    boundingBox = boundingBox,
                    bottom = boundingBox.bottom
                )
            }
            .sortedByDescending { it.bottom }
            .firstOrNull { candidate ->
                val normalized = candidate.text.replace(" ", "").replace("-", "")
                serialPattern.matches(normalized)
            }

        if (prioritizedLine != null) {
            val normalized = prioritizedLine.text
                .replace(" ", "")
                .replace("-", "")
                .uppercase()
            if (normalized.isNotEmpty()) {
                return normalized to prioritizedLine.boundingBox
            }
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

private data class LineCandidate(
    val text: String,
    val boundingBox: Rect,
    val bottom: Int
)

data class QrDetection(
    val payload: String,
    val boundingBox: Rect?,
    val normalizedBoundingBox: NormalizedBoundingBox?
)

data class NormalizedBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private fun createNormalizedBoundingBox(
    rect: Rect,
    imageProxy: ImageProxy
): NormalizedBoundingBox {
    val rotation = ((imageProxy.imageInfo.rotationDegrees % 360) + 360) % 360
    val width: Float
    val height: Float
    if (rotation == 90 || rotation == 270) {
        width = imageProxy.height.toFloat()
        height = imageProxy.width.toFloat()
    } else {
        width = imageProxy.width.toFloat()
        height = imageProxy.height.toFloat()
    }
    val safeWidth = max(width, 1f)
    val safeHeight = max(height, 1f)
    val left = (rect.left / safeWidth).coerceIn(0f, 1f)
    val top = (rect.top / safeHeight).coerceIn(0f, 1f)
    val right = (rect.right / safeWidth).coerceIn(0f, 1f)
    val bottom = (rect.bottom / safeHeight).coerceIn(0f, 1f)
    return NormalizedBoundingBox(
        left = min(left, right),
        top = min(top, bottom),
        right = max(left, right),
        bottom = max(top, bottom)
    )
}
