package edu.cit.audioscholar.ui.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.cit.audioscholar.domain.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import javax.inject.Inject

data class SubscriptionPlanUIModel(
    val id: String,
    val name: String,
    val priceAmount: Double,
    val formattedPrice: String,
    val features: List<FeatureUIModel>,
    val isRecommended: Boolean = false,
    val showAsPrimary: Boolean = false,
    val billingPeriod: BillingPeriod = BillingPeriod.MONTHLY
)

data class FeatureUIModel(
    val text: String,
    val isAvailableInPlan: Boolean = true
)

enum class BillingPeriod {
    MONTHLY, ANNUALLY
}

data class SubscriptionUiState(
    val isLoading: Boolean = false,
    val selectedBillingPeriod: BillingPeriod = BillingPeriod.MONTHLY,
    val plans: List<SubscriptionPlanUIModel> = emptyList(),
    val currentPlanId: String? = "basic",
    val error: String? = null,
    val processingPurchase: Boolean = false
)

sealed class SubscriptionEvent {
    data class NavigateToPayment(val planId: String) : SubscriptionEvent()
    data class ShowError(val message: String) : SubscriptionEvent()
    data class ShowMessage(val message: String) : SubscriptionEvent()
}

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    private val _eventChannel = Channel<SubscriptionEvent>()
    val eventFlow = _eventChannel.receiveAsFlow()

    init {
        loadSubscriptionPlans()
    }

    private fun loadSubscriptionPlans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val plans = createMockSubscriptionPlans()
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        plans = plans,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Failed to load subscription plans: ${e.message}"
                    )
                }
                _eventChannel.send(SubscriptionEvent.ShowError("Failed to load subscription plans"))
            }
        }
    }

    fun selectPlan(planId: String) {
        viewModelScope.launch {
            if (planId == _uiState.value.currentPlanId) {
                _eventChannel.send(SubscriptionEvent.ShowMessage("You are already subscribed to this plan"))
                return@launch
            }

            _uiState.update { it.copy(processingPurchase = true) }
            
            _eventChannel.send(SubscriptionEvent.NavigateToPayment(planId))
            
            _uiState.update { it.copy(processingPurchase = false) }
        }
    }

    fun toggleBillingPeriod() {
        val newPeriod = if (_uiState.value.selectedBillingPeriod == BillingPeriod.MONTHLY) {
            BillingPeriod.ANNUALLY
        } else {
            BillingPeriod.MONTHLY
        }
        
        _uiState.update { 
            it.copy(
                selectedBillingPeriod = newPeriod,
                plans = createMockSubscriptionPlans(newPeriod)
            )
        }
    }

    private fun createMockSubscriptionPlans(billingPeriod: BillingPeriod = BillingPeriod.MONTHLY): List<SubscriptionPlanUIModel> {
        val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
        currencyFormatter.currency = Currency.getInstance("PHP")
        
        val annualDiscount = 0.8

        return listOf(
            SubscriptionPlanUIModel(
                id = "basic",
                name = "Basic",
                priceAmount = 0.0,
                formattedPrice = "Free",
                features = listOf(
                    FeatureUIModel("Record and save unlimited audio"),
                    FeatureUIModel("Basic transcription"),
                    FeatureUIModel("Local storage only"),
                    FeatureUIModel("Max 30 minutes per recording", true),
                    FeatureUIModel("AI Summary", false),
                    FeatureUIModel("Cloud Storage", false)
                ),
                billingPeriod = billingPeriod
            ),
            SubscriptionPlanUIModel(
                id = "premium",
                name = "Premium",
                priceAmount = if (billingPeriod == BillingPeriod.MONTHLY) 150.0 else 150.0 * 12 * annualDiscount,
                formattedPrice = if (billingPeriod == BillingPeriod.MONTHLY) {
                    currencyFormatter.format(150)
                } else {
                    currencyFormatter.format(150 * 12 * annualDiscount) + "/year"
                },
                features = listOf(
                    FeatureUIModel("Everything in Basic"),
                    FeatureUIModel("Advanced AI transcription"),
                    FeatureUIModel("AI Summary & Study Notes"),
                    FeatureUIModel("Unlimited recording length"),
                    FeatureUIModel("Cloud storage for all recordings"),
                    FeatureUIModel("Priority processing")
                ),
                isRecommended = true,
                showAsPrimary = true,
                billingPeriod = billingPeriod
            )
        )
    }
} 