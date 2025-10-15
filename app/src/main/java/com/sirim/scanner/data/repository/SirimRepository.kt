package com.sirim.scanner.data.repository

import com.sirim.scanner.data.db.ProductScan
import com.sirim.scanner.data.db.QrRecord
import com.sirim.scanner.data.db.SkuRecord
import com.sirim.scanner.data.db.SkuExportRecord
import kotlinx.coroutines.flow.Flow

interface SirimRepository {
    val qrRecords: Flow<List<QrRecord>>
    val skuRecords: Flow<List<SkuRecord>>
    val productScans: Flow<List<ProductScan>>
    val skuExports: Flow<List<SkuExportRecord>>

    fun searchQr(query: String): Flow<List<QrRecord>>
    fun searchSku(query: String): Flow<List<SkuRecord>>

    suspend fun upsertQr(record: QrRecord): Long
    suspend fun upsertSku(record: SkuRecord): Long
    suspend fun recordProductScan(
        skuReport: String,
        skuName: String,
        sirimData: String?,
        imagePath: String?
    ): Long
    suspend fun updateProductScan(scan: ProductScan): Long

    suspend fun deleteQr(record: QrRecord)
    suspend fun deleteSku(record: SkuRecord)
    suspend fun deleteProductScan(scan: ProductScan)

    suspend fun clearQr()
    suspend fun deleteSkuExport(record: SkuExportRecord)

    suspend fun getQrRecord(id: Long): QrRecord?
    suspend fun getSkuRecord(id: Long): SkuRecord?
    suspend fun getProductScan(id: Long): ProductScan?
    suspend fun getAllSkuRecords(): List<SkuRecord>
    suspend fun getAllQrRecords(): List<QrRecord>

    suspend fun findByQrPayload(qrPayload: String): QrRecord?
    suspend fun findByBarcode(barcode: String): SkuRecord?
    suspend fun findProductScanBySku(sku: String): ProductScan?

    suspend fun updateProductScanSirimData(id: Long, data: String?)

    suspend fun persistImage(bytes: ByteArray, extension: String = "jpg"): String

    suspend fun recordSkuExport(record: SkuExportRecord): Long
}
