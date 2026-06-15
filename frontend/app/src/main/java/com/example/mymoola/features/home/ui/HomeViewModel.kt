package com.example.mymoola.features.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymoola.features.auth.data.AuthApiClient
import com.example.mymoola.features.auth.data.AuthSession
import com.example.mymoola.features.home.data.HomeApiClient
import com.example.mymoola.features.home.data.WalletRealtimeClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale

data class HomeUiState(
    val userName: String = "User",
    val currentUserId: String = "",
    val totalBalanceText: String = "KES 0.00",
    val loadError: String? = null,
    val walletCreditMessage: String? = null,
    val balanceCurrencies: List<BalanceCurrency> = listOf(
        BalanceCurrency("usdc_logo", "USDC", "USD Coin", "0.00 USDC"),
        BalanceCurrency("bitcoin_logo", "BTC", "Bitcoin", "0.00 BTC"),
        BalanceCurrency("ethereum_logo", "ETH", "Ethereum", "0.00 ETH")
    ),
    val activities: List<HomeActivity> = emptyList(),
    val isRefreshing: Boolean = false,
    val hasLoadedOnce: Boolean = false
)

class HomeViewModel : ViewModel() {
    private companion object {
        const val WalletCreditRefreshDebounceMs = 750L
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var walletCreditRefreshJob: Job? = null
    private var walletCreditRefreshInFlight = false

    init {
        applyCachedHomeData()
        WalletRealtimeClient.setWalletCreditedListener { payload ->
            updateState {
                it.copy(
                    walletCreditMessage = "Received ${formatMeaningfulAmount(payload.amount)} ${payload.currency.uppercase(Locale.US)}"
                )
            }
            scheduleWalletCreditRefresh()
        }
        reloadHomeData()
    }

    fun onPullRefresh() {
        viewModelScope.launch {
            updateState { it.copy(isRefreshing = true) }
            reloadHomeData()
            updateState { it.copy(isRefreshing = false) }
        }
    }

    fun refreshForNavigation() {
        reloadHomeData()
    }

    fun onWalletCreditMessageShown() {
        updateState { it.copy(walletCreditMessage = null) }
    }

    override fun onCleared() {
        walletCreditRefreshJob?.cancel()
        WalletRealtimeClient.setWalletCreditedListener(null)
        super.onCleared()
    }

    private fun reloadHomeData() {
        viewModelScope.launch {
            if (AuthSession.accessToken.isNullOrBlank()) {
                val refreshed = AuthApiClient.refreshSession()
                if (!refreshed) {
                    updateState {
                        it.copy(
                            loadError = "Session missing. Please log in again.",
                            hasLoadedOnce = true
                        )
                    }
                    return@launch
                }
            }

            updateState { it.copy(loadError = null) }

            coroutineScope {
                val meDeferred = async { HomeApiClient.getMe() }
                val balanceDeferred = async { HomeApiClient.getBalance() }
                val txDeferred = async { HomeApiClient.getAllTransactions() }

                launch {
                    val balanceResult = balanceDeferred.await()
                    if (balanceResult.isSuccess) {
                        balanceResult.data?.let { applyBalance(it) }
                    } else {
                        updateState { it.copy(loadError = balanceResult.errorMessage, hasLoadedOnce = true) }
                    }
                }

                val meResult = meDeferred.await()
                if (meResult.isSuccess) {
                    val me = meResult.data
                    updateState {
                        it.copy(
                            userName = me?.fullName?.ifBlank { "User" } ?: "User",
                            currentUserId = me?.id.orEmpty(),
                            hasLoadedOnce = true
                        )
                    }
                } else {
                    updateState { it.copy(loadError = meResult.errorMessage, hasLoadedOnce = true) }
                }

                val transactionsResult = txDeferred.await()
                if (transactionsResult.isSuccess) {
                    applyTransactions(
                        all = transactionsResult.data.orEmpty(),
                        userId = _uiState.value.currentUserId
                    )
                } else {
                    updateState { it.copy(loadError = transactionsResult.errorMessage ?: it.loadError, hasLoadedOnce = true) }
                }
            }
        }
    }

    private fun scheduleWalletCreditRefresh() {
        if (walletCreditRefreshInFlight) return
        if (walletCreditRefreshJob?.isActive == true) return

        walletCreditRefreshJob = viewModelScope.launch {
            delay(WalletCreditRefreshDebounceMs)
            refreshFromWalletCredit()
        }
    }

    private suspend fun refreshFromWalletCredit() {
        if (AuthSession.accessToken.isNullOrBlank()) return
        if (walletCreditRefreshInFlight) return

        walletCreditRefreshInFlight = true
        try {
            val balanceResult = HomeApiClient.getBalance()
            if (balanceResult.isSuccess) {
                balanceResult.data?.let { applyBalance(it) }
            }

            val transactionsResult = HomeApiClient.getAllTransactions()
            if (transactionsResult.isSuccess) {
                applyTransactions(
                    all = transactionsResult.data.orEmpty(),
                    userId = _uiState.value.currentUserId
                )
            }
        } finally {
            walletCreditRefreshInFlight = false
            walletCreditRefreshJob = null
        }
    }

    private fun applyCachedHomeData() {
        val cachedMe = HomeApiClient.getCachedMe()
        val cachedBalance = HomeApiClient.getCachedBalance()
        val cachedTransactions = HomeApiClient.getCachedTransactions()

        if (cachedMe != null) {
            updateState {
                it.copy(
                    userName = cachedMe.fullName.ifBlank { "User" },
                    currentUserId = cachedMe.id
                )
            }
        }

        cachedBalance?.let { applyBalance(it) }
        cachedTransactions?.let { applyTransactions(it, _uiState.value.currentUserId) }
    }

    private fun applyBalance(balance: HomeApiClient.BalanceResponse) {
        val preferredCurrencyCode = balance.wallets
            .firstOrNull { it.total > 0.0 }
            ?.currency

        val wallets = balance.wallets.map { wallet ->
            val icon = when (wallet.currency.uppercase(Locale.US)) {
                "BTC" -> "bitcoin_logo"
                "ETH" -> "ethereum_logo"
                "USDC" -> "usdc_logo"
                else -> "onb_wallet_manage"
            }
            BalanceCurrency(
                iconResName = icon,
                code = wallet.currency,
                label = wallet.currency,
                balance = "${formatMeaningfulAmount(wallet.total)} ${wallet.currency}"
            )
        }

        updateState {
            it.copy(
                totalBalanceText = "${balance.displayCurrency} ${String.format(Locale.US, "%,.2f", balance.totalFiatEquivalent)}",
                balanceCurrencies = if (wallets.isNotEmpty()) wallets else it.balanceCurrencies,
                hasLoadedOnce = true
            )
        }
    }

    private fun applyTransactions(all: List<HomeApiClient.UserTransaction>, userId: String) {
        if (userId.isBlank()) return
        val txs = all
            .filter { tx ->
                tx.initiatorUserId.equals(userId, ignoreCase = true) ||
                    tx.counterpartyUserId.equals(userId, ignoreCase = true)
            }
            .groupBy { it.id }
            .map { (_, group) ->
                group.firstOrNull { it.counterpartyUserId.equals(userId, ignoreCase = true) }
                    ?: group.firstOrNull { it.initiatorUserId.equals(userId, ignoreCase = true) }
                    ?: group.first()
            }
            .sortedByDescending { it.createdAt }

        val activities = txs.map { tx ->
            val isSendType = tx.type.equals("Send", ignoreCase = true)
            val isInitiator = tx.initiatorUserId.equals(userId, ignoreCase = true)
            val isReceiver = tx.counterpartyUserId.equals(userId, ignoreCase = true)

            val displayType = when {
                isSendType && isInitiator -> "Send"
                isSendType && isReceiver -> "Receive"
                tx.type.equals("MerchantPayment", ignoreCase = true) ->
                    homeMerchantPaymentLabel(tx.merchantType) ?: "Merchant Payment"
                else -> tx.type.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
                }
            }

            val isCredit = when {
                isSendType && isReceiver -> true
                isSendType && isInitiator -> false
                else -> tx.type.uppercase(Locale.US) in setOf("BUY", "DEPOSIT", "RECEIVE")
            }

            val isFailed = tx.status.equals("Failed", ignoreCase = true)
            val amountColor = when {
                isFailed -> androidx.compose.ui.graphics.Color(0xFFDC2626)
                isCredit -> androidx.compose.ui.graphics.Color(0xFF10B981)
                else -> androidx.compose.ui.graphics.Color(0xFFEF4444)
            }
            val amountPrefix = if (isCredit) "+" else "-"
            HomeActivity(
                type = displayType,
                status = tx.status.lowercase(Locale.US),
                detail = buildString {
                    append(tx.currency)
                    append(" • ")
                    append(formatHomeTime(tx.createdAt))
                    tx.interactedPhone?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }?.let {
                        append(" • ")
                        append(it)
                    }
                    append(" • ")
                    append(tx.referenceCode)
                },
                amount = "$amountPrefix${formatMeaningfulAmount(tx.amount)} ${tx.currency}",
                amountColor = amountColor,
                marketRateSnapshot = tx.marketRateSnapshot,
                onChainTxHash = tx.onChainTxHash,
                onChainConfirmations = tx.onChainConfirmations,
                mpesaReference = tx.mpesaReference
            )
        }

        updateState { it.copy(activities = activities, hasLoadedOnce = true) }
    }

    private fun updateState(transform: (HomeUiState) -> HomeUiState) {
        _uiState.value = transform(_uiState.value)
    }
}

private fun homeMerchantPaymentLabel(merchantType: String?): String? = when (merchantType?.trim()?.lowercase(Locale.US)) {
    "paybill" -> "Paybill"
    "till" -> "Till"
    "pochi" -> "Pochi"
    "sendmoney" -> "Send M-PESA"
    else -> null
}

private fun formatHomeTime(raw: String): String {
    val parsers = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    )
    val outputFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
    for (parser in parsers) {
        val parsed = runCatching { parser.parse(raw) }.getOrNull()
        if (parsed != null) {
            return outputFormat.format(parsed)
        }
    }
    return raw
}

private fun formatMeaningfulAmount(amount: Double): String {
    val formatter = DecimalFormat("#,##0.######", DecimalFormatSymbols(Locale.US))
    return formatter.format(amount)
}
