package com.example.mymoola.features.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.mymoola.features.home.data.HomeApiClient
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TransactionQuoteState {
    var quote by mutableStateOf<HomeApiClient.QuoteResponse?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var refreshSecondsRemaining by mutableLongStateOf(25L)
        private set

    suspend fun load(currency: String) {
        loading = true
        error = null
        val result = HomeApiClient.getQuote(currency)
        loading = false
        if (result.isSuccess) {
            quote = result.data
        } else {
            quote = null
            error = result.errorMessage ?: "Unable to load quote."
        }
    }

    suspend fun refreshIfExpired(currency: String, successPrompt: String): String? {
        val activeQuote = quote ?: return "Quote is unavailable. Please refresh and try again."
        val expiresMs = parseQuoteExpiryMillis(activeQuote.expiresAt)
        if (expiresMs > System.currentTimeMillis()) return null

        val refreshed = HomeApiClient.getQuote(currency)
        return if (refreshed.isSuccess && refreshed.data != null) {
            quote = refreshed.data
            error = null
            successPrompt
        } else {
            refreshed.errorMessage ?: "Quote expired. Unable to refresh rate right now."
        }
    }

    suspend fun startCountdown(currency: String) {
        if (quote == null) {
            refreshSecondsRemaining = 25L
            return
        }

        var remaining = 25L
        refreshSecondsRemaining = remaining
        while (true) {
            delay(1000)
            remaining -= 1
            refreshSecondsRemaining = remaining.coerceAtLeast(0L)
            if (remaining <= 0L) {
                val result = HomeApiClient.getQuote(currency)
                if (result.isSuccess) {
                    quote = result.data
                    error = null
                } else if (quote == null) {
                    error = result.errorMessage ?: "Unable to refresh quote."
                }
                remaining = 25L
                refreshSecondsRemaining = remaining
            }
        }
    }
}

@Composable
fun rememberTransactionQuoteState(selectedCurrency: String): TransactionQuoteState {
    val state = remember { TransactionQuoteState() }

    LaunchedEffect(selectedCurrency) {
        state.load(selectedCurrency)
    }

    LaunchedEffect(selectedCurrency, state.quote?.quoteId, state.quote != null) {
        state.startCountdown(selectedCurrency)
    }

    return state
}

fun parseQuoteExpiryMillis(value: String): Long {
    if (value.isBlank()) return 0L

    return runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli() }
        .getOrDefault(0L)
}
