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

private fun HomeApiClient.UserTransaction.isWithdrawalTransaction(): Boolean =
    type.equals("Withdrawal", ignoreCase = true)

private fun String?.isCompletedWithdrawalStatus(): Boolean {
    val value = this?.trim().orEmpty()
    return value.equals("Completed", ignoreCase = true) ||
        value.equals("Success", ignoreCase = true) ||
        value.equals("Succeeded", ignoreCase = true)
}

private fun String?.isFailedWithdrawalStatus(): Boolean {
    val value = this?.trim().orEmpty()
    return value.equals("Failed", ignoreCase = true) ||
        value.equals("Declined", ignoreCase = true) ||
        value.equals("Cancelled", ignoreCase = true) ||
        value.equals("Canceled", ignoreCase = true) ||
        value.equals("Timeout", ignoreCase = true)
}

data class WithdrawUiState(
    val pendingMessage: String? = null,
    val pendingReference: String? = null,
    val pendingTransactionId: String? = null,
    val pendingStartedAtMs: Long? = null,
    val pendingStatus: String? = null,
    val pendingToAddress: String? = null,
    val finalOutcome: String? = null,
    val isSubmitting: Boolean = false,
    val submissionError: String? = null
)

class WithdrawViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        WithdrawUiState(
            pendingMessage = savedStateHandle[KEY_PENDING_MESSAGE],
            pendingReference = savedStateHandle[KEY_PENDING_REFERENCE],
            pendingTransactionId = savedStateHandle[KEY_PENDING_TX_ID],
            pendingStartedAtMs = savedStateHandle[KEY_PENDING_STARTED_AT_MS],
            pendingStatus = savedStateHandle[KEY_PENDING_STATUS],
            pendingToAddress = savedStateHandle[KEY_PENDING_TO_ADDRESS],
            finalOutcome = savedStateHandle[KEY_FINAL_OUTCOME]
        )
    )
    val uiState: StateFlow<WithdrawUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        startPollingIfNeeded()
    }

    fun onWithdrawInitiated(
        transactionId: String?,
        referenceCode: String?,
        toAddress: String,
        message: String?
    ) {
        updateState {
            it.copy(
                pendingMessage = message,
                pendingReference = referenceCode?.ifBlank { null } ?: "Awaiting broadcast",
                pendingTransactionId = transactionId?.ifBlank { null },
                pendingStartedAtMs = System.currentTimeMillis(),
                pendingStatus = "Pending",
                pendingToAddress = toAddress,
                finalOutcome = null,
                isSubmitting = false,
                submissionError = null
            )
        }
        startPollingIfNeeded()
    }

    fun onWithdrawInitiationFailed(errorMessage: String) {
        updateState { it.copy(finalOutcome = errorMessage, isSubmitting = false, submissionError = errorMessage) }
    }

    fun clearSubmissionError() {
        updateState { it.copy(submissionError = null) }
    }

    fun submitWithdrawal(
        currency: String,
        amount: Double,
        toAddress: String,
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
                    onWithdrawInitiationFailed("Session PIN unavailable. Please log in again.")
                    return@launch
                }
                if (pin != sessionPin) {
                    onWithdrawInitiationFailed("Incorrect PIN. Enter your account PIN to continue.")
                    return@launch
                }

                val expiryResult = refreshQuoteIfExpired()
                if (expiryResult != null) {
                    if (expiryResult.startsWith("Fee updated.")) {
                        onQuotePrompt(expiryResult)
                        updateState { it.copy(isSubmitting = false, submissionError = null) }
                    } else {
                        onWithdrawInitiationFailed(expiryResult)
                    }
                    return@launch
                }

                val key = savedStateHandle[KEY_ACTIVE_ATTEMPT_KEY] as String?
                    ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_ACTIVE_ATTEMPT_KEY] = it }

                val result = try {
                    withTimeout(20_000) {
                        HomeApiClient.withdrawCrypto(
                            request = HomeApiClient.WithdrawCryptoRequest(
                                currency = currency,
                                amount = amount,
                                toAddress = toAddress,
                                pin = pin,
                                quoteId = quoteId
                            ),
                            idempotencyKey = key
                        )
                    }
                } catch (_: Exception) {
                    onWithdrawInitiated(
                        transactionId = null,
                        referenceCode = null,
                        toAddress = toAddress,
                        message = "Withdrawal request sent. Waiting for network confirmation."
                    )
                    return@launch
                }

                if (result.isSuccess) {
                    onWithdrawInitiated(
                        transactionId = result.data?.transactionId,
                        referenceCode = result.data?.referenceCode,
                        toAddress = result.data?.toAddress ?: toAddress,
                        message = result.data?.message
                    )
                } else {
                    val error = when (result.statusCode) {
                        400 -> result.errorMessage ?: "Please check withdrawal details and try again."
                        401 -> "Session expired. Please sign in again."
                        403 -> result.errorMessage ?: "Withdrawals are currently disabled for this asset."
                        404 -> "Wallet or quote not found."
                        409 -> result.errorMessage ?: "A conflicting withdrawal request already exists."
                        422 -> result.errorMessage ?: "Unable to process this withdrawal right now."
                        429 -> "Too many requests. Please wait 30 seconds and try again."
                        else -> result.errorMessage ?: "Unable to initiate withdrawal."
                    }
                    onWithdrawInitiationFailed(error)
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
                pendingToAddress = null,
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
                if (!item.isWithdrawalTransaction()) return@filter false
                val createdAtMs = parseEpochMillis(item.createdAt) ?: return@filter false
                startedAtMs == null || createdAtMs >= (startedAtMs - 120_000L)
            }
            .maxByOrNull { parseEpochMillis(it.createdAt) ?: Long.MIN_VALUE }

        val normalizedStatus = tx?.status?.trim().orEmpty()

        when {
            tx == null && startedAtMs != null && System.currentTimeMillis() - startedAtMs > 300_000L -> {
                updateState {
                    it.copy(
                        pendingReference = null,
                        pendingTransactionId = null,
                        pendingStartedAtMs = null,
                        pendingStatus = "Failed",
                        finalOutcome = "No withdrawal confirmation received in time. Check transaction history."
                    )
                }
                return true
            }
            tx == null -> return false
            normalizedStatus.isCompletedWithdrawalStatus() -> {
                updateState {
                    it.copy(
                        pendingMessage = "Withdrawal confirmed on network.",
                        pendingReference = null,
                        pendingStatus = "Completed",
                        pendingTransactionId = null,
                        pendingStartedAtMs = null,
                        finalOutcome = "Success: withdrawal completed and recorded."
                    )
                }
                return true
            }
            normalizedStatus.isFailedWithdrawalStatus() -> {
                updateState {
                    it.copy(
                        pendingMessage = "Withdrawal failed.",
                        pendingReference = null,
                        pendingStatus = "Failed",
                        pendingTransactionId = null,
                        pendingStartedAtMs = null,
                        finalOutcome = "Failed: withdrawal did not complete. Try again with a fresh fee quote."
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

    private fun updateState(transform: (WithdrawUiState) -> WithdrawUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next
        savedStateHandle[KEY_PENDING_MESSAGE] = next.pendingMessage
        savedStateHandle[KEY_PENDING_REFERENCE] = next.pendingReference
        savedStateHandle[KEY_PENDING_TX_ID] = next.pendingTransactionId
        savedStateHandle[KEY_PENDING_STARTED_AT_MS] = next.pendingStartedAtMs
        savedStateHandle[KEY_PENDING_STATUS] = next.pendingStatus
        savedStateHandle[KEY_PENDING_TO_ADDRESS] = next.pendingToAddress
        savedStateHandle[KEY_FINAL_OUTCOME] = next.finalOutcome
    }

    companion object {
        private const val KEY_PENDING_MESSAGE = "withdraw_pending_message"
        private const val KEY_PENDING_REFERENCE = "withdraw_pending_reference"
        private const val KEY_PENDING_TX_ID = "withdraw_pending_tx_id"
        private const val KEY_PENDING_STARTED_AT_MS = "withdraw_pending_started_at_ms"
        private const val KEY_PENDING_STATUS = "withdraw_pending_status"
        private const val KEY_PENDING_TO_ADDRESS = "withdraw_pending_to_address"
        private const val KEY_FINAL_OUTCOME = "withdraw_final_outcome"
        private const val KEY_ACTIVE_ATTEMPT_KEY = "withdraw_active_attempt_key"
    }
}
