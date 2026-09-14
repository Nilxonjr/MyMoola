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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import com.example.mymoola.R
import java.util.Locale

data class HomeAction(
    val onClick: () -> Unit,
    val iconResId: Int,
    val label: String
)

data class HomeActivity(
    val type: String,
    val status: String,
    val detail: String,
    val amount: String,
    val amountColor: Color,
    val receiverName: String?,
    val marketRateSnapshot: Double?,
    val onChainTxHash: String?,
    val onChainConfirmations: Int,
    val mpesaReference: String?
)

data class BalanceCurrency(
    val iconResId: Int,
    val code: String,
    val label: String,
    val balance: String
)

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
    val homeViewModel: HomeViewModel = viewModel()
    val uiState by homeViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var selectedCurrency by remember { mutableStateOf(uiState.balanceCurrencies.firstOrNull()) }
    var balanceMenuExpanded by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, homeViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.onPullRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(refreshNonce) {
        if (refreshNonce != 0L) {
            homeViewModel.onPullRefresh()
        }
    }

    LaunchedEffect(uiState.balanceCurrencies) {
        val currencies = uiState.balanceCurrencies
        if (currencies.isEmpty()) {
            selectedCurrency = null
            return@LaunchedEffect
        }
        val currentCode = selectedCurrency?.code
        selectedCurrency = when {
            uiState.preferredCurrencyCode != null ->
                currencies.firstOrNull { it.code.equals(uiState.preferredCurrencyCode, ignoreCase = true) }
                    ?: currencies.firstOrNull { it.code == currentCode }
                    ?: currencies.first()
            else ->
                currencies.firstOrNull { it.code == currentCode } ?: currencies.first()
        }
    }

    LaunchedEffect(uiState.walletCreditMessage) {
        val message = uiState.walletCreditMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        homeViewModel.onWalletCreditMessageShown()
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh = {
            homeViewModel.onPullRefresh()
        }
    )

    val quickActions = listOf(
        HomeAction(onBuyClick, R.drawable.onb_buy_mpesa, "Buy Crypto"),
        HomeAction(onSellClick, R.drawable.onb_sell_kes, "Sell Crypto"),
        HomeAction(onWithdrawClick, R.drawable.onb_send_crypto, "Withdraw Crypto"),
        HomeAction(onReceiveCryptoClick, R.drawable.onb_receive_crypto, "Receive Crypto"),
        HomeAction(onPayWithMpesaClick, R.drawable.onb_pay_till, "Pay with MPESA"),
        HomeAction(onSendToUserClick, R.drawable.onb_send_crypto, "Send to Other Users"),
        HomeAction(onViewRecordsClick, R.drawable.onb_payment_records, "View Records"),
        HomeAction(onViewRatesClick, R.drawable.onb_view_rates, "View Rates")
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
                    text = "Welcome, ${uiState.userName}",
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
                                        val selected = selectedCurrency
                                        Image(
                                            painter = painterResource(
                                                id = selected?.iconResId ?: R.drawable.onb_wallet_manage
                                            ),
                                            contentDescription = "${selected?.code ?: "Wallet"} logo",
                                            modifier = Modifier.size(16.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                        Text(
                                            text = selected?.code ?: "Wallet",
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
                                    uiState.balanceCurrencies.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Image(
                                                        painter = painterResource(
                                                            id = option.iconResId
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
                            text = if (uiState.hasLoadedBalance) uiState.totalBalanceText else "Loading balance...",
                            style = MaterialTheme.typography.headlineLarge,
                            color = brandDark,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                selectedCurrency != null -> selectedCurrency?.balance.orEmpty()
                                uiState.hasLoadedBalance -> "No wallet balances yet."
                                else -> "Fetching wallets..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = mutedText
                        )
                        if (!uiState.balanceError.isNullOrBlank()) {
                            Text(
                                text = uiState.balanceError.orEmpty(),
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
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                        border = BorderStroke(1.dp, panelBorder),
                                        modifier = Modifier
                                            .size(width = 94.dp, height = 94.dp)
                                            .clickable(onClick = action.onClick)
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
                                                Image(
                                                    painter = painterResource(id = action.iconResId),
                                                    contentDescription = "${action.label} icon",
                                                    modifier = Modifier.size(18.dp),
                                                    contentScale = ContentScale.Fit
                                                )
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

            if (!uiState.hasLoadedActivities) {
                item {
                    Text(
                        text = "Loading activity...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedText
                    )
                }
            } else if (uiState.activities.isEmpty()) {
                item {
                    Text(
                        text = "No transactions yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedText
                    )
                }
            }
            if (!uiState.activitiesError.isNullOrBlank()) {
                item {
                    Text(
                        text = uiState.activitiesError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            items(uiState.activities.take(3)) { activity ->
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
            refreshing = uiState.isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen()
}
