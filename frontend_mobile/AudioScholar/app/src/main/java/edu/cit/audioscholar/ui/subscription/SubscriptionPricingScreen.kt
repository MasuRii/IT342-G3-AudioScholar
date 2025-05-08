package edu.cit.audioscholar.ui.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import edu.cit.audioscholar.R
import edu.cit.audioscholar.ui.main.Screen
import edu.cit.audioscholar.ui.theme.AudioScholarTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionPricingScreen(
    navController: NavController,
    drawerState: DrawerState? = null,
    scope: CoroutineScope? = null,
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is SubscriptionEvent.NavigateToPayment -> {
                    navController.navigate(Screen.PaymentMethodSelection.route)
                }
                is SubscriptionEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is SubscriptionEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.nav_audioscholar_pro)) },
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
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_navigate_back)
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Unlock Advanced Features",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                
                Text(
                    text = "Upgrade to AudioScholar Pro for AI-powered summaries, unlimited storage, and more.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                BillingPeriodSelector(
                    selectedBillingPeriod = uiState.selectedBillingPeriod,
                    onBillingPeriodSelected = { viewModel.toggleBillingPeriod() }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 32.dp)
                    )
                } else if (uiState.plans.isEmpty()) {
                    Text(
                        text = "No subscription plans available",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 32.dp)
                    )
                } else {
                    uiState.plans.forEach { plan ->
                        EnhancedSubscriptionPlanCard(
                            plan = plan,
                            isCurrentPlan = plan.id == uiState.currentPlanId,
                            onSelectPlan = { viewModel.selectPlan(plan.id) },
                            isProcessing = uiState.processingPurchase && uiState.currentPlanId != plan.id
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

            if (uiState.error != null) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("Error") },
                    text = { Text(uiState.error!!) },
                    confirmButton = {
                        Button(
                            onClick = { }
                        ) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun BillingPeriodSelector(
    selectedBillingPeriod: BillingPeriod,
    onBillingPeriodSelected: () -> Unit
) {
    val isMonthlySelected = selectedBillingPeriod == BillingPeriod.MONTHLY
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { if (!isMonthlySelected) onBillingPeriodSelected() },
                color = if (isMonthlySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (isMonthlySelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            ) {
                Text(
                    text = "Monthly",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                        .fillMaxWidth()
                )
            }
            
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { if (isMonthlySelected) onBillingPeriodSelected() },
                color = if (!isMonthlySelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (!isMonthlySelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Annually",
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Save 20%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!isMonthlySelected) 
                            MaterialTheme.colorScheme.onPrimary 
                        else 
                            MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedSubscriptionPlanCard(
    plan: SubscriptionPlanUIModel,
    isCurrentPlan: Boolean,
    onSelectPlan: () -> Unit,
    isProcessing: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .then(
                if (plan.isRecommended) 
                    Modifier.border(
                        width = 2.dp, 
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp)
                    )
                else 
                    Modifier
            ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (plan.isRecommended) 6.dp else 4.dp
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (plan.isRecommended) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MOST POPULAR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = plan.formattedPrice,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            
            if (plan.billingPeriod == BillingPeriod.MONTHLY) {
                Text(
                    text = "Billed monthly",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Billed annually",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.subscription_features_title),
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            plan.features.forEach { feature ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (feature.isAvailableInPlan) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = if (feature.isAvailableInPlan) "Included" else "Not included",
                        tint = if (feature.isAvailableInPlan) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = feature.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (feature.isAvailableInPlan) 
                            MaterialTheme.colorScheme.onSurface
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (isCurrentPlan) {
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth(),
                    enabled = false
                ) {
                    Text(stringResource(R.string.subscription_current_plan_button))
                }
            } else {
                Button(
                    onClick = onSelectPlan,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth(),
                    colors = if (plan.showAsPrimary)
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    else
                        ButtonDefaults.buttonColors(),
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    Text(
                        if (plan.showAsPrimary)
                            stringResource(R.string.subscription_upgrade_to_premium_button)
                        else
                            stringResource(R.string.subscription_choose_plan_button)
                    )
                }
            }
            
            if (plan.id == "premium") {
                Text(
                    text = "Includes 7-day free trial",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                )
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
fun EnhancedSubscriptionPlanCardPreview() {
    AudioScholarTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            EnhancedSubscriptionPlanCard(
                plan = SubscriptionPlanUIModel(
                    id = "premium",
                    name = "Premium",
                    priceAmount = 150.0,
                    formattedPrice = "₱150",
                    features = listOf(
                        FeatureUIModel("Everything in Basic"),
                        FeatureUIModel("Advanced AI transcription"),
                        FeatureUIModel("Cloud storage"),
                        FeatureUIModel("Export to PDF", false)
                    ),
                    isRecommended = true,
                    showAsPrimary = true
                ),
                isCurrentPlan = false,
                onSelectPlan = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BillingPeriodSelectorPreview() {
    AudioScholarTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            BillingPeriodSelector(
                selectedBillingPeriod = BillingPeriod.MONTHLY,
                onBillingPeriodSelected = {}
            )
        }
    }
} 