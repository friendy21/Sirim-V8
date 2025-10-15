package com.sirim.scanner.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sirim.scanner.BuildConfig
import com.sirim.scanner.R
import com.sirim.scanner.data.preferences.DefaultScanAction
import com.sirim.scanner.data.preferences.ImageQuality
import com.sirim.scanner.data.preferences.UserPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    authError: String?,
    onAuthenticate: (String, String) -> Unit,
    onLogout: () -> Unit,
    onDismissAuthError: () -> Unit,
    onBack: () -> Unit,
    onOpenFeedback: () -> Unit,
    onVibrationChanged: (Boolean) -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onAutoSaveChanged: (Boolean) -> Unit,
    onImageQualityChanged: (ImageQuality) -> Unit,
    onDefaultActionChanged: (DefaultScanAction) -> Unit
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showAuthDialog by remember { mutableStateOf(false) }

    LaunchedEffect(preferences.isSessionValid()) {
        if (preferences.isSessionValid()) {
            username = ""
            password = ""
            showAuthDialog = false
        }
    }

    if (showAuthDialog) {
        AdminAuthDialog(
            username = username,
            password = password,
            onUsernameChange = { username = it },
            onPasswordChange = { password = it },
            error = authError,
            onDismissError = onDismissAuthError,
            onDismiss = {
                showAuthDialog = false
                onDismissAuthError()
            },
            onAuthenticate = {
                onAuthenticate(username, password)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.settings_title)) },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection(title = stringResource(id = R.string.settings_section_scan_feedback)) {
                SettingsToggleRow(
                    title = stringResource(id = R.string.settings_vibration_title),
                    subtitle = stringResource(id = R.string.settings_vibration_subtitle),
                    checked = preferences.vibrationOnScan,
                    onCheckedChange = onVibrationChanged
                )
                SettingsToggleRow(
                    title = stringResource(id = R.string.settings_sound_title),
                    subtitle = stringResource(id = R.string.settings_sound_subtitle),
                    checked = preferences.soundOnScan,
                    onCheckedChange = onSoundChanged
                )
                SettingsToggleRow(
                    title = stringResource(id = R.string.settings_auto_save_title),
                    subtitle = stringResource(id = R.string.settings_auto_save_subtitle),
                    checked = preferences.autoSaveImage,
                    onCheckedChange = onAutoSaveChanged
                )
            }

            SettingsSection(title = stringResource(id = R.string.settings_section_image)) {
                ImageQualitySelector(
                    current = preferences.imageQuality,
                    onSelected = onImageQualityChanged
                )
            }

            SettingsSection(title = stringResource(id = R.string.settings_section_actions)) {
                DefaultActionSelector(
                    current = preferences.defaultScanAction,
                    onSelected = onDefaultActionChanged
                )
            }

            SettingsSection(title = stringResource(id = R.string.settings_section_admin)) {
                if (preferences.isSessionValid()) {
                    Text(
                        text = stringResource(id = R.string.settings_admin_active),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onLogout) {
                        Text(text = stringResource(id = R.string.settings_logout))
                    }
                } else {
                    Text(
                        text = stringResource(id = R.string.settings_admin_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { showAuthDialog = true }) {
                        Text(text = stringResource(id = R.string.settings_admin_request_access))
                    }
                }
            }

            AboutSection(onOpenFeedback = onOpenFeedback)
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageQualitySelector(
    current: ImageQuality,
    onSelected: (ImageQuality) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            value = qualityLabel(current),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(id = R.string.settings_image_quality_title)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ImageQuality.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(qualityLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
    Text(
        text = stringResource(id = R.string.settings_image_quality_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultActionSelector(
    current: DefaultScanAction,
    onSelected: (DefaultScanAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            value = defaultActionLabel(current),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(id = R.string.settings_default_action_title)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DefaultScanAction.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(defaultActionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
    Text(
        text = stringResource(id = R.string.settings_default_action_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AboutSection(onOpenFeedback: () -> Unit) {
    val context = LocalContext.current
    SettingsSection(title = stringResource(id = R.string.settings_section_about)) {
        Text(
            text = stringResource(id = R.string.settings_about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(id = R.string.settings_about_developer, context.getString(R.string.settings_about_developer_name)),
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onOpenFeedback, modifier = Modifier.align(Alignment.End)) {
            Icon(imageVector = Icons.Outlined.Feedback, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.settings_about_feedback))
        }
    }
}

@Composable
private fun qualityLabel(option: ImageQuality): String {
    return when (option) {
        ImageQuality.High -> stringResource(id = R.string.settings_image_quality_high)
        ImageQuality.Medium -> stringResource(id = R.string.settings_image_quality_medium)
        ImageQuality.Low -> stringResource(id = R.string.settings_image_quality_low)
    }
}

@Composable
private fun defaultActionLabel(option: DefaultScanAction): String {
    return when (option) {
        DefaultScanAction.SaveAndNew -> stringResource(id = R.string.settings_default_action_save_new)
        DefaultScanAction.ViewDetails -> stringResource(id = R.string.settings_default_action_view_details)
        DefaultScanAction.StayOnScreen -> stringResource(id = R.string.settings_default_action_stay)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminAuthDialog(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    error: String?,
    onDismissError: () -> Unit,
    onDismiss: () -> Unit,
    onAuthenticate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onAuthenticate) {
                Text(text = stringResource(id = R.string.settings_admin_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.settings_admin_cancel))
            }
        },
        title = { Text(text = stringResource(id = R.string.settings_admin_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        onUsernameChange(it)
                        onDismissError()
                    },
                    singleLine = true,
                    label = { Text(text = stringResource(id = R.string.settings_admin_username)) }
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        onPasswordChange(it)
                        onDismissError()
                    },
                    singleLine = true,
                    label = { Text(text = stringResource(id = R.string.settings_admin_password)) },
                    visualTransformation = PasswordVisualTransformation()
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}
