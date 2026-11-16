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
import com.example.mymoola.normalizeKenyanPhone
import com.example.mymoola.features.home.data.HomeApiClient
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

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
    val quoteState = rememberTransactionQuoteState(selectedCurrency)
    var selectedMerchantType by rememberSaveable { mutableStateOf(MerchantType.Paybill) }
    var amountKesInput by rememberSaveable { mutableStateOf("") }
    var paybillNumber by rememberSaveable { mutableStateOf("") }
    var accountNumber by rememberSaveable { mutableStateOf("") }
    var tillNumber by rememberSaveable { mutableStateOf("") }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
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

    LaunchedEffect(Unit) {
        payViewModel.startPollingIfNeeded()
    }

    val selectedWallet = currencies.firstOrNull { it.code == selectedCurrency }
    val amountKes = amountKesInput.toDoubleOrNull() ?: 0.0
    val sellRateKes = quoteState.quote?.sellRateKes ?: 0.0
    val spreadPercent = quoteState.quote?.spreadPercent ?: 0.0
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
    val normalizedPhone = normalizeKenyanPhone(phoneNumber).removePrefix("+")
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
        quoteState.quote != null &&
        hasSufficientBalance &&
        merchantValidationError == null &&
        !payUiState.isSubmitting &&
        payUiState.pendingTransactionId.isNullOrBlank()

    val hasPendingLocator =
        !payUiState.pendingTransactionId.isNullOrBlank() || !payUiState.pendingReference.isNullOrBlank()
    val showPendingScreen = hasPendingLocator &&
        !payUiState.pendingStatus.isCompletedMerchantPaymentStatus() &&
        !payUiState.pendingStatus.isFailedMerchantPaymentStatus()
    val showSuccessScreen =
        !hasPendingLocator && payUiState.pendingStatus.isCompletedMerchantPaymentStatus()
    val showFailedScreen =
        !hasPendingLocator && payUiState.pendingStatus.isFailedMerchantPaymentStatus()

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
                            text = "Merchant conversion rate: ${String.format(Locale.US, "%.2f", sellRateKes)} KES",
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
                    enabled = !payUiState.isSubmitting && payUiState.pendingTransactionId.isNullOrBlank()
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

                if (!payUiState.submissionError.isNullOrBlank()) {
                    Text(
                        text = payUiState.submissionError ?: "",
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
                            quoteState.quote?.quoteId,
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

                                    payViewModel.clearSubmissionError()
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
                                            payViewModel.submitPayment(
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
                                                refreshQuoteIfExpired = {
                                                    quoteState.refreshIfExpired(
                                                        currency = selectedCurrency,
                                                        successPrompt = "Rate updated. Please review the new estimate and hold Pay again."
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
                        text = if (payUiState.isSubmitting) "Submitting..." else "Hold 3 seconds to Pay",
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
