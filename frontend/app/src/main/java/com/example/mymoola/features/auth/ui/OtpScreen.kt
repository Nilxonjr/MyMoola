package com.example.mymoola.features.auth.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mymoola.BackIconButton
import com.example.mymoola.features.auth.data.AuthApiClient
import com.example.mymoola.features.auth.data.AuthSession
import com.example.mymoola.ui.theme.MyMoolaTheme
import com.example.mymoola.ui.theme.myMoolaOutlinedTextFieldColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun maskPhoneNumber(phoneNumber: String): String {
    if (phoneNumber.length <= 7) return phoneNumber
    val prefix = phoneNumber.take(5)
    val suffix = phoneNumber.takeLast(2)
    val stars = "*".repeat((phoneNumber.length - prefix.length - suffix.length).coerceAtLeast(0))
    return "$prefix$stars$suffix"
}

@Composable
fun OtpScreen(
    phoneNumber: String,
    purpose: AuthApiClient.OtpPurpose,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onVerified: (AuthApiClient.AuthTokenResponse) -> Unit = {}
) {
    var otp by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableIntStateOf(30) }
    val scope = rememberCoroutineScope()

    val pageBackground = Color(0xFFF8FAFC)
    val panelBorder = Color(0xFFE2E8F0)
    val buttonShape = RoundedCornerShape(12.dp)

    LaunchedEffect(secondsRemaining) {
        if (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining -= 1
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackground)
            .statusBarsPadding()
    ) {
        Text(
            text = "MyMoola",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0F172A),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp)
                .padding(top = 44.dp)
        )

        Surface(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, panelBorder),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Verify Phone Number",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Enter the 6-digit code sent to ${maskPhoneNumber(phoneNumber)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF64748B)
                )

                OutlinedTextField(
                    value = otp,
                    onValueChange = {
                        otp = it.filter(Char::isDigit).take(6)
                        error = null
                        successMessage = null
                    },
                    label = { Text("6-digit OTP") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = myMoolaOutlinedTextFieldColors(unfocusedBorderColor = panelBorder)
                )

                Text(
                    text = if (secondsRemaining > 0) {
                        "Resend code in 00:${secondsRemaining.toString().padStart(2, '0')}"
                    } else {
                        "You can resend the code now."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isResending = true
                            error = null
                            successMessage = null

                            val result = AuthApiClient.resendOtp(
                                AuthApiClient.ResendOtpRequest(
                                    phoneNumber = phoneNumber,
                                    purpose = purpose
                                )
                            )

                            isResending = false
                            if (result.isSuccess) {
                                successMessage = result.data?.message ?: "OTP resent to your phone number."
                                secondsRemaining = 30
                            } else {
                                error = result.errorMessage ?: "Unable to resend OTP right now."
                            }
                        }
                    },
                    enabled = secondsRemaining == 0 && !isResending && !isVerifying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = buttonShape,
                    border = BorderStroke(1.dp, panelBorder)
                ) {
                    Text(if (isResending) "Resending..." else "Resend Code")
                }

                Button(
                    onClick = {
                        if (otp.length != 6) {
                            error = "OTP must be exactly 6 digits."
                            successMessage = null
                            return@Button
                        }

                        scope.launch {
                            isVerifying = true
                            error = null
                            successMessage = null

                            val result = AuthApiClient.verifyOtp(
                                AuthApiClient.VerifyOtpRequest(
                                    phoneNumber = phoneNumber,
                                    otp = otp,
                                    purpose = purpose
                                )
                            )

                            isVerifying = false
                            if (result.isSuccess) {
                                successMessage = "Phone verified successfully."
                                AuthSession.promotePendingPin()
                                result.data?.let(onVerified)
                            } else {
                                error = result.errorMessage ?: "OTP verification failed."
                            }
                        }
                    },
                    enabled = !isVerifying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = buttonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        if (isVerifying) "Verifying..." else "Verify",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (error != null) {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (successMessage != null) {
                    Text(
                        text = successMessage ?: "",
                        color = Color(0xFF166534),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }

        BackIconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 8.dp)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun OtpScreenPreview() {
    MyMoolaTheme {
        OtpScreen(
            phoneNumber = "+254712345678",
            purpose = AuthApiClient.OtpPurpose.Registration
        )
    }
}
