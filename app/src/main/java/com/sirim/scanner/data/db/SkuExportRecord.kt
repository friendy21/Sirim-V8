package com.sirim.scanner.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sku_exports")
data class SkuExportRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "uri")
    val uri: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "record_count")
    val recordCount: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "barcode")
    val barcode: String? = null,
    @ColumnInfo(name = "field_count")
    val fieldCount: Int = 0,
    @ColumnInfo(name = "ocr_count")
    val ocrCount: Int = 0
)
