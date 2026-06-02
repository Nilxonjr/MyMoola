package com.example.mymoola.features.home.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymoola.features.home.data.HomeApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime

data class SellUiState(
    val pendingMessage: String? = null,
    val pendingReference: String? = null,
    val pendingTransactionId: String? = null,
    val pendingStartedAtMs: Long? = null,
    val pendingStatus: String? = null,
    val finalOutcome: String? = null
)

class SellViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SellUiState(
            pendingMessage = savedStateHandle[KEY_PENDING_MESSAGE],
            pendingReference = savedStateHandle[KEY_PENDING_REFERENCE],
            pendingTransactionId = savedStateHandle[KEY_PENDING_TX_ID],
            pendingStartedAtMs = savedStateHandle[KEY_PENDING_STARTED_AT_MS],
            pendingStatus = savedStateHandle[KEY_PENDING_STATUS],
            finalOutcome = savedStateHandle[KEY_FINAL_OUTCOME]
        )
    )
    val uiState: StateFlow<SellUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        startPollingIfNeeded()
    }

    fun onSellInitiated(
        transactionId: String?,
        referenceCode: String?,
        message: String?
    ) {
        updateState {
            it.copy(
                pendingMessage = message,
                pendingReference = referenceCode?.ifBlank { null } ?: "Awaiting payout",
                pendingTransactionId = transactionId?.ifBlank { null },
                pendingStartedAtMs = System.currentTimeMillis(),
                pendingStatus = "Pending",
                finalOutcome = null
            )
        }
        startPollingIfNeeded()
    }

    fun onSellInitiationFailed(errorMessage: String) {
        updateState { it.copy(finalOutcome = errorMessage) }
    }

    fun clearTerminalOutcome() {
        updateState {
            it.copy(
                pendingMessage = null,
                pendingReference = null,
                pendingTransactionId = null,
                pendingStartedAtMs = null,
                pendingStatus = null,
                finalOutcome = null
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
                if (!item.type.equals("Sell", ignoreCase = true)) return@filter false
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
                        finalOutcome = "No payout confirmation received in time. Check transaction history."
                    )
                }
                return true
            }
            tx == null -> return false
            isCompleted -> {
                updateState {
                    it.copy(
                        pendingMessage = "M-Pesa payout completed.",
                        pendingReference = null,
                        pendingStatus = "Completed",
                        pendingTransactionId = null,
                        pendingStartedAtMs = null,
                        finalOutcome = "Success: sell completed and payout recorded."
                    )
                }
                return true
            }
            isFailed -> {
                updateState {
                    it.copy(
                        pendingMessage = "M-Pesa payout failed.",
                        pendingReference = null,
                        pendingStatus = "Failed",
                        pendingTransactionId = null,
                        pendingStartedAtMs = null,
                        finalOutcome = "Failed: payout did not complete. Try again with a fresh quote."
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

    private fun updateState(transform: (SellUiState) -> SellUiState) {
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
        private const val KEY_PENDING_MESSAGE = "sell_pending_message"
        private const val KEY_PENDING_REFERENCE = "sell_pending_reference"
        private const val KEY_PENDING_TX_ID = "sell_pending_tx_id"
        private const val KEY_PENDING_STARTED_AT_MS = "sell_pending_started_at_ms"
        private const val KEY_PENDING_STATUS = "sell_pending_status"
        private const val KEY_FINAL_OUTCOME = "sell_final_outcome"
    }
}
