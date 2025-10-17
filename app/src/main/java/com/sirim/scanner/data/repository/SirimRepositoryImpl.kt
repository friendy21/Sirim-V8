package com.sirim.scanner.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.sirim.scanner.data.db.QrRecord
import com.sirim.scanner.data.db.QrRecordDao
import com.sirim.scanner.data.db.SkuRecord
import com.sirim.scanner.data.db.SkuRecordDao
import com.sirim.scanner.data.db.SkuExportDao
import com.sirim.scanner.data.db.SkuExportRecord
import com.sirim.scanner.data.db.StorageRecord
import com.sirim.scanner.data.db.toGalleryList
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SirimRepositoryImpl(
    private val qrDao: QrRecordDao,
    private val skuDao: SkuRecordDao,
    private val skuExportDao: SkuExportDao,
    private val context: Context
) : SirimRepository {

    private val fileMutex = Mutex()

    override val qrRecords: Flow<List<QrRecord>> = qrDao.getAllRecords()

    override val skuRecords: Flow<List<SkuRecord>> = skuDao.getAllRecords()

    override val skuExports: Flow<List<SkuExportRecord>> = skuExportDao.observeExports()

    override val storageRecords: Flow<List<StorageRecord>> = combine(qrRecords, skuExports) { qr, exports ->
        val storageItems = mutableListOf<StorageRecord>()
        val lastUpdated = qr.maxOfOrNull { it.capturedAt } ?: 0L
        storageItems += StorageRecord.SirimScannerV2(
            totalRecords = qr.size,
            lastUpdated = lastUpdated
        )
        storageItems += exports.map { StorageRecord.SkuExport(it) }
        storageItems.sortedByDescending { it.createdAt }
    }

    override fun searchQr(query: String): Flow<List<QrRecord>> = qrDao.searchRecords("%$query%")

    override fun searchSku(query: String): Flow<List<SkuRecord>> = skuDao.searchRecords("%$query%")

    override fun searchAll(query: String): Flow<List<StorageRecord>> = storageRecords

    override suspend fun upsertQr(record: QrRecord): Long = qrDao.upsert(record)

    override suspend fun upsertSku(record: SkuRecord): Long = skuDao.upsert(record)

    override suspend fun deleteQr(record: QrRecord) {
        qrDao.delete(record)
    }

    override suspend fun deleteSku(record: SkuRecord) {
        record.imagePath?.let { path ->
            runCatching { File(path).takeIf(File::exists)?.delete() }
        }
        record.galleryPaths.toGalleryList().forEach { path ->
            runCatching { File(path).takeIf(File::exists)?.delete() }
        }
        skuDao.delete(record)
    }

    override suspend fun clearQr() = withContext(Dispatchers.IO) {
        qrDao.clearAll()
    }

    override suspend fun deleteSkuExport(record: SkuExportRecord) = withContext(Dispatchers.IO) {
        deleteSkuExportFile(record)
        skuExportDao.delete(record)
    }

    override suspend fun getQrRecord(id: Long): QrRecord? = qrDao.getRecordById(id)

    override suspend fun getSkuRecord(id: Long): SkuRecord? = skuDao.getRecordById(id)

    override suspend fun getAllSkuRecords(): List<SkuRecord> = skuDao.getAllRecordsOnce()

    override suspend fun getAllQrRecords(): List<QrRecord> = qrDao.getAllRecordsOnce()

    override suspend fun findByQrPayload(qrPayload: String): QrRecord? = qrDao.findByPayload(qrPayload)

    override suspend fun findByBarcode(barcode: String): SkuRecord? = skuDao.findByBarcode(barcode)

    override suspend fun getSkuExportByBarcode(barcode: String): SkuExportRecord? =
        skuExportDao.findByBarcode(barcode)

    override suspend fun persistImage(bytes: ByteArray, extension: String): String {
        val directory = File(context.filesDir, "captured")
        if (!directory.exists()) directory.mkdirs()
        return fileMutex.withLock {
            val file = File(directory, "qr_${System.currentTimeMillis()}.$extension")
            FileOutputStream(file).use { output ->
                output.write(bytes)
            }
            file.absolutePath
        }
    }

    override suspend fun recordSkuExport(record: SkuExportRecord): Long = skuExportDao.upsert(record)

    private fun deleteSkuExportFile(record: SkuExportRecord) {
        val uri = Uri.parse(record.uri)
        val file = when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> {
                val segments = uri.pathSegments
                if (segments.isEmpty()) {
                    null
                } else {
                    val relativePath = segments.drop(1).joinToString(File.separator)
                    val baseDir = context.getExternalFilesDir(null)
                    if (baseDir != null && relativePath.isNotEmpty()) {
                        File(baseDir, relativePath)
                    } else {
                        null
                    }
                }
            }

            ContentResolver.SCHEME_FILE -> uri.path?.let(::File)

            else -> uri.path?.let(::File) ?: File(record.uri)
        }

        file?.takeIf(File::exists)?.let { runCatching { it.delete() } }
    }
}
