package com.example.mymoola

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.mymoola.features.home.data.WalletCreditedPayload
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

object WalletNotificationHelper {
    private const val ChannelId = "wallet_activity"
    private const val ChannelName = "Wallet activity"
    private val nextNotificationId = AtomicInteger(1)

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            ChannelId,
            ChannelName,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Wallet credit notifications"
        }
        manager.createNotificationChannel(channel)
    }

    fun showWalletCredited(context: Context, payload: WalletCreditedPayload) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currency = payload.currency.uppercase(Locale.US)
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Wallet credited")
            .setContentText("Received ${formatAmount(payload.amount)} $currency")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Received ${formatAmount(payload.amount)} $currency. Open MyMoola to refresh your wallet and activity list.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = payload.transactionId
            .takeIf { it.isNotBlank() }
            ?.hashCode()
            ?: nextNotificationId.getAndIncrement()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun formatAmount(amount: Double): String {
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ','
            decimalSeparator = '.'
        }
        return DecimalFormat("#,##0.00", symbols).format(amount)
    }
}
