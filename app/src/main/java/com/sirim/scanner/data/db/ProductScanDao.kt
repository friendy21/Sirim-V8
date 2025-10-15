package com.sirim.scanner.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductScanDao {
    @Query("SELECT * FROM product_scans ORDER BY timestamp DESC")
    fun observeProductScans(): Flow<List<ProductScan>>

    @Query("SELECT * FROM product_scans WHERE id = :id")
    suspend fun getById(id: Long): ProductScan?

    @Query("SELECT * FROM product_scans WHERE sku_report = :sku LIMIT 1")
    suspend fun findBySkuReport(sku: String): ProductScan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(scan: ProductScan): Long

    @Update
    suspend fun update(scan: ProductScan)

    @Delete
    suspend fun delete(scan: ProductScan)

    @Query("UPDATE product_scans SET total_captured = total_captured + 1, timestamp = :timestamp WHERE id = :id")
    suspend fun incrementCapture(id: Long, timestamp: Long)

    @Query("UPDATE product_scans SET sirim_data = :data, timestamp = :timestamp WHERE id = :id")
    suspend fun updateSirimData(id: Long, data: String?, timestamp: Long)
}
