package edu.cit.audioscholar.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import edu.cit.audioscholar.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun EditProfileScreen(
    navController: NavController
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    val email = "mathlee.biacolo@cit.edu"

    var firstNameError by remember { mutableStateOf(false) }
    var lastNameError by remember { mutableStateOf(false) }
    var usernameError by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val avatarEditMessage = stringResource(R.string.snackbar_avatar_editing_soon)

    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    val lastNameFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }

    fun validateFields(): Boolean {
        firstNameError = firstName.isBlank()
        lastNameError = lastName.isBlank()
        usernameError = username.isBlank()
        return !firstNameError && !lastNameError && !usernameError
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.nav_edit_profile)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!isLoading) {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            navController.navigateUp()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Button(
                    onClick = {
                        if (validateFields()) {
                            isLoading = true
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            scope.launch {
                                delay(1000)
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("profileUpdateSuccess", true)
                                isLoading = false
                                navController.navigateUp()
                            }
                        }
                    },
                    enabled = firstName.isNotBlank() && lastName.isNotBlank() && username.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.button_save))
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clickable { showBottomSheet = true },
                contentAlignment = Alignment.BottomEnd
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_navigation_profile_placeholder),
                    contentDescription = stringResource(R.string.cd_user_avatar),
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentScale = ContentScale.Crop
                )
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = stringResource(R.string.cd_edit_avatar),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(6.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = firstName,
                onValueChange = {
                    firstName = it
                    if (firstNameError && it.isNotBlank()) firstNameError = false
                },
                label = { Text(stringResource(R.string.edit_profile_firstname_label)) },
                placeholder = { Text(stringResource(R.string.edit_profile_firstname_placeholder)) },
                singleLine = true, isError = firstNameError,
                supportingText = { if (firstNameError) Text(stringResource(R.string.edit_profile_error_required)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = lastName,
                onValueChange = {
                    lastName = it
                    if (lastNameError && it.isNotBlank()) lastNameError = false
                },
                label = { Text(stringResource(R.string.edit_profile_lastname_label)) },
                placeholder = { Text(stringResource(R.string.edit_profile_lastname_placeholder)) },
                singleLine = true, isError = lastNameError,
                supportingText = { if (lastNameError) Text(stringResource(R.string.edit_profile_error_required)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(lastNameFocusRequester)
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    if (usernameError && it.isNotBlank()) usernameError = false
                },
                label = { Text(stringResource(R.string.edit_profile_username_label)) },
                placeholder = { Text(stringResource(R.string.edit_profile_username_placeholder)) },
                singleLine = true, isError = usernameError,
                supportingText = { if (usernameError) Text(stringResource(R.string.edit_profile_error_required)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(usernameFocusRequester)
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { },
                label = { Text(stringResource(R.string.edit_profile_email_label)) },
                readOnly = true, enabled = false,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.avatar_option_gallery)) },
                    leadingContent = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                    modifier = Modifier.clickable {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                                scope.launch { snackbarHostState.showSnackbar(avatarEditMessage) }
                            }
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.avatar_option_take_photo)) },
                    leadingContent = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                    modifier = Modifier.clickable {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                                scope.launch { snackbarHostState.showSnackbar(avatarEditMessage) }
                            }
                        }
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.avatar_option_remove)) },
                    leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    modifier = Modifier.clickable {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                                scope.launch { snackbarHostState.showSnackbar(avatarEditMessage) }
                            }
                        }
                    }
                )
            }
        }
    }
}