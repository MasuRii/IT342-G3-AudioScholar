package edu.cit.audioscholar.ui.subscription

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhoneAndroid
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
) {
    var contactNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var contactNumberError by remember { mutableStateOf<Int?>(null) }

    fun validateContactNumber(number: String): Int? {
        if (number.isBlank()) return R.string.payment_error_field_required
        if (!number.all { it.isDigit() }) return R.string.payment_error_phone_numeric
        if (number.length != 10) return R.string.payment_error_phone_length_ph
        if (!number.startsWith("9")) return R.string.payment_error_phone_prefix_ph
        return null
    }

    val scope = rememberCoroutineScope()

    fun handleSubmit() {
        contactNumberError = validateContactNumber(contactNumber)
        if (contactNumberError == null) {
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
fun EWalletPaymentDetailsScreenPreview() {
    AudioScholarTheme {
        EWalletPaymentDetailsScreen(navController = rememberNavController())
    }
} 