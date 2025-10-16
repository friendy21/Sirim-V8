package com.sirim.scanner.data.repository

import com.sirim.scanner.data.db.QrRecord
import com.sirim.scanner.data.db.SkuRecord
import com.sirim.scanner.data.db.SkuExportRecord
import com.sirim.scanner.data.db.StorageRecord
import kotlinx.coroutines.flow.Flow

interface SirimRepository {
    val qrRecords: Flow<List<QrRecord>>
    val skuRecords: Flow<List<SkuRecord>>
    val storageRecords: Flow<List<StorageRecord>>
    val skuExports: Flow<List<SkuExportRecord>>

    val records: Flow<List<QrRecord>>
        get() = qrRecords

    fun searchQr(query: String): Flow<List<QrRecord>>
    fun searchSku(query: String): Flow<List<SkuRecord>>
    fun searchAll(query: String): Flow<List<StorageRecord>>

    fun search(query: String): Flow<List<QrRecord>> = searchQr(query)

    suspend fun upsertQr(record: QrRecord): Long
    suspend fun upsertSku(record: SkuRecord): Long

    suspend fun deleteQr(record: QrRecord)
    suspend fun deleteSku(record: SkuRecord)

    suspend fun clearQr()
    suspend fun deleteSkuExport(record: SkuExportRecord)

    suspend fun getQrRecord(id: Long): QrRecord?
    suspend fun getSkuRecord(id: Long): SkuRecord?
    suspend fun getAllSkuRecords(): List<SkuRecord>
    suspend fun getAllQrRecords(): List<QrRecord>
    fun observeQrRecordsForSku(skuId: Long): Flow<List<QrRecord>>
    suspend fun getQrRecordsForSku(skuId: Long): List<QrRecord>

    suspend fun findByQrPayload(qrPayload: String): QrRecord?
    suspend fun findByBarcode(barcode: String): SkuRecord?
    suspend fun findSkuExportByBarcode(barcode: String): SkuExportRecord?

    suspend fun persistImage(bytes: ByteArray, extension: String = "jpg"): String

    suspend fun recordSkuExport(record: SkuExportRecord): Long
}
