package edu.cit.audioscholar.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import edu.cit.audioscholar.R
import kotlinx.coroutines.launch

enum class PasswordStrength {
    NONE, WEAK, MEDIUM, STRONG
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChangePasswordScreen(
    navController: NavHostController
) {
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmNewPassword by rememberSaveable { mutableStateOf("") }
    var currentPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var newPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmNewPasswordVisible by rememberSaveable { mutableStateOf(false) }

    var currentPasswordError by remember { mutableStateOf<String?>(null) }
    var newPasswordError by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var passwordStrength by remember { mutableStateOf(PasswordStrength.NONE) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun validateNewPassword(password: String): List<String> {
        val errors = mutableListOf<String>()
        if (password.length < 8) errors.add(context.getString(R.string.settings_password_validation_length))
        if (!password.any { it.isUpperCase() }) errors.add(context.getString(R.string.settings_password_validation_uppercase))
        if (!password.any { it.isLowerCase() }) errors.add(context.getString(R.string.settings_password_validation_lowercase))
        if (!password.any { it.isDigit() }) errors.add(context.getString(R.string.settings_password_validation_number))
        if (!password.any { "!@#$%^&*()".contains(it) }) errors.add(context.getString(R.string.settings_password_validation_special))
        return errors
    }

    fun calculateStrength(password: String): PasswordStrength {
        val score = mutableSetOf<Int>()
        if (password.length >= 8) score.add(1)
        if (password.any { it.isUpperCase() }) score.add(1)
        if (password.any { it.isLowerCase() }) score.add(1)
        if (password.any { it.isDigit() }) score.add(1)
        if (password.any { "!@#$%^&*()".contains(it) }) score.add(1)

        return when (score.size) {
            5 -> PasswordStrength.STRONG
            in 3..4 -> PasswordStrength.MEDIUM
            in 1..2 -> PasswordStrength.WEAK
            else -> PasswordStrength.NONE
        }
    }

    LaunchedEffect(newPassword) {
        newPasswordError = validateNewPassword(newPassword)
        passwordStrength = calculateStrength(newPassword)
        confirmPasswordError = if (confirmNewPassword.isNotEmpty() && newPassword != confirmNewPassword) {
            context.getString(R.string.settings_password_validation_match)
        } else {
            null
        }
    }

    LaunchedEffect(confirmNewPassword, newPassword) {
        confirmPasswordError = if (confirmNewPassword.isNotEmpty() && newPassword != confirmNewPassword) {
            context.getString(R.string.settings_password_validation_match)
        } else {
            null
        }
    }

    fun validateCurrentPassword(password: String): String? {
        return null
    }

    LaunchedEffect(currentPassword) {
        currentPasswordError = validateCurrentPassword(currentPassword)
    }

    val isFormValid = currentPassword.isNotEmpty() &&
            newPassword.isNotEmpty() &&
            confirmNewPassword.isNotEmpty() &&
            currentPasswordError == null &&
            newPasswordError.isEmpty() &&
            confirmPasswordError == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.nav_change_password)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it },
                label = { Text(stringResource(R.string.settings_current_password)) },
                singleLine = true,
                visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                trailingIcon = {
                    val image = if (currentPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                        Icon(imageVector = image, stringResource(R.string.cd_toggle_password_visibility))
                    }
                },
                isError = currentPasswordError != null,
                supportingText = { if (currentPasswordError != null) Text(currentPasswordError!!) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text(stringResource(R.string.settings_new_password)) },
                singleLine = true,
                visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                trailingIcon = {
                    val image = if (newPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                        Icon(imageVector = image, stringResource(R.string.cd_toggle_password_visibility))
                    }
                },
                isError = newPassword.isNotEmpty() && newPasswordError.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )

            if (newPassword.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val strengthText = when (passwordStrength) {
                        PasswordStrength.WEAK -> stringResource(R.string.settings_password_strength_weak)
                        PasswordStrength.MEDIUM -> stringResource(R.string.settings_password_strength_medium)
                        PasswordStrength.STRONG -> stringResource(R.string.settings_password_strength_strong)
                        PasswordStrength.NONE -> ""
                    }
                    if (strengthText.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_password_strength_indicator, strengthText),
                            style = MaterialTheme.typography.bodySmall,
                            color = when (passwordStrength) {
                                PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
                                PasswordStrength.MEDIUM -> MaterialTheme.colorScheme.primary
                                PasswordStrength.STRONG -> MaterialTheme.colorScheme.primary
                                PasswordStrength.NONE -> LocalContentColor.current
                            }
                        )
                    }
                    if (newPasswordError.isNotEmpty()) {
                        newPasswordError.forEach { error ->
                            Text(
                                text = "• $error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }


            OutlinedTextField(
                value = confirmNewPassword,
                onValueChange = { confirmNewPassword = it },
                label = { Text(stringResource(R.string.settings_confirm_new_password)) },
                singleLine = true,
                visualTransformation = if (confirmNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    val image = if (confirmNewPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { confirmNewPasswordVisible = !confirmNewPasswordVisible }) {
                        Icon(imageVector = image, stringResource(R.string.cd_toggle_password_visibility))
                    }
                },
                isError = confirmPasswordError != null,
                supportingText = { if (confirmPasswordError != null) Text(confirmPasswordError!!) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    keyboardController?.hide()
                    currentPasswordError = validateCurrentPassword(currentPassword)
                    if (isFormValid) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.settings_password_change_success),
                                duration = SnackbarDuration.Short
                            )
                            kotlinx.coroutines.delay(1000)
                            navController.navigateUp()
                        }
                    } else {
                        if (currentPasswordError == null && newPasswordError.isEmpty() && confirmPasswordError == null) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Please fill all fields correctly.",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }
                },
                enabled = isFormValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(stringResource(R.string.settings_change_password_button))
            }
        }
    }
}