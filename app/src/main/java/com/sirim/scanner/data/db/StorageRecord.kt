package com.sirim.scanner.data.db

sealed class StorageRecord {
    abstract val id: Long
    abstract val createdAt: Long
    abstract val title: String
    abstract val description: String

    data class SirimScannerV2(
        val totalRecords: Int,
        val linkedRecords: Int,
        val lastUpdated: Long
    ) : StorageRecord() {
        override val id: Long = 1
        override val createdAt: Long = lastUpdated
        override val title: String = "SIRIM Scanner 2.0"
        override val description: String = when {
            totalRecords == 0 -> "No text captures stored"
            linkedRecords == 0 -> "$totalRecords text captures stored"
            linkedRecords == totalRecords -> "$totalRecords text captures linked to SKUs"
            else -> "$totalRecords text captures stored ($linkedRecords linked to SKUs)"
        }
    }

    data class SkuExport(
        val export: SkuExportRecord
    ) : StorageRecord() {
        override val id: Long = export.id + 10_000
        override val createdAt: Long = export.updatedAt
        override val title: String = export.fileName
        override val description: String = "${export.recordCount} SKU captures"
    }
}
