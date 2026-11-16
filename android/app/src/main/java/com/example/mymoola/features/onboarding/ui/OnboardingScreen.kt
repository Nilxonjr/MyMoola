package com.example.mymoola.features.onboarding.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mymoola.R
import com.example.mymoola.ui.theme.MyMoolaTheme

data class OnboardingFeature(
    val iconResId: Int,
    val text: String
)

private val previewFeatures = listOf(
    OnboardingFeature(R.drawable.onb_buy_mpesa, "Buy crypto using M-Pesa"),
    OnboardingFeature(R.drawable.onb_sell_kes, "Sell crypto back to KES"),
    OnboardingFeature(R.drawable.onb_wallet_manage, "Manage everything from one wallet")
)

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    title: String = "",
    subtitle: String = "",
    features: List<OnboardingFeature> = emptyList(),
    currentPage: Int = 0,
    totalPages: Int = 3,
    buttonText: String = "Continue",
    showBackButton: Boolean = false,
    showSignInPrompt: Boolean = false,
    onLoginClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onRegisterClick: () -> Unit = {}
) {
    val pageBackground = Color(0xFFF8FAFC)
    val panelBackground = Color.White
    val panelBorder = Color(0xFFE2E8F0)
    val accent = Color(0xFF0A7C6A)
    val buttonShape = RoundedCornerShape(12.dp)
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackground)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MyMoola",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
                modifier = Modifier.padding(top = 28.dp)
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(16.dp),
            color = panelBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, panelBorder),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(20.dp)
            ) {
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    features.forEach { feature ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, panelBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = feature.iconResId),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                Text(
                                    text = feature.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    for (index in 0 until totalPages) {
                        val isActive = index == currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (isActive) 10.dp else 8.dp)
                                .background(
                                    color = if (isActive) MaterialTheme.colorScheme.primary else Color(0xFFCBD5E1),
                                    shape = RoundedCornerShape(50)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (showBackButton) {
                        OutlinedButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = buttonShape,
                            border = BorderStroke(1.dp, panelBorder)
                        ) {
                            Text(text = "Back", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Button(
                        onClick = onRegisterClick,
                        modifier = Modifier
                            .height(50.dp)
                            .then(if (showBackButton) Modifier.weight(1f) else Modifier.fillMaxWidth()),
                        shape = buttonShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = buttonText, style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (showSignInPrompt) {
                    TextButton(
                        onClick = onLoginClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                append("Already have an account? ")
                                withStyle(style = androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                                    append("Sign in")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun OnboardingScreenPreview() {
    MyMoolaTheme {
        OnboardingScreen(
            title = "Welcome to MyMoola",
            subtitle = "Buy, sell, send, and spend crypto with M-Pesa support in Kenya.",
            features = previewFeatures
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, widthDp = 360, heightDp = 640)
@Composable
fun OnboardingScreenCompactPreview() {
    MyMoolaTheme {
        OnboardingScreen(
            title = "Welcome to MyMoola",
            subtitle = "Buy, sell, send, and spend crypto with M-Pesa support in Kenya.",
            features = previewFeatures,
            showSignInPrompt = true
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, widthDp = 700, heightDp = 900)
@Composable
fun OnboardingScreenTabletPreview() {
    MyMoolaTheme {
        OnboardingScreen(
            title = "Welcome to MyMoola",
            subtitle = "Buy, sell, send, and spend crypto with M-Pesa support in Kenya.",
            features = previewFeatures
        )
    }
}
