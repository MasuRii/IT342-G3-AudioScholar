package edu.cit.audioscholar.ui.subscription

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import edu.cit.audioscholar.R
import edu.cit.audioscholar.domain.repository.AuthRepository
import edu.cit.audioscholar.ui.theme.AudioScholarTheme
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.material3.HorizontalDivider

class PhilippinePhoneNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rawDigits = text.text
        var out = "+63 "
        for (i in rawDigits.indices) {
            out += rawDigits[i]
            if (i == 2 || i == 5) {
                out += " "
            }
        }

        val numberOffsetTranslator = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var transformedOffset = offset + 4
                if (offset > 2) transformedOffset++
                if (offset > 5) transformedOffset++
                return kotlin.math.min(transformedOffset, out.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                var originalOffset = offset - 4
                if (offset > 7) originalOffset--
                if (offset > 11) originalOffset--
                return kotlin.math.max(0, kotlin.math.min(originalOffset, rawDigits.length))
            }
        }
        return TransformedText(AnnotatedString(out), numberOffsetTranslator)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EWalletPaymentDetailsScreen(
    navController: NavController,
    formattedPrice: String,
    priceAmount: Double,
    viewModel: PaymentViewModel = hiltViewModel(),
    authRepository: AuthRepository
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val taxRate = 0.12
    val taxAmount = priceAmount * taxRate
    val totalAmount = priceAmount + taxAmount
    
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
    val formattedTax = currencyFormat.format(taxAmount).replace("PHP", "₱")
    val formattedTotal = currencyFormat.format(totalAmount).replace("PHP", "₱")
    
    var contactNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var contactNumberError by remember { mutableStateOf<Int?>(null) }
    
    var showVerificationModal by remember { mutableStateOf(false) }

    fun validateContactNumber(number: String): Int? {
        if (number.isBlank()) return R.string.payment_error_field_required
        if (!number.all { it.isDigit() }) return R.string.payment_error_phone_numeric
        if (number.length != 10) return R.string.payment_error_phone_length_ph
        if (!number.startsWith("9")) return R.string.payment_error_phone_prefix_ph
        return null
    }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearErrorMessage()
        }
    }

    fun handleSubmit() {
        contactNumberError = validateContactNumber(contactNumber)
        if (contactNumberError == null) {
            isLoading = true
            scope.launch {
                kotlinx.coroutines.delay(1500)
                isLoading = false
                showVerificationModal = true
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Please provide a valid mobile number")
            }
        }
    }
    
    fun onVerificationComplete() {
        showVerificationModal = false
        navController.navigate(edu.cit.audioscholar.ui.main.Screen.Record.route) { 
            popUpTo(navController.graph.id) { inclusive = false }
            launchSingleTop = true
        }
    }
    
    VerificationCodeModal(
        isVisible = showVerificationModal,
        onDismissRequest = { showVerificationModal = false },
        onVerificationComplete = { onVerificationComplete() },
        paymentDetails = PaymentDetails(
            method = PaymentMethod.E_WALLET,
            contactNumber = if (contactNumber.startsWith("9")) "63$contactNumber" else contactNumber
        ),
        userId = uiState.userId,
        authRepository = authRepository
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.nav_ewallet_payment_details)) },
                navigationIcon = {
                    IconButton(onClick = { if (!isLoading) navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF4A5397),
                                Color(0xFF6B87B8)
                            )
                        )
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_gcash),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "E-Wallet Payment",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Pay using GCash or other e-wallets",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "How It Works",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "1",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Enter your mobile number linked to your e-wallet",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "2",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "You'll receive a payment notification on your e-wallet app",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "3",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Complete the payment in your e-wallet app",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Contact Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = contactNumber,
                        onValueChange = {
                            val newText = it.filter { char -> char.isDigit() }
                            if (newText.length <= 10) {
                                contactNumber = newText
                            }
                            contactNumberError = null
                        },
                        label = { Text(stringResource(R.string.payment_ewallet_contact_label)) },
                        placeholder = { Text("+63 9XX XXX XXXX") },
                        leadingIcon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                        visualTransformation = PhilippinePhoneNumberVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        isError = contactNumberError != null,
                        supportingText = { contactNumberError?.let { Text(stringResource(id = it)) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Order Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Premium Subscription",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formattedPrice,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tax (12%)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formattedTax,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formattedTotal,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { handleSubmit() },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Proceed to Payment",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Secure Transaction",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Supported E-Wallets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_gcash),
                        contentDescription = "GCash",
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Image(
                        painter = painterResource(id = R.drawable.ic_paymaya),
                        contentDescription = "Paymaya",
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Image(
                        painter = painterResource(id = R.drawable.ic_coinsph),
                        contentDescription = "Coins.ph",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EWalletPaymentDetailsScreenPreview() {
    AudioScholarTheme {
        EWalletPaymentDetailsScreen(
            navController = rememberNavController(),
            formattedPrice = "₱1,440.00/year",
            priceAmount = 1440.0,
            authRepository = PreviewAuthRepository()
        )
    }
}

