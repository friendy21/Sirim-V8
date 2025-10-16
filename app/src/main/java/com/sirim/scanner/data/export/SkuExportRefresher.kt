package com.sirim.scanner.data.export

import com.sirim.scanner.data.db.SkuExportRecord
import com.sirim.scanner.data.db.SkuRecord
import com.sirim.scanner.data.repository.SirimRepository

open class SkuExportRefresher(
    private val repository: SirimRepository,
    private val exportManager: ExportManager
) {

    open suspend fun refreshForSkuId(skuId: Long) {
        val skuRecord = repository.getSkuRecord(skuId) ?: return
        val ocrRecords = repository.getQrRecordsForSku(skuId)
        val exportResult = exportManager.exportSkuToExcel(skuRecord, ocrRecords)
        val now = System.currentTimeMillis()
        val metadata = repository.findSkuExportByBarcode(skuRecord.barcode)
            ?.toUpdatedRecord(exportResult, now)
            ?: exportResult.toNewRecord(skuRecord, now)
        repository.recordSkuExport(metadata)
    }

    private fun SkuExportRecord.toUpdatedRecord(
        exportResult: ExportManager.SkuExportResult,
        timestamp: Long
    ): SkuExportRecord {
        return copy(
            uri = exportResult.uri.toString(),
            fileName = exportResult.fileName,
            fieldCount = exportResult.fieldCount,
            ocrCount = exportResult.ocrCount,
            recordCount = exportResult.fieldCount + exportResult.ocrCount,
            updatedAt = timestamp
        )
    }

    private fun ExportManager.SkuExportResult.toNewRecord(
        skuRecord: SkuRecord,
        timestamp: Long
    ): SkuExportRecord {
        return SkuExportRecord(
            barcode = skuRecord.barcode,
            uri = uri.toString(),
            fileName = fileName,
            fieldCount = fieldCount,
            ocrCount = ocrCount,
            recordCount = fieldCount + ocrCount,
            updatedAt = timestamp
        )
    }
}
