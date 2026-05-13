package com.example.mymoola.features.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mymoola.BackIconButton
import com.example.mymoola.ui.theme.MyMoolaTheme

private data class SettingsRow(
    val title: String,
    val value: String? = null
)

private data class SettingsSection(
    val title: String,
    val rows: List<SettingsRow>,
    val showTitle: Boolean = true
)

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onProfileSummaryClick: () -> Unit = {},
    onChangePinClick: () -> Unit = {},
    onBiometricLoginClick: () -> Unit = {},
    onDefaultCurrencyClick: () -> Unit = {},
    onTransactionNotificationsClick: () -> Unit = {},
    onHelpSupportClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {}
) {
    val pageBackground = Color(0xFFF8FAFC)
    val panelBorder = Color(0xFFE2E8F0)
    val panelBackground = Color.White
    val titleColor = Color(0xFF0F172A)
    val mutedText = Color(0xFF64748B)

    val sections = listOf(
        SettingsSection(
            title = "Account",
            rows = listOf(
                SettingsRow("Profile Summary")
            )
        ),
        SettingsSection(
            title = "Security",
            rows = listOf(
                SettingsRow("Change PIN"),
                SettingsRow("Biometric Login", "Off")
            )
        ),
        SettingsSection(
            title = "Preferences",
            rows = listOf(
                SettingsRow("Default Currency", "KES"),
                SettingsRow("Transaction Notifications", "On")
            )
        ),
        SettingsSection(
            title = "Support",
            rows = listOf(
                SettingsRow("Help & Support")
            )
        ),
        SettingsSection(
            title = "Session",
            rows = listOf(
                SettingsRow("Log out")
            ),
            showTitle = false
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            BackIconButton(onClick = onBackClick)
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sections) { section ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (section.showTitle) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = titleColor
                        )
                    }

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = panelBackground),
                        border = BorderStroke(1.dp, panelBorder)
                    ) {
                        section.rows.forEachIndexed { index, row ->
                            val isLogoutRow = row.title == "Log out"
                            val rowClick: () -> Unit = when (row.title) {
                                "Profile Summary" -> onProfileSummaryClick
                                "Change PIN" -> onChangePinClick
                                "Biometric Login" -> onBiometricLoginClick
                                "Default Currency" -> onDefaultCurrencyClick
                                "Transaction Notifications" -> onTransactionNotificationsClick
                                "Help & Support" -> onHelpSupportClick
                                "Log out" -> onLogoutClick
                                else -> ({})
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = rowClick)
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                horizontalArrangement = if (isLogoutRow) Arrangement.Center else Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = row.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isLogoutRow) Color(0xFFDC2626) else titleColor
                                )
                                if (!isLogoutRow) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (!row.value.isNullOrBlank()) {
                                            Text(
                                                text = row.value,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = mutedText
                                            )
                                        }
                                        Text(
                                            text = ">",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = mutedText
                                        )
                                    }
                                }
                            }

                            if (index != section.rows.lastIndex) {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(panelBorder)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SettingsScreenPreview() {
    MyMoolaTheme {
        SettingsScreen(onBackClick = {})
    }
}
