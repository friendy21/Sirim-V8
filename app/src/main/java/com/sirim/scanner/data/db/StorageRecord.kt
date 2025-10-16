package com.sirim.scanner.data.db

sealed class StorageRecord {
    abstract val id: Long
    abstract val createdAt: Long
    abstract val title: String
    abstract val description: String

    data class SirimScannerV2(
        val totalRecords: Int,
        val lastUpdated: Long
    ) : StorageRecord() {
        override val id: Long = 1
        override val createdAt: Long = lastUpdated
        override val title: String = "SIRIM Scanner 2.0"
        override val description: String = when (totalRecords) {
            0 -> "No text captures stored"
            1 -> "1 text capture stored"
            else -> "$totalRecords text captures stored"
        }
    }

    data class SkuExport(
        val export: SkuExportRecord
    ) : StorageRecord() {
        override val id: Long = export.id + 10_000
        override val createdAt: Long = export.updatedAt
        override val title: String = export.fileName
        override val description: String = "${export.fieldCount} form fields • ${export.ocrCount} OCR captures"
        val fieldCount: Int = export.fieldCount
        val ocrCount: Int = export.ocrCount
    }
}
