package com.sirim.scanner.data.ocr

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

interface QrAnalyzer {
    suspend fun analyze(imageProxy: ImageProxy): QrDetection?
}

class QrCodeAnalyzer : QrAnalyzer {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val serialPattern = Regex("^[A-Z0-9]{6,}$")
    private val referenceKeywords = listOf("SIRIM", "SIRIM QAS", "CERTIFIED")

    override suspend fun analyze(imageProxy: ImageProxy): QrDetection? {
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
            val hasReferenceMarkers = fallback?.hasReferenceMarkers(referenceKeywords) == true
            return if (hasReferenceMarkers) {
                fallback?.normalizeSerial()
                    ?.takeIf { serialPattern.matches(it) }
                    ?.let { it to null }
            } else {
                null
            }
        }

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
