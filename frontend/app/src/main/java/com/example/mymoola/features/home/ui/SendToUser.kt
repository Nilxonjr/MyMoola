package com.example.mymoola.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mymoola.BackIconButton
import com.example.mymoola.R
import com.example.mymoola.features.home.data.HomeApiClient
import com.example.mymoola.ui.theme.MyMoolaTheme
import com.example.mymoola.ui.theme.myMoolaOutlinedTextFieldColors
import kotlinx.coroutines.launch

private const val KenyaPrefix = "+254"

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendToUserScreen(
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
        CurrencyOption("usdc_logo", "USDC", "USD Coin", "0.000000 USDC", 0.0),
        CurrencyOption("bitcoin_logo", "BTC", "Bitcoin", "0.000000 BTC", 0.0),
        CurrencyOption("ethereum_logo", "ETH", "Ethereum", "0.000000 ETH", 0.0)
    )
    var currencyOptions by remember { mutableStateOf(fallbackCurrencyOptions) }
    var selectedCurrency by remember { mutableStateOf<CurrencyOption?>(null) }
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var insufficientBalanceMessage by remember { mutableStateOf<String?>(null) }
    var transferSuccessMessage by remember { mutableStateOf<String?>(null) }
    var isLookingUp by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var myPhoneNumber by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val normalizedPhone = normalizeKenyanPhone(phoneNumber)
    val normalizedMyPhone = normalizeKenyanPhone(myPhoneNumber)
    val isSelfRecipient = normalizedPhone.isNotBlank() && normalizedPhone == normalizedMyPhone
    val parsedAmount = amount.toDoubleOrNull()
    val canSend = normalizedPhone.isNotBlank() &&
        !isSelfRecipient &&
        selectedCurrency != null &&
        parsedAmount != null &&
        parsedAmount > 0 &&
        pin.length == 4 &&
        !isSending &&
        !isLookingUp

    LaunchedEffect(Unit) {
        val me = HomeApiClient.getMe()
        if (me.isSuccess) {
            myPhoneNumber = me.data?.phone.orEmpty()
        }

        val balance = HomeApiClient.getBalance()
        if (balance.isSuccess) {
            val wallets = balance.data?.wallets.orEmpty()
            if (wallets.isNotEmpty()) {
                val mapped = wallets.map { wallet ->
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
                        code = wallet.currency,
                        label = label,
                        balanceText = String.format("%.6f %s", wallet.total, wallet.currency),
                        balanceAmount = wallet.total
                    )
                }
                currencyOptions = mapped
                if (selectedCurrency == null) {
                    selectedCurrency = mapped.firstOrNull { option ->
                        wallets.firstOrNull { it.currency.equals(option.code, ignoreCase = true) }?.total?.let { it > 0.0 } == true
                    } ?: mapped.first()
                }
            }
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
                text = "Send To User",
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
                .border(
                    width = 1.dp,
                    color = Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(16.dp)
                )
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

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Phone Number",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF334155)
                    )
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { input ->
                            phoneNumber = input
                            recipientName = null
                            infoMessage = null
                            errorMessage = null
                            insufficientBalanceMessage = null
                            transferSuccessMessage = null
                        },
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
                            containerColor = Color(0xFF0F172A),
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

                    if (!recipientName.isNullOrBlank()) {
                        Text(
                            text = "Sending to: $recipientName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF0A7C6A)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Currency",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF334155)
                    )
                    ExposedDropdownMenuBox(
                        expanded = currencyMenuExpanded,
                        onExpandedChange = { currencyMenuExpanded = !currencyMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCurrency?.let { "${it.code} - ${it.label}" } ?: "",
                            onValueChange = {},
                            singleLine = true,
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(
                                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                    enabled = true
                                ),
                            placeholder = { Text("Choose currency") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyMenuExpanded)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = myMoolaOutlinedTextFieldColors(
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC)
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = currencyMenuExpanded,
                            onDismissRequest = { currencyMenuExpanded = false },
                            modifier = Modifier
                                .exposedDropdownSize()
                                .background(
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(12.dp)
                                )
                        ) {
                            currencyOptions.forEach { option ->
                                DropdownMenuItem(
                                    colors = MenuDefaults.itemColors(
                                        textColor = Color(0xFF0F172A)
                                    ),
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Image(
                                                    painter = painterResource(
                                                        id = context.resources.getIdentifier(
                                                            option.iconResName,
                                                            "drawable",
                                                            context.packageName
                                                        ).takeIf { it != 0 } ?: R.drawable.onb_wallet_manage
                                                    ),
                                                    contentDescription = "${option.code} icon placeholder",
                                                    modifier = Modifier
                                                        .width(18.dp)
                                                        .height(18.dp),
                                                    contentScale = ContentScale.Fit
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(text = "${option.code} - ${option.label}")
                                            }
                                            Text(
                                                text = option.balanceText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedCurrency = option
                                        currencyMenuExpanded = false
                                        insufficientBalanceMessage = null
                                    }
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Amount",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF334155)
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = {
                            amount = it
                            insufficientBalanceMessage = null
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        placeholder = { Text("e.g. 100") },
                        shape = RoundedCornerShape(12.dp),
                        colors = myMoolaOutlinedTextFieldColors()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PIN",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF334155)
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { input ->
                            pin = input.filter { it.isDigit() }.take(4)
                            errorMessage = null
                        },
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
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Note: Sending only works to users who are also registered with the system.",
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

                if (!transferSuccessMessage.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = Color(0xFFA7F3D0),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(Color(0xFFECFDF5), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = transferSuccessMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF065F46)
                            )
                            Button(
                                onClick = onGoHomeClick,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0A7C6A),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Back to Home")
                            }
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFDC2626)
                    )
                }

                if (!insufficientBalanceMessage.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = Color(0xFFFCA5A5),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(Color(0xFFFEF2F2), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = insufficientBalanceMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF991B1B)
                        )
                    }
                }

                Button(
                    onClick = {
                        val currency = selectedCurrency?.code ?: return@Button
                        val sendAmount = parsedAmount ?: return@Button
                        val availableBalance = selectedCurrency?.balanceAmount ?: 0.0
                        if (isSelfRecipient) {
                            errorMessage = "You cannot send to your own phone number."
                            return@Button
                        }
                        if (sendAmount > availableBalance) {
                            insufficientBalanceMessage =
                                "Insufficient balance. Available: ${selectedCurrency?.balanceText.orEmpty()}."
                            return@Button
                        }

                        isSending = true
                        errorMessage = null
                        infoMessage = null
                        insufficientBalanceMessage = null
                        transferSuccessMessage = null

                        coroutineScope.launch {
                            val result = HomeApiClient.sendToUser(
                                HomeApiClient.SendToUserRequest(
                                    recipientPhone = normalizedPhone,
                                    currency = currency,
                                    amount = sendAmount,
                                    pin = pin
                                )
                            )
                            isSending = false

                            if (result.isSuccess) {
                                val response = result.data
                                transferSuccessMessage = if (response != null) {
                                    "${response.message} Ref: ${response.referenceCode}"
                                } else {
                                    "Transfer completed successfully."
                                }
                                amount = ""
                                pin = ""
                            } else {
                                errorMessage = result.errorMessage ?: "Transfer failed."
                            }
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A7C6A),
                        contentColor = Color.White
                    )
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
                            text = "Send",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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
