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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mymoola.BackIconButton
import java.util.Locale

@Composable
fun ActivityDetailsScreen(
    type: String,
    status: String,
    detail: String,
    amount: String,
    marketRateSnapshot: Double?,
    onChainConfirmations: Int,
    mpesaReference: String?,
    onBackClick: () -> Unit
) {
    val isCredit = amount.trim().startsWith("+")
    val amountColor = if (isCredit) Color(0xFF10B981) else Color(0xFFEF4444)
    val statusColor = when {
        status.equals("completed", ignoreCase = true) -> Color(0xFF0A7C6A)
        status.equals("failed", ignoreCase = true) -> Color(0xFFDC2626)
        else -> Color(0xFF334155)
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
                text = "Activity Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(16.dp)
                )
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = type,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = "Status: ${status.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}",
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF64748B)
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = amountColor
            )
            marketRateSnapshot?.let { snapshot ->
                Text(
                    text = "Market rate snapshot: ${String.format(Locale.US, "%,.4f", snapshot)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF475569)
                )
            }
            if (onChainConfirmations > 0) {
                Text(
                    text = "On-chain confirmations: $onChainConfirmations",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF475569)
                )
            }
            mpesaReference
                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                ?.let { mpesaRef ->
                    Text(
                        text = "M-PESA reference: $mpesaRef",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF475569)
                    )
                }
        }
    }
}
