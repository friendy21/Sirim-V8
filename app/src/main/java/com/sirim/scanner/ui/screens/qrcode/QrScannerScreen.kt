package com.sirim.scanner.ui.screens.qrcode

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sirim.scanner.data.ocr.QrCodeAnalyzer
import com.sirim.scanner.data.repository.SirimRepository
import com.sirim.scanner.R
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    onRecordSaved: (Long) -> Unit,
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
    val snackbarHostState = remember { SnackbarHostState() }
    var label by rememberSaveable { mutableStateOf("") }
    var fieldSource by rememberSaveable { mutableStateOf("") }
    var fieldNote by rememberSaveable { mutableStateOf("") }
    var brightnessSlider by rememberSaveable { mutableFloatStateOf(0f) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val zoomState = camera?.cameraInfo?.zoomState?.observeAsState()
    val torchState = camera?.cameraInfo?.torchState?.observeAsState()
    val exposureState = camera?.cameraInfo?.exposureState
    val exposureRange = exposureState?.exposureCompensationRange
    val brightnessRange = exposureRange?.lower?.toFloat()?.let { lower ->
        lower..exposureRange.upper.toFloat()
    }
    val isExposureSupported = exposureState?.isExposureCompensationSupported == true && brightnessRange != null
    val isFlashOn = torchState?.value == TorchState.ON
    val hasFlashUnit = camera?.cameraInfo?.hasFlashUnit() == true

    LaunchedEffect(camera) {
        brightnessSlider = camera?.cameraInfo?.exposureState?.exposureCompensationIndex?.toFloat() ?: 0f
    }

    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.status.collect { message ->
            coroutineScope.launch { snackbarHostState.showSnackbar(message) }
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

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        if (!hasCameraPermission) {
            PermissionRationale(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                QrCameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    lifecycleOwner = lifecycleOwner,
                    viewModel = viewModel,
                    onCameraReady = { camera = it }
                )

                CameraOverlay(
                    modifier = Modifier.fillMaxSize(),
                    onBack = onBack,
                    isFlashOn = isFlashOn,
                    flashEnabled = hasFlashUnit,
                    onToggleFlash = {
                        if (hasFlashUnit) {
                            camera?.cameraControl?.enableTorch(!isFlashOn)
                        }
                    },
                    zoom = zoomState?.value?.linearZoom ?: 0f,
                    zoomEnabled = zoomState?.value != null,
                    onZoomChange = { value ->
                        camera?.cameraControl?.setLinearZoom(value.coerceIn(0f, 1f))
                    },
                    brightnessValue = brightnessSlider,
                    brightnessRange = brightnessRange,
                    brightnessEnabled = isExposureSupported,
                    onBrightnessChange = { value ->
                        brightnessSlider = value
                        camera?.cameraControl?.setExposureCompensationIndex(value.roundToInt())
                    }
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.qr_detected_payload_label),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(lastDetection?.payload ?: stringResource(id = R.string.qr_detection_waiting))
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(id = R.string.qr_optional_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fieldSource,
                        onValueChange = { fieldSource = it },
                        label = { Text(stringResource(id = R.string.qr_field_source_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fieldNote,
                        onValueChange = { fieldNote = it },
                        label = { Text(stringResource(id = R.string.qr_field_note_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Button(
                        onClick = {
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
                        },
                        enabled = captureState is QrCaptureState.Ready || captureState is QrCaptureState.Duplicate
                    ) {
                        val actionLabel = when (captureState) {
                            is QrCaptureState.Ready -> R.string.qr_action_save
                            is QrCaptureState.Duplicate -> R.string.qr_action_open_existing
                            is QrCaptureState.Saved -> R.string.qr_action_saved
                            is QrCaptureState.Saving -> R.string.qr_action_saving
                            else -> R.string.qr_action_save
                        }
                        Text(text = stringResource(id = actionLabel))
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraOverlay(
    modifier: Modifier,
    onBack: () -> Unit,
    isFlashOn: Boolean,
    flashEnabled: Boolean,
    onToggleFlash: () -> Unit,
    zoom: Float,
    zoomEnabled: Boolean,
    onZoomChange: (Float) -> Unit,
    brightnessValue: Float,
    brightnessRange: ClosedFloatingPointRange<Float>?,
    brightnessEnabled: Boolean,
    onBrightnessChange: (Float) -> Unit
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CameraIconButton(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(id = R.string.cd_back),
                onClick = onBack,
                enabled = true
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
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
        }

        val zoomValue = zoom.coerceIn(0f, 1f)
        val sliderRange = brightnessRange ?: (brightnessValue..brightnessValue)
        val brightnessMin = sliderRange.start
        val brightnessMax = sliderRange.endInclusive
        val clampedBrightness = brightnessValue.coerceIn(brightnessMin, brightnessMax)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 8.dp,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.ZoomOut,
                        contentDescription = stringResource(id = R.string.qr_controls_zoom_decrease)
                    )
                    Slider(
                        value = zoomValue,
                        onValueChange = { onZoomChange(it.coerceIn(0f, 1f)) },
                        modifier = Modifier.weight(1f),
                        enabled = zoomEnabled,
                        valueRange = 0f..1f
                    )
                    Icon(
                        imageVector = Icons.Rounded.ZoomIn,
                        contentDescription = stringResource(id = R.string.qr_controls_zoom_increase)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Brightness6,
                        contentDescription = stringResource(id = R.string.qr_controls_brightness_adjust)
                    )
                    Slider(
                        value = clampedBrightness,
                        onValueChange = { value ->
                            val clamped = value.coerceIn(brightnessMin, brightnessMax)
                            onBrightnessChange(clamped)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = brightnessEnabled,
                        valueRange = sliderRange
                    )
                    Text(
                        text = String.format("%+d", clampedBrightness.roundToInt()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isActive: Boolean = false
) {
    Surface(
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
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    viewModel: QrScannerViewModel,
    onCameraReady: (Camera?) -> Unit
) {
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

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
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(analyzerExecutor) { image ->
                    viewModel.analyzeFrame(image)
                }
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
        onCameraReady(camera)

        onDispose {
            onCameraReady(null)
            runCatching { cameraProvider.unbindAll() }
            analyzerExecutor.shutdown()
        }
    }
}
