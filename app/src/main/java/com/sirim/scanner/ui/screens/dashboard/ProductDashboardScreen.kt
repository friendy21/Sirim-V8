package com.sirim.scanner.ui.screens.dashboard

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sirim.scanner.R
import com.sirim.scanner.data.db.ProductScan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDashboardScreen(
    viewModel: ProductDashboardViewModel,
    onOpenScanner: (ProductScan) -> Unit,
    onBack: () -> Unit
) {
    val scans by viewModel.productScans.collectAsState()
    val context = LocalContext.current
    var viewScan by remember { mutableStateOf<ProductScan?>(null) }
    var editScan by remember { mutableStateOf<ProductScan?>(null) }
    var deleteScan by remember { mutableStateOf<ProductScan?>(null) }
    val todayTotal = remember(scans) { calculateTodayTotal(scans) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.dashboard_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = stringResource(id = R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (scans.isEmpty()) {
            EmptyDashboardState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)
            ) {
                items(scans, key = { it.id }) { scan ->
                    ProductScanCard(
                        scan = scan,
                        todayTotal = todayTotal,
                        onOpenScanner = { onOpenScanner(scan) },
                        onView = { viewScan = scan },
                        onShare = {
                            shareProductScan(context, scan)
                        },
                        onEdit = { editScan = scan },
                        onDelete = { deleteScan = scan }
                    )
                }
            }
        }
    }

    viewScan?.let { scan ->
        AlertDialog(
            onDismissRequest = { viewScan = null },
            confirmButton = {
                TextButton(onClick = { viewScan = null }) {
                    Text(text = stringResource(id = R.string.dashboard_close))
                }
            },
            title = { Text(text = scan.skuName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(id = R.string.dashboard_sku_report, scan.skuReport))
                    Text(text = stringResource(id = R.string.dashboard_sirim_data, scan.sirimData ?: "-"))
                    Text(text = stringResource(id = R.string.dashboard_total_captured, scan.totalCaptured))
                    Text(text = stringResource(id = R.string.dashboard_last_updated, formatTimestamp(scan.timestamp)))
                }
            }
        )
    }

    editScan?.let { scan ->
        EditProductScanDialog(
            scan = scan,
            onDismiss = { editScan = null },
            onSave = { updated ->
                viewModel.updateScan(updated)
                editScan = null
            }
        )
    }

    deleteScan?.let { scan ->
        AlertDialog(
            onDismissRequest = { deleteScan = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteScan(scan)
                        deleteScan = null
                    }
                ) {
                    Text(text = stringResource(id = R.string.dashboard_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteScan = null }) {
                    Text(text = stringResource(id = R.string.dashboard_cancel))
                }
            },
            title = { Text(text = stringResource(id = R.string.dashboard_delete_title)) },
            text = { Text(text = stringResource(id = R.string.dashboard_delete_message, scan.skuName)) }
        )
    }
}

@Composable
private fun EmptyDashboardState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(id = R.string.dashboard_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(id = R.string.dashboard_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProductScanCard(
    scan: ProductScan,
    todayTotal: Int,
    onOpenScanner: () -> Unit,
    onView: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProductThumbnail(name = scan.skuName)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = scan.skuName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(id = R.string.dashboard_sku_report, scan.skuReport),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            id = R.string.dashboard_sirim_data,
                            scan.sirimData?.ifBlank { "-" } ?: "-"
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(id = R.string.dashboard_total_captured, scan.totalCaptured),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(id = R.string.dashboard_today_total, todayTotal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(id = R.string.dashboard_last_updated, formatTimestamp(scan.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(onClick = onOpenScanner) {
                    Icon(imageVector = Icons.Outlined.CameraAlt, contentDescription = stringResource(id = R.string.dashboard_action_scan))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = onView) {
                        Icon(imageVector = Icons.Outlined.Visibility, contentDescription = stringResource(id = R.string.dashboard_action_view))
                    }
                    IconButton(onClick = onShare) {
                        Icon(imageVector = Icons.Outlined.Share, contentDescription = stringResource(id = R.string.dashboard_action_share))
                    }
                    IconButton(onClick = onEdit) {
                        Icon(imageVector = Icons.Outlined.Edit, contentDescription = stringResource(id = R.string.dashboard_action_edit))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = stringResource(id = R.string.dashboard_action_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductThumbnail(name: String) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Text(text = initial, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun EditProductScanDialog(
    scan: ProductScan,
    onDismiss: () -> Unit,
    onSave: (ProductScan) -> Unit
) {
    var name by rememberSaveable(scan.id) { mutableStateOf(scan.skuName) }
    var sirim by rememberSaveable(scan.id) { mutableStateOf(scan.sirimData ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = scan.copy(
                        skuName = name.trim().ifEmpty { scan.skuName },
                        sirimData = sirim.trim().ifEmpty { null },
                        timestamp = System.currentTimeMillis()
                    )
                    onSave(updated)
                }
            ) {
                Text(text = stringResource(id = R.string.dashboard_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.dashboard_cancel))
            }
        },
        title = { Text(text = stringResource(id = R.string.dashboard_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(id = R.string.dashboard_edit_name)) }
                )
                TextField(
                    value = sirim,
                    onValueChange = { sirim = it },
                    label = { Text(text = stringResource(id = R.string.dashboard_edit_sirim)) }
                )
            }
        }
    )
}

private fun shareProductScan(context: android.content.Context, scan: ProductScan) {
    val shareText = context.getString(
        R.string.dashboard_share_template,
        scan.skuName,
        scan.skuReport,
        scan.sirimData ?: context.getString(R.string.dashboard_share_empty_sirim)
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.dashboard_action_share))
    runCatching { ContextCompat.startActivity(context, chooser, null) }
}

private fun calculateTodayTotal(scans: List<ProductScan>): Int {
    if (scans.isEmpty()) return 0
    val formatter = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val todayKey = formatter.format(Date())
    return scans.filter { formatter.format(Date(it.timestamp)) == todayKey }
        .sumOf { it.totalCaptured }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
