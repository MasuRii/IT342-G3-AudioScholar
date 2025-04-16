package edu.cit.audioscholar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
        if (!password.any { """!"#$%&'()*+,-./:;<=>?@[\]^_`{|}~""".contains(it) }) errors.add(context.getString(R.string.settings_password_validation_special_1))
        return errors
    }

    fun calculateStrength(password: String): PasswordStrength {
        val score = mutableSetOf<Int>()
        if (password.length >= 8) score.add(1)
        if (password.any { it.isUpperCase() }) score.add(1)
        if (password.any { it.isLowerCase() }) score.add(1)
        if (password.any { it.isDigit() }) score.add(1)
        if (password.any { """!"#$%&'()*+,-./:;<=>?@[\]^_`{|}~""".contains(it) }) score.add(1)

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
        if (password.isBlank()) {
            return "Current password cannot be empty."
        }
        return null
    }

    LaunchedEffect(currentPassword) {
        if (currentPassword.isNotEmpty()) {
            currentPasswordError = validateCurrentPassword(currentPassword)
        } else {
            currentPasswordError = null
        }
    }

    currentPassword.isNotEmpty() &&
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = stringResource(R.string.settings_change_password_header),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = stringResource(R.string.settings_change_password_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

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
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null
                    )
                },
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

            Spacer(modifier = Modifier.height(16.dp))

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
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null
                    )
                },
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
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
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
                                PasswordStrength.STRONG -> Color(0xFF2E7D32)
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
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(16.dp))
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
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null
                    )
                },
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

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    keyboardController?.hide()
                    currentPasswordError = validateCurrentPassword(currentPassword)
                    newPasswordError = validateNewPassword(newPassword)
                    confirmPasswordError = if (newPassword != confirmNewPassword) {
                        context.getString(R.string.settings_password_validation_match)
                    } else {
                        null
                    }

                    val stillValid = currentPassword.isNotEmpty() &&
                            newPassword.isNotEmpty() &&
                            confirmNewPassword.isNotEmpty() &&
                            currentPasswordError == null &&
                            newPasswordError.isEmpty() &&
                            confirmPasswordError == null

                    if (stillValid) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.settings_password_change_success),
                                duration = SnackbarDuration.Short
                            )
                            kotlinx.coroutines.delay(1000)
                            navController.navigateUp()
                        }
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = currentPasswordError ?: newPasswordError.firstOrNull() ?: confirmPasswordError ?: context.getString(R.string.settings_password_form_invalid),
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                enabled = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(stringResource(R.string.settings_change_password_button))
            }
        }
    }
}

