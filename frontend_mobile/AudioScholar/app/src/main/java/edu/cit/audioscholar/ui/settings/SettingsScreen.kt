package edu.cit.audioscholar.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import edu.cit.audioscholar.BuildConfig
import edu.cit.audioscholar.R
import edu.cit.audioscholar.ui.main.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

    if (showThemeDialog.value) {
        SelectionDialog(
            title = stringResource(R.string.settings_dialog_title_theme),
            options = ThemeSetting.values().toList(),
            currentSelection = viewModel.selectedTheme.value,
            onSelectionChanged = { viewModel.updateTheme(it) },
            onDismissRequest = { showThemeDialog.value = false }
        )
    }

    if (showQualityDialog.value) {
        SelectionDialog(
            title = stringResource(R.string.settings_dialog_title_quality),
            options = QualitySetting.values().toList(),
            currentSelection = viewModel.selectedQuality.value,
            onSelectionChanged = { viewModel.updateQuality(it) },
            onDismissRequest = { showQualityDialog.value = false }
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
            SettingsSectionHeader(title = stringResource(R.string.settings_section_account))
            SettingsItemRow(
                title = stringResource(R.string.settings_item_edit_profile),
                onClick = { navController.navigate(Screen.EditProfile.route) }
            )
            SettingsItemRow(
                title = stringResource(R.string.settings_item_change_password),
                onClick = { }
            )
            SettingsItemRow(
                title = stringResource(R.string.settings_item_logout),
                onClick = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Screen.Login.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

            SettingsSectionHeader(title = stringResource(R.string.settings_section_cloud_sync))
            SettingsItemRow(
                title = stringResource(R.string.settings_item_sync_mode),
                subtitle = "Automatic",
                onClick = { }
            )
            SettingsItemRow(
                title = stringResource(R.string.settings_item_sync_frequency),
                subtitle = "Daily",
                onClick = { }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

            SettingsSectionHeader(title = stringResource(R.string.settings_section_app_prefs))
            SettingsItemRow(
                title = stringResource(R.string.settings_item_theme),
                subtitle = viewModel.selectedTheme.value.name,
                onClick = { showThemeDialog.value = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

            SettingsSectionHeader(title = stringResource(R.string.settings_section_audio))
            SettingsItemRow(
                title = stringResource(R.string.settings_item_recording_quality),
                subtitle = viewModel.selectedQuality.value.name,
                onClick = { showQualityDialog.value = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp))

            SettingsSectionHeader(title = stringResource(R.string.settings_section_support))
            SettingsItemRow(
                title = stringResource(R.string.settings_item_help_center),
                onClick = { }
            )
            SettingsItemRow(
                title = stringResource(R.string.settings_item_privacy_policy),
                onClick = { }
            )
            SettingsItemRow(
                title = stringResource(R.string.settings_item_terms_service),
                onClick = { }
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
    optionLabel: @Composable (T) -> String = { it.toString() }
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

