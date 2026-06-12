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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.mymoola.features.auth.data.AuthSession
import com.example.mymoola.features.home.data.HomeApiClient
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.UUID

private val WithdrawCurrencyOrder = listOf("BTC", "ETH", "USDC")

private fun normalizeWithdrawAddress(raw: String): String = raw.trim()

private fun isValidWithdrawAddress(raw: String): Boolean {
    val value = normalizeWithdrawAddress(raw)
    if (!value.startsWith("0x")) return false
    if (value.length != 42) return false
    return value.drop(2).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
}

private fun shortWithdrawAddress(address: String): String {
    val value = normalizeWithdrawAddress(address)
    if (value.length <= 12) return value
    return "${value.take(6)}...${value.takeLast(4)}"
}

@Composable
fun WithdrawCryptoScreen(
    onBackClick: () -> Unit,
    onDoneClick: () -> Unit = onBackClick
) {
    data class CurrencyOption(
        val code: String,
        val balanceAmount: Double,
        val iconResName: String
    )

    val localContext = LocalContext.current
    val withdrawViewModel: WithdrawViewModel = viewModel()
    val withdrawUiState by withdrawViewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val fallbackCurrencies = listOf(
        CurrencyOption("BTC", 0.0, "bitcoin_logo"),
        CurrencyOption("ETH", 0.0, "ethereum_logo"),
        CurrencyOption("USDC", 0.0, "usdc_logo")
    )
    var currencies by remember { mutableStateOf(fallbackCurrencies) }
    var selectedCurrency by rememberSaveable { mutableStateOf("BTC") }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var destinationAddress by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var quote by remember { mutableStateOf<HomeApiClient.WithdrawalQuoteResponse?>(null) }
    var loadingQuote by remember { mutableStateOf(false) }
    var quoteError by remember { mutableStateOf<String?>(null) }
    var quoteRefreshPrompt by remember { mutableStateOf<String?>(null) }
    var refreshSecondsRemaining by remember { mutableLongStateOf(25L) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var submitting by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var activeAttemptKey by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val balance = HomeApiClient.getBalance()
        if (balance.isSuccess) {
            val mappedByCode = balance.data?.wallets
                .orEmpty()
                .filter { wallet -> wallet.currency.uppercase() in setOf("BTC", "ETH", "USDC") }
                .associateBy { it.currency.uppercase() }
            val mapped = WithdrawCurrencyOrder.mapNotNull { code ->
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

    fun clearQuoteState() {
        quote = null
        loadingQuote = false
        quoteError = null
        quoteRefreshPrompt = null
        refreshSecondsRemaining = 25L
    }

    val selectedWallet = currencies.firstOrNull { it.code == selectedCurrency }
    val amountValue = amountInput.toDoubleOrNull() ?: 0.0
    val availableBalance = selectedWallet?.balanceAmount ?: 0.0
    val normalizedAddress = normalizeWithdrawAddress(destinationAddress)
    val addressValid = normalizedAddress.isNotBlank() && isValidWithdrawAddress(normalizedAddress)
    val hasSufficientBalance = amountValue > 0.0 && amountValue <= availableBalance
    val feeAmount = quote?.feeAmount ?: 0.0
    val netAmount = (amountValue - feeAmount).coerceAtLeast(0.0)
    val quoteReady = quote != null && quote?.currency.equals(selectedCurrency, ignoreCase = true)

    LaunchedEffect(selectedCurrency, amountInput) {
        val validAmount = amountInput.toDoubleOrNull()
        if (validAmount == null || validAmount <= 0.0) {
            clearQuoteState()
            return@LaunchedEffect
        }

        loadingQuote = true
        quoteError = null
        val result = HomeApiClient.getWithdrawalQuote(selectedCurrency, validAmount)
        loadingQuote = false
        if (result.isSuccess) {
            quote = result.data
        } else {
            quote = null
            quoteError = result.errorMessage ?: "Unable to load withdrawal fee."
        }
    }

    LaunchedEffect(selectedCurrency, amountInput, quote?.quoteId, quote != null) {
        if (quote == null) {
            refreshSecondsRemaining = 25L
            return@LaunchedEffect
        }

        var remaining = minOf(25L, quote?.expiresInSeconds ?: 25L)
        refreshSecondsRemaining = remaining
        while (true) {
            delay(1000)
            remaining -= 1
            refreshSecondsRemaining = remaining.coerceAtLeast(0L)
            if (remaining <= 0L) {
                val activeAmount = amountInput.toDoubleOrNull()
                if (activeAmount == null || activeAmount <= 0.0) {
                    clearQuoteState()
                    return@LaunchedEffect
                }

                val result = HomeApiClient.getWithdrawalQuote(selectedCurrency, activeAmount)
                if (result.isSuccess) {
                    quote = result.data
                    quoteError = null
                } else if (quote == null) {
                    quoteError = result.errorMessage ?: "Unable to refresh withdrawal fee."
                }
                remaining = minOf(25L, quote?.expiresInSeconds ?: 25L)
                refreshSecondsRemaining = remaining
            }
        }
    }

    LaunchedEffect(Unit) {
        withdrawViewModel.startPollingIfNeeded()
    }

    val hasPendingLocator =
        !withdrawUiState.pendingTransactionId.isNullOrBlank() || !withdrawUiState.pendingReference.isNullOrBlank()
    val showPendingScreen = hasPendingLocator &&
        !withdrawUiState.pendingStatus.isCompletedWithdrawalStatus() &&
        !withdrawUiState.pendingStatus.isFailedWithdrawalStatus()
    val showSuccessScreen = !hasPendingLocator &&
        withdrawUiState.pendingStatus.isCompletedWithdrawalStatus()
    val showFailedScreen = !hasPendingLocator &&
        withdrawUiState.pendingStatus.isFailedWithdrawalStatus()

    val canSubmit = amountValue > 0.0 &&
        pin.length == 4 &&
        quoteReady &&
        hasSufficientBalance &&
        addressValid &&
        netAmount > 0.0 &&
        !submitting &&
        withdrawUiState.pendingTransactionId.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            BackIconButton(onClick = onBackClick)
            Text(
                text = "Withdraw Crypto",
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
                    text = "Withdrawal Details",
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

                if (loadingQuote && quote == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Loading withdrawal fee...", color = Color(0xFF334155))
                    }
                } else if (quote != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Network fee: ${String.format(Locale.US, "%.6f", feeAmount)} $selectedCurrency",
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "You will receive about ${String.format(Locale.US, "%.6f", netAmount)} $selectedCurrency",
                            color = Color(0xFF334155)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Quote refreshes in ${refreshSecondsRemaining}s",
                            color = if (refreshSecondsRemaining <= 5) Color(0xFFB91C1C) else Color(0xFF334155),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (!quoteError.isNullOrBlank()) {
                    Text(
                        text = quoteError.orEmpty(),
                        color = Color(0xFFB91C1C),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (!quoteRefreshPrompt.isNullOrBlank()) {
                    Text(
                        text = quoteRefreshPrompt.orEmpty(),
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

                OutlinedTextField(
                    value = destinationAddress,
                    onValueChange = { destinationAddress = it },
                    label = { Text("Destination address") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Text(
                    text = "Withdraw $selectedCurrency on Ethereum network only. Double-check address before sending.",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodySmall
                )

                if (showPendingScreen) {
                    WithdrawStatePanel(
                        title = "Withdrawal Pending",
                        message = withdrawUiState.pendingMessage
                            ?: "Your withdrawal is waiting for network processing.",
                        reference = withdrawUiState.pendingReference,
                        address = withdrawUiState.pendingToAddress,
                        statusLine = "Status: ${withdrawUiState.pendingStatus.toWithdrawalStatusLabel()} (auto-checking)",
                        actionLabel = "Refresh now",
                        onAction = { withdrawViewModel.refreshNow() }
                    )
                    return@Column
                }

                if (showSuccessScreen) {
                    WithdrawStatePanel(
                        title = "Withdrawal Successful",
                        message = withdrawUiState.finalOutcome ?: "Your withdrawal is complete.",
                        reference = withdrawUiState.pendingReference,
                        address = withdrawUiState.pendingToAddress,
                        statusLine = "Status: Completed",
                        statusColor = Color(0xFF166534),
                        actionLabel = "Done",
                        onAction = {
                            withdrawViewModel.clearTerminalOutcome()
                            onDoneClick()
                        }
                    )
                    return@Column
                }

                if (showFailedScreen) {
                    WithdrawStatePanel(
                        title = "Withdrawal Failed",
                        message = withdrawUiState.finalOutcome ?: "Withdrawal did not complete.",
                        reference = withdrawUiState.pendingReference,
                        address = withdrawUiState.pendingToAddress,
                        statusLine = "Status: Failed",
                        statusColor = Color(0xFFB91C1C),
                        actionLabel = "Try Again",
                        onAction = { withdrawViewModel.clearTerminalOutcome() }
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
                    enabled = !submitting && withdrawUiState.pendingTransactionId.isNullOrBlank()
                )

                if (amountValue > availableBalance && availableBalance > 0.0) {
                    Text(
                        text = "Insufficient $selectedCurrency balance.",
                        color = Color(0xFFB91C1C),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (normalizedAddress.isNotBlank() && !addressValid) {
                    Text(
                        text = "Enter valid Ethereum-style address starting with 0x.",
                        color = Color(0xFFB91C1C),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (quoteReady && netAmount <= 0.0 && amountValue > 0.0) {
                    Text(
                        text = "Amount too small to cover withdrawal fee.",
                        color = Color(0xFFB91C1C),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (!formError.isNullOrBlank()) {
                    Text(
                        text = formError.orEmpty(),
                        color = Color(0xFFB91C1C),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                val holdEnabledColor = if (canSubmit) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(holdEnabledColor, RoundedCornerShape(12.dp))
                        .pointerInput(canSubmit, amountValue, pin, quote?.quoteId, selectedCurrency, normalizedAddress) {
                            detectTapGestures(
                                onPress = {
                                    if (!canSubmit) return@detectTapGestures

                                    formError = null
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
                                            submitting = true
                                            try {
                                                val sessionPin = AuthSession.sessionPin
                                                if (sessionPin.isNullOrBlank()) {
                                                    formError = "Session PIN unavailable. Please log in again."
                                                    return@launch
                                                }
                                                if (pin != sessionPin) {
                                                    formError = "Incorrect PIN. Enter your account PIN to continue."
                                                    return@launch
                                                }

                                                val activeQuote = quote
                                                if (activeQuote == null) {
                                                    formError = "Withdrawal fee is unavailable. Please refresh and try again."
                                                    return@launch
                                                }

                                                if (!isValidWithdrawAddress(normalizedAddress)) {
                                                    formError = "Destination address is invalid."
                                                    return@launch
                                                }

                                                if (refreshSecondsRemaining <= 0L) {
                                                    val refreshed = HomeApiClient.getWithdrawalQuote(selectedCurrency, amountValue)
                                                    if (refreshed.isSuccess && refreshed.data != null) {
                                                        quote = refreshed.data
                                                        quoteRefreshPrompt = "Fee updated. Review new net amount and hold Withdraw again."
                                                        formError = null
                                                    } else {
                                                        formError = refreshed.errorMessage ?: "Quote expired. Unable to refresh fee right now."
                                                    }
                                                    return@launch
                                                }

                                                val key = activeAttemptKey ?: UUID.randomUUID().toString().also { activeAttemptKey = it }
                                                val result = try {
                                                    withTimeout(20_000) {
                                                        HomeApiClient.withdrawCrypto(
                                                            request = HomeApiClient.WithdrawCryptoRequest(
                                                                currency = selectedCurrency,
                                                                amount = amountValue,
                                                                toAddress = normalizedAddress,
                                                                pin = pin,
                                                                quoteId = activeQuote.quoteId
                                                            ),
                                                            idempotencyKey = key
                                                        )
                                                    }
                                                } catch (_: Exception) {
                                                    formError = null
                                                    quoteRefreshPrompt = null
                                                    withdrawViewModel.onWithdrawInitiated(
                                                        transactionId = null,
                                                        referenceCode = null,
                                                        toAddress = normalizedAddress,
                                                        message = "Withdrawal request sent. Waiting for network confirmation."
                                                    )
                                                    return@launch
                                                }

                                                if (result.isSuccess) {
                                                    withdrawViewModel.onWithdrawInitiated(
                                                        transactionId = result.data?.transactionId,
                                                        referenceCode = result.data?.referenceCode,
                                                        toAddress = result.data?.toAddress ?: normalizedAddress,
                                                        message = result.data?.message
                                                    )
                                                    quoteRefreshPrompt = null
                                                    formError = null
                                                } else {
                                                    val mappedError = when (result.statusCode) {
                                                        400 -> result.errorMessage ?: "Please check withdrawal details and try again."
                                                        401 -> "Session expired. Please sign in again."
                                                        403 -> result.errorMessage ?: "Withdrawals are currently disabled for this asset."
                                                        404 -> "Wallet or quote not found."
                                                        409 -> result.errorMessage ?: "A conflicting withdrawal request already exists."
                                                        422 -> result.errorMessage ?: "Unable to process this withdrawal right now."
                                                        429 -> "Too many requests. Please wait 30 seconds and try again."
                                                        else -> result.errorMessage ?: "Unable to initiate withdrawal."
                                                    }
                                                    formError = mappedError
                                                    withdrawViewModel.onWithdrawInitiationFailed(mappedError)
                                                }
                                            } finally {
                                                submitting = false
                                                holdProgress = 0f
                                            }
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
                        text = if (submitting) "Submitting..." else "Hold 3 seconds to Withdraw",
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
private fun WithdrawStatePanel(
    title: String,
    message: String,
    reference: String?,
    address: String?,
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
        if (!address.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Address: ${shortWithdrawAddress(address)}",
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

private fun String?.toWithdrawalStatusLabel(): String {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return "Pending"
    return value.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
    }
}
