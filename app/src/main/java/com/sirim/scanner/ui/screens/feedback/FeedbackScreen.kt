package com.sirim.scanner.ui.screens.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sirim.scanner.feedback.FeedbackManager
import com.sirim.scanner.feedback.FeedbackSubmission
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var descriptionState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var contactState by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var includeDiagnostics by rememberSaveable { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Feedback") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Feedback,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = "Share your thoughts or report an issue so we can keep improving the scanner.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = descriptionState,
                onValueChange = { descriptionState = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                label = { Text("What happened?") },
                supportingText = { Text("Describe the QR code or label and what went wrong.") },
                minLines = 5
            )
            OutlinedTextField(
                value = contactState,
                onValueChange = { contactState = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Contact (optional)") },
                supportingText = { Text("Leave an email or phone number if you'd like us to follow up.") }
            )
            DiagnosticsToggle(
                checked = includeDiagnostics,
                onCheckedChange = { includeDiagnostics = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (descriptionState.text.isBlank() || isSubmitting) return@Button
                    scope.launch {
                        isSubmitting = true
                        val submission = FeedbackSubmission(
                            description = descriptionState.text.trim(),
                            contact = contactState.text.trim().takeIf { it.isNotEmpty() },
                            includeDiagnostics = includeDiagnostics
                        )
                        val result = runCatching { FeedbackManager.submitFeedback(submission) }
                        isSubmitting = false
                        if (result.isSuccess) {
                            descriptionState = TextFieldValue()
                            contactState = TextFieldValue()
                            includeDiagnostics = true
                            snackbarHostState.showSnackbar(
                                message = "Thanks! Your feedback has been queued.",
                                withDismissAction = true
                            )
                        } else {
                            snackbarHostState.showSnackbar(
                                message = "Unable to send feedback right now.",
                                withDismissAction = true
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = descriptionState.text.isNotBlank() && !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Sending...")
                } else {
                    Text("Submit feedback")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Attach diagnostic logs", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Includes anonymised scan metrics to help reproduce issues.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                Text(
                    text = if (checked) "Diagnostics will be attached" else "Diagnostics won't be attached",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
