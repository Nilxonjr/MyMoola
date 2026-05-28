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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mymoola.BackIconButton
import com.example.mymoola.features.auth.data.AuthApiClient
import com.example.mymoola.features.auth.data.AuthSession
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

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onSignUpClick: () -> Unit = {},
    onLoginSuccess: (String) -> Unit = {}
) {
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(emptyList<String>()) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val pageBackground = Color(0xFFF8FAFC)
    val panelBorder = Color(0xFFE2E8F0)
    val buttonShape = RoundedCornerShape(12.dp)

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
                    text = "Login",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Sign in with your phone number and 4-digit PIN.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF64748B)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        errors = emptyList()
                        successMessage = null
                    },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = myMoolaOutlinedTextFieldColors()
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter(Char::isDigit).take(4)
                        errors = emptyList()
                        successMessage = null
                    },
                    label = { Text("4-digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = myMoolaOutlinedTextFieldColors()
                )

                OutlinedButton(
                    onClick = { showPin = !showPin },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = buttonShape,
                    border = BorderStroke(1.dp, panelBorder)
                ) {
                    Text(if (showPin) "Hide PIN" else "Show PIN")
                }

                Button(
                    onClick = {
                        val normalizedPhone = normalizeKenyanPhone(phone)
                        val validationErrors = buildList {
                            if (normalizedPhone.isBlank()) add("Phone number must be in format +2547XXXXXXXX.")
                            if (pin.length != 4) add("PIN must be exactly 4 digits.")
                        }
                        errors = validationErrors
                        successMessage = null

                        if (validationErrors.isNotEmpty()) return@Button

                        scope.launch {
                            isSubmitting = true
                            val result = AuthApiClient.login(
                                AuthApiClient.LoginRequest(
                                    phoneNumber = normalizedPhone,
                                    pin = pin
                                )
                            )
                            isSubmitting = false

                            if (result.isSuccess) {
                                successMessage = result.data?.message ?: "OTP sent to your phone number."
                                AuthSession.setPendingPin(pin)
                                onLoginSuccess(normalizedPhone)
                            } else {
                                errors = listOf(result.errorMessage ?: "Login failed.")
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = buttonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0F172A),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        if (isSubmitting) "Logging in..." else "Login",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                errors.forEach { error ->
                    Text(
                        text = "• $error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (successMessage != null) {
                    Text(
                        text = successMessage ?: "",
                        color = Color(0xFF166534),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                TextButton(
                    onClick = onSignUpClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append("Need an account? ")
                            withStyle(style = androidx.compose.ui.text.SpanStyle(color = Color(0xFF0F172A), fontWeight = FontWeight.SemiBold)) {
                                append("Register")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
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
fun LoginScreenPreview() {
    MyMoolaTheme {
        LoginScreen()
    }
}
