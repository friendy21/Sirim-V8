package com.sirim.scanner.data.export

import com.sirim.scanner.data.db.QrRecord
import com.sirim.scanner.data.db.SkuExportRecord
import com.sirim.scanner.data.db.SkuRecord
import com.sirim.scanner.data.preferences.SkuSessionTracker
import com.sirim.scanner.data.repository.SirimRepository

suspend fun refreshActiveSkuExport(
    sessionTracker: SkuSessionTracker,
    repository: SirimRepository,
    exportManager: ExportManager
) {
    val activeSkuId = sessionTracker.getCurrentSkuId() ?: return
    val activeSku = repository.getSkuRecord(activeSkuId) ?: return

    val allSkuRecords = repository.getAllSkuRecords()
    val allOcrRecords = repository.getAllQrRecords()
    val associatedOcr = resolveOcrForSku(activeSku, allSkuRecords, allOcrRecords)

    val exportResult = exportManager.exportSkuToExcel(activeSku, associatedOcr)
    val existingExport = repository.getSkuExportByBarcode(activeSku.barcode)

    val exportRecord = (existingExport?.copy(
        uri = exportResult.uri.toString(),
        fileName = exportResult.file.name,
        recordCount = exportResult.fieldCount + exportResult.ocrCount,
        updatedAt = System.currentTimeMillis(),
        barcode = activeSku.barcode,
        fieldCount = exportResult.fieldCount,
        ocrCount = exportResult.ocrCount
    )) ?: SkuExportRecord(
        uri = exportResult.uri.toString(),
        fileName = exportResult.file.name,
        recordCount = exportResult.fieldCount + exportResult.ocrCount,
        updatedAt = System.currentTimeMillis(),
        barcode = activeSku.barcode,
        fieldCount = exportResult.fieldCount,
        ocrCount = exportResult.ocrCount
    )

    repository.recordSkuExport(exportRecord)
}

fun resolveOcrForSku(
    sku: SkuRecord,
    allSkuRecords: List<SkuRecord>,
    allOcrRecords: List<QrRecord>
): List<QrRecord> {
    if (allOcrRecords.isEmpty()) return emptyList()

    val sortedSku = allSkuRecords.sortedBy { it.createdAt }
    val sortedOcr = allOcrRecords.sortedBy { it.capturedAt }
    val targetIndex = sortedSku.indexOfFirst { it.id == sku.id }
    if (targetIndex == -1) return emptyList()

    val startTime = sortedSku[targetIndex].createdAt
    val endTime = sortedSku.getOrNull(targetIndex + 1)?.createdAt

    return sortedOcr.filter { record ->
        val afterStart = record.capturedAt >= startTime
        val beforeEnd = endTime?.let { record.capturedAt < it } ?: true
        afterStart && beforeEnd
    }
}
