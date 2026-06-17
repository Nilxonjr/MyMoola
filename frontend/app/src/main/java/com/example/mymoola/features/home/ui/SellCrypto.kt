package com.example.mymoola.features.home.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mymoola.BackIconButton
import com.example.mymoola.features.home.data.HomeApiClient
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.floor

private const val SellPlatformFeeRate = 0.015
private val SellCurrencyOrder = listOf("BTC", "ETH", "USDC")

@Composable
fun SellCryptoScreen(
    onBackClick: () -> Unit,
    onDoneClick: () -> Unit = onBackClick
) {
    data class CurrencyOption(
        val code: String,
        val balanceAmount: Double,
        val iconResName: String
    )

    val localContext = LocalContext.current
    val sellViewModel: SellViewModel = viewModel()
    val sellUiState by sellViewModel.uiState.collectAsState()

    val fallbackCurrencies = listOf(
        CurrencyOption("BTC", 0.0, "bitcoin_logo"),
        CurrencyOption("ETH", 0.0, "ethereum_logo"),
        CurrencyOption("USDC", 0.0, "usdc_logo")
    )
    var currencies by remember { mutableStateOf(fallbackCurrencies) }
    var selectedCurrency by rememberSaveable { mutableStateOf("BTC") }
    val quoteState = rememberTransactionQuoteState(selectedCurrency)
    var amountInput by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var quoteRefreshPrompt by remember { mutableStateOf<String?>(null) }
    var holdProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val balance = HomeApiClient.getBalance()
        if (balance.isSuccess) {
            val mappedByCode = balance.data?.wallets
                .orEmpty()
                .filter { wallet -> wallet.currency.uppercase() in setOf("BTC", "ETH", "USDC") }
                .associateBy { it.currency.uppercase() }
            val mapped = SellCurrencyOrder.mapNotNull { code ->
                val wallet = mappedByCode[code] ?: return@mapNotNull null
                CurrencyOption(
                    code = wallet.currency.uppercase(),
                    balanceAmount = wallet.total,
                    iconResName = when (wallet.currency.uppercase()) {
                        "BTC" -> "bitcoin_logo"
                        "ETH" -> "ethereum_logo"
                        "USDC" -> "usdc_logo"
                        else -> "onb_wallet_manage"
                    }
                )
            }

            if (mapped.isNotEmpty()) {
                currencies = mapped
                if (mapped.none { it.code == selectedCurrency }) {
                    selectedCurrency = mapped.first().code
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        sellViewModel.startPollingIfNeeded()
    }

    val selectedWallet = currencies.firstOrNull { it.code == selectedCurrency }
    val cryptoAmount = amountInput.toDoubleOrNull() ?: 0.0
    val marketRateKes = quoteState.quote?.rateKes ?: 0.0
    val spreadPercent = quoteState.quote?.spreadPercent ?: 0.0
    val grossKes = cryptoAmount * marketRateKes
    val platformFeeKes = grossKes * SellPlatformFeeRate
    val spreadKes = grossKes * (spreadPercent / 100.0)
    val estimatedPayoutKes = floor((grossKes - platformFeeKes - spreadKes).coerceAtLeast(0.0))
    val availableBalance = selectedWallet?.balanceAmount ?: 0.0
    val hasSufficientBalance = cryptoAmount > 0.0 && cryptoAmount <= availableBalance
    val canSubmit = cryptoAmount > 0.0 &&
        pin.length == 4 &&
        quoteState.quote != null &&
        hasSufficientBalance &&
        !sellUiState.isSubmitting &&
        sellUiState.pendingTransactionId.isNullOrBlank()

    val hasPendingLocator =
        !sellUiState.pendingTransactionId.isNullOrBlank() || !sellUiState.pendingReference.isNullOrBlank()
    val showPendingScreen = hasPendingLocator &&
        !sellUiState.pendingStatus.equals("Completed", ignoreCase = true) &&
        !sellUiState.pendingStatus.equals("Failed", ignoreCase = true)
    val showSuccessScreen = !hasPendingLocator &&
        sellUiState.pendingStatus.equals("Completed", ignoreCase = true)
    val showFailedScreen = !hasPendingLocator &&
        sellUiState.pendingStatus.equals("Failed", ignoreCase = true)

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
                text = "Sell Crypto",
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
                    text = "Sell Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    currencies.forEach { currency ->
                        val isSelected = currency.code == selectedCurrency
                        val iconResId = remember(currency.iconResName) {
                            localContext.resources.getIdentifier(
                                currency.iconResName,
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
                                .clickable { selectedCurrency = currency.code }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (iconResId != 0) {
                                Image(
                                    painter = painterResource(id = iconResId),
                                    contentDescription = "${currency.code} logo",
                                    modifier = Modifier.width(16.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Text(
                                text = currency.code,
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
                            text = "Sell rate: ${String.format(Locale.US, "%.2f", quoteState.quote?.sellRateKes ?: 0.0)} KES",
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
                    label = { Text("Amount ($selectedCurrency)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Text(
                    text = "Available: ${String.format(Locale.US, "%.6f", availableBalance)} $selectedCurrency",
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gross value: ${String.format(Locale.US, "%,.2f", grossKes)} KES",
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Platform fee: ${String.format(Locale.US, "%,.2f", platformFeeKes)} KES (1.5%)",
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Estimated M-Pesa payout: ${String.format(Locale.US, "%,.0f", estimatedPayoutKes)} KES",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Medium
                )

                if (showPendingScreen) {
                    SellStatePanel(
                        title = "Payout Pending",
                        message = sellUiState.pendingMessage ?: "Your M-Pesa payout is being processed.",
                        reference = sellUiState.pendingReference,
                        statusLine = "Status: ${sellUiState.pendingStatus ?: "Pending"} (auto-checking)",
                        actionLabel = "Refresh now",
                        onAction = { sellViewModel.refreshNow() }
                    )
                    return@Column
                }

                if (showSuccessScreen) {
                    SellStatePanel(
                        title = "Sell Successful",
                        message = sellUiState.finalOutcome ?: "Your payout is complete.",
                        reference = sellUiState.pendingReference,
                        statusLine = "Status: Completed",
                        statusColor = Color(0xFF166534),
                        actionLabel = "Done",
                        onAction = {
                            sellViewModel.clearTerminalOutcome()
                            onDoneClick()
                        }
                    )
                    return@Column
                }

                if (showFailedScreen) {
                    SellStatePanel(
                        title = "Sell Failed",
                        message = sellUiState.finalOutcome ?: "Payout did not complete.",
                        reference = sellUiState.pendingReference,
                        statusLine = "Status: Failed",
                        statusColor = Color(0xFFB91C1C),
                        actionLabel = "Try Again",
                        onAction = { sellViewModel.clearTerminalOutcome() }
                    )
                    return@Column
                }

                OutlinedTextField(
                    value = pin,
                    onValueChange = { input -> pin = input.filter(Char::isDigit).take(4) },
                    label = { Text("PIN (4 digits)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !sellUiState.isSubmitting && sellUiState.pendingTransactionId.isNullOrBlank()
                )

                if (cryptoAmount > availableBalance && availableBalance > 0.0) {
                    Text(
                        text = "Insufficient $selectedCurrency balance.",
                        color = Color(0xFFB91C1C),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (!sellUiState.submissionError.isNullOrBlank()) {
                    Text(
                        text = sellUiState.submissionError ?: "",
                        color = Color(0xFFB91C1C),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                val holdEnabledColor = if (canSubmit) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(holdEnabledColor, RoundedCornerShape(12.dp))
                        .pointerInput(canSubmit, cryptoAmount, pin, quoteState.quote?.quoteId, selectedCurrency) {
                            detectTapGestures(
                                onPress = {
                                    if (!canSubmit) return@detectTapGestures

                                    sellViewModel.clearSubmissionError()
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
                                            sellViewModel.submitSell(
                                                currency = selectedCurrency,
                                                cryptoAmount = cryptoAmount,
                                                pin = pin,
                                                quoteId = activeQuote.quoteId,
                                                refreshQuoteIfExpired = {
                                                    quoteState.refreshIfExpired(
                                                        currency = selectedCurrency,
                                                        successPrompt = "Rate updated. Please review new payout and hold Sell again."
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
                        text = if (sellUiState.isSubmitting) "Submitting..." else "Hold 3 seconds to Sell",
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
    }
}

@Composable
private fun SellStatePanel(
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
