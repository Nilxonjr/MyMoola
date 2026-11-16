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
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

data class BuyUiState(
    val pendingMessage: String? = null,
    val pendingReference: String? = null,
    val pendingTransactionId: String? = null,
    val pendingStartedAtMs: Long? = null,
    val pendingStatus: String? = null,
    val finalOutcome: String? = null,
    val showSuccessDialog: Boolean = false,
    val successToastShown: Boolean = false,
    val isSubmitting: Boolean = false,
    val submissionError: String? = null
)

class BuyViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        BuyUiState(
            pendingMessage = savedStateHandle[KEY_PENDING_MESSAGE],
            pendingReference = savedStateHandle[KEY_PENDING_REFERENCE],
            pendingTransactionId = savedStateHandle[KEY_PENDING_TX_ID],
            pendingStartedAtMs = savedStateHandle[KEY_PENDING_STARTED_AT_MS],
            pendingStatus = savedStateHandle[KEY_PENDING_STATUS],
            finalOutcome = savedStateHandle[KEY_FINAL_OUTCOME],
            showSuccessDialog = savedStateHandle[KEY_SHOW_SUCCESS_DIALOG] ?: false,
            successToastShown = savedStateHandle[KEY_SUCCESS_TOAST_SHOWN] ?: false
        )
    )
    val uiState: StateFlow<BuyUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        startPollingIfNeeded()
    }

    fun onBuyInitiated(
        transactionId: String?,
        referenceCode: String?,
        message: String?
    ) {
        val normalizedTxId = transactionId?.ifBlank { null }
        val normalizedReference = referenceCode?.ifBlank { null }
        updateState {
            it.copy(
                pendingMessage = message,
                pendingReference = normalizedReference ?: "Awaiting confirmation",
                pendingTransactionId = normalizedTxId,
                pendingStartedAtMs = System.currentTimeMillis(),
                pendingStatus = "Pending",
                finalOutcome = null,
                showSuccessDialog = false,
                successToastShown = false,
                isSubmitting = false,
                submissionError = null
            )
        }
        startPollingIfNeeded()
    }

    fun onBuyInitiationFailed(errorMessage: String) {
        updateState {
            it.copy(finalOutcome = errorMessage, isSubmitting = false, submissionError = errorMessage)
        }
    }

    fun clearSubmissionError() {
        updateState { it.copy(submissionError = null) }
    }

    fun submitBuy(
        currency: String,
        grossKes: Double,
        pin: String,
        quoteId: String,
        refreshQuoteIfExpired: suspend () -> String?,
        onQuotePrompt: (String) -> Unit
    ) {
        updateState { it.copy(isSubmitting = true, submissionError = null) }
        viewModelScope.launch {
            try {
                val sessionPin = AuthSession.sessionPin
                if (sessionPin.isNullOrBlank()) {
                    onBuyInitiationFailed("Session PIN unavailable. Please log in again.")
                    return@launch
                }
                if (pin != sessionPin) {
                    onBuyInitiationFailed("Incorrect PIN. Enter your account PIN to continue.")
                    return@launch
                }

                val expiryResult = refreshQuoteIfExpired()
                if (expiryResult != null) {
                    if (expiryResult.startsWith("Rate updated.") || expiryResult.startsWith("Quote expired.")) {
                        onQuotePrompt(expiryResult)
                        updateState { it.copy(isSubmitting = false, submissionError = null) }
                    } else {
                        onBuyInitiationFailed(expiryResult)
                    }
                    return@launch
                }

                val key = savedStateHandle[KEY_ACTIVE_ATTEMPT_KEY] as String?
                    ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_ACTIVE_ATTEMPT_KEY] = it }

                val result = try {
                    withTimeout(20_000) {
                        HomeApiClient.buyCrypto(
                            request = HomeApiClient.BuyCryptoRequest(
                                currency = currency,
                                grossKes = grossKes,
                                quoteId = quoteId,
                                pin = pin
                            ),
                            idempotencyKey = key
                        )
                    }
                } catch (_: Exception) {
                    onBuyInitiated(
                        transactionId = null,
                        referenceCode = null,
                        message = "Payment request sent. Waiting for confirmation."
                    )
                    return@launch
                }

                if (result.isSuccess) {
                    onBuyInitiated(
                        transactionId = result.data?.transactionId,
                        referenceCode = result.data?.referenceCode,
                        message = result.data?.message
                    )
                } else {
                    val error = when (result.statusCode) {
                        400 -> result.errorMessage ?: "Please check your inputs and try again."
                        401 -> "Session expired. Please sign in again."
                        403 -> result.errorMessage ?: "This operation is currently disabled for your account."
                        404 -> "User or wallet not found."
                        422 -> "Quote expired. Fetching latest rate..."
                        429 -> "Too many requests. Please wait 30 seconds and try again."
                        else -> result.errorMessage ?: "Unable to initiate payment."
                    }
                    onBuyInitiationFailed(error)
                    if (result.statusCode == 422) {
                        val refreshedMessage = refreshQuoteIfExpired()
                        if (refreshedMessage != null && refreshedMessage.startsWith("Quote expired.")) {
                            onQuotePrompt(refreshedMessage)
                        }
                    }
                }
            } finally {
                updateState { current ->
                    if (current.pendingStatus.equals("Pending", ignoreCase = true) || current.pendingTransactionId != null) {
                        current.copy(isSubmitting = false)
                    } else {
                        current.copy(isSubmitting = false)
                    }
                }
            }
        }
    }

    fun dismissSuccessDialog() {
        updateState { it.copy(showSuccessDialog = false) }
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
                showSuccessDialog = false,
                successToastShown = false,
                isSubmitting = false,
                submissionError = null
            )
        }
    }

    fun markSuccessToastShown() {
        updateState { it.copy(successToastShown = true) }
    }

    fun startPollingIfNeeded() {
        val current = _uiState.value
        val txId = current.pendingTransactionId
        val reference = current.pendingReference
        val startedAtMs = current.pendingStartedAtMs

        if (txId.isNullOrBlank() && reference.isNullOrBlank() && startedAtMs == null) return
        if (pollingJob?.isActive == true) return

        pollingJob = viewModelScope.launch {
            var pollCount = 0
            while (true) {
                if (refreshAndResolve(txId, reference, startedAtMs)) return@launch
                pollCount += 1
                val nextDelayMs = if (pollCount < 30) 2_000L else 5_000L
                delay(nextDelayMs)
            }
        }
    }

    fun refreshNow() {
        val current = _uiState.value
        val txId = current.pendingTransactionId
        val reference = current.pendingReference
        val startedAtMs = current.pendingStartedAtMs
        if (txId.isNullOrBlank() && reference.isNullOrBlank() && startedAtMs == null) return

        viewModelScope.launch {
            refreshAndResolve(txId, reference, startedAtMs)
        }
    }

    private suspend fun refreshAndResolve(
        txId: String?,
        reference: String?,
        startedAtMs: Long?
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
                if (!item.type.equals("Buy", ignoreCase = true)) return@filter false
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
                        finalOutcome = "No confirmation received in time. Please check transaction history."
                    )
                }
                return true
            }
            tx == null -> return false
            isCompleted -> {
                updateState {
                    it.copy(
                        pendingMessage = "Payment completed successfully.",
                        pendingReference = null,
                        pendingStatus = "Completed",
                        pendingTransactionId = null,
                        pendingStartedAtMs = null,
                        finalOutcome = "Success: crypto credited to your wallet.",
                        showSuccessDialog = true,
                        successToastShown = false
                    )
                }
                return true
            }
            isFailed -> {
                updateState {
                    it.copy(
                        pendingMessage = "Payment failed.",
                        pendingReference = null,
                        pendingStatus = "Failed",
                        pendingTransactionId = null,
                        pendingStartedAtMs = null,
                        finalOutcome = "Failed: payment did not complete. Please try again with a fresh quote."
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

    private fun updateState(transform: (BuyUiState) -> BuyUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next

        savedStateHandle[KEY_PENDING_MESSAGE] = next.pendingMessage
        savedStateHandle[KEY_PENDING_REFERENCE] = next.pendingReference
        savedStateHandle[KEY_PENDING_TX_ID] = next.pendingTransactionId
        savedStateHandle[KEY_PENDING_STARTED_AT_MS] = next.pendingStartedAtMs
        savedStateHandle[KEY_PENDING_STATUS] = next.pendingStatus
        savedStateHandle[KEY_FINAL_OUTCOME] = next.finalOutcome
        savedStateHandle[KEY_SHOW_SUCCESS_DIALOG] = next.showSuccessDialog
        savedStateHandle[KEY_SUCCESS_TOAST_SHOWN] = next.successToastShown
    }

    companion object {
        private const val KEY_PENDING_MESSAGE = "buy_pending_message"
        private const val KEY_PENDING_REFERENCE = "buy_pending_reference"
        private const val KEY_PENDING_TX_ID = "buy_pending_tx_id"
        private const val KEY_PENDING_STARTED_AT_MS = "buy_pending_started_at_ms"
        private const val KEY_PENDING_STATUS = "buy_pending_status"
        private const val KEY_FINAL_OUTCOME = "buy_final_outcome"
        private const val KEY_SHOW_SUCCESS_DIALOG = "buy_show_success_dialog"
        private const val KEY_SUCCESS_TOAST_SHOWN = "buy_success_toast_shown"
        private const val KEY_ACTIVE_ATTEMPT_KEY = "buy_active_attempt_key"
    }
}
