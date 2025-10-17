package com.sirim.scanner.ui.screens.qrcode

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sirim.scanner.data.db.QrRecord
import com.sirim.scanner.data.ocr.QrCodeAnalyzer
import com.sirim.scanner.data.ocr.QrDetection
import com.sirim.scanner.data.repository.SirimRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QrScannerViewModel private constructor(
    private val repository: SirimRepository,
    private val analyzer: QrCodeAnalyzer
) : ViewModel() {

    private val processing = AtomicBoolean(false)

    private val _captureState = MutableStateFlow<QrCaptureState>(QrCaptureState.Searching)
    val captureState: StateFlow<QrCaptureState> = _captureState.asStateFlow()

    private val _lastDetection = MutableStateFlow<QrDetection?>(null)
    val lastDetection: StateFlow<QrDetection?> = _lastDetection.asStateFlow()

    private val _scannerState = MutableStateFlow<ScannerWorkflowState>(ScannerWorkflowState.Idle)
    val scannerState: StateFlow<ScannerWorkflowState> = _scannerState.asStateFlow()

    private val _status = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val status: SharedFlow<String> = _status.asSharedFlow()

    private var pendingConfirmationPayload: String? = null

    fun analyzeFrame(imageProxy: ImageProxy) {
        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        if (_scannerState.value is ScannerWorkflowState.Success) {
            imageProxy.close()
            processing.set(false)
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val detection = analyzer.analyze(imageProxy)
                withContext(Dispatchers.Main) {
                    if (detection != null) {
                        val previous = _lastDetection.value?.payload
                        _lastDetection.value = detection
                        if (_captureState.value !is QrCaptureState.Saving) {
                            _captureState.value = QrCaptureState.Ready("Text detected")
                        }
                        if (pendingConfirmationPayload == detection.payload) {
                            _scannerState.value = ScannerWorkflowState.Success(detection)
                            pendingConfirmationPayload = null
                        } else {
                            pendingConfirmationPayload = detection.payload
                            _scannerState.value = ScannerWorkflowState.Detecting(detection)
                        }
                        if (previous == null || previous != detection.payload) {
                            _status.tryEmit("Text detected")
                        }
                    } else {
                        pendingConfirmationPayload = null
                        if (_captureState.value !is QrCaptureState.Saving) {
                            _captureState.value = QrCaptureState.Searching
                        }
                        if (_scannerState.value !is ScannerWorkflowState.Success) {
                            _scannerState.value = ScannerWorkflowState.Idle
                        }
                    }
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    _status.tryEmit("Scanning failed: ${error.message ?: "Unknown error"}")
                }
            } finally {
                imageProxy.close()
                processing.set(false)
            }
        }
    }

    fun analyzeBitmap(bitmap: Bitmap) {
        if (_captureState.value is QrCaptureState.Saving) {
            _status.tryEmit("Wait for save to finish")
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val detection = analyzer.analyze(bitmap)
                withContext(Dispatchers.Main) {
                    if (detection != null) {
                        pendingConfirmationPayload = null
                        _lastDetection.value = detection
                        _captureState.value = QrCaptureState.Ready("Text detected")
                        _scannerState.value = ScannerWorkflowState.Success(detection)
                        _status.tryEmit("Text detected from image")
                    } else {
                        pendingConfirmationPayload = null
                        _lastDetection.value = null
                        if (_captureState.value !is QrCaptureState.Saving) {
                            _captureState.value = QrCaptureState.Searching
                        }
                        _scannerState.value = ScannerWorkflowState.Idle
                        _status.tryEmit("No readable text found in image")
                    }
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    _status.tryEmit("Scanning failed: ${error.message ?: "Unknown error"}")
                }
            }
        }
    }

    fun clearDetection() {
        retry()
    }

    fun saveRecord(
        label: String?,
        fieldSource: String?,
        fieldNote: String?,
        onSaved: (Long) -> Unit,
        onDuplicate: (Long) -> Unit
    ) {
        val detection = _lastDetection.value ?: run {
            _status.tryEmit("Scan text first")
            return
        }
        if (_captureState.value is QrCaptureState.Saving) return
        viewModelScope.launch(Dispatchers.IO) {
            _captureState.value = QrCaptureState.Saving
            val normalizedLabel = label?.takeIf { it.isNotBlank() }?.trim()
            val normalizedSource = fieldSource?.takeIf { it.isNotBlank() }?.trim()
            val normalizedNote = fieldNote?.takeIf { it.isNotBlank() }?.trim()
            val existing = repository.findByQrPayload(detection.payload)
            if (existing != null) {
                _captureState.value = QrCaptureState.Duplicate("Text already saved")
                withContext(Dispatchers.Main) { onDuplicate(existing.id) }
                _status.tryEmit("Text already exists in database")
                return@launch
            }
            val record = QrRecord(
                payload = detection.payload,
                label = normalizedLabel,
                fieldSource = normalizedSource,
                fieldNote = normalizedNote
            )
            val id = repository.upsertQr(record)
            _captureState.value = QrCaptureState.Saved("Text saved")
            _status.tryEmit("OCR record saved")
            withContext(Dispatchers.Main) { onSaved(id) }
        }
    }

    fun retry() {
        pendingConfirmationPayload = null
        _lastDetection.value = null
        _captureState.value = QrCaptureState.Searching
        _scannerState.value = ScannerWorkflowState.Idle
    }

    companion object {
        fun Factory(
            repository: SirimRepository,
            analyzer: QrCodeAnalyzer
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(QrScannerViewModel::class.java))
                    return QrScannerViewModel(repository, analyzer) as T
                }
            }
        }
    }
}

sealed interface QrCaptureState {
    data object Searching : QrCaptureState
    data class Ready(val message: String) : QrCaptureState
    data object Saving : QrCaptureState
    data class Saved(val message: String) : QrCaptureState
    data class Duplicate(val message: String) : QrCaptureState
}

sealed interface ScannerWorkflowState {
    data object Idle : ScannerWorkflowState
    data class Detecting(val detection: QrDetection) : ScannerWorkflowState
    data class Success(val detection: QrDetection) : ScannerWorkflowState
}
