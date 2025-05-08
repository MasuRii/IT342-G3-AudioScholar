package edu.cit.audioscholar.ui.subscription

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationCompat
import androidx.hilt.navigation.compose.hiltViewModel
import edu.cit.audioscholar.R
import edu.cit.audioscholar.domain.repository.AuthRepository
import edu.cit.audioscholar.util.PremiumStatusManager
import edu.cit.audioscholar.util.Resource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val NOTIFICATION_CHANNEL_ID = "verification_channel"
private const val NOTIFICATION_ID = 1001
private const val TAG = "VerificationCodeModal"

enum class PaymentMethod {
    CARD,
    E_WALLET
}

data class PaymentDetails(
    val method: PaymentMethod,
    val cardholderName: String = "",
    val cardLastFour: String = "", 
    val contactNumber: String = ""
)

@Composable
fun VerificationCodeModal(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    onVerificationComplete: () -> Unit,
    paymentDetails: PaymentDetails = PaymentDetails(PaymentMethod.CARD),
    userId: String? = null,
    authRepository: AuthRepository = hiltViewModel<PaymentViewModel>().authRepository,
    premiumStatusManager: PremiumStatusManager = hiltViewModel<PaymentViewModel>().premiumStatusManager
) {
    val verificationCode = remember { generateVerificationCode() }
    var userInputCode by remember { mutableStateOf("") }
    var isCodeSent by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isRoleUpdateSuccess by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val incompleteCodeError = stringResource(R.string.verification_error_incomplete)
    val invalidCodeError = stringResource(R.string.verification_error_invalid)
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            userInputCode = ""
            isCodeSent = false
            isVerifying = false
            isError = false
            isRoleUpdateSuccess = false
            
            delay(2000)
            isCodeSent = true
            
            sendVerificationCodeNotification(context, verificationCode, paymentDetails)
        }
    }
    
    if (isVisible) {
        Dialog(
            onDismissRequest = { if (!isVerifying) onDismissRequest() },
            properties = DialogProperties(dismissOnBackPress = !isVerifying, dismissOnClickOutside = !isVerifying)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.verification_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = if (isCodeSent) 
                            stringResource(R.string.verification_enter_code)
                        else 
                            stringResource(R.string.verification_sending),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isCodeSent && !isVerifying) {
                        ImprovedVerificationCodeInput(
                            code = userInputCode,
                            onCodeChanged = { 
                                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                    userInputCode = it
                                    isError = false
                                }
                            },
                            isError = isError
                        )
                        
                        if (isError) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else if (isVerifying) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.verification_processing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismissRequest,
                            enabled = !isVerifying,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.verification_button_cancel))
                        }
                        
                        Button(
                            onClick = {
                                if (userInputCode.length < 6) {
                                    isError = true
                                    errorMessage = incompleteCodeError
                                } else if (userInputCode != verificationCode) {
                                    isError = true
                                    errorMessage = invalidCodeError
                                } else {
                                    isVerifying = true
                                    scope.launch {
                                        if (userId != null) {
                                            Log.d(TAG, "Updating user role for userId: $userId")
                                            val result = authRepository.updateUserRole(userId, "ROLE_PREMIUM")
                                            when (result) {
                                                is Resource.Success -> {
                                                    Log.i(TAG, "User role updated successfully to ROLE_PREMIUM")
                                                    isRoleUpdateSuccess = true
                                                    
                                                    premiumStatusManager.updatePremiumStatus(true)
                                                    Log.d(TAG, "Updated premium status in local storage to true")
                                                }
                                                is Resource.Error -> {
                                                    Log.e(TAG, "Failed to update user role: ${result.message}")
                                                }
                                                is Resource.Loading -> {
                                                }
                                            }
                                        } else {
                                            Log.w(TAG, "No userId provided, skipping role update")
                                        }
                                        
                                        delay(3000)
                                        onVerificationComplete()
                                    }
                                }
                            },
                            enabled = isCodeSent && !isVerifying,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.verification_button_verify))
                        }
                    }
                }
            }
        }
    }
}

private fun sendVerificationCodeNotification(context: Context, code: String, paymentDetails: PaymentDetails) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Verification Codes",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Channel for verification code notifications"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    
    val (title, content) = getNotificationContent(code, paymentDetails)
    
    val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(content)
        .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setSound(soundUri)
        .setAutoCancel(true)
    
    notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
}

private fun getNotificationContent(code: String, paymentDetails: PaymentDetails): Pair<String, String> {
    return when (paymentDetails.method) {
        PaymentMethod.CARD -> {
            if (paymentDetails.cardholderName.isNotEmpty() && paymentDetails.cardLastFour.isNotEmpty()) {
                Pair(
                    "Bank Authentication Required",
                    "Verification code: $code\n\nFor: ${paymentDetails.cardholderName}\nCard ending: ${paymentDetails.cardLastFour}"
                )
            } else {
                Pair(
                    "Card Verification",
                    "Your verification code is: $code"
                )
            }
        }
        PaymentMethod.E_WALLET -> {
            if (paymentDetails.contactNumber.isNotEmpty()) {
                val eWalletProvider = "GCash"
                Pair(
                    "$eWalletProvider Payment Verification",
                    "Verification code: $code\n\nFor: $eWalletProvider account\nPhone: +${paymentDetails.contactNumber}"
                )
            } else {
                Pair(
                    "E-Wallet Verification",
                    "Your verification code is: $code"
                )
            }
        }
    }
}

@Composable
private fun ImprovedVerificationCodeInput(
    code: String,
    onCodeChanged: (String) -> Unit,
    isError: Boolean
) {
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    BasicTextField(
        value = code,
        onValueChange = onCodeChanged,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        textStyle = LocalTextStyle.current.copy(
            color = Color.Transparent,
            textAlign = TextAlign.Center
        ),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                innerTextField()
                
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    for (i in 0 until 6) {
                        val char = when {
                            i < code.length -> code[i].toString()
                            else -> ""
                        }
                        
                        val isFilled = i < code.length
                        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isError) MaterialTheme.colorScheme.errorContainer
                                    else if (isFilled) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isError) MaterialTheme.colorScheme.error
                                           else if (i == code.length && !isError) MaterialTheme.colorScheme.primary
                                           else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                        ) {
                            Text(
                                text = char,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                                       else if (isFilled) MaterialTheme.colorScheme.onPrimaryContainer
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    )
}

private fun generateVerificationCode(): String {
    return Random.nextInt(100000, 1000000).toString()
} 