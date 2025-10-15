package com.sirim.scanner.ui.screens.qrcode

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sirim.scanner.R
import com.sirim.scanner.data.ocr.QrCodeAnalyzer
import com.sirim.scanner.data.ocr.QrDetection
import com.sirim.scanner.data.repository.SirimRepository
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    onRecordSaved: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    repository: SirimRepository,
    analyzer: QrCodeAnalyzer
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val viewModel: QrScannerViewModel = viewModel(
        factory = QrScannerViewModel.Factory(repository, analyzer)
    )

    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    val lastDetection by viewModel.lastDetection.collectAsStateWithLifecycle()
    val scannerState by viewModel.scannerState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var label by rememberSaveable { mutableStateOf("") }
    var fieldSource by rememberSaveable { mutableStateOf("") }
    var fieldNote by rememberSaveable { mutableStateOf("") }

    var brightnessSlider by rememberSaveable { mutableFloatStateOf(0f) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewController by remember { mutableStateOf<PreviewController?>(null) }
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val zoomState = camera?.cameraInfo?.zoomState?.observeAsState()
    val torchState = camera?.cameraInfo?.torchState?.observeAsState()
    val exposureState = camera?.cameraInfo?.exposureState
    val exposureRange = exposureState?.exposureCompensationRange
    val brightnessRange = exposureRange?.lower?.toFloat()?.let { lower ->
        lower..exposureRange.upper.toFloat()
    }
    val isExposureSupported =
        exposureState?.isExposureCompensationSupported == true && brightnessRange != null
    val isFlashOn = torchState?.value == TorchState.ON
    val hasFlashUnit = camera?.cameraInfo?.hasFlashUnit() == true

    LaunchedEffect(camera) {
        brightnessSlider =
            camera?.cameraInfo?.exposureState?.exposureCompensationIndex?.toFloat() ?: 0f
    }

    LaunchedEffect(viewModel) {
        viewModel.status.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(scannerState, previewController) {
        when (scannerState) {
            is ScannerWorkflowState.Success -> {
                previewController?.pauseAnalysis()
                frozenBitmap = previewController?.captureBitmap() ?: frozenBitmap
            }

            is ScannerWorkflowState.Idle -> {
                previewController?.resumeAnalysis()
                frozenBitmap = null
            }

            is ScannerWorkflowState.Detecting -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        if (!hasCameraPermission) {
            PermissionRationale(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(camera, zoomState?.value) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            val currentZoom = zoomState?.value?.linearZoom ?: 0f
                            val newZoom = (currentZoom * zoomChange).coerceIn(0f, 1f)
                            camera?.cameraControl?.setLinearZoom(newZoom)
                        }
                    }
            ) {
                QrCameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    lifecycleOwner = lifecycleOwner,
                    viewModel = viewModel,
                    onCameraReady = { camera = it },
                    onControllerChanged = { previewController = it }
                )

                frozenBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            ViewfinderOverlay(
                modifier = Modifier.fillMaxSize(),
                state = scannerState
            )

            CameraTopBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                onBack = onBack
            )

            BrightnessControl(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                value = brightnessSlider,
                range = brightnessRange,
                enabled = isExposureSupported,
                onValueChange = { value ->
                    if (brightnessRange == null) return@BrightnessControl
                    val clamped = value.coerceIn(
                        brightnessRange.start,
                        brightnessRange.endInclusive
                    )
                    brightnessSlider = clamped
                    camera?.cameraControl?.setExposureCompensationIndex(clamped.roundToInt())
                }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (scannerState !is ScannerWorkflowState.Success) {
                    SirimReferenceCard(
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                DetectionDetailsPanel(
                    modifier = Modifier.fillMaxWidth(),
                    lastDetection = lastDetection,
                    label = label,
                    onLabelChange = { label = it },
                    fieldSource = fieldSource,
                    onFieldSourceChange = { fieldSource = it },
                    fieldNote = fieldNote,
                    onFieldNoteChange = { fieldNote = it },
                    captureState = captureState,
                    onSaveRecord = {
                        viewModel.saveRecord(
                            label = label,
                            fieldSource = fieldSource,
                            fieldNote = fieldNote,
                            onSaved = { id ->
                                label = ""
                                fieldSource = ""
                                fieldNote = ""
                                onRecordSaved(id)
                            },
                            onDuplicate = { id ->
                                label = ""
                                fieldSource = ""
                                fieldNote = ""
                                onRecordSaved(id)
                            }
                        )
                    }
                )

                DynamicButtonPanel(
                    modifier = Modifier.fillMaxWidth(),
                    state = scannerState,
                    isFlashOn = isFlashOn,
                    flashEnabled = hasFlashUnit,
                    onToggleFlash = {
                        if (hasFlashUnit) {
                            camera?.cameraControl?.enableTorch(!isFlashOn)
                        }
                    },
                    onOpenSettings = onOpenSettings,
                    onSaveFrame = {
                        val bitmap = frozenBitmap ?: return@DynamicButtonPanel
                        coroutineScope.launch {
                            val saved = saveBitmapToPictures(context, bitmap)
                            val message = if (saved != null) {
                                context.getString(R.string.qr_save_image_success)
                            } else {
                                context.getString(R.string.qr_save_image_failure)
                            }
                            snackbarHostState.showSnackbar(message)
                        }
                    },
                    onRetake = {
                        previewController?.resumeAnalysis()
                        frozenBitmap = null
                        label = ""
                        fieldSource = ""
                        fieldNote = ""
                        viewModel.retry()
                    }
                )
            }
        }
    }
}

@Composable
private fun CameraTopBar(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CameraIconButton(
            icon = Icons.Rounded.ArrowBack,
            contentDescription = stringResource(id = R.string.cd_back),
            onClick = onBack,
            enabled = true
        )

        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = stringResource(id = R.string.qr_scanner_title),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall
            )
        }

        Spacer(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun DynamicButtonPanel(
    modifier: Modifier = Modifier,
    state: ScannerWorkflowState,
    isFlashOn: Boolean,
    flashEnabled: Boolean,
    onToggleFlash: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaveFrame: () -> Unit,
    onRetake: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(28.dp)
    ) {
        when (state) {
            ScannerWorkflowState.Idle, is ScannerWorkflowState.Detecting -> {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val flashIcon = if (isFlashOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff
                    val flashDescription = if (isFlashOn) {
                        R.string.qr_controls_flash_on
                    } else {
                        R.string.qr_controls_flash_off
                    }
                    CameraIconButton(
                        icon = flashIcon,
                        contentDescription = stringResource(id = flashDescription),
                        onClick = onToggleFlash,
                        enabled = flashEnabled,
                        isActive = isFlashOn
                    )

                    CameraIconButton(
                        icon = Icons.Rounded.Settings,
                        contentDescription = stringResource(id = R.string.qr_controls_settings),
                        onClick = onOpenSettings,
                        enabled = true
                    )
                }
            }

            is ScannerWorkflowState.Success -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilledTonalButton(onClick = onSaveFrame) {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = stringResource(id = R.string.qr_controls_save_frame)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(id = R.string.qr_controls_save_frame))
                    }

                    FilledTonalButton(onClick = onRetake) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(id = R.string.qr_controls_retake)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(id = R.string.qr_controls_retake))
                    }
                }
            }
        }
    }
}

