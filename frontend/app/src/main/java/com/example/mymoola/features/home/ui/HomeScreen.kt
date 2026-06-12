package com.example.mymoola.features.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mymoola.R
import com.example.mymoola.features.auth.data.AuthApiClient
import com.example.mymoola.features.auth.data.AuthSession
import com.example.mymoola.features.home.data.HomeApiClient
import com.example.mymoola.ui.theme.MyMoolaTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeAction(
    val iconResName: String,
    val fallbackIcon: String,
    val label: String
)

data class HomeActivity(
    val type: String,
    val status: String,
    val detail: String,
    val amount: String,
    val amountColor: Color,
    val marketRateSnapshot: Double?,
    val onChainTxHash: String?,
    val onChainConfirmations: Int,
    val mpesaReference: String?
)

data class BalanceCurrency(
    val iconResName: String,
    val code: String,
    val label: String,
    val balance: String
)

private fun merchantPaymentLabel(merchantType: String?): String? = when (merchantType?.trim()?.lowercase(Locale.US)) {
    "paybill" -> "Paybill"
    "till" -> "Till"
    "pochi" -> "Pochi"
    "sendmoney" -> "Send M-PESA"
    else -> null
}

@OptIn(ExperimentalMaterialApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    refreshNonce: Long = 0L,
    onSettingsClick: () -> Unit = {},
    onBuyClick: () -> Unit = {},
    onSellClick: () -> Unit = {},
    onWithdrawClick: () -> Unit = {},
    onReceiveCryptoClick: () -> Unit = {},
    onPayWithMpesaClick: () -> Unit = {},
    onSendToUserClick: () -> Unit = {},
    onViewRecordsClick: () -> Unit = {},
    onViewRatesClick: () -> Unit = {},
    onActivityClick: (HomeActivity) -> Unit = {}
) {
    val pageBackground = Color(0xFFF8FAFC)
    val panelBorder = Color(0xFFE2E8F0)
    val brandDark = Color(0xFF0F172A)
    val brandAccent = Color(0xFF0A7C6A)
    val mutedText = Color(0xFF64748B)
    val panelBackground = Color.White
    val context = LocalContext.current

    var userName by remember { mutableStateOf("User") }
    var currentUserId by remember { mutableStateOf("") }
    var totalBalanceText by remember { mutableStateOf("KES 0.00") }
    var loadError by remember { mutableStateOf<String?>(null) }
    var balanceCurrencies by remember {
        mutableStateOf(
            listOf(
                BalanceCurrency("usdc_logo", "USDC", "USD Coin", "0.00 USDC"),
                BalanceCurrency("bitcoin_logo", "BTC", "Bitcoin", "0.00 BTC"),
                BalanceCurrency("ethereum_logo", "ETH", "Ethereum", "0.00 ETH")
            )
        )
    }
    var selectedCurrency by remember { mutableStateOf(balanceCurrencies.first()) }
    var balanceMenuExpanded by remember { mutableStateOf(false) }
    var activities by remember { mutableStateOf<List<HomeActivity>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun applyBalance(balance: HomeApiClient.BalanceResponse?) {
        if (balance == null) return
        totalBalanceText = "${balance.displayCurrency} ${String.format(Locale.US, "%,.2f", balance.totalFiatEquivalent)}"
        val preferredCurrencyCode = balance.wallets
            .firstOrNull { it.total > 0.0 }
            ?.currency

        val wallets = balance.wallets.map { wallet ->
            val icon = when (wallet.currency.uppercase(Locale.US)) {
                "BTC" -> "bitcoin_logo"
                "ETH" -> "ethereum_logo"
                "USDC" -> "usdc_logo"
                else -> "onb_wallet_manage"
            }
            BalanceCurrency(
                iconResName = icon,
                code = wallet.currency,
                label = wallet.currency,
                balance = "${formatMeaningfulAmount(wallet.total)} ${wallet.currency}"
            )
        }
        if (wallets.isNotEmpty()) {
            balanceCurrencies = wallets
            selectedCurrency = wallets.firstOrNull { it.code == preferredCurrencyCode } ?: wallets.first()
        }
    }

    fun applyTransactions(all: List<HomeApiClient.UserTransaction>, userId: String) {
        if (userId.isBlank()) return
        val txs = all
            .filter { tx ->
                tx.initiatorUserId.equals(userId, ignoreCase = true) ||
                    tx.counterpartyUserId.equals(userId, ignoreCase = true)
            }
            .groupBy { it.id }
            .map { (_, group) ->
                group.firstOrNull { it.counterpartyUserId.equals(userId, ignoreCase = true) }
                    ?: group.firstOrNull { it.initiatorUserId.equals(userId, ignoreCase = true) }
                    ?: group.first()
            }
            .sortedByDescending { it.createdAt }

        activities = txs.map { tx ->
            val isSendType = tx.type.equals("Send", ignoreCase = true)
            val isInitiator = tx.initiatorUserId.equals(userId, ignoreCase = true)
            val isReceiver = tx.counterpartyUserId.equals(userId, ignoreCase = true)

            val displayType = when {
                isSendType && isInitiator -> "Send"
                isSendType && isReceiver -> "Receive"
                tx.type.equals("MerchantPayment", ignoreCase = true) ->
                    merchantPaymentLabel(tx.merchantType) ?: "Merchant Payment"
                else -> tx.type.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
                }
            }

            val isCredit = when {
                isSendType && isReceiver -> true
                isSendType && isInitiator -> false
                else -> tx.type.uppercase(Locale.US) in setOf("BUY", "DEPOSIT", "RECEIVE")
            }

            val isFailed = tx.status.equals("Failed", ignoreCase = true)
            val amountColor = when {
                isFailed -> Color(0xFFDC2626)
                isCredit -> Color(0xFF10B981)
                else -> Color(0xFFEF4444)
            }
            val amountPrefix = if (isCredit) "+" else "-"
            HomeActivity(
                type = displayType,
                status = tx.status.lowercase(Locale.US),
                detail = buildString {
                    append(tx.currency)
                    append(" • ")
                    append(formatHomeTime(tx.createdAt))
                    tx.interactedPhone?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }?.let {
                        append(" • ")
                        append(it)
                    }
                    append(" • ")
                    append(tx.referenceCode)
                },
                amount = "$amountPrefix${formatMeaningfulAmount(tx.amount)} ${tx.currency}",
                amountColor = amountColor,
                marketRateSnapshot = tx.marketRateSnapshot,
                onChainTxHash = tx.onChainTxHash,
                onChainConfirmations = tx.onChainConfirmations,
                mpesaReference = tx.mpesaReference
            )
        }
    }

    fun applyCachedHomeData() {
        HomeApiClient.getCachedMe()?.let {
            userName = it.fullName.ifBlank { "User" }
            currentUserId = it.id
        }
        applyBalance(HomeApiClient.getCachedBalance())
        HomeApiClient.getCachedTransactions()?.let { cachedTxs ->
            applyTransactions(cachedTxs, currentUserId)
        }
    }

    suspend fun reloadHomeData() {
        if (AuthSession.accessToken.isNullOrBlank()) {
            val refreshed = AuthApiClient.refreshSession()
            if (!refreshed) {
                loadError = "Session missing. Please log in again."
                return
            }
        }

        loadError = null

        coroutineScope {
            val meDeferred = async { HomeApiClient.getMe() }
            val balanceDeferred = async { HomeApiClient.getBalance() }
            val txDeferred = async { HomeApiClient.getAllTransactions() }

            launch {
                val balanceResult = balanceDeferred.await()
                if (balanceResult.isSuccess) {
                    applyBalance(balanceResult.data)
                } else {
                    loadError = balanceResult.errorMessage
                }
            }

            val meResult = meDeferred.await()
            if (meResult.isSuccess) {
                userName = meResult.data?.fullName?.ifBlank { "User" } ?: "User"
                currentUserId = meResult.data?.id.orEmpty()
            } else {
                loadError = meResult.errorMessage
            }

            val transactionsResult = txDeferred.await()
            if (transactionsResult.isSuccess) {
                applyTransactions(transactionsResult.data.orEmpty(), currentUserId)
            } else {
                loadError = transactionsResult.errorMessage ?: loadError
            }
        }
    }

    LaunchedEffect(Unit) {
        applyCachedHomeData()
        reloadHomeData()
    }

    LaunchedEffect(refreshNonce) {
        if (refreshNonce != 0L) {
            reloadHomeData()
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                reloadHomeData()
                isRefreshing = false
            }
        }
    )

    val quickActions = listOf(
        HomeAction("onb_buy_mpesa", "B", "Buy Crypto"),
        HomeAction("onb_sell_kes", "S", "Sell Crypto"),
        HomeAction("onb_send_crypto", "W", "Withdraw Crypto"),
        HomeAction("onb_receive_crypto", "W", "Receive Crypto"),
        HomeAction("onb_pay_till", "P", "Pay with MPESA"),
        HomeAction("onb_send_crypto", "M", "Send to Other Users"),
        HomeAction("onb_payment_records", "V", "View Records"),
        HomeAction("onb_view_rates", "R", "View Rates")
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackground)
            .statusBarsPadding()
            .pullRefresh(pullRefreshState)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.mymoola),
                            contentDescription = "MyMoola app icon",
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Column {
                            Text(
                                text = "MyMoola",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = brandDark
                            )
                            Text(
                                text = "Wallet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = mutedText
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, panelBorder)
                    ) {
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.onb_wallet_manage),
                                    contentDescription = "Settings",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Welcome, $userName",
                    style = MaterialTheme.typography.titleMedium,
                    color = brandDark,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, panelBorder),
                    color = panelBackground,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL BALANCE",
                                style = MaterialTheme.typography.labelLarge,
                                color = mutedText
                            )

                            ExposedDropdownMenuBox(
                                expanded = balanceMenuExpanded,
                                onExpandedChange = { balanceMenuExpanded = !balanceMenuExpanded }
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .menuAnchor(
                                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                            enabled = true
                                        )
                                        .clickable { balanceMenuExpanded = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, panelBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val selectedIconResId = remember(selectedCurrency.iconResName) {
                                            context.resources.getIdentifier(
                                                selectedCurrency.iconResName,
                                                "drawable",
                                                context.packageName
                                            )
                                        }
                                        Image(
                                            painter = painterResource(
                                                id = selectedIconResId.takeIf { it != 0 } ?: R.drawable.onb_wallet_manage
                                            ),
                                            contentDescription = "${selectedCurrency.code} logo",
                                            modifier = Modifier.size(16.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                        Text(
                                            text = selectedCurrency.code,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = brandDark
                                        )
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = balanceMenuExpanded)
                                    }
                                }

                                ExposedDropdownMenu(
                                    expanded = balanceMenuExpanded,
                                    onDismissRequest = { balanceMenuExpanded = false }
                                ) {
                                    balanceCurrencies.forEach { option ->
                                        val optionIconResId = remember(option.iconResName) {
                                            context.resources.getIdentifier(
                                                option.iconResName,
                                                "drawable",
                                                context.packageName
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Image(
                                                        painter = painterResource(
                                                            id = optionIconResId.takeIf { it != 0 } ?: R.drawable.onb_wallet_manage
                                                        ),
                                                        contentDescription = "${option.code} logo",
                                                        modifier = Modifier.size(16.dp),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                    Text("${option.code} - ${option.label}")
                                                }
                                            },
                                            onClick = {
                                                selectedCurrency = option
                                                balanceMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = totalBalanceText,
                            style = MaterialTheme.typography.headlineLarge,
                            color = brandDark,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = selectedCurrency.balance,
                            style = MaterialTheme.typography.bodyMedium,
                            color = mutedText
                        )
                        if (!loadError.isNullOrBlank()) {
                            Text(
                                text = loadError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, panelBorder),
                    color = panelBackground,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        quickActions.chunked(3).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                rowItems.forEach { action ->
                                    val actionClick: () -> Unit = when (action.label) {
                                        "Buy Crypto" -> onBuyClick
                                        "Sell Crypto" -> onSellClick
                                        "Withdraw Crypto" -> onWithdrawClick
                                        "Receive Crypto" -> onReceiveCryptoClick
                                        "Pay with MPESA" -> onPayWithMpesaClick
                                        "Send to Other Users" -> onSendToUserClick
                                        "View Records" -> onViewRecordsClick
                                        "View Rates" -> onViewRatesClick
                                        else -> ({})
                                    }
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                        border = BorderStroke(1.dp, panelBorder),
                                        modifier = Modifier
                                            .size(width = 94.dp, height = 94.dp)
                                            .clickable(onClick = actionClick)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(vertical = 10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Top
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .background(
                                                        color = brandAccent.copy(alpha = 0.12f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val localContext = LocalContext.current
                                                val iconResId = remember(action.iconResName) {
                                                    localContext.resources.getIdentifier(
                                                        action.iconResName,
                                                        "drawable",
                                                        localContext.packageName
                                                    )
                                                }
                                                if (iconResId != 0) {
                                                    Image(
                                                        painter = painterResource(id = iconResId),
                                                        contentDescription = "${action.label} icon",
                                                        modifier = Modifier.size(18.dp),
                                                        contentScale = ContentScale.Fit
                                                    )
                                                } else {
                                                    Text(
                                                        text = action.fallbackIcon,
                                                        color = brandAccent,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = action.label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color(0xFF1E293B),
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth()
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

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium,
                        color = brandDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (activities.isEmpty()) {
                item {
                    Text(
                        text = "No transactions yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedText
                    )
                }
            }

            items(activities.take(3)) { activity ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = panelBackground),
                    border = BorderStroke(1.dp, panelBorder),
                    modifier = Modifier.clickable { onActivityClick(activity) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    activity.type,
                                    color = Color(0xFF1E293B),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    activity.status,
                                    color = if (activity.status.equals("failed", ignoreCase = true)) Color(0xFFDC2626) else brandAccent,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                activity.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = mutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            activity.onChainTxHash
                                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                                ?.let { txHash ->
                                    Text(
                                        text = "Hash: $txHash",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = mutedText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = activity.amount,
                            modifier = Modifier.width(120.dp),
                            textAlign = TextAlign.End,
                            color = activity.amountColor,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

private fun formatHomeTime(raw: String): String {
    val parsers = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    )
    val outputFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)
    for (parser in parsers) {
        val parsed = runCatching { parser.parse(raw) }.getOrNull()
        if (parsed != null) {
            return outputFormat.format(parsed)
        }
    }
    return raw
}

private fun formatMeaningfulAmount(amount: Double): String {
    val formatter = DecimalFormat("#,##0.######", DecimalFormatSymbols(Locale.US))
    return formatter.format(amount)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    MyMoolaTheme {
        HomeScreen()
    }
}
