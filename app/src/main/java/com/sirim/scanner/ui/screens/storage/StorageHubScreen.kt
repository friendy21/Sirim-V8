package com.sirim.scanner.ui.screens.storage

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.sirim.scanner.R
import com.sirim.scanner.data.db.StorageRecord
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageHubScreen(
    viewModel: StorageHubViewModel,
    onRequireAuthentication: (forcePrompt: Boolean, afterAuth: () -> Unit) -> Unit,
    onBack: () -> Unit,
    onOpenQrScanner: () -> Unit
) {
    val records by viewModel.storageRecords.collectAsState()
    val context = LocalContext.current
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    val coroutineScope = rememberCoroutineScope()
    var pendingThumbnailRecord by remember { mutableStateOf<StorageRecord.SkuExport?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val targetRecord = pendingThumbnailRecord
        if (uri != null && targetRecord != null) {
            coroutineScope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes == null || bytes.isEmpty()) {
                    Toast.makeText(context, R.string.storage_thumbnail_add_error, Toast.LENGTH_LONG).show()
                } else {
                    viewModel.updateSkuExportThumbnail(targetRecord.export, bytes) {
                        Toast.makeText(
                            context,
                            R.string.storage_thumbnail_update_error,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } else if (uri != null) {
            Toast.makeText(context, R.string.storage_thumbnail_add_error, Toast.LENGTH_LONG).show()
        }
        pendingThumbnailRecord = null
    }

    fun requireAdmin(forcePrompt: Boolean = false, action: () -> Unit) {
        onRequireAuthentication(forcePrompt, action)
    }

    fun viewExport(record: StorageRecord.SkuExport) {
        val uri = Uri.parse(record.export.uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                uri,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { activityLauncher.launch(intent) }
            .onFailure {
                Toast.makeText(context, R.string.storage_export_view_error, Toast.LENGTH_LONG).show()
            }
    }

    fun shareExport(record: StorageRecord.SkuExport) {
        val uri = Uri.parse(record.export.uri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activityLauncher.launch(Intent.createChooser(intent, context.getString(R.string.storage_share_export)))
    }

    fun editExport(record: StorageRecord.SkuExport) {
        val uri = Uri.parse(record.export.uri)
        val intent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(
                uri,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { activityLauncher.launch(intent) }
            .onFailure {
                Toast.makeText(context, R.string.storage_export_edit_error, Toast.LENGTH_LONG).show()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.storage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (records.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = stringResource(id = R.string.storage_empty_title), fontWeight = FontWeight.SemiBold)
                Text(
                    text = stringResource(id = R.string.storage_empty_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    if (record is StorageRecord.SkuExport) {
                        val onThumbnailClick = if (record.export.thumbnailPath == null) {
                            {
                                pendingThumbnailRecord = record
                                imagePickerLauncher.launch("image/*")
                            }
                        } else {
                            {
                                requireAdmin {
                                    pendingThumbnailRecord = record
                                    imagePickerLauncher.launch("image/*")
                                }
                            }
                        }

                        StorageHubCard(
                            title = stringResource(
                                id = R.string.storage_sku_export_title,
                                record.export.fileName
                            ),
                            description = stringResource(
                                id = R.string.storage_sku_export_description,
                                record.export.fieldCount,
                                record.export.ocrCount
                            ),
                            updatedAt = record.createdAt,
                            barcode = record.export.barcode,
                            thumbnailPath = record.export.thumbnailPath,
                            scannerLabel = R.string.storage_action_scanner,
                            onThumbnailClick = onThumbnailClick,
                            onScanner = onOpenQrScanner,
                            onView = { viewExport(record) },
                            onShare = { requireAdmin { shareExport(record) } },
                            onEdit = { requireAdmin { editExport(record) } },
                            onDelete = { requireAdmin { viewModel.deleteSkuExport(record.export) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageHubCard(
    title: String,
    description: String,
    updatedAt: Long,
    barcode: String?,
    thumbnailPath: String?,
    @StringRes scannerLabel: Int,
    onThumbnailClick: () -> Unit,
    onScanner: () -> Unit,
    onView: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val thumbnailDescription = if (thumbnailPath == null) {
                stringResource(id = R.string.storage_thumbnail_add_cd)
            } else {
                stringResource(id = R.string.storage_thumbnail_edit_cd)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                StorageThumbnail(
                    path = thumbnailPath,
                    contentDescription = thumbnailDescription,
                    onClick = onThumbnailClick
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = description, style = MaterialTheme.typography.bodyMedium)
                    barcode?.let {
                        Text(
                            text = stringResource(id = R.string.storage_sku_label, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (updatedAt > 0) {
                        Text(
                            text = "Updated ${formatTimestamp(updatedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScanner, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = scannerLabel))
                }
                Button(onClick = onView, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.storage_action_view))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.storage_action_share))
                }
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(id = R.string.storage_action_edit))
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(text = stringResource(id = R.string.storage_action_delete))
                }
            }
        }
    }
}

@Composable
private fun StorageThumbnail(
    path: String?,
    contentDescription: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    var imageBitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var loadError by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        if (path == null) {
            imageBitmap = null
            loadError = false
        } else {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
            }
            if (bitmap != null) {
                imageBitmap = bitmap.asImageBitmap()
                loadError = false
            } else {
                imageBitmap = null
                loadError = true
            }
        }
    }

    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            path == null -> {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            imageBitmap != null -> {
                Image(
                    bitmap = imageBitmap!!,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            loadError -> {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = stringResource(id = R.string.storage_thumbnail_error),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
