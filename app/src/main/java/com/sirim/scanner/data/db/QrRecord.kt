package com.sirim.scanner.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "qr_records",
    indices = [
        Index(value = ["payload"], unique = true),
        Index(value = ["captured_at"])
    ]
)
data class QrRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "payload")
    val payload: String,
    @ColumnInfo(name = "label")
    val label: String? = null,
    @ColumnInfo(name = "field_source")
    val fieldSource: String? = null,
    @ColumnInfo(name = "field_note")
    val fieldNote: String? = null,
    @ColumnInfo(name = "sku_id")
    val skuId: Long? = null,
    @ColumnInfo(name = "captured_at")
    val capturedAt: Long = System.currentTimeMillis()
)
