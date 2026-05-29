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
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.mymoola.features.auth.data.AuthSession
import com.example.mymoola.features.home.data.HomeApiClient
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

@Composable
fun BuyCryptoScreen(
    onBackClick: () -> Unit,
    onDoneClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val localContext = LocalContext.current
    val buyViewModel: BuyViewModel = viewModel()
    val buyUiState by buyViewModel.uiState.collectAsState()
    val supportedCurrencies = listOf("BTC", "ETH", "USDC")
    var selectedCurrency by rememberSaveable { mutableStateOf("BTC") }
    var amountInput by rememberSaveable { mutableStateOf("1000") }
    var quote by remember { mutableStateOf<HomeApiClient.QuoteResponse?>(null) }
    var loadingQuote by remember { mutableStateOf(false) }
    var quoteError by remember { mutableStateOf<String?>(null) }
    var quoteRefreshPrompt by remember { mutableStateOf<String?>(null) }
    var refreshSecondsRemaining by remember { mutableLongStateOf(25L) }
    var pin by rememberSaveable { mutableStateOf("") }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var submitting by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var activeAttemptKey by rememberSaveable { mutableStateOf<String?>(null) }

    fun parseExpiryMillis(value: String): Long {
        if (value.isBlank()) return 0L

        return runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .recoverCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .getOrDefault(0L)
    }

    LaunchedEffect(selectedCurrency) {
        loadingQuote = true
        quoteError = null
        val result = HomeApiClient.getQuote(selectedCurrency)
        loadingQuote = false
        if (result.isSuccess) {
            quote = result.data
        } else {
            quote = null
            quoteError = result.errorMessage ?: "Unable to load quote."
        }
    }

    LaunchedEffect(selectedCurrency, quote?.quoteId, quote != null) {
        if (quote == null) {
            refreshSecondsRemaining = 25L
            return@LaunchedEffect
        }

        var remaining = 25L
        refreshSecondsRemaining = remaining
        while (true) {
            delay(1000)
            remaining -= 1
            refreshSecondsRemaining = remaining.coerceAtLeast(0L)
            if (remaining <= 0L) {
                val result = HomeApiClient.getQuote(selectedCurrency)
                if (result.isSuccess) {
                    quote = result.data
                    quoteError = null
                } else if (quote == null) {
                    quoteError = result.errorMessage ?: "Unable to refresh quote."
                }
                remaining = 25L
                refreshSecondsRemaining = remaining
            }
        }
    }

    val grossKes = amountInput.toDoubleOrNull() ?: 0.0
    val platformFee = grossKes * 0.015
    val netKes = (grossKes - platformFee).coerceAtLeast(0.0)
    val buyRateKes = quote?.buyRateKes ?: 0.0
    val receiveAmount = if (buyRateKes > 0.0) netKes / buyRateKes else 0.0
    val canSubmit = grossKes > 0.0 &&
        pin.length == 4 &&
        quote != null &&
        !submitting &&
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
                            color = if (isSelected) Color(0xFF0F172A) else Color(0xFFE2E8F0),
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

        Spacer(modifier = Modifier.height(16.dp))

        if (loadingQuote && quote == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Loading quote...", color = Color(0xFF334155))
            }
        } else if (quote != null) {
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
                    text = "Quote refreshes in ${refreshSecondsRemaining}s",
                    color = if (refreshSecondsRemaining <= 5) Color(0xFFB91C1C) else Color(0xFF334155),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (!quoteError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = quoteError ?: "",
                color = Color(0xFFB91C1C),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (!quoteRefreshPrompt.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = quoteRefreshPrompt ?: "",
                color = Color(0xFF1D4ED8),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(16.dp))
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

        Spacer(modifier = Modifier.height(20.dp))

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
            enabled = !submitting && buyUiState.pendingTransactionId.isNullOrBlank()
        )

        if (!formError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formError ?: "",
                color = Color(0xFFB91C1C),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val holdEnabledColor = if (canSubmit) Color(0xFF0F172A) else Color(0xFF94A3B8)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(holdEnabledColor, RoundedCornerShape(12.dp))
                .pointerInput(canSubmit, grossKes, pin, quote?.quoteId, selectedCurrency) {
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
                                            formError = "Quote is unavailable. Please refresh and try again."
                                            return@launch
                                        }

                                        val expiresMs = parseExpiryMillis(activeQuote.expiresAt)
                                        if (expiresMs <= System.currentTimeMillis()) {
                                            val refreshed = HomeApiClient.getQuote(selectedCurrency)
                                            if (refreshed.isSuccess && refreshed.data != null) {
                                                quote = refreshed.data
                                                quoteRefreshPrompt = "Rate updated. Please review new price and hold Buy again."
                                                formError = null
                                            } else {
                                                formError = refreshed.errorMessage ?: "Quote expired. Unable to refresh rate right now."
                                            }
                                            return@launch
                                        }

                                        val key = activeAttemptKey ?: UUID.randomUUID().toString().also { activeAttemptKey = it }
                                        val result = try {
                                            withTimeout(20_000) {
                                                HomeApiClient.buyCrypto(
                                                    request = HomeApiClient.BuyCryptoRequest(
                                                        currency = selectedCurrency,
                                                        grossKes = grossKes,
                                                        quoteId = activeQuote.quoteId,
                                                        pin = pin
                                                    ),
                                                    idempotencyKey = key
                                                )
                                            }
                                        } catch (_: Exception) {
                                            formError = null
                                            quoteRefreshPrompt = null
                                            buyViewModel.onBuyInitiated(
                                                transactionId = null,
                                                referenceCode = null,
                                                message = "Payment request sent. Waiting for confirmation."
                                            )
                                            return@launch
                                        }

                                        if (result.isSuccess) {
                                            buyViewModel.onBuyInitiated(
                                                transactionId = result.data?.transactionId,
                                                referenceCode = result.data?.referenceCode,
                                                message = result.data?.message
                                            )
                                            quoteRefreshPrompt = null
                                            formError = null
                                        } else {
                                            val mappedError = when (result.statusCode) {
                                                400 -> result.errorMessage ?: "Please check your inputs and try again."
                                                401 -> "Session expired. Please sign in again."
                                                403 -> result.errorMessage ?: "This operation is currently disabled for your account."
                                                404 -> "User or wallet not found."
                                                422 -> "Quote expired. Fetching latest rate..."
                                                429 -> "Too many requests. Please wait 30 seconds and try again."
                                                else -> result.errorMessage ?: "Unable to initiate payment."
                                            }
                                            val error = mappedError
                                            formError = error
                                            buyViewModel.onBuyInitiationFailed(error)

                                            if (result.statusCode == 422) {
                                                val refreshed = HomeApiClient.getQuote(selectedCurrency)
                                                if (refreshed.isSuccess && refreshed.data != null) {
                                                    quote = refreshed.data
                                                    quoteRefreshPrompt = "Quote expired. New rate loaded. Review and hold Buy again."
                                                }
                                            }
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
                text = if (submitting) "Submitting..." else "Hold 3 seconds to Buy",
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
                        .background(Color(0xFF0F172A), RoundedCornerShape(999.dp))
                )
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
