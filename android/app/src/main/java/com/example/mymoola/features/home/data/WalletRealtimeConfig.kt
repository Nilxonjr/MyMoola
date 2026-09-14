package com.example.mymoola.features.home.data

object WalletRealtimeConfig {
    fun buildHubUrl(apiBaseUrl: String): String =
        "${apiBaseUrl.trimEnd('/')}/hubs/wallet"
}
