package edu.cit.audioscholar.ui.subscription

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import edu.cit.audioscholar.R
import edu.cit.audioscholar.ui.main.Screen
import edu.cit.audioscholar.ui.theme.AudioScholarTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodSelectionScreen(
    navController: NavController,
    planId: String,
    formattedPrice: String,
    priceAmount: Double
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.nav_payment_method_selection)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select Payment Method",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
            
            Text(
                text = "Choose your preferred payment method to continue with your subscription.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            CreditCardPaymentOption(
                onClick = {
                    val route = Screen.CardPaymentDetails.createRoute(
                        planId = planId,
                        formattedPrice = formattedPrice,
                        priceAmount = priceAmount.toString()
                    )
                    navController.navigate(route)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            EWalletPaymentOption(
                onClick = {
                    val route = Screen.EWalletPaymentDetails.createRoute(
                        planId = planId,
                        formattedPrice = formattedPrice,
                        priceAmount = priceAmount.toString()
                    )
                    navController.navigate(route)
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Secure Payments",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Your payment information is encrypted and securely processed. We do not store your payment details.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { }) {
                    Text(
                        text = "Terms of Service",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { }) {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun CreditCardPaymentOption(onClick: () -> Unit) {
    val isLight = MaterialTheme.colorScheme.isLight()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isLight)
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF5D647F),
                                    Color(0xFF7D8299)
                                )
                            )
                        else
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_credit_card),
                    contentDescription = stringResource(R.string.payment_method_cards),
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = stringResource(R.string.payment_method_cards),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Pay using credit or debit cards.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go to Credit Card Payment",
                modifier = Modifier.rotate(180f)
            )
        }
    }
}

@Composable
fun EWalletPaymentOption(onClick: () -> Unit) {
    val isLight = MaterialTheme.colorScheme.isLight()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isLight)
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF4A5397),
                                    Color(0xFF6B87B8)
                                )
                            )
                        else
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_gcash),
                    contentDescription = stringResource(R.string.payment_method_ewallets),
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = stringResource(R.string.payment_method_ewallets),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Pay using GCash or other e-wallets.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go to E-Wallet Payment",
                modifier = Modifier.rotate(180f)
            )
        }
    }
}

@Composable
fun ColorScheme.isLight(): Boolean {
    return this.background.luminance() > 0.5f
}

@Composable
fun Modifier.rotate(degrees: Float): Modifier = this.then(graphicsLayer(rotationZ = degrees))

@Preview(showBackground = true)
@Composable
fun PaymentMethodSelectionScreenPreview() {
    AudioScholarTheme {
        PaymentMethodSelectionScreen(
            navController = rememberNavController(),
            planId = "premium",
            formattedPrice = "₱1,440.00/year",
            priceAmount = 1440.0
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CreditCardPaymentOptionPreview() {
    AudioScholarTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CreditCardPaymentOption(onClick = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EWalletPaymentOptionPreview() {
    AudioScholarTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            EWalletPaymentOption(onClick = {})
        }
    }
} 