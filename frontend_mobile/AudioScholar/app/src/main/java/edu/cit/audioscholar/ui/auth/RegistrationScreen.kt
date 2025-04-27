package edu.cit.audioscholar.ui.auth

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import edu.cit.audioscholar.R
import edu.cit.audioscholar.ui.main.Screen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    navController: NavController,
    viewModel: RegistrationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val passwordValidation by viewModel.passwordValidationResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val passwordsMatch = uiState.password == uiState.confirmPassword
    val showPasswordMismatchError = !passwordsMatch && uiState.confirmPassword.isNotEmpty() && uiState.password.isNotEmpty()

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d("RegistrationScreen", "Google Sign-In success in launcher, passing to ViewModel.")
                viewModel.handleGoogleSignInResult(account)
            } catch (e: ApiException) {
                Log.w("RegistrationScreen", "Google Sign-In failed in launcher: ${e.statusCode}", e)
                viewModel.handleGoogleSignInResult(null)
            }
        } else {
            Log.w("RegistrationScreen", "Google Sign-In cancelled or failed. Result code: ${result.resultCode}")
            viewModel.handleGoogleSignInResult(null)
        }
    }

    LaunchedEffect(uiState.registrationSuccess) {
        if (uiState.registrationSuccess) {
            Log.d("RegistrationScreen", "registrationSuccess is true. Navigating to Record screen.")
            navController.navigate(Screen.Login.route) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
            viewModel.registrationNavigated()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
                viewModel.consumeErrorMessage()
            }
        }
    }

    LaunchedEffect(key1 = viewModel) {
        viewModel.registrationScreenEventFlow.collectLatest { event ->
            when (event) {
                is RegistrationScreenEvent.ShowMessage -> {
                    scope.launch { snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short) }
                }
                is RegistrationScreenEvent.LaunchGoogleSignIn -> {
                    Log.d("RegistrationScreen", "Launching Google Sign-In Intent...")
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                }
                is RegistrationScreenEvent.LaunchGitHubSignIn -> {
                    Log.d("RegistrationScreen", "Launching GitHub Sign-In via Custom Tab...")
                    val customTabsIntent = CustomTabsIntent.Builder().build()
                    try {
                        customTabsIntent.launchUrl(context, event.url)
                    } catch (e: Exception) {
                        Log.e("RegistrationScreen", "Could not launch Custom Tab for GitHub: ${e.message}")
                        viewModel.handleGitHubLaunchFailed()
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                isError = uiState.firstName.isNotBlank() && uiState.errorMessage?.contains("Name fields") == true,
                enabled = !uiState.isAnyLoading
            )

            OutlinedTextField(
                value = uiState.lastName,
                onValueChange = viewModel::onLastNameChange,
                label = { Text(stringResource(R.string.registration_last_name)) },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                isError = uiState.lastName.isNotBlank() && uiState.errorMessage?.contains("Name fields") == true,
                enabled = !uiState.isAnyLoading
            )

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(R.string.registration_email)) },
                leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                isError = uiState.email.isNotBlank() && uiState.errorMessage?.contains("email address") == true,
                enabled = !uiState.isAnyLoading
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
                    IconButton(
                        onClick = viewModel::togglePasswordVisibility,
                        enabled = !uiState.isAnyLoading
                    ) {
                        Icon(
                            imageVector = if (uiState.isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (uiState.isPasswordVisible) R.string.cd_hide_password else R.string.cd_show_password)
                        )
                    }
                },
                isError = uiState.password.isNotEmpty() && !passwordValidation.isValid,
                enabled = !uiState.isAnyLoading
            )

            PasswordRequirementsIndicator(result = passwordValidation)

            OutlinedTextField(
                value = uiState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                label = { Text(stringResource(R.string.registration_confirm_password)) },
                leadingIcon = { Icon(Icons.Outlined.Password, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    if (viewModel.isFormValid) {
                        viewModel.registerUser()
                    }
                }),
                trailingIcon = {
                    IconButton(
                        onClick = viewModel::togglePasswordVisibility,
                        enabled = !uiState.isAnyLoading
                    ) {
                        Icon(
                            imageVector = if (uiState.isPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (uiState.isPasswordVisible) R.string.cd_hide_password else R.string.cd_show_password)
                        )
                    }
                },
                isError = showPasswordMismatchError || (uiState.confirmPassword.isBlank() && uiState.errorMessage != null),
                enabled = !uiState.isAnyLoading
            )

            if (showPasswordMismatchError) {
                Text(
                    text = stringResource(R.string.error_passwords_do_not_match),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                    textAlign = TextAlign.Start
                )
            } else {
                Spacer(modifier = Modifier.height(MaterialTheme.typography.bodySmall.lineHeight.value.dp + 8.dp))
            }


            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = uiState.termsAccepted,
                    onCheckedChange = viewModel::onTermsAcceptedChange,
                    enabled = !uiState.isAnyLoading
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
                        if (!uiState.isAnyLoading) {
                            annotatedTermsText.getStringAnnotations(tag = "T&C", start = offset, end = offset)
                                .firstOrNull()?.let {
                                    scope.launch { snackbarHostState.showSnackbar("Terms & Conditions clicked (Placeholder)") }
                                }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.registerUser()
                },
                enabled = viewModel.isFormValid && !uiState.isAnyLoading,
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
                    onClick = viewModel::onGoogleRegisterClick,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    enabled = !uiState.isAnyLoading
                ) {
                    if (uiState.isGoogleRegistrationLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    } else {
                        Icon(painterResource(id = R.drawable.ic_google_icon), contentDescription = "Google", modifier = Modifier.size(20.dp), tint = Color.Unspecified)
                        Spacer(Modifier.width(8.dp))
                        Text("Google")
                    }
                }
                OutlinedButton(
                    onClick = viewModel::onGitHubRegisterClick,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    enabled = !uiState.isAnyLoading
                ) {
                    if (uiState.isGitHubRegistrationLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    } else {
                        Icon(painterResource(id = R.drawable.ic_github_icon), contentDescription = "GitHub", modifier = Modifier.size(20.dp), tint = LocalContentColor.current)
                        Spacer(Modifier.width(8.dp))
                        Text("GitHub")
                    }
                }
            }

            TextButton(
                onClick = {
                    if (!uiState.isAnyLoading) {
                        navController.navigate(Screen.Login.route) {
                            launchSingleTop = true
                        }
                    }
                },
                modifier = Modifier.padding(top = 24.dp),
                enabled = !uiState.isAnyLoading
            ) {
                Text(stringResource(R.string.registration_login_link))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PasswordRequirementsIndicator(
    result: PasswordValidationResult,
    modifier: Modifier = Modifier
) {
    val requirementColorMet = MaterialTheme.colorScheme.primary
    val requirementColorUnmet = MaterialTheme.colorScheme.error

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
    ) {
        PasswordRequirement(
            text = stringResource(R.string.settings_password_validation_length),
            isMet = result.meetsLength,
            metColor = requirementColorMet,
            unmetColor = requirementColorUnmet
        )
        PasswordRequirement(
            text = stringResource(R.string.settings_password_validation_lowercase),
            isMet = result.hasLowercase,
            metColor = requirementColorMet,
            unmetColor = requirementColorUnmet
        )
        PasswordRequirement(
            text = stringResource(R.string.settings_password_validation_uppercase),
            isMet = result.hasUppercase,
            metColor = requirementColorMet,
            unmetColor = requirementColorUnmet
        )
        PasswordRequirement(
            text = stringResource(R.string.settings_password_validation_number),
            isMet = result.hasDigit,
            metColor = requirementColorMet,
            unmetColor = requirementColorUnmet
        )
        PasswordRequirement(
            text = stringResource(R.string.settings_password_validation_special_1),
            isMet = result.hasSpecial,
            metColor = requirementColorMet,
            unmetColor = requirementColorUnmet
        )
    }
}

@Composable
fun PasswordRequirement(
    text: String,
    isMet: Boolean,
    metColor: Color,
    unmetColor: Color,
    modifier: Modifier = Modifier
) {
    val icon = if (isMet) Icons.Filled.CheckCircle else Icons.Filled.Cancel
    val color = if (isMet) metColor else unmetColor
    val iconDesc = if (isMet) stringResource(R.string.cd_password_requirement_met) else stringResource(R.string.cd_password_requirement_unmet)

    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDesc,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

