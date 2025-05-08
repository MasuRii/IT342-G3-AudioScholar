package edu.cit.audioscholar.ui.subscription

import androidx.compose.animation.AnimatedVisibility
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
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import edu.cit.audioscholar.R
import edu.cit.audioscholar.ui.theme.AudioScholarTheme
import kotlinx.coroutines.launch
import java.util.Calendar

class CreditCardNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length > 16) text.text.substring(0..15) else text.text
        var out = ""
        for (i in trimmed.indices) {
            out += trimmed[i]
            if ((i + 1) % 4 == 0 && i != trimmed.length - 1) {
                out += " "
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                if (offset > trimmed.length) return out.length
                
                val spaces = kotlin.math.max(0, (offset - 1) / 4)
                return offset + spaces
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                if (offset > out.length) return trimmed.length
                
                val spaces = kotlin.math.max(0, offset / 5)
                return offset - spaces
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

class ExpiryDateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rawDigits = text.text.filter { it.isDigit() }.take(4)
        val out = buildString {
            for (i in rawDigits.indices) {
                append(rawDigits[i])
                if (i == 1 && rawDigits.length > 2) {
                    append('/')
                }
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val transformedOffset = if (rawDigits.length > 2 && offset > 1) {
                    offset + 1
                } else {
                    offset
                }
                return transformedOffset.coerceIn(0, out.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val originalOffset = if (rawDigits.length > 2 && offset > 2) {
                    offset - 1
                } else {
                    offset
                }
                return originalOffset.coerceIn(0, rawDigits.length)
            }
        }
        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardPaymentDetailsScreen(
    navController: NavController,
    formattedPrice: String,
    priceAmount: Double
) {
    var cardNumber by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var cardholderName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    var cardNumberError by remember { mutableStateOf<Int?>(null) }
    var expiryDateError by remember { mutableStateOf<Int?>(null) }
    var cvvError by remember { mutableStateOf<Int?>(null) }
    var cardholderNameError by remember { mutableStateOf<Int?>(null) }

    fun validateCardNumber(number: String): Int? {
        if (number.isBlank()) return R.string.payment_error_field_required
        if (!number.all { it.isDigit() }) return R.string.payment_error_card_number_numeric
        if (number.length !in 13..19) return R.string.payment_error_card_number_length
        return null
    }

    fun validateExpiryDate(date: String): Int? {
        if (date.isBlank()) return R.string.payment_error_field_required
        if (date.length != 4) return R.string.payment_error_expiry_format_mmyy
        if (!date.all { it.isDigit() }) return R.string.payment_error_expiry_format_mmyy

        val month = date.substring(0, 2).toIntOrNull()
        val year = date.substring(2, 4).toIntOrNull()

        if (month == null || year == null || month !in 1..12) {
            return R.string.payment_error_expiry_invalid_date
        }

        val calendar = Calendar.getInstance()
        val currentYearLastTwoDigits = calendar.get(Calendar.YEAR) % 100
        val currentMonth = calendar.get(Calendar.MONTH) + 1

        if (year < currentYearLastTwoDigits || (year == currentYearLastTwoDigits && month < currentMonth)) {
            return R.string.payment_error_expiry_past_date
        }
        return null
    }

    fun validateCvv(cvvNum: String): Int? {
        if (cvvNum.isBlank()) return R.string.payment_error_field_required
        if (!cvvNum.all { it.isDigit() }) return R.string.payment_error_cvv_numeric
        if (cvvNum.length !in 3..4) return R.string.payment_error_cvv_length
        return null
    }

    fun validateCardholderName(name: String): Int? {
        if (name.isBlank()) return R.string.payment_error_field_required
        return null
    }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun handleSubmit() {
        cardNumberError = validateCardNumber(cardNumber)
        expiryDateError = validateExpiryDate(expiryDate)
        cvvError = validateCvv(cvv)
        cardholderNameError = validateCardholderName(cardholderName)

        if (cardNumberError == null && expiryDateError == null && cvvError == null && cardholderNameError == null) {
            isLoading = true
            scope.launch {
                kotlinx.coroutines.delay(2000)
                isLoading = false
                 navController.navigate(edu.cit.audioscholar.ui.main.Screen.Record.route) { 
                    popUpTo(navController.graph.id) { inclusive = false }
                    launchSingleTop = true
                }
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Please fix the errors in the form")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.nav_card_payment_details)) },
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
                                Color(0xFF5D647F),
                                Color(0xFF7D8299)
                            )
                        )
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_credit_card),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (cardNumber.isEmpty()) "•••• •••• •••• ••••" else {
                            if (cardNumber.length < 16) {
                                cardNumber.padEnd(16, '•').chunked(4).joinToString(" ")
                            } else {
                                cardNumber.take(16).chunked(4).joinToString(" ")
                            }
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "CARDHOLDER NAME",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = if (cardholderName.isEmpty()) "YOUR NAME" else cardholderName.uppercase(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }

                        Column {
                            Text(
                                text = "EXPIRES",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = if (expiryDate.isEmpty()) "MM/YY" else {
                                    if (expiryDate.length < 2) "${expiryDate.padEnd(2, '•')}/••"
                                    else "${expiryDate.take(2)}/${if (expiryDate.length > 2) expiryDate.substring(2) else "••"}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                        text = "Card Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { cardNumber = it.filter { char -> char.isDigit() }.take(19); cardNumberError = null },
                        label = { Text(stringResource(R.string.payment_card_number_label)) },
                        leadingIcon = { Icon(Icons.Filled.CreditCard, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        visualTransformation = CreditCardNumberVisualTransformation(),
                        singleLine = true,
                        isError = cardNumberError != null,
                        supportingText = { cardNumberError?.let { Text(stringResource(id = it)) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = expiryDate,
                            onValueChange = {
                                val newText = it.filter { char -> char.isDigit() }
                                if (newText.length <= 4) {
                                    expiryDate = newText
                                }
                                expiryDateError = null
                            },
                            label = { Text(stringResource(R.string.payment_expiry_date_label)) },
                            placeholder = { Text("MM/YY") },
                            leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                            visualTransformation = ExpiryDateVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            isError = expiryDateError != null,
                            supportingText = { expiryDateError?.let { Text(stringResource(id = it)) } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = cvv,
                            onValueChange = { cvv = it.filter { char -> char.isDigit() }.take(4); cvvError = null },
                            label = { Text(stringResource(R.string.payment_cvv_label)) },
                            placeholder = { Text("123") },
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            isError = cvvError != null,
                            supportingText = { cvvError?.let { Text(stringResource(id = it)) } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = cardholderName,
                        onValueChange = { cardholderName = it; cardholderNameError = null },
                        label = { Text(stringResource(R.string.payment_cardholder_name_label)) },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        isError = cardholderNameError != null,
                        supportingText = { cardholderNameError?.let { Text(stringResource(id = it)) } },
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
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
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
                            text = "Tax",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "₱0.00",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
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
                            text = formattedPrice,
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
                        "Complete Payment",
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
                    text = "Secure Payment",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CardPaymentDetailsScreenPreview() {
    AudioScholarTheme {
        CardPaymentDetailsScreen(
            navController = rememberNavController(),
            formattedPrice = "₱1,440.00/year",
            priceAmount = 1440.0
        )
    }
} 