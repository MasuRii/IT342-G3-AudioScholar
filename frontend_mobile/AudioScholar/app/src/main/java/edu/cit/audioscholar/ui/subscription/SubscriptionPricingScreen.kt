package edu.cit.audioscholar.ui.subscription

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import edu.cit.audioscholar.R
import edu.cit.audioscholar.ui.main.Screen
import edu.cit.audioscholar.ui.theme.AudioScholarTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionPricingScreen(
    navController: NavController,
    drawerState: DrawerState? = null,
    scope: CoroutineScope? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.nav_subscription_pricing)) },
                navigationIcon = {
                    if (drawerState != null && scope != null) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.cd_open_navigation_drawer)
                            )
                        }
                    } else {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_navigate_back)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SubscriptionPlanCard(
                planName = stringResource(R.string.subscription_basic_title),
                price = stringResource(R.string.subscription_basic_price),
                features = listOf(
                    stringResource(R.string.subscription_basic_feature1_placeholder),
                    stringResource(R.string.subscription_basic_feature2_placeholder)
                ),
                isCurrentPlan = true,
                onSelectPlan = { }
            )

            SubscriptionPlanCard(
                planName = stringResource(R.string.subscription_premium_title),
                price = stringResource(R.string.subscription_premium_price_php, 150),
                features = listOf(
                    stringResource(R.string.subscription_premium_feature1_placeholder),
                    stringResource(R.string.subscription_premium_feature2_placeholder),
                    stringResource(R.string.subscription_premium_feature3_placeholder)
                ),
                isCurrentPlan = false,
                onSelectPlan = {
                    navController.navigate(Screen.PaymentMethodSelection.route)
                },
                isPrimaryAction = true
            )
        }
    }
}

@Composable
fun SubscriptionPlanCard(
    planName: String,
    price: String,
    features: List<String>,
    isCurrentPlan: Boolean,
    onSelectPlan: () -> Unit,
    isPrimaryAction: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = planName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = price, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(R.string.subscription_features_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            features.forEach { feature ->
                Text(text = "• $feature", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isCurrentPlan) {
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enabled = false
                ) {
                    Text(stringResource(R.string.subscription_current_plan_button))
                }
            } else {
                Button(
                    onClick = onSelectPlan,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = if (isPrimaryAction) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.buttonColors()
                ) {
                    Text(if (isPrimaryAction) stringResource(R.string.subscription_upgrade_to_premium_button) else stringResource(R.string.subscription_choose_plan_button))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SubscriptionPricingScreenPreview() {
    AudioScholarTheme {
        SubscriptionPricingScreen(navController = rememberNavController())
    }
}

@Preview(showBackground = true)
@Composable
fun SubscriptionPlanCard_BasicPreview() {
    AudioScholarTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SubscriptionPlanCard(
                planName = "Basic Plan",
                price = "Free",
                features = listOf("Feature A", "Feature B"),
                isCurrentPlan = true,
                onSelectPlan = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SubscriptionPlanCard_PremiumPreview() {
    AudioScholarTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SubscriptionPlanCard(
                planName = "Premium Plan",
                price = "150 PHP",
                features = listOf("All Basic Features", "Premium Feature X", "Premium Feature Y"),
                isCurrentPlan = false,
                onSelectPlan = {},
                isPrimaryAction = true
            )
        }
    }
} 