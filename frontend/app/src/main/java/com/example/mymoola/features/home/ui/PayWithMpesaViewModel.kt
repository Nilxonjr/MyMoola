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
import java.util.Locale
import java.util.UUID

data class PayWithMpesaUiState(
    val pendingMessage: String? = null,
    val pendingReference: String? = null,
    val pendingTransactionId: String? = null,
    val pendingStartedAtMs: Long? = null,
    val pendingStatus: String? = null,
    val finalOutcome: String? = null,
    val isSubmitting: Boolean = false,
    val submissionError: String? = null
)

class PayWithMpesaViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        PayWithMpesaUiState(
            pendingMessage = savedStateHandle[KEY_PENDING_MESSAGE],
            pendingReference = savedStateHandle[KEY_PENDING_REFERENCE],
            pendingTransactionId = savedStateHandle[KEY_PENDING_TX_ID],
            pendingStartedAtMs = savedStateHandle[KEY_PENDING_STARTED_AT_MS],
            pendingStatus = savedStateHandle[KEY_PENDING_STATUS],
            finalOutcome = savedStateHandle[KEY_FINAL_OUTCOME]
        )
    )
    val uiState: StateFlow<PayWithMpesaUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        startPollingIfNeeded()
    }

    fun onPaymentInitiated(
        transactionId: String?,
        referenceCode: String?,
        message: String?
    ) {
        updateState {
            it.copy(
                pendingMessage = message,
                pendingReference = referenceCode?.ifBlank { null } ?: "Awaiting merchant confirmation",
                pendingTransactionId = transactionId?.ifBlank { null },
                pendingStartedAtMs = System.currentTimeMillis(),
                pendingStatus = "Pending",
                finalOutcome = null,
                isSubmitting = false,
                submissionError = null
            )
        }
        startPollingIfNeeded()
    }

    fun onPaymentInitiationFailed(errorMessage: String) {
        updateState { it.copy(finalOutcome = errorMessage, isSubmitting = false, submissionError = errorMessage) }
    }

    fun clearSubmissionError() {
        updateState { it.copy(submissionError = null) }
    }

    fun submitPayment(
        request: HomeApiClient.PayMerchantRequest,
        refreshQuoteIfExpired: suspend () -> String?,
        onQuotePrompt: (String) -> Unit
    ) {
        updateState { it.copy(isSubmitting = true, submissionError = null) }
        viewModelScope.launch {
            try {
                val sessionPin = AuthSession.sessionPin
                if (sessionPin.isNullOrBlank()) {
                    onPaymentInitiationFailed("Session PIN unavailable. Please log in again.")
                    return@launch
                }
                if (request.pin != sessionPin) {
                    onPaymentInitiationFailed("Incorrect PIN. Enter your account PIN to continue.")
                    return@launch
                }

                val expiryResult = refreshQuoteIfExpired()
                if (expiryResult != null) {
                    if (expiryResult.startsWith("Rate updated.")) {
                        onQuotePrompt(expiryResult)
                        updateState { it.copy(isSubmitting = false, submissionError = null) }
                    } else {
                        onPaymentInitiationFailed(expiryResult)
                    }
                    return@launch
                }

                val key = savedStateHandle[KEY_ACTIVE_ATTEMPT_KEY] as String?
                    ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_ACTIVE_ATTEMPT_KEY] = it }

                val result = try {
                    withTimeout(20_000) {
                        HomeApiClient.payMerchant(request, key)
                    }
                } catch (_: Exception) {
                    onPaymentInitiated(
                        transactionId = null,
                        referenceCode = null,
                        message = "Payment request sent. Waiting for merchant confirmation."
                    )
                    return@launch
                }

                if (result.isSuccess) {
                    onPaymentInitiated(
                        transactionId = result.data?.transactionId,
                        referenceCode = result.data?.referenceCode,
                        message = result.data?.message
                    )
                } else {
                    val error = when (result.statusCode) {
                        400 -> result.errorMessage ?: "Please check your payment details and try again."
                        401 -> "Session expired. Please sign in again."
                        403 -> result.errorMessage ?: "This operation is currently disabled for your account."
                        404 -> "User or wallet not found."
                        409 -> result.errorMessage ?: "A conflicting merchant payment request already exists."
                        422 -> result.errorMessage ?: "Unable to process this merchant payment right now."
                        429 -> "Too many requests. Please wait 30 seconds and try again."
                        else -> result.errorMessage ?: "Unable to initiate merchant payment."
                    }
                    onPaymentInitiationFailed(error)
                }
            } finally {
                updateState { it.copy(isSubmitting = false) }
            }
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
                isSubmitting = false,
                submissionError = null
            )
        }
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
                delay(if (pollCount < 30) 2_000L else 5_000L)
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
                val type = item.type.trim().lowercase(Locale.US)
                if (type != "merchantpayment" && type != "merchant payment") return@filter false
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
                        finalOutcome = "No merchant confirmation received in time. Check transaction history."
                    )
                }
                return true
            }
            tx == null -> return false
            isCompleted -> {
                updateState {
                    it.copy(
                        pendingMessage = "Merchant payment completed.",
                        pendingReference = null,
                        pendingStatus = "Completed",
                        pendingTransactionId = null,
                        pendingStartedAtMs = null,
                        finalOutcome = "Success: merchant payment completed and recorded."
                    )
                }
                return true
            }
            isFailed -> {
                updateState {
                    it.copy(
                        pendingMessage = "Merchant payment failed.",
                        pendingReference = null,
                        pendingStatus = "Failed",
                        pendingTransactionId = null,
                        pendingStartedAtMs = null,
                        finalOutcome = "Failed: merchant payment did not complete. Try again with a fresh quote."
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

    private fun updateState(transform: (PayWithMpesaUiState) -> PayWithMpesaUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next
        savedStateHandle[KEY_PENDING_MESSAGE] = next.pendingMessage
        savedStateHandle[KEY_PENDING_REFERENCE] = next.pendingReference
        savedStateHandle[KEY_PENDING_TX_ID] = next.pendingTransactionId
        savedStateHandle[KEY_PENDING_STARTED_AT_MS] = next.pendingStartedAtMs
        savedStateHandle[KEY_PENDING_STATUS] = next.pendingStatus
        savedStateHandle[KEY_FINAL_OUTCOME] = next.finalOutcome
    }

    companion object {
        private const val KEY_PENDING_MESSAGE = "pay_mpesa_pending_message"
        private const val KEY_PENDING_REFERENCE = "pay_mpesa_pending_reference"
        private const val KEY_PENDING_TX_ID = "pay_mpesa_pending_tx_id"
        private const val KEY_PENDING_STARTED_AT_MS = "pay_mpesa_pending_started_at_ms"
        private const val KEY_PENDING_STATUS = "pay_mpesa_pending_status"
        private const val KEY_FINAL_OUTCOME = "pay_mpesa_final_outcome"
        private const val KEY_ACTIVE_ATTEMPT_KEY = "pay_mpesa_active_attempt_key"
    }
}
