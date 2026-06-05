package com.example.mymoola.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mymoola.BackIconButton
import com.example.mymoola.R
import com.example.mymoola.features.auth.data.AuthSession
import com.example.mymoola.features.home.data.HomeApiClient
import com.example.mymoola.ui.theme.MyMoolaTheme
import com.example.mymoola.ui.theme.myMoolaOutlinedTextFieldColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

private const val KenyaPrefix = "+254"
private val SendCurrencyOrder = listOf("BTC", "ETH", "USDC")
private const val SendMpesaPlatformFeeRate = 0.015

private enum class SendMode(
    val label: String,
    val helper: String
) {
    Crypto(
        label = "Send Crypto",
        helper = "Transfer crypto directly to another registered user."
    ),
    Mpesa(
        label = "Send M-PESA",
        helper = "Convert your crypto and send M-PESA to any Kenyan number."
    )
}

private fun normalizeKenyanPhone(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    if (digits.isEmpty()) return ""
    val local = when {
        digits.startsWith("254") -> digits.drop(3)
        digits.startsWith("0") -> digits.drop(1)
        else -> digits
    }.take(9)
    return if (local.length == 9) "$KenyaPrefix$local" else ""
}

@Composable
fun SendToUserChoiceScreen(
    onBackClick: () -> Unit,
    onSendCryptoClick: () -> Unit,
    onSendMpesaClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            BackIconButton(onClick = onBackClick)
            Text(
                text = "Send To User",
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
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Choose Transfer Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A)
            )
            SendMode.entries.forEach { mode ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                        .clickable {
                            if (mode == SendMode.Crypto) onSendCryptoClick() else onSendMpesaClick()
                        }
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = mode.helper,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SendToUserScreen(
    initialMode: String = "crypto",
    allowModeSwitch: Boolean = true,
    onBackClick: () -> Unit,
    onGoHomeClick: () -> Unit = {}
) {
    data class CurrencyOption(
        val iconResName: String,
        val code: String,
        val label: String,
        val balanceText: String,
        val balanceAmount: Double
    )

    var phoneNumber by remember { mutableStateOf("") }
    val fallbackCurrencyOptions = listOf(
        CurrencyOption("bitcoin_logo", "BTC", "Bitcoin", "0.000000 BTC", 0.0),
        CurrencyOption("ethereum_logo", "ETH", "Ethereum", "0.000000 ETH", 0.0),
        CurrencyOption("usdc_logo", "USDC", "USD Coin", "0.000000 USDC", 0.0)
    )
    var currencyOptions by remember { mutableStateOf(fallbackCurrencyOptions) }
    val sendViewModel: SendToUserViewModel = viewModel()
    val sendUiState by sendViewModel.uiState.collectAsState()
    var selectedCurrency by remember { mutableStateOf<CurrencyOption?>(null) }
    var selectedMode by remember(initialMode) {
        mutableStateOf(if (initialMode.equals("mpesa", ignoreCase = true)) SendMode.Mpesa else SendMode.Crypto)
    }
    var amount by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var transferSuccessMessage by remember { mutableStateOf<String?>(null) }
    var transferSuccessSummary by remember { mutableStateOf<String?>(null) }
    var isLookingUp by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var myPhoneNumber by remember { mutableStateOf("") }
    var quote by remember { mutableStateOf<HomeApiClient.QuoteResponse?>(null) }
    var loadingQuote by remember { mutableStateOf(false) }
    var quoteError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val normalizedPhone = normalizeKenyanPhone(phoneNumber)
    val normalizedMyPhone = normalizeKenyanPhone(myPhoneNumber)
    val isSelfRecipient = normalizedPhone.isNotBlank() && normalizedPhone == normalizedMyPhone
    val parsedAmount = amount.toDoubleOrNull()
    val isRecipientVerified = !recipientName.isNullOrBlank()
    val requiresRecipientVerification = selectedMode == SendMode.Crypto
    val availableBalance = selectedCurrency?.balanceAmount ?: 0.0
    val sellRateKes = quote?.sellRateKes ?: 0.0
    val spreadPercent = quote?.spreadPercent ?: 0.0
    val spreadRate = spreadPercent / 100.0
    val mpesaAmountKes = if (selectedMode == SendMode.Mpesa) parsedAmount ?: 0.0 else 0.0
    val grossKesForMpesa = if (mpesaAmountKes > 0.0) {
        mpesaAmountKes / (1.0 - SendMpesaPlatformFeeRate - spreadRate)
    } else {
        0.0
    }
    val mpesaCryptoCost = if (sellRateKes > 0.0) grossKesForMpesa / sellRateKes else 0.0
    val availableBalanceKes = if (sellRateKes > 0.0) availableBalance * sellRateKes else 0.0
    val hasInsufficientBalance = when (selectedMode) {
        SendMode.Crypto -> parsedAmount != null && parsedAmount > availableBalance
        SendMode.Mpesa -> mpesaCryptoCost > availableBalance
    }
    val canSend = normalizedPhone.isNotBlank() &&
        !isSelfRecipient &&
        (!requiresRecipientVerification || isRecipientVerified) &&
        selectedCurrency != null &&
        parsedAmount != null &&
        parsedAmount > 0 &&
        !hasInsufficientBalance &&
        (selectedMode == SendMode.Crypto || quote != null) &&
        pin.length == 4 &&
        !isSending &&
        !isLookingUp
    val hasPendingLocator =
        !sendUiState.pendingTransactionId.isNullOrBlank() || !sendUiState.pendingReference.isNullOrBlank()
    val hasPendingSession =
        sendUiState.pendingStartedAtMs != null || hasPendingLocator
    val showPendingScreen = hasPendingSession &&
        (sendUiState.pendingStatus.equals("Pending", ignoreCase = true) ||
            sendUiState.pendingStatus.equals("Processing", ignoreCase = true))
    val showSuccessScreen =
        sendUiState.pendingStatus.equals("Completed", ignoreCase = true)
    val showFailedScreen =
        sendUiState.pendingStatus.equals("Failed", ignoreCase = true)
    val pendingIsCrypto = sendUiState.pendingMode == SendToUserViewModel.MODE_CRYPTO

    LaunchedEffect(Unit) {
        sendViewModel.startPollingIfNeeded()
        val me = HomeApiClient.getMe()
        if (me.isSuccess) {
            myPhoneNumber = me.data?.phone.orEmpty()
        }

        val balance = HomeApiClient.getBalance()
        if (balance.isSuccess) {
            val wallets = balance.data?.wallets.orEmpty()
            if (wallets.isNotEmpty()) {
                val walletByCode = wallets.associateBy { it.currency.uppercase() }
                val mapped = SendCurrencyOrder.mapNotNull { code ->
                    val wallet = walletByCode[code] ?: return@mapNotNull null
                    val icon = when (wallet.currency.uppercase()) {
                        "BTC" -> "bitcoin_logo"
                        "ETH" -> "ethereum_logo"
                        "USDC" -> "usdc_logo"
                        else -> "onb_wallet_manage"
                    }
                    val label = when (wallet.currency.uppercase()) {
                        "BTC" -> "Bitcoin"
                        "ETH" -> "Ethereum"
                        "USDC" -> "USD Coin"
                        else -> wallet.currency
                    }
                    CurrencyOption(
                        iconResName = icon,
                        code = wallet.currency.uppercase(),
                        label = label,
                        balanceText = String.format("%.6f %s", wallet.total, wallet.currency.uppercase()),
                        balanceAmount = wallet.total
                    )
                }
                currencyOptions = mapped
                if (selectedCurrency == null) {
                    selectedCurrency = mapped.firstOrNull { it.balanceAmount > 0.0 } ?: mapped.first()
                }
            }
        }
    }

    LaunchedEffect(selectedMode, selectedCurrency?.code) {
        if (selectedMode != SendMode.Mpesa || selectedCurrency == null) {
            quote = null
            quoteError = null
            loadingQuote = false
            return@LaunchedEffect
        }

        loadingQuote = true
        quoteError = null
        val result = HomeApiClient.getQuote(selectedCurrency!!.code)
        loadingQuote = false
        if (result.isSuccess) {
            quote = result.data
        } else {
            quote = null
            quoteError = result.errorMessage ?: "Unable to load conversion quote."
        }
    }

    LaunchedEffect(showPendingScreen, showSuccessScreen, showFailedScreen) {
        if (showPendingScreen || showSuccessScreen || showFailedScreen) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

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
                text = if (selectedMode == SendMode.Crypto) "Send Crypto" else "Send M-PESA",
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
                    text = "Send Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A)
                )

                if (allowModeSwitch) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SendMode.entries.forEach { mode ->
                            val isSelected = selectedMode == mode
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
                                    .clickable {
                                        selectedMode = mode
                                        errorMessage = null
                                        transferSuccessMessage = null
                                        transferSuccessSummary = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = mode.helper,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) Color(0xFFE2E8F0) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    currencyOptions.forEach { option ->
                        val isSelected = selectedCurrency?.code == option.code
                        val iconResId = remember(option.iconResName) {
                            context.resources.getIdentifier(
                                option.iconResName,
                                "drawable",
                                context.packageName
                            ).takeIf { it != 0 } ?: R.drawable.onb_wallet_manage
                        }
                        Row(
                            modifier = Modifier
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .border(
                                    width = if (isSelected) 0.dp else 1.dp,
                                    color = Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .clickable {
                                    selectedCurrency = option
                                    transferSuccessMessage = null
                                    transferSuccessSummary = null
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Image(
                                painter = painterResource(id = iconResId),
                                contentDescription = "${option.code} logo",
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(16.dp),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                text = option.code,
                                color = if (isSelected) Color.White else Color(0xFF0F172A)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { input ->
                        phoneNumber = input
                        recipientName = null
                        infoMessage = null
                        errorMessage = null
                        transferSuccessMessage = null
                        transferSuccessSummary = null
                    },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    placeholder = { Text("e.g. 0712345678 or +254712345678") },
                    isError = phoneNumber.isNotEmpty() && normalizedPhone.isBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = myMoolaOutlinedTextFieldColors()
                )
                if (phoneNumber.isNotEmpty() && normalizedPhone.isBlank()) {
                    Text(
                        text = "Phone number must be a valid Kenyan number.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFDC2626)
                    )
                }
                if (isSelfRecipient) {
                    Text(
                        text = "You cannot verify or send to your own phone number.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFDC2626)
                    )
                }

                if (requiresRecipientVerification) {
                    Button(
                        onClick = {
                            if (normalizedPhone.isBlank()) return@Button
                            if (isSelfRecipient) {
                                errorMessage = "You cannot verify your own number as recipient."
                                return@Button
                            }
                            isLookingUp = true
                            errorMessage = null
                            infoMessage = null
                            recipientName = null

                            coroutineScope.launch {
                                val result = HomeApiClient.lookupUserByPhone(normalizedPhone)
                                isLookingUp = false
                                if (result.isSuccess) {
                                    val found = result.data
                                    recipientName = found?.fullName
                                    infoMessage = if (found != null) {
                                        "Recipient found: ${found.fullName} (${found.phoneNumber})"
                                    } else {
                                        "Recipient found."
                                    }
                                } else {
                                    errorMessage = result.errorMessage ?: "Recipient lookup failed."
                                }
                            }
                        },
                        enabled = normalizedPhone.isNotBlank() && !isSelfRecipient && !isLookingUp && !isSending,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        if (isLookingUp) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(18.dp)
                                    .width(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("Verify Recipient")
                        }
                    }
                }

                if (requiresRecipientVerification && !recipientName.isNullOrBlank()) {
                    Text(
                        text = "Sending to: $recipientName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF0A7C6A)
                    )
                } else if (requiresRecipientVerification && normalizedPhone.isNotBlank() && !isLookingUp && !isSelfRecipient) {
                    Text(
                        text = "Verify the recipient before sending.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                } else if (!requiresRecipientVerification && normalizedPhone.isNotBlank() && !isSelfRecipient) {
                    Text(
                        text = "M-PESA will be sent to $normalizedPhone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                }

                if (selectedMode == SendMode.Mpesa) {
                    if (loadingQuote && quote == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(18.dp)
                                    .width(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Loading conversion quote...", color = Color(0xFF334155))
                        }
                    }

                    if (!quoteError.isNullOrBlank()) {
                        Text(
                            text = quoteError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFDC2626)
                        )
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it.filter { char -> char.isDigit() || char == '.' }
                    },
                    label = {
                        Text(
                            if (selectedMode == SendMode.Crypto) {
                                "Amount (${selectedCurrency?.code ?: ""})"
                            } else {
                                "Amount (KES)"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("e.g. 100") },
                    shape = RoundedCornerShape(12.dp),
                    colors = myMoolaOutlinedTextFieldColors()
                )

                selectedCurrency?.let { selected ->
                    Text(
                        text = "Available balance: ${selected.balanceText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF334155)
                    )
                }
                if (selectedMode == SendMode.Mpesa) {
                    Text(
                        text = "Approx KES value: ${String.format(Locale.US, "%,.2f", availableBalanceKes)} KES",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF334155)
                    )
                }
                if (selectedMode == SendMode.Mpesa && quote != null) {
                    Text(
                        text = "Approx crypto cost: ${String.format(Locale.US, "%.6f", mpesaCryptoCost)} ${selectedCurrency?.code.orEmpty()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF334155)
                    )
                    Text(
                        text = "Rate: ${String.format(Locale.US, "%.2f", sellRateKes)} KES, fee: ${String.format(Locale.US, "%,.2f", grossKesForMpesa * SendMpesaPlatformFeeRate)} KES",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                }
                if (hasInsufficientBalance) {
                    Text(
                        text = if (selectedMode == SendMode.Crypto) "Insufficient balance." else "Insufficient balance for this M-PESA amount.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB91C1C)
                    )
                }

                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        pin = input.filter { it.isDigit() }.take(4)
                        errorMessage = null
                    },
                    label = { Text("PIN (4 digits)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter 4-digit PIN") },
                    isError = pin.isNotEmpty() && pin.length < 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = myMoolaOutlinedTextFieldColors()
                )
                if (pin.isNotEmpty() && pin.length < 4) {
                    Text(
                        text = "PIN must be exactly 4 digits",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFDC2626)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = if (selectedMode == SendMode.Crypto) {
                            "Note: Sending only works to users who are also registered with the system."
                        } else {
                            "Note: M-PESA sending uses the entered phone number and funds the payout from your selected crypto wallet."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF475569)
                    )
                }

                if (!infoMessage.isNullOrBlank()) {
                    Text(
                        text = infoMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF0F766E)
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFDC2626)
                    )
                }

                val holdEnabledColor = if (canSend) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .background(holdEnabledColor, RoundedCornerShape(12.dp))
                        .pointerInput(canSend, normalizedPhone, parsedAmount, pin, selectedCurrency?.code, recipientName, selectedMode, quote?.quoteId) {
                            detectTapGestures(
                                onPress = {
                                    if (!canSend) return@detectTapGestures

                                    errorMessage = null
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

                                            val currency = selectedCurrency?.code ?: return@launch
                                            val sendAmount = parsedAmount ?: return@launch
                                            if (isSelfRecipient) {
                                                errorMessage = "You cannot send to your own phone number."
                                                return@launch
                                            }
                                            if (selectedMode == SendMode.Crypto && sendAmount > availableBalance) {
                                                errorMessage = "Insufficient balance."
                                                return@launch
                                            }
                                            if (selectedMode == SendMode.Mpesa && mpesaCryptoCost > availableBalance) {
                                                errorMessage = "Insufficient balance for this M-PESA amount."
                                                return@launch
                                            }
                                            if (requiresRecipientVerification && !isRecipientVerified) {
                                                errorMessage = "Verify the recipient before sending."
                                                return@launch
                                            }
                                            if (selectedMode == SendMode.Mpesa && quote == null) {
                                                errorMessage = "Conversion quote unavailable. Please try again."
                                                return@launch
                                            }
                                            val sessionPin = AuthSession.sessionPin
                                            if (sessionPin.isNullOrBlank()) {
                                                errorMessage = "Session PIN unavailable. Please log in again."
                                                return@launch
                                            }
                                            if (pin != sessionPin) {
                                                errorMessage = "Incorrect PIN. Enter your account PIN to continue."
                                                return@launch
                                            }

                                            isSending = true
                                            errorMessage = null
                                            infoMessage = null

                                            try {
                                                val sendSucceeded: Boolean
                                                val successMessage: String?
                                                val successReference: String?
                                                val successTransactionId: String?
                                                val failureMessage: String?

                                                if (selectedMode == SendMode.Crypto) {
                                                    val result = HomeApiClient.sendToUser(
                                                        HomeApiClient.SendToUserRequest(
                                                            recipientPhone = normalizedPhone,
                                                            currency = currency,
                                                            amount = sendAmount,
                                                            pin = pin
                                                        )
                                                    )
                                                    sendSucceeded = result.isSuccess
                                                    successMessage = result.data?.message
                                                    successReference = result.data?.referenceCode
                                                    successTransactionId = result.data?.transactionId
                                                    failureMessage = result.errorMessage
                                                } else {
                                                    val result = HomeApiClient.payMerchant(
                                                        request = HomeApiClient.PayMerchantRequest(
                                                            merchantType = "SendMoney",
                                                            currency = currency,
                                                            amountKes = sendAmount,
                                                            quoteId = quote?.quoteId.orEmpty(),
                                                            pin = pin,
                                                            phoneNumber = normalizedPhone.removePrefix("+")
                                                        ),
                                                        idempotencyKey = java.util.UUID.randomUUID().toString()
                                                    )
                                                    sendSucceeded = result.isSuccess
                                                    successMessage = result.data?.message
                                                    successReference = result.data?.referenceCode
                                                    successTransactionId = result.data?.transactionId
                                                    failureMessage = result.errorMessage
                                                }

                                                if (sendSucceeded) {
                                                    if (selectedMode == SendMode.Crypto) {
                                                        sendViewModel.onSendCompleted(
                                                            mode = SendToUserViewModel.MODE_CRYPTO,
                                                            referenceCode = successReference,
                                                            message = successMessage ?: "Transfer completed successfully."
                                                        )
                                                    } else {
                                                        sendViewModel.onSendInitiated(
                                                            mode = SendToUserViewModel.MODE_MPESA,
                                                            transactionId = successTransactionId,
                                                            referenceCode = successReference,
                                                            message = successMessage ?: "M-PESA transfer request sent. Waiting for confirmation."
                                                        )
                                                    }
                                                    amount = ""
                                                    pin = ""
                                                    infoMessage = null
                                                } else {
                                                    errorMessage = failureMessage ?: if (selectedMode == SendMode.Crypto) "Transfer failed." else "M-PESA transfer failed."
                                                    sendViewModel.onSendInitiationFailed(errorMessage.orEmpty())
                                                }
                                            } finally {
                                                isSending = false
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
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .height(18.dp)
                                .width(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = if (!requiresRecipientVerification || isRecipientVerified) {
                                if (selectedMode == SendMode.Crypto) "Hold 3 seconds to Send" else "Hold 3 seconds to Send M-PESA"
                            } else {
                                "Verify Recipient First"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                if (holdProgress > 0f) {
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

                if (showPendingScreen) {
                    SendStatePanel(
                        title = if (pendingIsCrypto) "Transfer Pending" else "M-PESA Pending",
                        message = sendUiState.pendingMessage ?: if (pendingIsCrypto) {
                            "Your transfer is being processed."
                        } else {
                            "Your M-PESA transfer is being processed."
                        },
                        reference = sendUiState.pendingReference,
                        statusLine = "Status: ${sendUiState.pendingStatus ?: "Pending"} (auto-checking)",
                        actionLabel = "Refresh now",
                        onAction = { sendViewModel.refreshNow() }
                    )
                }

                if (showSuccessScreen) {
                    SendStatePanel(
                        title = if (pendingIsCrypto) "Transfer Successful" else "M-PESA Successful",
                        message = sendUiState.finalOutcome ?: if (pendingIsCrypto) {
                            "Your transfer is complete."
                        } else {
                            "Your M-PESA transfer is complete."
                        },
                        reference = sendUiState.pendingReference,
                        statusLine = "Status: Completed",
                        statusColor = Color(0xFF166534),
                        actionLabel = "Back to Home",
                        onAction = {
                            sendViewModel.clearTerminalOutcome()
                            onGoHomeClick()
                        }
                    )
                }

                if (showFailedScreen) {
                    SendStatePanel(
                        title = if (pendingIsCrypto) "Transfer Failed" else "M-PESA Failed",
                        message = sendUiState.finalOutcome ?: if (pendingIsCrypto) {
                            "Your transfer did not complete."
                        } else {
                            "Your M-PESA transfer did not complete."
                        },
                        reference = sendUiState.pendingReference,
                        statusLine = "Status: Failed",
                        statusColor = Color(0xFFB91C1C),
                        actionLabel = "Try Again",
                        onAction = { sendViewModel.clearTerminalOutcome() }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SendToUserScreenPreview() {
    MyMoolaTheme {
        SendToUserScreen(onBackClick = {})
    }
}

@Composable
private fun SendStatePanel(
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
