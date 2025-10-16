package com.sirim.scanner.ui.screens.qrcode

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sirim.scanner.data.db.QrRecord
import com.sirim.scanner.data.db.SkuExportRecord
import com.sirim.scanner.data.db.SkuRecord
import com.sirim.scanner.data.db.StorageRecord
import com.sirim.scanner.data.export.ExportManager
import com.sirim.scanner.data.export.SkuExportRefresher
import com.sirim.scanner.data.ocr.QrAnalyzer
import com.sirim.scanner.data.ocr.QrDetection
import com.sirim.scanner.data.preferences.SkuSessionTracker
import com.sirim.scanner.data.repository.SirimRepository
import com.sirim.scanner.ui.screens.qrcode.SkuSessionState
import com.sirim.scanner.util.MainDispatcherRule
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QrScannerViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `saveRecord triggers export refresher`() = runBlocking {
        val repository = FakeSirimRepository()
        val skuRecord = SkuRecord(id = 1, barcode = "ABC123")
        repository.setSkuRecords(listOf(skuRecord))
        val sessionTracker = FakeSkuSessionTracker(initialSkuId = skuRecord.id)
        val refresher = RecordingSkuExportRefresher()
        val viewModel = createViewModel(repository, sessionTracker, refresher)
        waitForActiveSession(viewModel)
        viewModel.setDetectionForTest(QrDetection(payload = "PAYLOAD-1", boundingBox = null, normalizedBoundingBox = null))

        viewModel.saveRecord(
            label = "Serial",
            fieldSource = "Plate",
            fieldNote = "",
            onSaved = {},
            onDuplicate = {}
        )

        withTimeout(5_000) {
            while (refresher.refreshedSkuIds.isEmpty()) {
                delay(10)
            }
        }

        assertEquals(listOf(skuRecord.id), refresher.refreshedSkuIds)
    }

    @Test
    fun `saveRecord rewrites workbook with new ocr row`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = FakeSirimRepository()
        val skuRecord = SkuRecord(id = 2, barcode = "XYZ789")
        repository.setSkuRecords(listOf(skuRecord))
        val sessionTracker = FakeSkuSessionTracker(initialSkuId = skuRecord.id)
        val exportManager = ExportManager(context)
        val refresher = SkuExportRefresher(repository, exportManager)
        val viewModel = createViewModel(repository, sessionTracker, refresher)
        waitForActiveSession(viewModel)
        val payload = "NEW-OCR-ROW"
        viewModel.setDetectionForTest(QrDetection(payload = payload, boundingBox = null, normalizedBoundingBox = null))

        viewModel.saveRecord(
            label = "Serial",
            fieldSource = "Tag",
            fieldNote = "",
            onSaved = {},
            onDuplicate = {}
        )

        val exportRecord = withTimeout(5_000) {
            while (repository.lastExportRecord.get() == null) {
                delay(10)
            }
            repository.lastExportRecord.get()
        }

        assertNotNull(exportRecord)
        assertEquals(1, exportRecord!!.ocrCount)
        assertEquals(exportRecord.fieldCount + exportRecord.ocrCount, exportRecord.recordCount)

        val exportFile = File(context.getExternalFilesDir(null), "exports/sku/${exportRecord.fileName}")
        assertTrue(exportFile.exists())
        FileInputStream(exportFile).use { input ->
            XSSFWorkbook(input).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val firstColumnValues = (0..sheet.lastRowNum).mapNotNull { rowIndex ->
                    sheet.getRow(rowIndex)?.getCell(0)?.stringCellValue
                }
                assertTrue(firstColumnValues.contains(payload))
            }
        }
    }

    private fun createViewModel(
        repository: SirimRepository,
        sessionTracker: SkuSessionTracker,
        refresher: SkuExportRefresher
    ): QrScannerViewModel {
        return QrScannerViewModel.Factory(
            repository = repository,
            analyzer = NoopQrAnalyzer,
            sessionTracker = sessionTracker,
            exportRefresher = refresher
        ).create(QrScannerViewModel::class.java)
    }

    private fun QrScannerViewModel.setDetectionForTest(detection: QrDetection) {
        val field = QrScannerViewModel::class.java.getDeclaredField("_lastDetection")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = field.get(this) as MutableStateFlow<QrDetection?>
        state.value = detection
    }

    private suspend fun waitForActiveSession(viewModel: QrScannerViewModel) {
        withTimeout(5_000) {
            while (viewModel.sessionState.value !is SkuSessionState.Active) {
                delay(10)
            }
        }
    }

    private object NoopQrAnalyzer : QrAnalyzer {
        override suspend fun analyze(imageProxy: androidx.camera.core.ImageProxy) = null
    }

    private class RecordingSkuExportRefresher : SkuExportRefresher(
        repository = FakeSirimRepository(),
        exportManager = ExportManager(ApplicationProvider.getApplicationContext<Context>())
    ) {
        val refreshedSkuIds = mutableListOf<Long>()

        override suspend fun refreshForSkuId(skuId: Long) {
            refreshedSkuIds += skuId
        }
    }

    private class FakeSkuSessionTracker(initialSkuId: Long?) : SkuSessionTracker {
        private val current = MutableStateFlow(initialSkuId)

        override val currentSkuIdFlow: Flow<Long?>
            get() = current

        override suspend fun setCurrentSku(recordId: Long?) {
            current.value = recordId
        }

        override suspend fun getCurrentSkuId(): Long? = current.value
    }

    private class FakeSirimRepository : SirimRepository {
        private val qrRecordsFlow = MutableStateFlow<List<QrRecord>>(emptyList())
        private val skuRecordsFlow = MutableStateFlow<List<SkuRecord>>(emptyList())
        private val skuExportsFlow = MutableStateFlow<List<SkuExportRecord>>(emptyList())
        private val storageFlow = MutableStateFlow<List<StorageRecord>>(emptyList())
        private val qrRecordsList = mutableListOf<QrRecord>()
        private val skuRecordsMap = mutableMapOf<Long, SkuRecord>()
        private var nextQrId = 1L
        private var nextExportId = 1L

        val lastExportRecord: AtomicReference<SkuExportRecord?> = AtomicReference(null)

        override val qrRecords: Flow<List<QrRecord>>
            get() = qrRecordsFlow

        override val skuRecords: Flow<List<SkuRecord>>
            get() = skuRecordsFlow

        override val storageRecords: Flow<List<StorageRecord>>
            get() = storageFlow

        override val skuExports: Flow<List<SkuExportRecord>>
            get() = skuExportsFlow

        fun setSkuRecords(records: List<SkuRecord>) {
            skuRecordsMap.clear()
            records.forEach { skuRecordsMap[it.id] = it }
            skuRecordsFlow.value = records
        }

        override fun searchQr(query: String): Flow<List<QrRecord>> = qrRecordsFlow

        override fun searchSku(query: String): Flow<List<SkuRecord>> = skuRecordsFlow

        override fun searchAll(query: String): Flow<List<StorageRecord>> = storageFlow

        override suspend fun upsertQr(record: QrRecord): Long {
            val id = if (record.id == 0L) nextQrId++ else record.id
            val stored = record.copy(id = id)
            qrRecordsList.removeAll { it.id == id }
            qrRecordsList.add(stored)
            qrRecordsFlow.value = qrRecordsList.toList()
            return id
        }

        override suspend fun upsertSku(record: SkuRecord): Long {
            val assignedId = if (record.id == 0L) (skuRecordsMap.keys.maxOrNull() ?: 0L) + 1 else record.id
            val stored = record.copy(id = assignedId)
            skuRecordsMap[stored.id] = stored
            skuRecordsFlow.value = skuRecordsMap.values.toList()
            return stored.id
        }

        override suspend fun deleteQr(record: QrRecord) = Unit

        override suspend fun deleteSku(record: SkuRecord) = Unit

        override suspend fun clearQr() {
            qrRecordsList.clear()
            qrRecordsFlow.value = emptyList()
        }

        override suspend fun deleteSkuExport(record: SkuExportRecord) {
            lastExportRecord.set(null)
            skuExportsFlow.value = emptyList()
        }

        override suspend fun getQrRecord(id: Long): QrRecord? = qrRecordsList.firstOrNull { it.id == id }

        override suspend fun getSkuRecord(id: Long): SkuRecord? = skuRecordsMap[id]

        override suspend fun getAllSkuRecords(): List<SkuRecord> = skuRecordsMap.values.toList()

        override suspend fun getAllQrRecords(): List<QrRecord> = qrRecordsList.toList()

        override fun observeQrRecordsForSku(skuId: Long): Flow<List<QrRecord>> =
            qrRecordsFlow.map { records -> records.filter { it.skuId == skuId } }

        override suspend fun getQrRecordsForSku(skuId: Long): List<QrRecord> =
            qrRecordsList.filter { it.skuId == skuId }

        override suspend fun findByQrPayload(qrPayload: String): QrRecord? =
            qrRecordsList.firstOrNull { it.payload == qrPayload }

        override suspend fun findByBarcode(barcode: String): SkuRecord? =
            skuRecordsMap.values.firstOrNull { it.barcode == barcode }

        override suspend fun findSkuExportByBarcode(barcode: String): SkuExportRecord? {
            return lastExportRecord.get()?.takeIf { it.barcode == barcode }
        }

        override suspend fun persistImage(bytes: ByteArray, extension: String): String {
            throw UnsupportedOperationException("Not needed in tests")
        }

        override suspend fun recordSkuExport(record: SkuExportRecord): Long {
            val assigned = if (record.id == 0L) {
                record.copy(id = nextExportId++)
            } else {
                record
            }
            lastExportRecord.set(assigned)
            skuExportsFlow.value = listOf(assigned)
            return assigned.id
        }
    }
}
