package com.sirim.scanner.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sirim.scanner.R
import com.sirim.scanner.data.db.SkuRecord
import java.text.DateFormat

sealed interface SkuSessionGuardState {
    data class PromptCapture(
        val onDismiss: () -> Unit,
        val onOpenSkuScanner: () -> Unit
    ) : SkuSessionGuardState

    data class SelectSession(
        val options: List<SkuRecord>,
        val defaultSelection: Long?,
        val onConfirm: (Long) -> Unit,
        val onDismiss: () -> Unit
    ) : SkuSessionGuardState
}

@Composable
fun SkuSessionGuardDialog(state: SkuSessionGuardState?) {
    when (state) {
        is SkuSessionGuardState.PromptCapture -> MissingSessionDialog(state)
        is SkuSessionGuardState.SelectSession -> SessionPickerDialog(state)
        null -> Unit
    }
}

@Composable
private fun MissingSessionDialog(state: SkuSessionGuardState.PromptCapture) {
    AlertDialog(
        onDismissRequest = state.onDismiss,
        title = { Text(text = stringResource(id = R.string.session_guard_missing_title)) },
        text = {
            Text(
                text = stringResource(id = R.string.session_guard_missing_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = state.onOpenSkuScanner) {
                Text(text = stringResource(id = R.string.session_guard_missing_capture))
            }
        },
        dismissButton = {
            TextButton(onClick = state.onDismiss) {
                Text(text = stringResource(id = R.string.session_guard_cancel))
            }
        }
    )
}

@Composable
private fun SessionPickerDialog(state: SkuSessionGuardState.SelectSession) {
    var selectedId by remember(state) { mutableStateOf(state.defaultSelection) }

    AlertDialog(
        onDismissRequest = state.onDismiss,
        title = { Text(text = stringResource(id = R.string.session_guard_picker_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(id = R.string.session_guard_picker_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                state.options.forEach { record ->
                    SessionOptionRow(
                        record = record,
                        isSelected = selectedId == record.id,
                        onSelected = { selectedId = record.id }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedId?.let(state.onConfirm) },
                enabled = selectedId != null
            ) {
                Text(text = stringResource(id = R.string.session_guard_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = state.onDismiss) {
                Text(text = stringResource(id = R.string.session_guard_cancel))
            }
        }
    )
}

@Composable
private fun SessionOptionRow(
    record: SkuRecord,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        RadioButton(selected = isSelected, onClick = onSelected)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = record.barcode,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = formatTimestamp(record.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(timestamp)
}
