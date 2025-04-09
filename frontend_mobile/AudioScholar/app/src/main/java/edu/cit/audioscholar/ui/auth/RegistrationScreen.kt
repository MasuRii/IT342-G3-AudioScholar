package edu.cit.audioscholar.ui.auth

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import edu.cit.audioscholar.R
import edu.cit.audioscholar.ui.main.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    navController: NavController,
    viewModel: RegistrationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val passwordsMatch = uiState.password == uiState.confirmPassword
    val showPasswordMismatchError = !passwordsMatch && uiState.confirmPassword.isNotEmpty()

    LaunchedEffect(uiState.registrationSuccess) {
        if (uiState.registrationSuccess) {
            Toast.makeText(context, R.string.registration_success_message, Toast.LENGTH_SHORT).show()
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Onboarding.route) { inclusive = true }
                launchSingleTop = true
            }
            viewModel.registrationNavigated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_registration)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Image(
                painter = painterResource(id = R.drawable.ic_app_logo_nobg),
                contentDescription = stringResource(R.string.cd_app_logo),
                modifier = Modifier
                    .size(130.dp)
                    .padding(bottom = 8.dp),
                contentScale = ContentScale.Fit
            )

            Text(
                text = stringResource(R.string.registration_welcome_message),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            OutlinedTextField(
                value = uiState.firstName,
                onValueChange = viewModel::onFirstNameChange,
                label = { Text(stringResource(R.string.registration_first_name)) },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                isError = uiState.firstName.isBlank() && uiState.registrationError != null
            )

            OutlinedTextField(
                value = uiState.lastName,
                onValueChange = viewModel::onLastNameChange,
                label = { Text(stringResource(R.string.registration_last_name)) },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                isError = uiState.lastName.isBlank() && uiState.registrationError != null
            )

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(R.string.registration_email)) },
                leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                isError = uiState.email.isBlank() && uiState.registrationError != null
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.registration_password)) },
                leadingIcon = { Icon(Icons.Outlined.Password, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                trailingIcon = {
                    IconButton(onClick = viewModel::togglePasswordVisibility) {
                        Icon(
                            imageVector = if (uiState.isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (uiState.isPasswordVisible) R.string.cd_hide_password else R.string.cd_show_password)
                        )
                    }
                },
                isError = uiState.password.isBlank() && uiState.registrationError != null
            )

            PasswordStrengthIndicator(strength = viewModel.passwordStrength)

            OutlinedTextField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = { Text(stringResource(R.string.registration_confirm_password)) },
                leadingIcon = { Icon(Icons.Outlined.Password, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (uiState.isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                trailingIcon = {
                    IconButton(onClick = viewModel::toggleConfirmPasswordVisibility) {
                        Icon(
                            imageVector = if (uiState.isConfirmPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (uiState.isConfirmPasswordVisible) R.string.cd_hide_password else R.string.cd_show_password)
                        )
                    }
                },
                isError = showPasswordMismatchError || (uiState.confirmPassword.isBlank() && uiState.registrationError != null)
            )

            if (showPasswordMismatchError) {
                Text(
                    text = stringResource(R.string.error_passwords_do_not_match),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp, bottom = 4.dp),
                    textAlign = TextAlign.Start
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }


            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = uiState.termsAccepted,
                    onCheckedChange = viewModel::onTermsAcceptedChange
                )
                val annotatedTermsText = buildAnnotatedString {
                    append(stringResource(R.string.registration_terms_prefix).trimEnd())
                    append(" ")
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        pushStringAnnotation(tag = "T&C", annotation = "terms")
                        append(stringResource(R.string.registration_terms_link))
                        pop()
                    }
                }
                ClickableText(
                    text = annotatedTermsText,
                    onClick = { offset ->
                        annotatedTermsText.getStringAnnotations(tag = "T&C", start = offset, end = offset)
                            .firstOrNull()?.let {
                                Toast.makeText(context, "T&C Clicked (Placeholder)", Toast.LENGTH_SHORT).show()
                            }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            val error = uiState.registrationError
            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.registerUser()
                },
                enabled = viewModel.isFormValid && !uiState.registrationInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 16.dp)
                    .height(48.dp)
            ) {
                if (uiState.registrationInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.registration_register_button))
                }
            }

            Text(
                text = stringResource(R.string.registration_or_continue_with),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { Toast.makeText(context, R.string.oauth_not_implemented, Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(painterResource(id = R.drawable.ic_google_icon), contentDescription = "Google", modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Google")
                }
                OutlinedButton(
                    onClick = { Toast.makeText(context, R.string.oauth_not_implemented, Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(painterResource(id = R.drawable.ic_github_icon), contentDescription = "GitHub", modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("GitHub")
                }
            }

            TextButton(
                onClick = {
                    navController.navigate(Screen.Login.route) {
                        launchSingleTop = true
                    }
                },
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text(stringResource(R.string.registration_login_link))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PasswordStrengthIndicator(strength: PasswordStrength) {
    val (text, color) = when (strength) {
        PasswordStrength.NONE -> "" to Color.Transparent
        PasswordStrength.WEAK -> stringResource(R.string.password_strength_weak) to Color.Red
        PasswordStrength.MEDIUM -> stringResource(R.string.password_strength_medium) to Color(0xFFFFA500)
        PasswordStrength.STRONG -> stringResource(R.string.password_strength_strong) to Color(0xFF008000)
    }

    Box(modifier = Modifier.height(20.dp)) {
        if (text.isNotEmpty()) {
            Text(
                text = "${stringResource(R.string.password_strength_label)}: $text",
                color = color,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 2.dp),
                textAlign = TextAlign.Start
            )
        }
    }
}