package com.sirim.scanner.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_scans",
    indices = [
        Index(value = ["sku_report"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class ProductScan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "sku_report")
    val skuReport: String,
    @ColumnInfo(name = "sku_name")
    val skuName: String,
    @ColumnInfo(name = "sirim_data")
    val sirimData: String? = null,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "product_image_path")
    val productImagePath: String? = null,
    @ColumnInfo(name = "total_captured")
    val totalCaptured: Int = 1
)
