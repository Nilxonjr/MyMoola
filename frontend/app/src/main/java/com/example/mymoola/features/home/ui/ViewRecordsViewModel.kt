package com.example.mymoola.features.home.ui

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymoola.features.home.data.HomeApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale

data class ViewRecordItem(
    val id: String,
    val displayType: String,
    val amountText: String,
    val amountColor: Color,
    val statusText: String,
    val statusColor: Color,
    val referenceCode: String,
    val receiverName: String?,
    val interactedPhone: String?,
    val dateText: String,
    val marketRateSnapshot: Double?,
    val onChainTxHash: String?,
    val onChainConfirmations: Int,
    val mpesaReference: String?
)

data class ViewRecordsUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val records: List<ViewRecordItem> = emptyList(),
    val currentUserId: String = ""
)

class ViewRecordsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ViewRecordsUiState())
    val uiState: StateFlow<ViewRecordsUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, errorMessage = null) }

            val me = HomeApiClient.getMe()
            val currentUserId = if (me.isSuccess) me.data?.id.orEmpty() else ""

            val result = HomeApiClient.getAllTransactions()
            if (result.isSuccess) {
                val records = mapRecords(
                    transactions = result.data.orEmpty(),
                    currentUserId = currentUserId
                )
                updateState {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        currentUserId = currentUserId,
                        records = records
                    )
                }
            } else {
                updateState {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.errorMessage ?: "Failed to load transaction records.",
                        currentUserId = currentUserId,
                        records = emptyList()
                    )
                }
            }
        }
    }

    private fun updateState(transform: (ViewRecordsUiState) -> ViewRecordsUiState) {
        _uiState.value = transform(_uiState.value)
    }
}

private fun mapRecords(
    transactions: List<HomeApiClient.UserTransaction>,
    currentUserId: String
): List<ViewRecordItem> {
    return transactions
        .filter { tx ->
            tx.initiatorUserId.equals(currentUserId, ignoreCase = true) ||
                tx.counterpartyUserId.equals(currentUserId, ignoreCase = true)
        }
        .groupBy { it.id }
        .map { (_, group) ->
            group.firstOrNull { it.counterpartyUserId.equals(currentUserId, ignoreCase = true) }
                ?: group.firstOrNull { it.initiatorUserId.equals(currentUserId, ignoreCase = true) }
                ?: group.first()
        }
        .sortedByDescending { it.createdAt }
        .map { tx ->
            val isSendType = tx.type.equals("Send", ignoreCase = true)
            val isInitiator = tx.initiatorUserId.equals(currentUserId, ignoreCase = true)
            val isReceiver = tx.counterpartyUserId.equals(currentUserId, ignoreCase = true)
            val displayType = when {
                isSendType && isInitiator -> "Send"
                isSendType && isReceiver -> "Receive"
                tx.type.equals("MerchantPayment", ignoreCase = true) ->
                    recordsMerchantPaymentLabel(tx.merchantType) ?: "Merchant Payment"
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
                isFailed -> Color(0xFFDC2626)
                isCredit -> Color(0xFF10B981)
                else -> Color(0xFFEF4444)
            }
            val amountPrefix = if (isCredit) "+" else "-"
            ViewRecordItem(
                id = tx.id,
                displayType = displayType,
                amountText = "$amountPrefix${formatMeaningfulRecordAmount(tx.amount)} ${tx.currency}",
                amountColor = amountColor,
                statusText = tx.status.lowercase(Locale.US),
                statusColor = if (isFailed) Color(0xFFDC2626) else Color(0xFF0A7C6A),
                referenceCode = tx.referenceCode,
                receiverName = tx.receiverName
                    ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
                interactedPhone = tx.interactedPhone
                    ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
                dateText = formatRecordDate(tx.createdAt),
                marketRateSnapshot = tx.marketRateSnapshot,
                onChainTxHash = tx.onChainTxHash
                    ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
                onChainConfirmations = tx.onChainConfirmations,
                mpesaReference = tx.mpesaReference
                    ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            )
        }
}

private fun recordsMerchantPaymentLabel(merchantType: String?): String? = when (merchantType?.trim()?.lowercase(Locale.US)) {
    "paybill" -> "Paybill"
    "till" -> "Till"
    "pochi" -> "Pochi"
    "sendmoney" -> "Send M-PESA"
    else -> null
}

private fun formatRecordDate(raw: String): String {
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

private fun formatMeaningfulRecordAmount(amount: Double): String {
    val formatter = DecimalFormat("#,##0.######", DecimalFormatSymbols(Locale.US))
    return formatter.format(amount)
}
