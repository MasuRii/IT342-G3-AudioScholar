package edu.cit.audioscholar.ui.subscription

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import edu.cit.audioscholar.R
import edu.cit.audioscholar.ui.theme.AudioScholarTheme
import kotlinx.coroutines.launch
import java.util.Calendar

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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = cardNumber,
                onValueChange = { cardNumber = it.filter { char -> char.isDigit() }.take(19); cardNumberError = null },
                label = { Text(stringResource(R.string.payment_card_number_label)) },
                leadingIcon = { Icon(Icons.Filled.CreditCard, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                isError = cardNumberError != null,
                supportingText = { cardNumberError?.let { Text(stringResource(id = it)) } },
                modifier = Modifier.fillMaxWidth()
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
                    visualTransformation = ExpiryDateVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    isError = expiryDateError != null,
                    supportingText = { expiryDateError?.let { Text(stringResource(id = it)) } },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = cvv,
                    onValueChange = { cvv = it.filter { char -> char.isDigit() }.take(4); cvvError = null },
                    label = { Text(stringResource(R.string.payment_cvv_label)) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    isError = cvvError != null,
                    supportingText = { cvvError?.let { Text(stringResource(id = it)) } },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = cardholderName,
                onValueChange = { cardholderName = it; cardholderNameError = null },
                label = { Text(stringResource(R.string.payment_cardholder_name_label)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                isError = cardholderNameError != null,
                supportingText = { cardholderNameError?.let { Text(stringResource(id = it)) } },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { handleSubmit() },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.payment_submit_button_php, 150))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CardPaymentDetailsScreenPreview() {
    AudioScholarTheme {
        CardPaymentDetailsScreen(navController = rememberNavController())
    }
} 