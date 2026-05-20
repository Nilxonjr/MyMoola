package com.example.mymoola.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mymoola.BackIconButton
import com.example.mymoola.features.home.data.HomeApiClient
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ViewRecordsScreen(
    onBackClick: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var records by remember { mutableStateOf<List<HomeApiClient.UserTransaction>>(emptyList()) }
    var currentUserId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val me = HomeApiClient.getMe()
        if (me.isSuccess) {
            currentUserId = me.data?.id.orEmpty()
        }

        val result = HomeApiClient.getAllTransactions()
        isLoading = false
        if (result.isSuccess) {
            records = result.data.orEmpty()
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
        } else {
            errorMessage = result.errorMessage ?: "Failed to load transaction records."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            BackIconButton(onClick = onBackClick)
            Text(
                text = "View Records",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        when {
            isLoading -> {
                CircularProgressIndicator(color = Color(0xFF0A7C6A))
            }
            !errorMessage.isNullOrBlank() -> {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFDC2626)
                )
            }
            records.isEmpty() -> {
                Text(
                    text = "No records found yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF334155)
                )
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(records) { tx ->
                        val isSendType = tx.type.equals("Send", ignoreCase = true)
                        val isInitiator = tx.initiatorUserId.equals(currentUserId, ignoreCase = true)
                        val isReceiver = tx.counterpartyUserId.equals(currentUserId, ignoreCase = true)
                        val displayType = when {
                            isSendType && isInitiator -> "Send"
                            isSendType && isReceiver -> "Receive"
                            else -> tx.type.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
                            }
                        }
                        val isCredit = when {
                            isSendType && isReceiver -> true
                            isSendType && isInitiator -> false
                            else -> tx.type.uppercase(Locale.US) in setOf("BUY", "DEPOSIT", "RECEIVE")
                        }
                        val amountColor = if (isCredit) Color(0xFF10B981) else Color(0xFFEF4444)
                        val amountPrefix = if (isCredit) "+" else "-"

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(14.dp)
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = displayType,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFF0F172A),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "$amountPrefix${String.format(Locale.US, "%.6f", tx.amount)} ${tx.currency}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = amountColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "Status: ${tx.status.lowercase(Locale.US)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF0A7C6A)
                                )
                                Text(
                                    text = "Reference: ${tx.referenceCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF475569)
                                )
                                if (!tx.interactedPhone.isNullOrBlank()) {
                                    Text(
                                        text = "With: ${tx.interactedPhone}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF334155)
                                    )
                                }
                                Text(
                                    text = "Date: ${formatRecordDate(tx.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )
                                tx.marketRateSnapshot?.let { snapshot ->
                                    Text(
                                        text = "Market rate snapshot: ${String.format(Locale.US, "%,.4f", snapshot)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                if (tx.onChainConfirmations > 0) {
                                    Text(
                                        text = "On-chain confirmations: ${tx.onChainConfirmations}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                tx.mpesaReference
                                    ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                                    ?.let { mpesaRef ->
                                    Text(
                                        text = "M-PESA reference: $mpesaRef",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B)
                                    )
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
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
