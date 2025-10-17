package com.sirim.scanner.data.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.tasks.await

import com.sirim.scanner.data.preferences.PreferencesManager

class QrCodeAnalyzer(
    initialReferenceKeywords: List<String> = PreferencesManager.DEFAULT_REFERENCE_MARKERS
) {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val serialPattern = Regex("^T[A-Z]{2}\\d{7}$")
    @Volatile
    private var referenceKeywords: List<String> = normalizeKeywords(initialReferenceKeywords)

    suspend fun analyze(imageProxy: ImageProxy): QrDetection? {
        val mediaImage = imageProxy.image ?: return null
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val result = recognizeText(inputImage) ?: return null

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

    suspend fun analyze(bitmap: Bitmap): QrDetection? {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val result = recognizeText(inputImage) ?: return null

        val detection = selectPayload(result)
        val payload = detection?.first?.takeIf { it.isNotBlank() }
        return payload?.let { text ->
            val boundingBox = detection.second
            val normalized = boundingBox?.let { box ->
                createNormalizedBoundingBox(box, bitmap.width, bitmap.height)
            }
            QrDetection(
                payload = text,
                boundingBox = boundingBox,
                normalizedBoundingBox = normalized
            )
        }
    }

    private suspend fun recognizeText(inputImage: InputImage): Text? {
        return runCatching { textRecognizer.process(inputImage).await() }.getOrNull()
    }

    private fun currentReferenceKeywords(): List<String> {
        val keywords = referenceKeywords
        return if (keywords.isNotEmpty()) keywords else PreferencesManager.DEFAULT_REFERENCE_MARKERS
    }

    fun updateReferenceKeywords(keywords: List<String>) {
        referenceKeywords = normalizeKeywords(keywords)
    }

    private fun selectPayload(result: Text): Pair<String, Rect?>? {
        val blocks = result.textBlocks
        if (blocks.isEmpty()) {
            val fallback = result.text.trim().ifEmpty { null }
            val referenceKeywords = currentReferenceKeywords()
            val hasReferenceMarkers = fallback?.hasReferenceMarkers(referenceKeywords) == true
            return if (hasReferenceMarkers) {
                fallback?.normalizeSerial()
                    ?.takeIf { serialPattern.matches(it) }
                    ?.let { it to null }
            } else {
                null
            }
        }

        val referenceKeywords = currentReferenceKeywords()
        val blockCandidates = blocks.mapNotNull { block ->
            val boundingBox = block.boundingBox ?: return@mapNotNull null
            val blockText = block.text.normalizeForComparison()
            val hasReference = referenceKeywords.any { keyword ->
                blockText.contains(keyword)
            }
            BlockCandidate(
                block = block,
                boundingBox = boundingBox,
                bottom = boundingBox.bottom,
                hasReference = hasReference
            )
        }

        if (blockCandidates.isEmpty()) {
            return null
        }

        val hasReferenceBlock = blockCandidates.any { it.hasReference }
        val hasReferenceAnywhere = hasReferenceBlock || result.text.hasReferenceMarkers(referenceKeywords)
        if (!hasReferenceAnywhere) {
            return null
        }

        val serialCandidate = blockCandidates
            .flatMap { candidate ->
                candidate.block.lines.mapNotNull { line ->
                    val boundingBox = line.boundingBox ?: return@mapNotNull null
                    val rawText = line.text.trim()
                    if (rawText.isEmpty()) return@mapNotNull null
                    val normalized = rawText.normalizeSerial()
                    if (!serialPattern.matches(normalized)) return@mapNotNull null
                    LineCandidate(
                        normalized = normalized,
                        boundingBox = boundingBox,
                        bottom = boundingBox.bottom,
                        blockHasReference = candidate.hasReference
                    )
                }
            }
            .sortedByDescending { it.bottom }
            .firstOrNull { lineCandidate ->
                val referenceAbove = blockCandidates.any { candidate ->
                    candidate.hasReference && candidate.bottom <= lineCandidate.boundingBox.top
                }
                lineCandidate.blockHasReference || referenceAbove
            }

        return serialCandidate?.let { it.normalized to it.boundingBox }
    }
}

private fun normalizeKeywords(keywords: List<String>): List<String> {
    return keywords
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.uppercase(Locale.ROOT) }
        .map { it.uppercase(Locale.ROOT) }
}


private data class LineCandidate(
    val normalized: String,
    val boundingBox: Rect,
    val bottom: Int,
    val blockHasReference: Boolean
)

private data class BlockCandidate(
    val block: Text.TextBlock,
    val boundingBox: Rect,
    val bottom: Int,
    val hasReference: Boolean
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

private fun createNormalizedBoundingBox(
    rect: Rect,
    width: Int,
    height: Int
): NormalizedBoundingBox {
    val safeWidth = max(width.toFloat(), 1f)
    val safeHeight = max(height.toFloat(), 1f)
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

private fun String.normalizeSerial(): String {
    return trim()
        .replace(" ", "")
        .replace("-", "")
        .uppercase(Locale.ROOT)
}

private fun String.normalizeForComparison(): String {
    return trim()
        .replace("\\s+".toRegex(), " ")
        .uppercase(Locale.ROOT)
}

private fun String.hasReferenceMarkers(referenceKeywords: List<String>): Boolean {
    if (isBlank()) return false
    val normalized = normalizeForComparison()
    return referenceKeywords.any { keyword -> normalized.contains(keyword) }
}
