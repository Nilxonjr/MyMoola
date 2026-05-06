package com.example.mymoola.features.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mymoola.R
import com.example.mymoola.ui.theme.MyMoolaTheme

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
    val amountColor: Color
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {}
) {
    val pageBackground = Color(0xFFF8FAFC)
    val panelBorder = Color(0xFFE2E8F0)
    val brandDark = Color(0xFF0F172A)
    val brandAccent = Color(0xFF0A7C6A)
    val mutedText = Color(0xFF64748B)
    val panelBackground = Color.White
    val placeholderUserName = "Austin"

    val quickActions = listOf(
        HomeAction("onb_buy_mpesa", "B", "Buy"),
        HomeAction("onb_sell_kes", "S", "Sell"),
        HomeAction("onb_pay_till", "P", "Pay with MPESA"),
        HomeAction("onb_send_crypto", "M", "Send with MPESA"),
        HomeAction("onb_paybill", "W", "Withdraw"),
        HomeAction("onb_payment_records", "V", "View Records")
    )
    val activities = listOf(
        HomeActivity("Buy", "completed", "+254712345678 • 11:12", "+$1,000.00", Color(0xFF10B981)),
        HomeActivity("Payment", "completed", "Coffee Shop • 11:12", "-$25.50", Color(0xFFEF4444)),
        HomeActivity("Send", "completed", "0x83...8fd2 • 09:44", "-$120.00", Color(0xFFEF4444))
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackground)
            .statusBarsPadding()
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
                    text = "Welcome, $placeholderUserName",
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
                        Text(
                            text = "TOTAL BALANCE",
                            style = MaterialTheme.typography.labelLarge,
                            color = mutedText
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "KSH 2,500.50",
                            style = MaterialTheme.typography.headlineLarge,
                            color = brandDark,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "19.36 USDC",
                            style = MaterialTheme.typography.bodyMedium,
                            color = mutedText
                        )
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
                                        modifier = Modifier.size(width = 94.dp, height = 94.dp)
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
                                                val context = LocalContext.current
                                                val iconResId = remember(action.iconResName) {
                                                    context.resources.getIdentifier(
                                                        action.iconResName,
                                                        "drawable",
                                                        context.packageName
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
                    Text(
                        text = "VIEW ALL >",
                        style = MaterialTheme.typography.labelMedium,
                        color = mutedText
                    )
                }
            }

            items(activities) { activity ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = panelBackground),
                    border = BorderStroke(1.dp, panelBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(activity.type, color = Color(0xFF1E293B), fontWeight = FontWeight.Medium)
                                Text(
                                    activity.status,
                                    color = brandAccent,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                activity.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = mutedText
                            )
                        }
                        Text(
                            text = activity.amount,
                            color = activity.amountColor,
                            style = MaterialTheme.typography.titleSmall,
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
fun HomeScreenPreview() {
    MyMoolaTheme {
        HomeScreen()
    }
}
