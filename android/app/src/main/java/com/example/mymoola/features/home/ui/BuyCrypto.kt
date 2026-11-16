package com.example.mymoola.features.home.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.mymoola.BackIconButton
import com.example.mymoola.features.home.data.HomeApiClient
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun BuyCryptoScreen(
    onBackClick: () -> Unit,
    onDoneClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val localContext = LocalContext.current
    val supportedCurrencies = listOf("BTC", "ETH", "USDC")
    var selectedCurrency by rememberSaveable { mutableStateOf("BTC") }
    val buyViewModel: BuyViewModel = viewModel()
    val buyUiState by buyViewModel.uiState.collectAsState()
    val quoteState = rememberTransactionQuoteState(selectedCurrency)
    var amountInput by rememberSaveable { mutableStateOf("1000") }
    var quoteRefreshPrompt by remember { mutableStateOf<String?>(null) }
    var pin by rememberSaveable { mutableStateOf("") }
    var holdProgress by remember { mutableFloatStateOf(0f) }

    val grossKes = amountInput.toDoubleOrNull() ?: 0.0
    val platformFee = grossKes * 0.015
    val netKes = (grossKes - platformFee).coerceAtLeast(0.0)
    val buyRateKes = quoteState.quote?.buyRateKes ?: 0.0
    val receiveAmount = if (buyRateKes > 0.0) netKes / buyRateKes else 0.0
    val canSubmit = grossKes > 0.0 &&
        pin.length == 4 &&
        quoteState.quote != null &&
        !buyUiState.isSubmitting &&
        buyUiState.pendingTransactionId.isNullOrBlank()

    val hasPendingLocator =
        !buyUiState.pendingTransactionId.isNullOrBlank() || !buyUiState.pendingReference.isNullOrBlank()
    val showPendingScreen = hasPendingLocator &&
        !buyUiState.pendingStatus.equals("Completed", ignoreCase = true) &&
        !buyUiState.pendingStatus.equals("Failed", ignoreCase = true)
    val showSuccessScreen = !hasPendingLocator &&
        buyUiState.pendingStatus.equals("Completed", ignoreCase = true)
    val showFailedScreen = !hasPendingLocator &&
        buyUiState.pendingStatus.equals("Failed", ignoreCase = true)

    LaunchedEffect(Unit) {
        buyViewModel.startPollingIfNeeded()
    }

    LaunchedEffect(buyUiState.showSuccessDialog, buyUiState.successToastShown) {
        if (buyUiState.showSuccessDialog && !buyUiState.successToastShown) {
            Toast.makeText(
                context,
                "Transaction successful. Wallet updated.",
                Toast.LENGTH_LONG
            ).show()
            buyViewModel.markSuccessToastShown()
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
                text = "Buy Crypto",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Buy Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    supportedCurrencies.forEach { currency ->
                        val isSelected = currency == selectedCurrency
                        val iconResName = when (currency) {
                            "USDC" -> "usdc_logo"
                            "BTC" -> "bitcoin_logo"
                            "ETH" -> "ethereum_logo"
                            else -> "onb_wallet_manage"
                        }
                        val iconResId = remember(iconResName) {
                            localContext.resources.getIdentifier(
                                iconResName,
                                "drawable",
                                localContext.packageName
                            )
                        }
                        Row(
                            modifier = Modifier
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .clickable { selectedCurrency = currency }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (iconResId != 0) {
                                Image(
                                    painter = painterResource(id = iconResId),
                                    contentDescription = "$currency logo",
                                    modifier = Modifier.width(16.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Text(
                                text = currency,
                                color = if (isSelected) Color.White else Color(0xFF0F172A)
                            )
                        }
                    }
                }

                if (quoteState.loading && quoteState.quote == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Loading quote...", color = Color(0xFF334155))
                    }
                } else if (quoteState.quote != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Buy rate: ${String.format(Locale.US, "%.2f", buyRateKes)} KES",
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Quote refreshes in ${quoteState.refreshSecondsRemaining}s",
                            color = if (quoteState.refreshSecondsRemaining <= 5) Color(0xFFB91C1C) else Color(0xFF334155),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (!quoteState.error.isNullOrBlank()) {
                    Text(
                        text = quoteState.error ?: "",
                        color = Color(0xFFB91C1C),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (!quoteRefreshPrompt.isNullOrBlank()) {
                    Text(
                        text = quoteRefreshPrompt ?: "",
                        color = Color(0xFF1D4ED8),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { input ->
                        amountInput = input.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text("Amount (KES)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(autoCorrectEnabled = false)
                )

                Text(
                    text = "You pay: ${String.format(Locale.US, "%,.2f", grossKes)} KES",
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Platform fee: ${String.format(Locale.US, "%,.2f", platformFee)} KES (1.5%)",
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "You receive ≈ ${String.format(Locale.US, "%.6f", receiveAmount)} $selectedCurrency",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Medium
                )

                if (showPendingScreen) {
                    StatePanel(
                        title = "Payment Pending",
                        message = "Check your phone — enter your M-Pesa PIN to complete payment.",
                        reference = buyUiState.pendingReference,
                        statusLine = "Status: ${buyUiState.pendingStatus ?: "Pending"} (auto-checking)",
                        actionLabel = "Refresh now",
                        onAction = { buyViewModel.refreshNow() }
                    )
                    return@Column
                }

                if (showSuccessScreen) {
                    StatePanel(
                        title = "Purchase Successful",
                        message = buyUiState.finalOutcome ?: "Crypto credited to your wallet.",
                        reference = buyUiState.pendingReference,
                        statusLine = "Status: Completed",
                        statusColor = Color(0xFF166534),
                        actionLabel = "Done",
                        onAction = {
                            buyViewModel.clearTerminalOutcome()
                            onDoneClick()
                        }
                    )
                    return@Column
                }

                if (showFailedScreen) {
                    StatePanel(
                        title = "Purchase Failed",
                        message = buyUiState.finalOutcome ?: "Payment did not complete.",
                        reference = buyUiState.pendingReference,
                        statusLine = "Status: Failed",
                        statusColor = Color(0xFFB91C1C),
                        actionLabel = "Try Again",
                        onAction = { buyViewModel.clearTerminalOutcome() }
                    )
                    return@Column
                }

                OutlinedTextField(
                    value = pin,
                    onValueChange = { input -> pin = input.filter(Char::isDigit).take(4) },
                    label = { Text("PIN (4 digits)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !buyUiState.isSubmitting && buyUiState.pendingTransactionId.isNullOrBlank()
                )

                if (!buyUiState.submissionError.isNullOrBlank()) {
                    Text(
                        text = buyUiState.submissionError ?: "",
                        color = Color(0xFFB91C1C),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                val holdEnabledColor = if (canSubmit) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(holdEnabledColor, RoundedCornerShape(12.dp))
                        .pointerInput(canSubmit, grossKes, pin, quoteState.quote?.quoteId, selectedCurrency) {
                            detectTapGestures(
                                onPress = {
                                    if (!canSubmit) return@detectTapGestures

                                    buyViewModel.clearSubmissionError()
                                    holdProgress = 0f
                                    coroutineScope {
                                        var triggered = false
                                        val holdJob = launch {
                                            val totalMs = 3000
                                            val stepMs = 50
                                            var elapsed = 0
                                            while (elapsed < totalMs) {
                                                delay(stepMs.toLong())
                                                elapsed += stepMs
                                                holdProgress = (elapsed.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                                            }

                                            triggered = true
                                            val activeQuote = quoteState.quote ?: return@launch
                                            quoteRefreshPrompt = null
                                            buyViewModel.submitBuy(
                                                currency = selectedCurrency,
                                                grossKes = grossKes,
                                                pin = pin,
                                                quoteId = activeQuote.quoteId,
                                                refreshQuoteIfExpired = {
                                                    quoteState.refreshIfExpired(
                                                        currency = selectedCurrency,
                                                        successPrompt = "Rate updated. Please review new price and hold Buy again."
                                                    )
                                                },
                                                onQuotePrompt = { prompt -> quoteRefreshPrompt = prompt }
                                            )
                                            holdProgress = 0f
                                        }

                                        val released = tryAwaitRelease()
                                        if (released && !triggered) {
                                            holdJob.cancel()
                                            holdProgress = 0f
                                        }
                                    }
                                }
                            )
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (buyUiState.isSubmitting) "Submitting..." else "Hold 3 seconds to Buy",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (holdProgress > 0f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color(0xFFE2E8F0), RoundedCornerShape(999.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(holdProgress)
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp))
                        )
                    }
                }
            }
        }

        if (buyUiState.showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { buyViewModel.dismissSuccessDialog() },
                title = {
                    Text("Transaction successful")
                },
                text = {
                    Text("Your crypto purchase is complete and your wallet has been updated.")
                },
                confirmButton = {
                    TextButton(onClick = { buyViewModel.dismissSuccessDialog() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
private fun StatePanel(
    title: String,
    message: String,
    reference: String?,
    statusLine: String,
    statusColor: Color = Color(0xFF334155),
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = Color(0xFF0F172A),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = Color(0xFF334155),
            style = MaterialTheme.typography.bodyMedium
        )
        if (!reference.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Reference: $reference",
                color = Color(0xFF334155)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = statusLine,
            color = statusColor,
            fontWeight = FontWeight.Medium
        )
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
