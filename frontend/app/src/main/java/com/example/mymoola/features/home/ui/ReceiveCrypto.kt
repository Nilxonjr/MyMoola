package com.example.mymoola.features.home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mymoola.BackIconButton
import com.example.mymoola.R
import com.example.mymoola.features.home.data.HomeApiClient
import com.example.mymoola.ui.theme.MyMoolaTheme

@Composable
fun ReceiveCryptoScreen(
    onBackClick: () -> Unit
) {
    val pageBackground = Color(0xFFF8FAFC)
    val panelBorder = Color(0xFFE2E8F0)
    val brandDark = Color(0xFF0F172A)
    val brandAccent = MaterialTheme.colorScheme.primary
    val mutedText = Color(0xFF64748B)
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var isLoading by remember { mutableStateOf(true) }
    var addressResponse by remember { mutableStateOf<HomeApiClient.DepositAddressResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var copiedMessage by remember { mutableStateOf<String?>(null) }
    var refreshNonce by remember { mutableIntStateOf(0) }

    suspend fun loadAddress() {
        isLoading = true
        copiedMessage = null
        val result = HomeApiClient.getDepositAddress()
        if (result.isSuccess) {
            addressResponse = result.data
            errorMessage = null
        } else {
            addressResponse = null
            errorMessage = result.errorMessage ?: "Unable to load wallet address."
        }
        isLoading = false
    }

    LaunchedEffect(refreshNonce) {
        loadAddress()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackIconButton(onClick = onBackClick)
            Text(
                text = "Receive Crypto",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = brandDark,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, panelBorder),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(brandAccent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.onb_receive_crypto),
                        contentDescription = "Receive crypto",
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Text(
                    text = "Get your wallet address and share it only with the sender. Assets sent to unsupported networks may be lost.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = mutedText
                )

                when {
                    isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = brandAccent
                            )
                            Text(
                                text = "Loading wallet address...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = brandDark
                            )
                        }
                    }

                    !errorMessage.isNullOrBlank() -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = { refreshNonce += 1 },
                                colors = ButtonDefaults.buttonColors(containerColor = brandAccent)
                            ) {
                                Text("Try Again")
                            }
                        }
                    }

                    addressResponse != null -> {
                        val response = addressResponse!!
                        InfoRow(label = "Chain", value = response.chain)
                        InfoRow(label = "Network", value = response.network)
                        InfoRow(
                            label = "Supported assets",
                            value = response.supportedAssets.joinToString(", ").ifBlank { "None provided" }
                        )

                        Text(
                            text = "Wallet address",
                            style = MaterialTheme.typography.labelLarge,
                            color = mutedText
                        )
                        Text(
                            text = response.address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = brandDark,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, panelBorder, RoundedCornerShape(14.dp))
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        )

                        if (!copiedMessage.isNullOrBlank()) {
                            Text(
                                text = copiedMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = brandAccent
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(response.address))
                                    copiedMessage = "Wallet address copied."
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = brandAccent)
                            ) {
                                Text("Copy Address")
                            }

                            Button(
                                onClick = { refreshNonce += 1 },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2E8F0), contentColor = brandDark)
                            ) {
                                Text("Refresh Address")
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(1.dp, panelBorder)
                ) {
                    Text(
                        text = "Only share this address for the shown network.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF64748B)
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF0F172A)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReceiveCryptoScreenPreview() {
    MyMoolaTheme {
        ReceiveCryptoScreen(onBackClick = {})
    }
}
