package com.example.mymoola.features.home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.rememberScrollState
import androidx.lifecycle.viewmodel.compose.viewModel
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

private const val MerchantPlatformFeeRate = 0.015

private enum class MerchantType(
    val wireValue: String,
    val label: String,
    val helper: String
) {
    Paybill(
        wireValue = "Paybill",
        label = "Paybill",
        helper = "Use a business paybill and account number."
    ),
    Till(
        wireValue = "Till",
        label = "Till",
        helper = "Pay a till number directly."
    ),
    Pochi(
        wireValue = "Pochi",
        label = "Pochi",
        helper = "Pay a Pochi la Biashara phone number."
    )
}

private data class CurrencyOption(
    val code: String,
    val balanceAmount: Double,
    val iconResName: String
)

private val MerchantCurrencyOrder = listOf("BTC", "ETH", "USDC")

@Composable
fun PayWithMpesaScreen(
    onBackClick: () -> Unit,
    onDoneClick: () -> Unit = onBackClick
) {
    val localContext = LocalContext.current
    val payViewModel: PayWithMpesaViewModel = viewModel()
    val payUiState by payViewModel.uiState.collectAsState()

    val fallbackCurrencies = listOf(
        CurrencyOption("BTC", 0.0, "bitcoin_logo"),
        CurrencyOption("ETH", 0.0, "ethereum_logo"),
        CurrencyOption("USDC", 0.0, "usdc_logo")
    )
    var currencies by remember { mutableStateOf(fallbackCurrencies) }
    var selectedCurrency by rememberSaveable { mutableStateOf("BTC") }
    var selectedMerchantType by rememberSaveable { mutableStateOf(MerchantType.Paybill) }
    var amountKesInput by rememberSaveable { mutableStateOf("") }
    var paybillNumber by rememberSaveable { mutableStateOf("") }
    var accountNumber by rememberSaveable { mutableStateOf("") }
    var tillNumber by rememberSaveable { mutableStateOf("") }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var quote by remember { mutableStateOf<HomeApiClient.QuoteResponse?>(null) }
    var loadingQuote by remember { mutableStateOf(false) }
    var quoteError by remember { mutableStateOf<String?>(null) }
    var quoteRefreshPrompt by remember { mutableStateOf<String?>(null) }
    var refreshSecondsRemaining by remember { mutableLongStateOf(25L) }
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

    fun normalizePhone(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        return when {
            digits.startsWith("254") && digits.length == 12 -> digits
            digits.startsWith("0") && digits.length == 10 -> "254${digits.drop(1)}"
            digits.startsWith("7") && digits.length == 9 -> "254$digits"
            else -> digits
        }
    }

    LaunchedEffect(Unit) {
        val balance = HomeApiClient.getBalance()
        if (balance.isSuccess) {
            val mappedByCode = balance.data?.wallets
                .orEmpty()
                .filter { wallet -> wallet.currency.uppercase() in setOf("BTC", "ETH", "USDC") }
                .associateBy { it.currency.uppercase() }
            val mapped = MerchantCurrencyOrder.mapNotNull { code ->
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

    LaunchedEffect(Unit) {
        payViewModel.startPollingIfNeeded()
    }

    val selectedWallet = currencies.firstOrNull { it.code == selectedCurrency }
    val amountKes = amountKesInput.toDoubleOrNull() ?: 0.0
    val sellRateKes = quote?.sellRateKes ?: 0.0
    val spreadPercent = quote?.spreadPercent ?: 0.0
    val spreadRate = spreadPercent / 100.0
    val grossKes = if (amountKes > 0.0) {
        amountKes / (1.0 - MerchantPlatformFeeRate - spreadRate)
    } else {
        0.0
    }
    val platformFeeKes = grossKes * MerchantPlatformFeeRate
    val spreadKes = grossKes * spreadRate
    val cryptoCost = if (sellRateKes > 0.0) grossKes / sellRateKes else 0.0
    val availableBalance = selectedWallet?.balanceAmount ?: 0.0
    val availableBalanceKes = if (sellRateKes > 0.0) availableBalance * sellRateKes else 0.0
    val hasSufficientBalance = cryptoCost > 0.0 && cryptoCost <= availableBalance
    val normalizedPhone = normalizePhone(phoneNumber)
    val scrollState = rememberScrollState()

    val merchantValidationError = when (selectedMerchantType) {
        MerchantType.Paybill -> when {
            paybillNumber.isBlank() -> "Enter a paybill number."
            accountNumber.isBlank() -> "Enter the paybill account number."
            else -> null
        }
        MerchantType.Till -> if (tillNumber.isBlank()) "Enter a till number." else null
                    MerchantType.Pochi -> when {
                        normalizedPhone.isBlank() -> "Enter a phone number."
                        !normalizedPhone.matches(Regex("^2547\\d{8}$")) -> "Phone number must be in format 2547XXXXXXXX."
                        else -> null
                    }
    }

    val canSubmit = amountKes > 0.0 &&
        pin.length == 4 &&
        quote != null &&
        hasSufficientBalance &&
        merchantValidationError == null &&
        !submitting &&
        payUiState.pendingTransactionId.isNullOrBlank()

    val hasPendingLocator =
        !payUiState.pendingTransactionId.isNullOrBlank() || !payUiState.pendingReference.isNullOrBlank()
    val showPendingScreen = hasPendingLocator &&
        (payUiState.pendingStatus.equals("Pending", ignoreCase = true) ||
            payUiState.pendingStatus.equals("Processing", ignoreCase = true))
    val showSuccessScreen =
        payUiState.pendingStatus.equals("Completed", ignoreCase = true)
    val showFailedScreen =
        payUiState.pendingStatus.equals("Failed", ignoreCase = true)

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
                text = "Pay With M-PESA",
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
                    text = "Payment Details",
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
                            text = "Merchant conversion rate: ${String.format(Locale.US, "%.2f", sellRateKes)} KES",
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
                    Text(
                        text = quoteError ?: "",
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

                Text(
                    text = "Payment Type",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Medium
                )

                MerchantType.entries.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { merchantType ->
                            val isSelected = merchantType == selectedMerchantType
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFF8FAFC),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedMerchantType = merchantType }
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = merchantType.label,
                                        color = if (isSelected) Color.White else Color(0xFF0F172A),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = merchantType.helper,
                                        color = if (isSelected) Color(0xFFE2E8F0) else Color(0xFF64748B),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = amountKesInput,
                    onValueChange = { input ->
                        amountKesInput = input.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text("Merchant amount (KES)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                when (selectedMerchantType) {
                    MerchantType.Paybill -> {
                        OutlinedTextField(
                            value = paybillNumber,
                            onValueChange = { paybillNumber = it.filter(Char::isDigit).take(7) },
                            label = { Text("Paybill Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = accountNumber,
                            onValueChange = { accountNumber = it.take(20) },
                            label = { Text("Account Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    MerchantType.Till -> {
                        OutlinedTextField(
                            value = tillNumber,
                            onValueChange = { tillNumber = it.filter(Char::isDigit).take(7) },
                            label = { Text("Till Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    MerchantType.Pochi -> {
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it.filter { ch -> ch.isDigit() || ch == '+' }.take(13) },
                            label = { Text("Pochi Phone Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                    }
                }

                Text(
                    text = "Available: ${String.format(Locale.US, "%.6f", availableBalance)} $selectedCurrency",
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Approx KES value: ${String.format(Locale.US, "%,.2f", availableBalanceKes)} KES",
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Merchant receives: ${String.format(Locale.US, "%,.2f", amountKes)} KES",
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Platform fee: ${String.format(Locale.US, "%,.2f", platformFeeKes)} KES (1.5%)",
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Spread: ${String.format(Locale.US, "%,.2f", spreadKes)} KES",
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Estimated crypto cost: ${String.format(Locale.US, "%.6f", cryptoCost)} $selectedCurrency",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Medium
                )

                if (showPendingScreen) {
                    MerchantStatePanel(
                        title = "Payment Pending",
                        message = payUiState.pendingMessage ?: "Merchant payment is being processed.",
                        reference = payUiState.pendingReference,
                        statusLine = "Status: ${payUiState.pendingStatus ?: "Pending"} (auto-checking)",
                        actionLabel = "Refresh now",
                        onAction = { payViewModel.refreshNow() }
                    )
                    return@Column
                }

                if (showSuccessScreen) {
                    MerchantStatePanel(
                        title = "Payment Successful",
                        message = payUiState.finalOutcome ?: "Merchant payment completed.",
                        reference = payUiState.pendingReference,
                        statusLine = "Status: Completed",
                        statusColor = Color(0xFF166534),
                        actionLabel = "Done",
                        onAction = {
                            payViewModel.clearTerminalOutcome()
                            onDoneClick()
                        }
                    )
                    return@Column
                }

                if (showFailedScreen) {
                    MerchantStatePanel(
                        title = "Payment Failed",
                        message = payUiState.finalOutcome ?: "Merchant payment did not complete.",
                        reference = payUiState.pendingReference,
                        statusLine = "Status: Failed",
                        statusColor = Color(0xFFB91C1C),
                        actionLabel = "Try Again",
                        onAction = { payViewModel.clearTerminalOutcome() }
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
                    enabled = !submitting && payUiState.pendingTransactionId.isNullOrBlank()
                )

                if (merchantValidationError != null) {
                    Text(
                        text = merchantValidationError,
                        color = Color(0xFFB91C1C),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (cryptoCost > availableBalance && availableBalance > 0.0) {
                    Text(
                        text = "Insufficient $selectedCurrency balance.",
                        color = Color(0xFFB91C1C),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (!formError.isNullOrBlank()) {
                    Text(
                        text = formError ?: "",
                        color = Color(0xFFB91C1C),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                val holdEnabledColor = if (canSubmit) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(holdEnabledColor, RoundedCornerShape(12.dp))
                        .pointerInput(
                            canSubmit,
                            amountKes,
                            pin,
                            quote?.quoteId,
                            selectedCurrency,
                            selectedMerchantType,
                            paybillNumber,
                            accountNumber,
                            tillNumber,
                            normalizedPhone
                        ) {
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
                                                        quoteRefreshPrompt = "Rate updated. Please review the new estimate and hold Pay again."
                                                        formError = null
                                                    } else {
                                                        formError = refreshed.errorMessage ?: "Quote expired. Unable to refresh rate right now."
                                                    }
                                                    return@launch
                                                }

                                                val key = activeAttemptKey ?: UUID.randomUUID().toString().also { activeAttemptKey = it }
                                                val result = try {
                                                    withTimeout(20_000) {
                                                        HomeApiClient.payMerchant(
                                                            request = HomeApiClient.PayMerchantRequest(
                                                                merchantType = selectedMerchantType.wireValue,
                                                                currency = selectedCurrency,
                                                                amountKes = amountKes,
                                                                quoteId = activeQuote.quoteId,
                                                                pin = pin,
                                                                paybillNumber = paybillNumber.ifBlank { null },
                                                                accountNumber = accountNumber.ifBlank { null },
                                                                tillNumber = tillNumber.ifBlank { null },
                                                                phoneNumber = normalizedPhone.ifBlank { null }
                                                            ),
                                                            idempotencyKey = key
                                                        )
                                                    }
                                                } catch (_: Exception) {
                                                    formError = null
                                                    quoteRefreshPrompt = null
                                                    payViewModel.onPaymentInitiated(
                                                        transactionId = null,
                                                        referenceCode = null,
                                                        message = "Payment request sent. Waiting for merchant confirmation."
                                                    )
                                                    return@launch
                                                }

                                                if (result.isSuccess) {
                                                    payViewModel.onPaymentInitiated(
                                                        transactionId = result.data?.transactionId,
                                                        referenceCode = result.data?.referenceCode,
                                                        message = result.data?.message
                                                    )
                                                    quoteRefreshPrompt = null
                                                    formError = null
                                                } else {
                                                    val mappedError = when (result.statusCode) {
                                                        400 -> result.errorMessage ?: "Please check your payment details and try again."
                                                        401 -> "Session expired. Please sign in again."
                                                        403 -> result.errorMessage ?: "This operation is currently disabled for your account."
                                                        404 -> "User or wallet not found."
                                                        409 -> result.errorMessage ?: "A conflicting merchant payment request already exists."
                                                        422 -> result.errorMessage ?: "Unable to process this merchant payment right now."
                                                        429 -> "Too many requests. Please wait 30 seconds and try again."
                                                        else -> result.errorMessage ?: "Unable to initiate merchant payment."
                                                    }
                                                    formError = mappedError
                                                    payViewModel.onPaymentInitiationFailed(mappedError)
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
                        text = if (submitting) "Submitting..." else "Hold 3 seconds to Pay",
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

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun MerchantStatePanel(
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
