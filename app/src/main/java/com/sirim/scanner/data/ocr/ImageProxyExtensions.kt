package com.sirim.scanner.data.ocr

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import kotlin.math.min

fun ImageProxy.toBitmap(): Bitmap? {
    val nv21 = toNv21ByteArray() ?: return null
    val bitmap = BitmapUtils.nv21ToBitmap(nv21, width, height) ?: return null
    if (imageInfo.rotationDegrees == 0) {
        return bitmap
    }
    val matrix = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { rotated ->
        if (rotated !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

private fun ImageProxy.toNv21ByteArray(): ByteArray? {
    if (planes.size < 3) return null

    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    val width = width
    val height = height
    val nv21 = ByteArray(width * height * 3 / 2)

    // Copy Y plane row by row accounting for row stride.
    val yRowStride = yPlane.rowStride
    val yRow = ByteArray(yRowStride)
    var outputOffset = 0
    for (row in 0 until height) {
        val length = min(yRowStride, yBuffer.remaining())
        yBuffer.get(yRow, 0, length)
        val copyLength = min(width, length)
        yRow.copyInto(
            destination = nv21,
            destinationOffset = outputOffset,
            startIndex = 0,
            endIndex = copyLength
        )
        outputOffset += width
    }

    val uvHeight = height / 2
    val uRowStride = uPlane.rowStride
    val vRowStride = vPlane.rowStride
    val uPixelStride = uPlane.pixelStride
    val vPixelStride = vPlane.pixelStride

    uBuffer.rewind()
    vBuffer.rewind()

    for (row in 0 until uvHeight) {
        val uRowStart = row * uRowStride
        val vRowStart = row * vRowStride
        for (col in 0 until width / 2) {
            val uIndex = uRowStart + col * uPixelStride
            val vIndex = vRowStart + col * vPixelStride
            val uByte = if (uIndex < uBuffer.limit()) uBuffer.get(uIndex) else 0
            val vByte = if (vIndex < vBuffer.limit()) vBuffer.get(vIndex) else 0
            nv21[outputOffset++] = vByte
            nv21[outputOffset++] = uByte
        }
    }

    return nv21
}
