package com.sirim.scanner.ui.screens.qrcode

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest

@Suppress("UNUSED_PARAMETER")
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

    val lastDetection by viewModel.lastDetection.collectAsStateWithLifecycle()
    val scannerState by viewModel.scannerState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var brightnessSlider by rememberSaveable { mutableFloatStateOf(0f) }
    var isBrightnessExpanded by rememberSaveable { mutableStateOf(false) }
    var isZoomExpanded by rememberSaveable { mutableStateOf(false) }
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
    val currentZoomState = zoomState?.value
    var zoomSlider by rememberSaveable { mutableFloatStateOf(currentZoomState?.linearZoom ?: 0f) }

    LaunchedEffect(camera) {
        brightnessSlider =
            camera?.cameraInfo?.exposureState?.exposureCompensationIndex?.toFloat() ?: 0f
        zoomSlider = camera?.cameraInfo?.zoomState?.value?.linearZoom ?: 0f
    }

    LaunchedEffect(currentZoomState?.linearZoom) {
        currentZoomState?.linearZoom?.let { zoomSlider = it }
    }

    val currentExposureIndex = exposureState?.exposureCompensationIndex
    LaunchedEffect(currentExposureIndex) {
        brightnessSlider = currentExposureIndex?.toFloat() ?: 0f
    }

    LaunchedEffect(isExposureSupported) {
        if (!isExposureSupported) {
            isBrightnessExpanded = false
        }
    }

    LaunchedEffect(scannerState) {
        if (scannerState is ScannerWorkflowState.Success) {
            isBrightnessExpanded = false
        }
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

            val overlayDetection = when (val state = scannerState) {
                is ScannerWorkflowState.Detecting -> state.detection
                is ScannerWorkflowState.Success -> state.detection
                else -> null
            }
            ViewfinderOverlay(
                modifier = Modifier.fillMaxSize(),
                state = scannerState,
                detection = overlayDetection
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
                expanded = isBrightnessExpanded,
                onToggle = {
                    if (isExposureSupported) {
                        isBrightnessExpanded = !isBrightnessExpanded
                        if (isBrightnessExpanded) {
                            isZoomExpanded = false
                        }
                    }
                },
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
                if (scannerState is ScannerWorkflowState.Idle && lastDetection == null) {
                    SirimReferenceCard(
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                DetectionDetailsPanel(
                    modifier = Modifier.fillMaxWidth(),
                    lastDetection = lastDetection,
                    zoomValue = zoomSlider,
                    zoomState = currentZoomState,
                    zoomExpanded = isZoomExpanded,
                    onToggleZoom = {
                        isZoomExpanded = !isZoomExpanded
                        if (isZoomExpanded) {
                            isBrightnessExpanded = false
                        }
                    },
                    onZoomChange = { value ->
                        val newZoom = value.coerceIn(0f, 1f)
                        zoomSlider = newZoom
                        camera?.cameraControl?.setLinearZoom(newZoom)
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
                    onZoomTap = {
                        val nextZoom = if (zoomSlider < 0.5f) 0.5f else 0f
                        zoomSlider = nextZoom
                        camera?.cameraControl?.setLinearZoom(nextZoom)
                        isZoomExpanded = false
                    },
                    onRetake = {
                        previewController?.resumeAnalysis()
                        frozenBitmap = null
                        isBrightnessExpanded = false
                        isZoomExpanded = false
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
    onZoomTap: () -> Unit,
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
                    FilledTonalButton(onClick = onZoomTap) {
                        Icon(
                            imageVector = Icons.Rounded.ZoomIn,
                            contentDescription = stringResource(id = R.string.qr_controls_zoom)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(id = R.string.qr_controls_zoom))
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
    expanded: Boolean,
    onToggle: () -> Unit,
    onValueChange: (Float) -> Unit
) {
    if (range == null) return
    val clampedValue = value.coerceIn(range.start, range.endInclusive)
    val containerAlpha = if (expanded) 0.95f else 0.8f

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = containerAlpha)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = if (expanded) 16.dp else 12.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onToggle,
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Rounded.Brightness6,
                    contentDescription = stringResource(id = R.string.qr_controls_brightness_adjust),
                    tint = if (expanded && enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f)
                    }
                )
            }

            AnimatedVisibility(visible = expanded && enabled) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val steps = (range.endInclusive.roundToInt() - range.start.roundToInt() - 1)
                            .coerceAtLeast(0)
                        Slider(
                            value = clampedValue,
                            onValueChange = { newValue ->
                                val normalized = newValue.coerceIn(range.start, range.endInclusive)
                                onValueChange(normalized)
                            },
                            valueRange = range,
                            steps = steps,
                            enabled = enabled,
                            modifier = Modifier
                                .width(200.dp)
                                .height(48.dp)
                                .graphicsLayer { rotationZ = -90f }
                        )

                        Text(
                            text = String.format(Locale.getDefault(), "%+d", clampedValue.roundToInt()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
    }
}

@Composable
private fun DetectionDetailsPanel(
    modifier: Modifier = Modifier,
    lastDetection: QrDetection?,
    zoomValue: Float,
    zoomState: ZoomState?,
    zoomExpanded: Boolean,
    onToggleZoom: () -> Unit,
    onZoomChange: (Float) -> Unit
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

            ZoomControl(
                zoomValue = zoomValue,
                zoomState = zoomState,
                expanded = zoomExpanded,
                onToggle = onToggleZoom,
                onZoomChange = onZoomChange
            )

            if (zoomExpanded && zoomState != null) {
                val ratio = zoomState.minZoomRatio +
                    (zoomState.maxZoomRatio - zoomState.minZoomRatio) * zoomValue
                Text(
                    text = stringResource(
                        id = R.string.qr_zoom_ratio_label,
                        (ratio * 10).roundToInt() / 10f
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ZoomControl(
    zoomValue: Float,
    zoomState: ZoomState?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onZoomChange: (Float) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FilledTonalButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        id = if (expanded) R.string.qr_controls_zoom_hide else R.string.qr_controls_zoom
                    )
                )
            }

            AnimatedVisibility(visible = expanded && zoomState != null) {
                zoomState?.let { state ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Slider(
                            value = zoomValue,
                            onValueChange = { onZoomChange(it.coerceIn(0f, 1f)) },
                            valueRange = 0f..1f,
                            steps = 0,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val zoomPercent = ((state.minZoomRatio +
                            (state.maxZoomRatio - state.minZoomRatio) * zoomValue) * 100).roundToInt()
                        Text(
                            text = stringResource(
                                id = R.string.qr_controls_zoom_value,
                                zoomPercent
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewfinderOverlay(
    modifier: Modifier = Modifier,
    state: ScannerWorkflowState,
    detection: QrDetection?
) {
    val borderColor = when (state) {
        ScannerWorkflowState.Idle -> Color.White.copy(alpha = 0.9f)
        is ScannerWorkflowState.Detecting -> Color(0xFFFFC107)
        is ScannerWorkflowState.Success -> Color(0xFF4CAF50)
    }
    val highlightColor = when (state) {
        is ScannerWorkflowState.Success -> Color(0xFF4CAF50)
        is ScannerWorkflowState.Detecting -> Color(0xFFFFC107)
        else -> Color(0xFF4CAF50)
    }
    val overlayAlpha = when (state) {
        ScannerWorkflowState.Idle -> 0.55f
        is ScannerWorkflowState.Detecting -> 0.42f
        is ScannerWorkflowState.Success -> 0.32f
    }

    Canvas(modifier = modifier.graphicsLayer(alpha = 0.99f)) {
        val overlayColor = Color.Black.copy(alpha = overlayAlpha)
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

        val normalizedBox = detection?.normalizedBoundingBox
        if (normalizedBox != null) {
            val leftRatio = normalizedBox.left.coerceIn(0f, 1f)
            val topRatio = normalizedBox.top.coerceIn(0f, 1f)
            val rightRatio = normalizedBox.right.coerceIn(0f, 1f)
            val bottomRatio = normalizedBox.bottom.coerceIn(0f, 1f)
            val widthPx = max((rightRatio - leftRatio) * size.width, 0f)
            val heightPx = max((bottomRatio - topRatio) * size.height, 0f)
            if (widthPx > 0f && heightPx > 0f) {
                val highlightTopLeft = Offset(leftRatio * size.width, topRatio * size.height)
                val highlightSize = Size(widthPx, heightPx)

                drawRoundRect(
                    color = highlightColor.copy(alpha = 0.25f),
                    topLeft = highlightTopLeft,
                    size = highlightSize,
                    cornerRadius = CornerRadius(32f, 32f)
                )

                drawRoundRect(
                    color = highlightColor,
                    topLeft = highlightTopLeft,
                    size = highlightSize,
                    cornerRadius = CornerRadius(32f, 32f),
                    style = Stroke(width = 4f)
                )
            }
        }
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

