package com.example.mymoola.features.settings.ui

import android.Manifest
import android.os.Build
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.example.mymoola.BackIconButton
import com.example.mymoola.ui.theme.MyMoolaTheme

@Composable
fun TransactionNotificationsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val statusText = if (permissionGranted && appNotificationsEnabled) {
        "On for wallet credit events."
    } else {
        "Off at device level. Enable app notifications in Android settings."
    }

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
                text = "Transaction Notifications",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF334155)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "MyMoola currently sends a phone notification when your wallet is credited while the app is open.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF64748B)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun TransactionNotificationsScreenPreview() {
    MyMoolaTheme {
        TransactionNotificationsScreen(onBackClick = {})
    }
}