@Composable
private fun SirimReferenceCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(8.dp),
                painter = painterResource(id = R.drawable.img_sirim_reference),
                contentDescription = stringResource(id = R.string.qr_reference_content_description)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.qr_reference_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(id = R.string.qr_reference_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun BrightnessControl(
    modifier: Modifier = Modifier,
    value: Float,
    range: ClosedFloatingPointRange<Float>?,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    if (range == null) return
    val clampedValue = value.coerceIn(range.start, range.endInclusive)

    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Brightness6,
            contentDescription = stringResource(id = R.string.qr_controls_brightness_adjust)
        )

        Slider(
            value = clampedValue,
            onValueChange = { newValue ->
                val normalized = newValue.coerceIn(range.start, range.endInclusive)
                onValueChange(normalized)
            },
            valueRange = range,
            enabled = enabled,
            modifier = Modifier
                .height(200.dp)
                .width(48.dp)
                .graphicsLayer { rotationZ = -90f }
        )

        Text(
            text = String.format(Locale.getDefault(), "%+d", clampedValue.roundToInt()),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun DetectionDetailsPanel(
    modifier: Modifier = Modifier,
    lastDetection: QrDetection?,
    label: String,
    onLabelChange: (String) -> Unit,
    fieldSource: String,
    onFieldSourceChange: (String) -> Unit,
    fieldNote: String,
    onFieldNoteChange: (String) -> Unit,
    captureState: QrCaptureState,
    onSaveRecord: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(id = R.string.qr_detected_payload_label),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(id = R.string.qr_detected_payload_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    text = lastDetection?.payload
                        ?: stringResource(id = R.string.qr_detection_waiting),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            OutlinedTextField(
                value = label,
                onValueChange = onLabelChange,
                label = { Text(stringResource(id = R.string.qr_optional_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = fieldSource,
                onValueChange = onFieldSourceChange,
                label = { Text(stringResource(id = R.string.qr_field_source_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = fieldNote,
                onValueChange = onFieldNoteChange,
                label = { Text(stringResource(id = R.string.qr_field_note_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            val actionLabel = when (captureState) {
                is QrCaptureState.Ready -> R.string.qr_action_save
                is QrCaptureState.Duplicate -> R.string.qr_action_open_existing
                is QrCaptureState.Saved -> R.string.qr_action_saved
                is QrCaptureState.Saving -> R.string.qr_action_saving
                else -> R.string.qr_action_save
            }

            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onSaveRecord,
                enabled = captureState is QrCaptureState.Ready || captureState is QrCaptureState.Duplicate
            ) {
                Text(text = stringResource(id = actionLabel))
            }

            if (captureState is QrCaptureState.Duplicate) {
                TextButton(onClick = onSaveRecord) {
                    Text(text = stringResource(id = R.string.qr_action_open_existing_secondary))
                }
                Text(text = stringResource(id = actionLabel))
            }
        }
    }
}

@Composable
private fun ViewfinderOverlay(
    modifier: Modifier = Modifier,
    state: ScannerWorkflowState
) {
    val borderColor = when (state) {
        ScannerWorkflowState.Idle -> Color.White.copy(alpha = 0.9f)
        is ScannerWorkflowState.Detecting -> Color(0xFFFFC107)
        is ScannerWorkflowState.Success -> Color(0xFF4CAF50)
    }

    Canvas(modifier = modifier.graphicsLayer(alpha = 0.99f)) {
        val overlayColor = Color.Black.copy(alpha = 0.55f)
        drawRect(color = overlayColor)

        val rectWidth = size.width * 0.72f
        val rectHeight = rectWidth * 0.62f
        val left = (size.width - rectWidth) / 2f
        val top = (size.height - rectHeight) / 2f
        val rectSize = Size(rectWidth, rectHeight)
        val topLeft = Offset(left, top)

        drawRoundRect(
            color = Color.Transparent,
            topLeft = topLeft,
            size = rectSize,
            cornerRadius = CornerRadius(48f, 48f),
            blendMode = BlendMode.Clear
        )

        drawRoundRect(
            color = borderColor,
            topLeft = topLeft,
            size = rectSize,
            cornerRadius = CornerRadius(48f, 48f),
            style = Stroke(width = 6f)
        )
    }
}

@Composable
private fun CameraIconButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isActive: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        },
        contentColor = if (isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}

@Composable
private fun PermissionRationale(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(id = R.string.qr_permission_rationale))
    }
}

@Composable
private fun QrCameraPreview(
    modifier: Modifier,
    lifecycleOwner: LifecycleOwner,
    viewModel: QrScannerViewModel,
    onCameraReady: (Camera?) -> Unit,
    onControllerChanged: (PreviewController?) -> Unit
) {
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }
    val analyzer by rememberUpdatedState(newValue = { image: ImageProxy ->
        viewModel.analyzeFrame(image)
    })

    AndroidView(
        modifier = modifier,
        factory = { previewView }
    )

    DisposableEffect(lifecycleOwner) {
        val cameraProvider = cameraProviderFuture.get()
        cameraProvider.unbindAll()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        analysis.setAnalyzer(analyzerExecutor) { image ->
            analyzer(image)
        }
        val camera = runCatching {
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        }.getOrElse { error ->
            onCameraReady(null)
            throw error
        }
        val controller = PreviewController(
            previewView = previewView,
            analysis = analysis,
            executor = analyzerExecutor,
            analyzer = analyzer
        )
        onCameraReady(camera)
        onControllerChanged(controller)

        onDispose {
            runCatching { cameraProvider.unbindAll() }
            analysis.clearAnalyzer()
            analyzerExecutor.shutdown()
            onCameraReady(null)
            onControllerChanged(null)
        }
    }
}

private class PreviewController(
    private val previewView: PreviewView,
    private val analysis: ImageAnalysis,
    private val executor: ExecutorService,
    private val analyzer: (ImageProxy) -> Unit
) {
    fun captureBitmap(): Bitmap? = previewView.bitmap

    fun pauseAnalysis() {
        analysis.clearAnalyzer()
    }

    fun resumeAnalysis() {
        analysis.clearAnalyzer()
        analysis.setAnalyzer(executor) { image ->
            analyzer(image)
        }
    }
}

private suspend fun saveBitmapToPictures(
    context: Context,
    bitmap: Bitmap
): Uri? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "SIRIM_Scan_$fileName.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SIRIM Scanner")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(collection, values) ?: return@withContext null
    try {
        resolver.openOutputStream(uri)?.use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                throw IOException("Failed to save bitmap")
            }
        } ?: throw IOException("Unable to open output stream")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    } catch (error: IOException) {
        resolver.delete(uri, null, null)
        null
    }
}
