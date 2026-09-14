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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mymoola.BackIconButton
import java.util.Locale

@Composable
fun ViewRecordsScreen(
    onBackClick: () -> Unit
) {
    val viewModel: ViewRecordsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

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
            uiState.isLoading -> {
                CircularProgressIndicator(color = Color(0xFF0A7C6A))
            }
            !uiState.errorMessage.isNullOrBlank() -> {
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFDC2626)
                )
            }
            uiState.records.isEmpty() -> {
                Text(
                    text = "No records found yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF334155)
                )
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.records, key = { it.id }) { record ->
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
                                        text = record.displayType,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFF0F172A),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = record.amountText,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = record.amountColor,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "Status: ${record.statusText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = record.statusColor
                                )
                                Text(
                                    text = "Reference: ${record.referenceCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF475569)
                                )
                                if (!record.receiverName.isNullOrBlank()) {
                                    Text(
                                        text = "Name: ${record.receiverName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF334155)
                                    )
                                }
                                if (!record.interactedPhone.isNullOrBlank()) {
                                    Text(
                                        text = "With: ${record.interactedPhone}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF334155)
                                    )
                                }
                                Text(
                                    text = "Date: ${record.dateText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )
                                record.marketRateSnapshot?.let { snapshot ->
                                    Text(
                                        text = "Market rate snapshot: ${String.format(Locale.US, "%,.4f", snapshot)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                record.onChainTxHash?.let { txHash ->
                                    Text(
                                        text = "Transaction hash: $txHash",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                if (record.onChainConfirmations > 0) {
                                    Text(
                                        text = "On-chain confirmations: ${record.onChainConfirmations}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                record.mpesaReference?.let { mpesaRef ->
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
