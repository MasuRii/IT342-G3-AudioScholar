package edu.cit.audioscholar.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import edu.cit.audioscholar.BuildConfig
import edu.cit.audioscholar.R
import edu.cit.audioscholar.domain.model.QualitySetting
import edu.cit.audioscholar.ui.main.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import edu.cit.audioscholar.ui.settings.SyncMode
import edu.cit.audioscholar.ui.settings.SyncFrequency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    drawerState: DrawerState,
    scope: CoroutineScope,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val showThemeDialog = remember { mutableStateOf(false) }
    val showQualityDialog = remember { mutableStateOf(false) }
    val showSyncModeDialog = remember { mutableStateOf(false) }
    val showSyncFrequencyDialog = remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val selectedTheme by viewModel.selectedTheme.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    val selectedSyncMode by viewModel.selectedSyncMode.collectAsState()
    val selectedSyncFrequency by viewModel.selectedSyncFrequency.collectAsState()

    if (showThemeDialog.value) {
        SelectionDialog(
            title = stringResource(R.string.settings_dialog_title_theme),
            options = ThemeSetting.entries,
            currentSelection = selectedTheme,
            onSelectionChanged = { viewModel.updateTheme(it) },
            onDismissRequest = { showThemeDialog.value = false },
            optionLabel = { theme -> stringResource(id = theme.labelResId) }
        )
    }

    if (showQualityDialog.value) {
        SelectionDialog(
            title = stringResource(R.string.settings_dialog_title_quality),
            options = QualitySetting.entries,
            currentSelection = selectedQuality,
            onSelectionChanged = { viewModel.updateQuality(it) },
            onDismissRequest = { showQualityDialog.value = false },
            optionLabel = { quality -> stringResource(id = quality.labelResId) }
        )
    }

    if (showSyncModeDialog.value) {
        SelectionDialog(
            title = stringResource(R.string.settings_dialog_title_sync_mode),
            options = SyncMode.entries,
            currentSelection = selectedSyncMode,
            onSelectionChanged = { viewModel.updateSyncMode(it) },
            onDismissRequest = { showSyncModeDialog.value = false },
            optionLabel = { mode -> stringResource(id = mode.labelResId) }
        )
    }

    if (showSyncFrequencyDialog.value) {
        SelectionDialog(
            title = stringResource(R.string.settings_dialog_title_sync_frequency),
            options = SyncFrequency.entries,
            currentSelection = selectedSyncFrequency,
            onSelectionChanged = { viewModel.updateSyncFrequency(it) },
            onDismissRequest = { showSyncFrequencyDialog.value = false },
            optionLabel = { frequency -> stringResource(id = frequency.labelResId) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = Screen.Settings.labelResId)) },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_open_navigation_drawer))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            SettingsSectionHeader(title = stringResource(R.string.settings_section_cloud_sync))
            SettingsItemRow(
                title = stringResource(R.string.settings_item_sync_mode),
                subtitle = stringResource(id = selectedSyncMode.labelResId),
                onClick = { showSyncModeDialog.value = true }
            )
            SettingsItemRow(
                title = stringResource(R.string.settings_item_sync_frequency),
                subtitle = stringResource(id = selectedSyncFrequency.labelResId),
                onClick = { showSyncFrequencyDialog.value = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

            SettingsSectionHeader(title = stringResource(R.string.settings_section_app_prefs))
            SettingsItemRow(
                title = stringResource(R.string.settings_item_theme),
                subtitle = stringResource(id = selectedTheme.labelResId),
                onClick = { showThemeDialog.value = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

            SettingsSectionHeader(title = stringResource(R.string.settings_section_audio))
            SettingsItemRow(
                title = stringResource(R.string.settings_item_recording_quality),
                subtitle = stringResource(id = selectedQuality.labelResId),
                onClick = { showQualityDialog.value = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

            SettingsSectionHeader(title = stringResource(R.string.settings_section_support))
            SettingsItemRow(
                title = stringResource(R.string.settings_item_help_center),
                onClick = { uriHandler.openUri(context.getString(R.string.settings_url_help_center)) }
            )
            SettingsItemRow(
                title = stringResource(R.string.settings_item_privacy_policy),
                onClick = { uriHandler.openUri(context.getString(R.string.settings_url_privacy_policy)) }
            )
            SettingsItemRow(
                title = stringResource(R.string.settings_item_terms_service),
                onClick = { uriHandler.openUri(context.getString(R.string.settings_url_terms_service)) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

            SettingsSectionHeader(title = stringResource(R.string.settings_section_app_info))
            SettingsInfoRow(
                title = stringResource(R.string.settings_item_app_version),
                value = BuildConfig.VERSION_NAME
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun <T> SelectionDialog(
    title: String,
    options: List<T>,
    currentSelection: T,
    onSelectionChanged: (T) -> Unit,
    onDismissRequest: () -> Unit,
    optionLabel: @Composable (T) -> String
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (option == currentSelection),
                                onClick = {
                                    onSelectionChanged(option)
                                    onDismissRequest()
                                },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (option == currentSelection),
                            onClick = null
                        )
                        Text(
                            text = optionLabel(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.dialog_button_cancel))
            }
        },
        properties = DialogProperties(dismissOnClickOutside = true)
    )
}


@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsItemRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun SettingsInfoRow(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}