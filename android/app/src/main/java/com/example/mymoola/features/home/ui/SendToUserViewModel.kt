package com.example.mymoola.features.home.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymoola.features.auth.data.AuthSession
import com.example.mymoola.features.home.data.HomeApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import java.util.UUID

data class SendCurrencyOption(
    val iconResName: String,
    val code: String,
    val label: String,
    val balanceText: String,
    val balanceAmount: Double
)

data class SendToUserUiState(
    val currencyOptions: List<SendCurrencyOption> = emptyList(),
    val myPhoneNumber: String = "",
    val selectedCurrencyCode: String? = null,
    val recipientName: String? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val isLookingUp: Boolean = false,
    val isSending: Boolean = false,
    val quote: HomeApiClient.QuoteResponse? = null,
    val loadingQuote: Boolean = false,
    val quoteError: String? = null,
    val pendingMessage: String? = null,
    val pendingReference: String? = null,
    val pendingTransactionId: String? = null,
    val pendingStartedAtMs: Long? = null,
    val pendingStatus: String? = null,
    val finalOutcome: String? = null,
    val pendingMode: String? = null
)

class SendToUserViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SendToUserUiState(
            pendingMessage = savedStateHandle[KEY_PENDING_MESSAGE],
            pendingReference = savedStateHandle[KEY_PENDING_REFERENCE],
            pendingTransactionId = savedStateHandle[KEY_PENDING_TX_ID],
            pendingStartedAtMs = savedStateHandle[KEY_PENDING_STARTED_AT_MS],
            pendingStatus = savedStateHandle[KEY_PENDING_STATUS],
            finalOutcome = savedStateHandle[KEY_FINAL_OUTCOME],
            pendingMode = savedStateHandle[KEY_PENDING_MODE]
        )
    )
    val uiState: StateFlow<SendToUserUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        startPollingIfNeeded()
    }

    fun loadInitialData() {
        if (_uiState.value.currencyOptions.isNotEmpty() && _uiState.value.myPhoneNumber.isNotBlank()) return

        viewModelScope.launch {
            val me = HomeApiClient.getMe()
            val balance = HomeApiClient.getBalance()

            val myPhoneNumber = if (me.isSuccess) me.data?.phone.orEmpty() else ""
            val walletOptions = if (balance.isSuccess) {
                mapCurrencyOptions(balance.data?.wallets.orEmpty())
            } else {
                emptyList()
            }

            updateState {
                it.copy(
                    myPhoneNumber = myPhoneNumber,
                    currencyOptions = walletOptions,
                    selectedCurrencyCode = it.selectedCurrencyCode
                        ?: walletOptions.firstOrNull { option -> option.balanceAmount > 0.0 }?.code
                        ?: walletOptions.firstOrNull()?.code,
                    errorMessage = it.errorMessage ?: balance.errorMessage
                )
            }
        }
    }

    fun selectCurrency(code: String) {
        updateState {
            it.copy(
                selectedCurrencyCode = code,
                errorMessage = null
            )
        }
    }

    fun switchMode() {
        clearTransientMessages()
    }

    fun onPhoneChanged() {
        updateState {
            it.copy(
                recipientName = null,
                infoMessage = null,
                errorMessage = null
            )
        }
    }

    fun onPinChanged() {
        updateState { it.copy(errorMessage = null) }
    }

    fun loadQuoteIfNeeded(mode: String, currencyCode: String?) {
        if (mode != MODE_MPESA || currencyCode.isNullOrBlank()) {
            updateState { it.copy(quote = null, loadingQuote = false, quoteError = null) }
            return
        }

        val current = _uiState.value
        if (current.loadingQuote) return
        if (current.quote?.currency.equals(currencyCode, ignoreCase = true) && current.quoteError == null) return

        updateState { it.copy(loadingQuote = true, quoteError = null, quote = null) }
        viewModelScope.launch {
            val result = HomeApiClient.getQuote(currencyCode)
            updateState {
                if (result.isSuccess) {
                    it.copy(
                        quote = result.data,
                        loadingQuote = false,
                        quoteError = null
                    )
                } else {
                    it.copy(
                        quote = null,
                        loadingQuote = false,
                        quoteError = result.errorMessage ?: "Unable to load conversion quote."
                    )
                }
            }
        }
    }

    fun verifyRecipient(normalizedPhone: String, isSelfRecipient: Boolean) {
        if (normalizedPhone.isBlank()) return
        if (isSelfRecipient) {
            updateState { it.copy(errorMessage = "You cannot verify your own number as recipient.") }
            return
        }

        updateState {
            it.copy(
                isLookingUp = true,
                errorMessage = null,
                infoMessage = null,
                recipientName = null
            )
        }

        viewModelScope.launch {
            val result = HomeApiClient.lookupUserByPhone(normalizedPhone)
            updateState {
                if (result.isSuccess) {
                    val found = result.data
                    it.copy(
                        isLookingUp = false,
                        recipientName = found?.fullName,
                        infoMessage = if (found != null) {
                            "Recipient found: ${found.fullName} (${found.phoneNumber})"
                        } else {
                            "Recipient found."
                        }
                    )
                } else {
                    it.copy(
                        isLookingUp = false,
                        errorMessage = result.errorMessage ?: "Recipient lookup failed."
                    )
                }
            }
        }
    }

    fun submitSend(
        mode: String,
        normalizedPhone: String,
        isSelfRecipient: Boolean,
        currency: String,
        amount: Double,
        pin: String,
        availableBalance: Double,
        requiresRecipientVerification: Boolean,
        quote: HomeApiClient.QuoteResponse?,
        mpesaCryptoCost: Double
    ) {
        val sessionPin = AuthSession.sessionPin
        val current = _uiState.value

        val validationError = when {
            isSelfRecipient -> "You cannot send to your own phone number."
            mode == MODE_CRYPTO && amount > availableBalance -> "Insufficient balance."
            mode == MODE_MPESA && mpesaCryptoCost > availableBalance -> "Insufficient balance for this M-PESA amount."
            requiresRecipientVerification && current.recipientName.isNullOrBlank() -> "Verify the recipient before sending."
            mode == MODE_MPESA && quote == null -> "Conversion quote unavailable. Please try again."
            sessionPin.isNullOrBlank() -> "Session PIN unavailable. Please log in again."
            pin != sessionPin -> "Incorrect PIN. Enter your account PIN to continue."
            else -> null
        }
        if (validationError != null) {
            updateState { it.copy(errorMessage = validationError) }
            return
        }

        updateState {
            it.copy(
                isSending = true,
                errorMessage = null,
                infoMessage = null
            )
        }

        viewModelScope.launch {
            try {
                if (mode == MODE_CRYPTO) {
                    val result = HomeApiClient.sendToUser(
                        HomeApiClient.SendToUserRequest(
                            recipientPhone = normalizedPhone,
                            currency = currency,
                            amount = amount,
                            pin = pin
                        )
                    )
                    if (result.isSuccess) {
                        onSendCompleted(
                            mode = MODE_CRYPTO,
                            referenceCode = result.data?.referenceCode,
                            message = result.data?.message ?: "Transfer completed successfully."
                        )
                    } else {
                        onSendInitiationFailed(result.errorMessage ?: "Transfer failed.")
                    }
                } else {
                    val result = HomeApiClient.payMerchant(
                        request = HomeApiClient.PayMerchantRequest(
                            merchantType = "SendMoney",
                            currency = currency,
                            amountKes = amount,
                            quoteId = quote?.quoteId.orEmpty(),
                            pin = pin,
                            phoneNumber = normalizedPhone.removePrefix("+")
                        ),
                        idempotencyKey = UUID.randomUUID().toString()
                    )
                    if (result.isSuccess) {
                        onSendInitiated(
                            mode = MODE_MPESA,
                            transactionId = result.data?.transactionId,
                            referenceCode = result.data?.referenceCode,
                            message = result.data?.message ?: "M-PESA transfer request sent. Waiting for confirmation."
                        )
                    } else {
                        onSendInitiationFailed(result.errorMessage ?: "M-PESA transfer failed.")
                    }
                }
            } finally {
                updateState { it.copy(isSending = false) }
            }
        }
    }

    fun onSendInitiated(
        mode: String,
        transactionId: String?,
        referenceCode: String?,
        message: String?
    ) {
        updateState {
            it.copy(
                pendingMessage = message,
                pendingReference = referenceCode?.ifBlank { null } ?: "Awaiting confirmation",
                pendingTransactionId = transactionId?.ifBlank { null },
                pendingStartedAtMs = System.currentTimeMillis(),
                pendingStatus = "Pending",
                finalOutcome = null,
                pendingMode = mode
            )
        }
        startPollingIfNeeded()
    }

    fun onSendCompleted(
        mode: String,
        referenceCode: String?,
        message: String?
    ) {
        updateState {
            it.copy(
                pendingMessage = message,
                pendingReference = referenceCode?.ifBlank { null },
                pendingTransactionId = null,
                pendingStartedAtMs = null,
                pendingStatus = "Completed",
                finalOutcome = if (mode == MODE_CRYPTO) {
                    "Success: transfer completed and recorded."
                } else {
                    "Success: M-PESA transfer completed and recorded."
                },
                pendingMode = mode
            )
        }
    }

    fun onSendInitiationFailed(errorMessage: String) {
        updateState {
            it.copy(
                errorMessage = errorMessage,
                finalOutcome = errorMessage
            )
        }
    }

    fun clearTerminalOutcome() {
        updateState {
            it.copy(
                pendingMessage = null,
                pendingReference = null,
                pendingTransactionId = null,
                pendingStartedAtMs = null,
                pendingStatus = null,
                finalOutcome = null,
                pendingMode = null,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun startPollingIfNeeded() {
        val current = _uiState.value
        val txId = current.pendingTransactionId
        val reference = current.pendingReference
        val startedAtMs = current.pendingStartedAtMs
        val status = current.pendingStatus

        if (txId.isNullOrBlank() &&
            reference.isNullOrBlank() &&
            startedAtMs == null &&
            !status.equals("Pending", ignoreCase = true) &&
            !status.equals("Processing", ignoreCase = true)
        ) return
        if (pollingJob?.isActive == true) return

        pollingJob = viewModelScope.launch {
            var pollCount = 0
            while (true) {
                if (pollCount == 0) {
                    delay(1_500L)
                }
                if (refreshAndResolve(txId, reference, startedAtMs, current.pendingMode)) return@launch
                pollCount += 1
                delay(if (pollCount < 30) 2_000L else 5_000L)
            }
        }
    }

    fun refreshNow() {
        val current = _uiState.value
        val txId = current.pendingTransactionId
        val reference = current.pendingReference
        val startedAtMs = current.pendingStartedAtMs
        val status = current.pendingStatus
        if (txId.isNullOrBlank() &&
            reference.isNullOrBlank() &&
            startedAtMs == null &&
            !status.equals("Pending", ignoreCase = true) &&
            !status.equals("Processing", ignoreCase = true)
        ) return

        viewModelScope.launch {
            refreshAndResolve(txId, reference, startedAtMs, current.pendingMode)
        }
    }

    private suspend fun refreshAndResolve(
        txId: String?,
        reference: String?,
        startedAtMs: Long?,
        mode: String?
    ): Boolean {
        val result = HomeApiClient.getAllTransactions()
        if (!result.isSuccess) return false

        val items = result.data.orEmpty()
        val tx = items.firstOrNull { item ->
            (!txId.isNullOrBlank() && item.id == txId) ||
                (!reference.isNullOrBlank() && item.referenceCode == reference)
        } ?: items
            .asSequence()
            .filter { item ->
                val matchesType = when (mode) {
                    MODE_CRYPTO -> item.type.equals("Send", ignoreCase = true)
                    MODE_MPESA -> item.type.equals("MerchantPayment", ignoreCase = true) ||
                        item.type.equals("Merchant Payment", ignoreCase = true)
                    else -> false
                }
                if (!matchesType) return@filter false
                val createdAtMs = parseEpochMillis(item.createdAt) ?: return@filter false
                startedAtMs == null || createdAtMs >= (startedAtMs - 120_000L)
            }
            .maxByOrNull { parseEpochMillis(it.createdAt) ?: Long.MIN_VALUE }

        val normalizedStatus = tx?.status?.trim().orEmpty()
        val isCompleted = normalizedStatus.equals("Completed", ignoreCase = true) ||
            normalizedStatus.equals("Success", ignoreCase = true) ||
            normalizedStatus.equals("Succeeded", ignoreCase = true)
        val isFailed = normalizedStatus.equals("Failed", ignoreCase = true) ||
            normalizedStatus.equals("Declined", ignoreCase = true) ||
            normalizedStatus.equals("Cancelled", ignoreCase = true) ||
            normalizedStatus.equals("Canceled", ignoreCase = true) ||
            normalizedStatus.equals("Timeout", ignoreCase = true)

        when {
            tx == null && startedAtMs != null && System.currentTimeMillis() - startedAtMs > 180_000L -> {
                updateState {
                    it.copy(
                        pendingReference = null,
                        pendingTransactionId = null,
                        pendingStartedAtMs = null,
                        pendingStatus = "Failed",
                        finalOutcome = "No confirmation received in time. Check transaction history."
                    )
                }
                return true
            }
            tx == null -> return false
            isCompleted -> {
                updateState {
                    it.copy(
                        pendingMessage = if (mode == MODE_CRYPTO) {
                            "Transfer completed successfully."
                        } else {
                            "M-PESA transfer recorded successfully."
                        },
                        pendingReference = null,
                        pendingStatus = "Completed",
                        pendingTransactionId = null,
                        pendingStartedAtMs = null,
                        finalOutcome = if (mode == MODE_CRYPTO) {
                            "Success: transfer completed and recorded."
                        } else {
                            "Success: M-PESA transfer completed and recorded."
                        }
                    )
                }
                return true
            }
            isFailed -> {
                updateState {
                    it.copy(
                        pendingMessage = if (mode == MODE_CRYPTO) {
                            "Transfer failed."
                        } else {
                            "M-PESA transfer failed."
                        },
                        pendingReference = null,
                        pendingStatus = "Failed",
                        pendingTransactionId = null,
                        pendingStartedAtMs = null,
                        finalOutcome = if (mode == MODE_CRYPTO) {
                            "Failed: transfer did not complete. Please try again."
                        } else {
                            "Failed: M-PESA transfer did not complete. Please try again."
                        }
                    )
                }
                return true
            }
            else -> {
                updateState { it.copy(pendingStatus = normalizedStatus.ifBlank { "Pending" }) }
                return false
            }
        }
    }

    private fun parseEpochMillis(value: String): Long? {
        return runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrNull()
    }

    private fun updateState(transform: (SendToUserUiState) -> SendToUserUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next
        savedStateHandle[KEY_PENDING_MESSAGE] = next.pendingMessage
        savedStateHandle[KEY_PENDING_REFERENCE] = next.pendingReference
        savedStateHandle[KEY_PENDING_TX_ID] = next.pendingTransactionId
        savedStateHandle[KEY_PENDING_STARTED_AT_MS] = next.pendingStartedAtMs
        savedStateHandle[KEY_PENDING_STATUS] = next.pendingStatus
        savedStateHandle[KEY_FINAL_OUTCOME] = next.finalOutcome
        savedStateHandle[KEY_PENDING_MODE] = next.pendingMode
    }

    private fun clearTransientMessages() {
        updateState {
            it.copy(
                infoMessage = null,
                errorMessage = null
            )
        }
    }

    private fun mapCurrencyOptions(wallets: List<HomeApiClient.WalletBalance>): List<SendCurrencyOption> {
        if (wallets.isEmpty()) return emptyList()
        val walletByCode = wallets.associateBy { it.currency.uppercase(Locale.US) }
        return SendCurrencyOrder.mapNotNull { code ->
            val wallet = walletByCode[code] ?: return@mapNotNull null
            SendCurrencyOption(
                iconResName = when (wallet.currency.uppercase(Locale.US)) {
                    "BTC" -> "bitcoin_logo"
                    "ETH" -> "ethereum_logo"
                    "USDC" -> "usdc_logo"
                    else -> "onb_wallet_manage"
                },
                code = wallet.currency.uppercase(Locale.US),
                label = when (wallet.currency.uppercase(Locale.US)) {
                    "BTC" -> "Bitcoin"
                    "ETH" -> "Ethereum"
                    "USDC" -> "USD Coin"
                    else -> wallet.currency
                },
                balanceText = String.format(Locale.US, "%.6f %s", wallet.total, wallet.currency.uppercase(Locale.US)),
                balanceAmount = wallet.total
            )
        }
    }

    companion object {
        const val MODE_CRYPTO = "crypto"
        const val MODE_MPESA = "mpesa"
        private val SendCurrencyOrder = listOf("BTC", "ETH", "USDC")
        private const val KEY_PENDING_MESSAGE = "send_pending_message"
        private const val KEY_PENDING_REFERENCE = "send_pending_reference"
        private const val KEY_PENDING_TX_ID = "send_pending_tx_id"
        private const val KEY_PENDING_STARTED_AT_MS = "send_pending_started_at_ms"
        private const val KEY_PENDING_STATUS = "send_pending_status"
        private const val KEY_FINAL_OUTCOME = "send_final_outcome"
        private const val KEY_PENDING_MODE = "send_pending_mode"
    }
}
