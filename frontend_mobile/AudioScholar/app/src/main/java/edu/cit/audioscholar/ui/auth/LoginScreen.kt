package edu.cit.audioscholar.ui.auth

import android.app.Activity.RESULT_OK
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import edu.cit.audioscholar.R
import edu.cit.audioscholar.ui.main.Screen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val loginState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var passwordVisible by rememberSaveable { mutableStateOf(false) }

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
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d("LoginScreen", "Google Sign-In success in launcher, passing to ViewModel.")
                viewModel.handleGoogleSignInResult(account)
            } catch (e: ApiException) {
                Log.w("LoginScreen", "Google Sign-In failed in launcher: ${e.statusCode}", e)
                viewModel.handleGoogleSignInResult(null)
            }
        } else {
            Log.w("LoginScreen", "Google Sign-In cancelled or failed. Result code: ${result.resultCode}")
            viewModel.handleGoogleSignInResult(null)
        }
    }

    LaunchedEffect(loginState.navigateToRecordScreen) {
        if (loginState.navigateToRecordScreen) {
            Log.d("LoginScreen", "navigateToRecordScreen state is true. Navigating to Record screen.")
            navController.navigate(Screen.Record.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
                launchSingleTop = true
            }
            viewModel.onNavigationHandled()
        }
    }

    LaunchedEffect(key1 = viewModel) {
        viewModel.loginScreenEventFlow.collectLatest { event ->
            when (event) {
                is LoginScreenEvent.ShowInfoMessage -> {
                    scope.launch { snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short) }
                }
                is LoginScreenEvent.LaunchGoogleSignIn -> {
                    Log.d("LoginScreen", "Launching Google Sign-In Intent...")
                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                }
                is LoginScreenEvent.LaunchGitHubSignIn -> {
                    Log.d("LoginScreen", "Launching GitHub Sign-In via Custom Tab...")
                    val customTabsIntent = CustomTabsIntent.Builder().build()
                    try {
                        customTabsIntent.launchUrl(context, event.url)
                    } catch (e: Exception) {
                        Log.e("LoginScreen", "Could not launch Custom Tab for GitHub: ${e.message}")
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Could not open browser for GitHub login.",
                                duration = SnackbarDuration.Short
                            )
                        }
                        viewModel.handleGitHubRedirect(null, null)
                    }
                }
            }
        }
    }

    LaunchedEffect(loginState.errorMessage) {
        loginState.errorMessage?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
                viewModel.consumeErrorMessage()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Image(
                painter = painterResource(id = R.drawable.ic_app_logo_nobg),
                contentDescription = stringResource(R.string.cd_app_logo),
                modifier = Modifier
                    .size(200.dp)
                    .padding(bottom = 32.dp)
            )

            val welcomeText = when (loginState.welcomeType) {
                WelcomeMessageType.NEW_AFTER_ONBOARDING -> stringResource(R.string.login_welcome_new)
                WelcomeMessageType.RETURNING -> stringResource(R.string.login_welcome_returning)
            }
            Text(
                text = welcomeText,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            OutlinedTextField(
                value = loginState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text(stringResource(R.string.login_email_label)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = stringResource(R.string.cd_email_icon)
                    )
                },
                singleLine = true,
                isError = loginState.errorMessage != null,
                enabled = !loginState.isAnyLoading
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = loginState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.login_password_label)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = stringResource(R.string.cd_password_icon)
                    )
                },
                trailingIcon = {
                    val image = if (passwordVisible)
                        Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff
                    val description = if (passwordVisible) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password)

                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        enabled = !loginState.isAnyLoading
                    ) {
                        Icon(imageVector = image, description)
                    }
                },
                singleLine = true,
                isError = loginState.errorMessage != null,
                enabled = !loginState.isAnyLoading
            )
            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = viewModel::onForgotPasswordClick,
                modifier = Modifier.align(Alignment.End),
                enabled = !loginState.isAnyLoading
            ) {
                Text(stringResource(R.string.login_forgot_password))
            }
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::onLoginClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loginState.isAnyLoading
            ) {
                if (loginState.isEmailLoginLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.login_button_text))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.login_or_continue_with),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = viewModel::onGoogleSignInClick,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    enabled = !loginState.isAnyLoading
                ) {
                    if (loginState.isGoogleLoginLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google_icon),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.Unspecified
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Google")
                    }
                }

                OutlinedButton(
                    onClick = viewModel::onGitHubSignInClick,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    enabled = !loginState.isAnyLoading
                ) {
                    if (loginState.isGitHubLoginLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github_icon),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = LocalContentColor.current
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("GitHub")
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))

            val annotatedString = buildAnnotatedString {
                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                    append(stringResource(R.string.login_no_account_prefix))
                    append(" ")
                }
                pushStringAnnotation(tag = "REGISTER", annotation = Screen.Registration.route)
                withStyle(style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )) {
                    append(stringResource(R.string.login_register_link))
                }
                pop()
            }

            ClickableText(
                text = annotatedString,
                onClick = { offset ->
                    if (!loginState.isAnyLoading) {
                        annotatedString.getStringAnnotations(tag = "REGISTER", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                navController.navigate(annotation.item) {
                                    launchSingleTop = true
                                }
                            }
                    }
                },
                style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}