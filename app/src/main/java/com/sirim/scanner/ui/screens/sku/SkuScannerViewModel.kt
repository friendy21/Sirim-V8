package com.sirim.scanner.ui.screens.sku

import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sirim.scanner.data.db.SkuExportRecord
import com.sirim.scanner.data.db.SkuRecord
import com.sirim.scanner.data.ocr.BarcodeAnalyzer
import com.sirim.scanner.data.ocr.BarcodeDetection
import com.sirim.scanner.data.export.ExportManager
import com.sirim.scanner.data.preferences.SkuSessionTracker
import com.sirim.scanner.data.repository.SirimRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class SkuScannerViewModel private constructor(
    private val repository: SirimRepository,
    private val analyzer: BarcodeAnalyzer,
    private val appScope: CoroutineScope,
    private val exportManager: ExportManager,
    private val sessionTracker: SkuSessionTracker
) : ViewModel() {

    private val processing = AtomicBoolean(false)

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _lastDetection = MutableStateFlow<BarcodeDetectionInfo?>(null)
    val lastDetection: StateFlow<BarcodeDetectionInfo?> = _lastDetection.asStateFlow()

    val databaseInfo: StateFlow<SkuDatabaseInfo?> = repository.skuRecords
        .map { records ->
            SkuDatabaseInfo(
                totalCount = records.size,
                uniqueCount = records.map { it.barcode }.distinct().size
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private var pendingDetection: BarcodeDetection? = null

    fun analyzeFrame(imageProxy: ImageProxy) {
        if (_captureState.value is CaptureState.Captured || _captureState.value is CaptureState.Processing) {
            imageProxy.close()
            return
        }
        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val detection = analyzer.analyze(imageProxy)
                withContext(Dispatchers.Main) {
                    if (detection != null && detection.value.isNotBlank()) {
                        pendingDetection = detection
                        _lastDetection.value = BarcodeDetectionInfo(
                            value = detection.value,
                            format = detection.format
                        )
                        if (_captureState.value !is CaptureState.Captured) {
                            _captureState.value = CaptureState.Ready("Barcode detected - Tap capture to save")
                        }
                    } else {
                        if (_captureState.value !is CaptureState.Processing &&
                            _captureState.value !is CaptureState.Saved &&
                            _captureState.value !is CaptureState.Captured) {
                            _captureState.value = CaptureState.Idle
                        }
                    }
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    _captureState.value = CaptureState.Error("Failed to analyze frame: ${error.message}")
                }
            } finally {
                imageProxy.close()
                processing.set(false)
            }
        }
    }

    fun onImageCaptured(bytes: ByteArray) {
        val detection = pendingDetection ?: run {
            _captureState.value = CaptureState.Error("No barcode detected")
            return
        }
        if (bytes.isEmpty()) {
            _captureState.value = CaptureState.Error("Captured image is empty")
            return
        }
        _captureState.value = CaptureState.Captured(
            msg = "Review captured image",
            detection = BarcodeDetectionInfo(
                value = detection.value,
                format = detection.format
            ),
            imageBytes = bytes
        )
    }

    fun onCaptureError(message: String) {
        _captureState.value = CaptureState.Error(message)
    }

    fun retakeCapture() {
        _captureState.value = if (pendingDetection != null) {
            CaptureState.Ready("Barcode detected - Tap capture to save")
        } else {
            CaptureState.Idle
        }
    }

    fun confirmCapture() {
        val detection = pendingDetection ?: run {
            _captureState.value = CaptureState.Error("No barcode detected")
            return
        }
        val captured = _captureState.value as? CaptureState.Captured ?: run {
            _captureState.value = CaptureState.Error("No captured image to save")
            return
        }

        appScope.launch {
            _captureState.value = CaptureState.Processing("Saving barcode...")

            try {
                val normalized = detection.value.trim()
                if (normalized.isEmpty()) {
                    _captureState.value = CaptureState.Error("Barcode value is empty")
                    return@launch
                }

                val imagePath = repository.persistImage(captured.imageBytes)

                val existing = repository.findByBarcode(normalized)

                val (recordId, isNew) = if (existing != null) {
                    val updated = existing.copy(
                        barcode = normalized,
                        imagePath = imagePath,
                        needsSync = true
                    )
                    repository.upsertSku(updated)
                    updated.id to false
                } else {
                    val record = SkuRecord(
                        barcode = normalized,
                        imagePath = imagePath,
                        createdAt = System.currentTimeMillis()
                    )
                    val id = repository.upsertSku(record)
                    id to true
                }

                sessionTracker.setCurrentSku(recordId)

                _captureState.value = CaptureState.Saved(
                    msg = if (isNew) {
                        "New barcode saved: $normalized"
                    } else {
                        "Barcode found in existing database: $normalized"
                    },
                    recordId = recordId,
                    isNewRecord = isNew,
                    imagePath = imagePath
                )

                exportSkuWorkbook()

                delay(2000)
                _captureState.value = CaptureState.Idle
                _lastDetection.value = null
                pendingDetection = null
            } catch (error: Exception) {
                _captureState.value = CaptureState.Error("Failed to save: ${error.message}")
            }
        }
    }

    private fun exportSkuWorkbook() {
        appScope.launch {
            runCatching {
                val skuRecords = repository.getAllSkuRecords()
                val ocrRecords = repository.getAllQrRecords()
                if (skuRecords.isEmpty() && ocrRecords.isEmpty()) return@launch

                val uri = exportManager.exportSkuToExcel(skuRecords, ocrRecords)
                val fileName = uri.lastPathSegment ?: "sku_records.xlsx"
                val totalCount = skuRecords.size + ocrRecords.size
                val exportRecord = SkuExportRecord(
                    uri = uri.toString(),
                    fileName = fileName,
                    recordCount = totalCount,
                    updatedAt = System.currentTimeMillis()
                )
                repository.recordSkuExport(exportRecord)
            }
        }
    }

    companion object {
        fun Factory(
            repository: SirimRepository,
            analyzer: BarcodeAnalyzer,
            appScope: CoroutineScope,
            exportManager: ExportManager,
            sessionTracker: SkuSessionTracker
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SkuScannerViewModel(
                    repository,
                    analyzer,
                    appScope,
                    exportManager,
                    sessionTracker
                ) as T
            }
        }
    }
}

sealed class CaptureState(val message: String) {
    data object Idle : CaptureState("Align barcode within the guide")
    data class Ready(val msg: String) : CaptureState(msg)
    data class Processing(val msg: String) : CaptureState(msg)
    data class Captured(
        val msg: String,
        val detection: BarcodeDetectionInfo,
        val imageBytes: ByteArray
    ) : CaptureState(msg)
    data class Saved(
        val msg: String,
        val recordId: Long?,
        val isNewRecord: Boolean,
        val imagePath: String?
    ) : CaptureState(msg)
    data class Error(val msg: String) : CaptureState(msg)
}

data class BarcodeDetectionInfo(
    val value: String,
    val format: String
)

data class SkuDatabaseInfo(
    val totalCount: Int,
    val uniqueCount: Int
)
