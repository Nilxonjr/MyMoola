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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mymoola.BackIconButton
import com.example.mymoola.R
import com.example.mymoola.ui.theme.MyMoolaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendToUserScreen(
    onBackClick: () -> Unit
) {
    data class CurrencyOption(val iconResName: String, val code: String, val label: String)

    var phoneNumber by remember { mutableStateOf("") }
    val currencyOptions = listOf(
        CurrencyOption("usdc_logo", "USDC", "USD Coin"),
        CurrencyOption("bitcoin_logo", "BTC", "Bitcoin"),
        CurrencyOption("ethereum_logo", "ETH", "Ethereum")
    )
    var selectedCurrency by remember { mutableStateOf<CurrencyOption?>(null) }
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    val context = LocalContext.current

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
                            phoneNumber = input.filter { it.isDigit() }.take(9)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        prefix = { Text("+254") },
                        placeholder = { Text("e.g. 712345678") },
                        isError = phoneNumber.isNotEmpty() && phoneNumber.length < 9,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedBorderColor = Color(0xFF0A7C6A)
                        )
                    )
                    if (phoneNumber.isNotEmpty() && phoneNumber.length < 9) {
                        Text(
                            text = "Phone number must be exactly 9 digits",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFDC2626)
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
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                focusedBorderColor = Color(0xFF0A7C6A),
                                focusedTextColor = Color(0xFF0F172A),
                                unfocusedTextColor = Color(0xFF0F172A),
                                focusedTrailingIconColor = Color(0xFF0A7C6A),
                                unfocusedTrailingIconColor = Color(0xFF64748B)
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
                                    },
                                    onClick = {
                                        selectedCurrency = option
                                        currencyMenuExpanded = false
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
                        onValueChange = { amount = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. 2500") },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedBorderColor = Color(0xFF0A7C6A)
                        )
                    )
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

                Button(
                    onClick = {},
                    enabled = phoneNumber.length == 9 && selectedCurrency != null && amount.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A7C6A),
                        contentColor = Color.White
                    )
                ) {
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

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SendToUserScreenPreview() {
    MyMoolaTheme {
        SendToUserScreen(onBackClick = {})
    }
}
