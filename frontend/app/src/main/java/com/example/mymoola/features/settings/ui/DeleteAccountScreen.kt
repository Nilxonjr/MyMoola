package com.example.mymoola.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mymoola.BackIconButton
import com.example.mymoola.features.auth.data.AuthApiClient
import com.example.mymoola.features.auth.data.AuthSession
import com.example.mymoola.ui.theme.MyMoolaTheme
import kotlinx.coroutines.launch

@Composable
fun DeleteAccountScreen(
    onBackClick: () -> Unit,
    onDeleted: () -> Unit = {}
) {
    var error by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                text = "Delete Account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "This action is permanent and removes your account data.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF334155)
        )

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                val token = AuthSession.accessToken
                if (token.isNullOrBlank()) {
                    error = "Session missing. Please log in again."
                    return@Button
                }

                scope.launch {
                    isDeleting = true
                    val result = AuthApiClient.deleteMyAccount(token)
                    isDeleting = false

                    if (result.isSuccess) {
                        AuthSession.accessToken = null
                        onDeleted()
                    } else {
                        error = result.errorMessage ?: "Failed to delete account."
                    }
                }
            },
            enabled = !isDeleting,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFDC2626),
                contentColor = Color.White
            )
        ) {
            Text(if (isDeleting) "Deleting..." else "Delete My Account")
        }

        if (!error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DeleteAccountScreenPreview() {
    MyMoolaTheme {
        DeleteAccountScreen(onBackClick = {})
    }
}

