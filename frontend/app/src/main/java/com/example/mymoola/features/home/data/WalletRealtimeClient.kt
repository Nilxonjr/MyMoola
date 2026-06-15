package com.example.mymoola.features.home.data

import android.util.Log
import com.example.mymoola.BuildConfig
import com.example.mymoola.features.auth.data.AuthSession
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import io.reactivex.rxjava3.core.Single
import java.util.concurrent.atomic.AtomicBoolean

data class WalletCreditedPayload(
    val transactionId: String = "",
    val amount: Double = 0.0,
    val currency: String = "",
    val availableBalance: Double = 0.0,
    val timestamp: String = ""
)

object WalletRealtimeClient {
    private const val Tag = "WalletRealtimeClient"
    private const val WalletCreditedEvent = "WalletCredited"

    @Volatile
    private var hubConnection: HubConnection? = null

    @Volatile
    private var listenerAttached = false

    private val started = AtomicBoolean(false)

    @Volatile
    private var onWalletCredited: ((WalletCreditedPayload) -> Unit)? = null

    fun setWalletCreditedListener(listener: ((WalletCreditedPayload) -> Unit)?) {
        onWalletCredited = listener
    }

    fun connect() {
        AuthSession.accessToken?.takeIf { it.isNotBlank() } ?: return
        val existing = hubConnection
        if (existing != null && started.get()) return

        val connection = existing ?: HubConnectionBuilder
            .create(WalletRealtimeConfig.buildHubUrl(BuildConfig.API_BASE_URL))
            .withAccessTokenProvider(Single.defer {
                val currentToken = AuthSession.accessToken.orEmpty()
                Single.just(currentToken)
            })
            .build()
            .also { hubConnection = it }

        if (!listenerAttached) {
            connection.on(
                WalletCreditedEvent,
                { payload: WalletCreditedPayload ->
                    onWalletCredited?.invoke(payload)
                },
                WalletCreditedPayload::class.java
            )
            listenerAttached = true
        }

        runCatching {
            connection.start().blockingAwait()
            started.set(true)
            Log.d(Tag, "Connected to wallet hub")
        }.onFailure { error ->
            started.set(false)
            Log.w(Tag, "Failed to connect to wallet hub", error)
        }
    }

    fun disconnect() {
        val connection = hubConnection ?: return
        runCatching {
            connection.stop().blockingAwait()
        }.onFailure { error ->
            Log.w(Tag, "Failed to stop wallet hub", error)
        }
        started.set(false)
    }
}
