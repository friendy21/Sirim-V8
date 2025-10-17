package com.sirim.scanner.ui.screens.sku

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sirim.scanner.data.ocr.BarcodeAnalyzer
import com.sirim.scanner.data.export.ExportManager
import com.sirim.scanner.data.preferences.SkuSessionTracker
import com.sirim.scanner.data.repository.SirimRepository
import androidx.camera.core.ZoomState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.rounded.LightMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import java.util.Locale
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkuScannerScreen(
    onBack: () -> Unit,
    onRecordSaved: (Long) -> Unit,
    repository: SirimRepository,
    analyzer: BarcodeAnalyzer,
    appScope: CoroutineScope,
    exportManager: ExportManager,
    sessionTracker: SkuSessionTracker
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val viewModel: SkuScannerViewModel = viewModel(
        factory = SkuScannerViewModel.Factory(
            repository,
            analyzer,
            appScope,
            exportManager,
            sessionTracker
        )
    )

    val captureState by viewModel.captureState.collectAsState()
    val lastDetection by viewModel.lastDetection.collectAsState()
    val databaseInfo by viewModel.databaseInfo.collectAsState()
    val captureAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    var lastAutoCaptureValue by rememberSaveable { mutableStateOf<String?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val activity = remember(context) { context.findActivity() }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val shouldShowRationale = !hasCameraPermission && activity?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
    } == true
    val showSettingsButton = !hasCameraPermission && !shouldShowRationale && permissionRequested

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(captureState, lastDetection?.value) {
        val detectionValue = lastDetection?.value
        if (captureState is CaptureState.Ready && detectionValue != null) {
            if (lastAutoCaptureValue != detectionValue) {
                delay(450)
                captureAction.value?.invoke()
                lastAutoCaptureValue = detectionValue
            }
        } else if (captureState !is CaptureState.Captured) {
            if (detectionValue == null) {
                lastAutoCaptureValue = null
            }
        }
    }

    LaunchedEffect(captureState) {
        if (captureState is CaptureState.Saved) {
            (captureState as CaptureState.Saved).recordId?.let(onRecordSaved)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SKU Barcode Scanner") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            SkuStatusCard(state = captureState, detection = lastDetection)

            // Database Info Card
            if (databaseInfo != null) {
                DatabaseInfoCard(info = databaseInfo!!)
            }

            // Camera Preview
            if (hasCameraPermission) {
                SkuCameraPreview(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    lifecycleOwner = lifecycleOwner,
                    viewModel = viewModel,
                    captureState = captureState,
                    captureAction = captureAction
                )
            } else {
                CameraPermissionCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    title = "Camera access required",
                    description = if (shouldShowRationale) {
                        "We need camera access to scan barcodes. Please grant the permission."
                    } else {
                        "Camera permission is required to scan barcodes. You can grant it to continue."
                    },
                    showSettingsButton = showSettingsButton,
                    onRequestPermission = {
                        permissionRequested = true
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onOpenSettings = { openAppSettings(context) },
                    onCheckPermission = {
                        hasCameraPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    }
                )
            }

            // Capture Button
            Button(
                onClick = { captureAction.value?.invoke() },
                enabled = captureState is CaptureState.Ready && captureAction.value != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    Icons.Rounded.Camera,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when (captureState) {
                        is CaptureState.Processing -> "Saving..."
                        is CaptureState.Saved -> "Saved!"
                        is CaptureState.Error -> "Try Again"
                        is CaptureState.Captured -> "Review above"
                        is CaptureState.Ready -> "Capture manually"
                        else -> "Capture Barcode"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun SkuStatusCard(
    state: CaptureState,
    detection: BarcodeDetectionInfo?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is CaptureState.Saved -> MaterialTheme.colorScheme.primaryContainer
                is CaptureState.Error -> MaterialTheme.colorScheme.errorContainer
                is CaptureState.Captured -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when (state) {
                        is CaptureState.Saved -> Icons.Rounded.CheckCircle
                        is CaptureState.Error -> Icons.Rounded.Error
                        is CaptureState.Processing -> Icons.Rounded.HourglassEmpty
                        is CaptureState.Captured -> Icons.Rounded.PhotoCamera
                        else -> Icons.Rounded.QrCodeScanner
                    },
                    contentDescription = null,
                    tint = when (state) {
                        is CaptureState.Saved -> MaterialTheme.colorScheme.primary
                        is CaptureState.Error -> MaterialTheme.colorScheme.error
                        is CaptureState.Captured -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state is CaptureState.Saved && state.isNewRecord) {
                        Text(
                            "New database created",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (state is CaptureState.Saved && !state.isNewRecord) {
                        Text(
                            "Using existing database",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            if (detection != null) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Detected Barcode:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        detection.value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Format: ${detection.format}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    showSettingsButton: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onCheckPermission: () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                Text("Grant permission")
            }
            if (showSettingsButton) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open app settings")
                }
                TextButton(onClick = onCheckPermission) {
                    Text("I've granted permission")
                }
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun DatabaseInfoCard(info: SkuDatabaseInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Database Information",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Barcodes", style = MaterialTheme.typography.labelMedium)
                    Text(
                        info.totalCount.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Unique Codes", style = MaterialTheme.typography.labelMedium)
                    Text(
                        info.uniqueCount.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SkuCameraPreview(
    modifier: Modifier,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    viewModel: SkuScannerViewModel,
    captureState: CaptureState,
    captureAction: MutableState<(() -> Unit)?>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val camera = remember { mutableStateOf<Camera?>(null) }
    val flashEnabled = rememberSaveable { mutableStateOf(false) }
    var zoomSlider by rememberSaveable { mutableStateOf(0f) }
    var brightnessSlider by rememberSaveable { mutableStateOf(0.5f) }
    val zoomState = remember { mutableStateOf<ZoomState?>(null) }
    val zoomStateLiveData = remember { mutableStateOf<LiveData<ZoomState>?>(null) }
    val zoomObserver = remember {
        Observer<ZoomState> { state ->
            zoomState.value = state
            zoomSlider = state?.linearZoom ?: 0f
        }
    }
    val brightnessRange = remember { mutableStateOf<IntRange?>(null) }
    var brightnessIndex by remember { mutableStateOf(0) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
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
            zoomStateLiveData.value?.removeObserver(zoomObserver)

            val boundCamera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
                imageCapture
            )
            camera.value = boundCamera
            val exposureState = boundCamera.cameraInfo.exposureState
            brightnessRange.value = exposureState.exposureCompensationRange
            brightnessIndex = exposureState.exposureCompensationIndex
            brightnessSlider = brightnessRange.value?.let { range ->
                val span = (range.last - range.first).takeIf { it != 0 } ?: 1
                (brightnessIndex - range.first).toFloat() / span
            } ?: 0.5f
            val liveData = boundCamera.cameraInfo.zoomState
            zoomStateLiveData.value = liveData
            zoomState.value = liveData.value
            zoomSlider = liveData.value?.linearZoom ?: 0f
            liveData.observe(lifecycleOwner, zoomObserver)
        }
        cameraProviderFuture.addListener(listener, mainExecutor)
        onDispose {
            captureAction.value = null
            runCatching { cameraProviderFuture.get().unbindAll() }
            analyzerExecutor.shutdown()
            zoomStateLiveData.value?.removeObserver(zoomObserver)
        }
    }

    LaunchedEffect(flashEnabled.value) {
        camera.value?.cameraControl?.enableTorch(flashEnabled.value)
    }

    LaunchedEffect(captureState, imageCapture) {
        captureAction.value = if (captureState is CaptureState.Ready) {
            {
                captureAction.value = null
                val executor = ContextCompat.getMainExecutor(context)
                val photoFile = File.createTempFile("sku_capture_", ".jpg", context.cacheDir)
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                imageCapture.takePicture(
                    outputOptions,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            scope.launch(Dispatchers.IO) {
                                val bytes = runCatching { photoFile.readBytes() }.getOrNull()
                                photoFile.delete()
                                if (bytes != null) {
                                    withContext(Dispatchers.Main) {
                                        viewModel.onImageCaptured(bytes)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        viewModel.onCaptureError("Unable to read captured image")
                                    }
                                }
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            photoFile.delete()
                            viewModel.onCaptureError("Capture failed: ${exception.message}")
                        }
                    }
                )
            }
        } else {
            null
        }
    }

    val capturedState = captureState as? CaptureState.Captured
    val previewBitmap = remember(capturedState) {
        capturedState?.imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (capturedState != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Captured preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Preview unavailable")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.retakeCapture() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retake")
                }
                Button(
                    onClick = { viewModel.confirmCapture() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use photo")
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                SkuScannerOverlay(state = captureState)
            }
            CameraControlPanel(
                modifier = Modifier.fillMaxWidth(),
                zoomState = zoomState.value,
                zoomValue = zoomSlider,
                onZoomChange = {
                    zoomSlider = it
                    camera.value?.cameraControl?.setLinearZoom(it)
                },
                brightnessRange = brightnessRange.value,
                brightnessValue = brightnessSlider,
                brightnessIndex = brightnessIndex,
                onBrightnessChange = { value ->
                    brightnessSlider = value
                    brightnessRange.value?.let { range ->
                        val span = (range.last - range.first).takeIf { it != 0 } ?: 1
                        val index = range.first + (span * value).roundToInt()
                        brightnessIndex = index
                        camera.value?.cameraControl?.setExposureCompensationIndex(index)
                    }
                },
                isFlashOn = flashEnabled.value,
                flashAvailable = camera.value?.cameraInfo?.hasFlashUnit() == true,
                onToggleFlash = { flashEnabled.value = !flashEnabled.value }
            )
        }
    }
}

@Composable
private fun SkuScannerOverlay(state: CaptureState) {
    val transition = rememberInfiniteTransition(label = "scanner-line")
    val scanProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanner-progress"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val paddingHorizontal = 64.dp.toPx()
        val paddingVertical = 96.dp.toPx()
        val scanWidth = size.width - paddingHorizontal * 2
        val scanHeight = (size.height - paddingVertical * 2).coerceAtMost(scanWidth * 0.6f)
        val left = paddingHorizontal
        val top = (size.height - scanHeight) / 2
        val cornerRadius = 20.dp.toPx()

        val strokeColor = when (state) {
            is CaptureState.Saved -> Color(0xFF4CAF50)
            is CaptureState.Error -> Color(0xFFF44336)
            is CaptureState.Processing -> Color(0xFFFFC107)
            is CaptureState.Captured -> Color(0xFF3F51B5)
            is CaptureState.Ready -> Color(0xFF2196F3)
            else -> Color.White.copy(alpha = 0.85f)
        }

        val overlayPath = androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left,
                    top,
                    left + scanWidth,
                    top + scanHeight,
                    cornerRadius,
                    cornerRadius
                )
            )
        }

        drawPath(
            path = overlayPath,
            color = Color(0xCC000000)
        )

        drawRoundRect(
            color = strokeColor,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(scanWidth, scanHeight),
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
        )

        val scanY = top + scanHeight * scanProgress
        drawLine(
            color = strokeColor.copy(alpha = 0.75f),
            start = androidx.compose.ui.geometry.Offset(left + 16.dp.toPx(), scanY),
            end = androidx.compose.ui.geometry.Offset(left + scanWidth - 16.dp.toPx(), scanY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun CameraControlPanel(
    modifier: Modifier = Modifier,
    zoomState: ZoomState?,
    zoomValue: Float,
    onZoomChange: (Float) -> Unit,
    brightnessRange: IntRange?,
    brightnessValue: Float,
    brightnessIndex: Int,
    onBrightnessChange: (Float) -> Unit,
    isFlashOn: Boolean,
    flashAvailable: Boolean,
    onToggleFlash: () -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = onToggleFlash,
                        enabled = flashAvailable
                    ) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Rounded.Bolt else Icons.Rounded.FlashOff,
                            contentDescription = if (isFlashOn) "Disable flash" else "Enable flash",
                            tint = if (isFlashOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Auto capture ready",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Keep barcode inside the guide – photo will snap automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ControlSliderCard(
                    icon = Icons.Rounded.ZoomIn,
                    title = "Zoom",
                    subtitle = zoomState?.let { state ->
                        val percent = (zoomValue * 100).roundToInt()
                        val ratioText = String.format(Locale.US, "%.1fx", state.zoomRatio)
                        "${percent}% • ${ratioText}"
                    } ?: "Not supported",
                    value = zoomValue,
                    enabled = zoomState != null,
                    onValueChange = { onZoomChange(it.coerceIn(0f, 1f)) }
                )

                ControlSliderCard(
                    icon = Icons.Rounded.LightMode,
                    title = "Brightness",
                    subtitle = if (brightnessRange != null && brightnessRange.first != brightnessRange.last) {
                        val percent = ((brightnessValue * 200f) - 100f).roundToInt()
                        "${percent}% • Compensation ${brightnessIndex}"
                    } else {
                        "Default"
                    },
                    value = brightnessValue,
                    enabled = brightnessRange != null && brightnessRange.first != brightnessRange.last,
                    onValueChange = { onBrightnessChange(it.coerceIn(0f, 1f)) }
                )
            }
        }
    }
}

@Composable
private fun ControlSliderCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            enabled = enabled
        )
    }
}
