package com.example.mymoola.features.home.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletRealtimeClientTest {
    @Test
    fun buildHubUrl_appendsWalletHubPathOnce() {
        assertEquals(
            "https://mymoola-production.up.railway.app/hubs/wallet",
            WalletRealtimeConfig.buildHubUrl("https://mymoola-production.up.railway.app/")
        )
    }

    @Test
    fun buildHubUrl_handlesBaseUrlWithoutTrailingSlash() {
        assertEquals(
            "https://mymoola-production.up.railway.app/hubs/wallet",
            WalletRealtimeConfig.buildHubUrl("https://mymoola-production.up.railway.app")
        )
    }
}
