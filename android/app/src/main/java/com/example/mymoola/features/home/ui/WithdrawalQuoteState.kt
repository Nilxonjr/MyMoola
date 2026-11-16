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

class WithdrawalQuoteState {
    var quote by mutableStateOf<HomeApiClient.WithdrawalQuoteResponse?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var refreshSecondsRemaining by mutableLongStateOf(25L)
        private set

    fun clear() {
        quote = null
        loading = false
        error = null
        refreshSecondsRemaining = 25L
    }

    suspend fun load(currency: String, amount: Double) {
        loading = true
        error = null
        val result = HomeApiClient.getWithdrawalQuote(currency, amount)
        loading = false
        if (result.isSuccess) {
            quote = result.data
        } else {
            quote = null
            error = result.errorMessage ?: "Unable to load withdrawal fee."
        }
    }

    suspend fun refreshIfExpired(currency: String, amount: Double, successPrompt: String): String? {
        if (refreshSecondsRemaining > 0L) return null

        val result = HomeApiClient.getWithdrawalQuote(currency, amount)
        return if (result.isSuccess && result.data != null) {
            quote = result.data
            error = null
            successPrompt
        } else {
            result.errorMessage ?: "Quote expired. Unable to refresh fee right now."
        }
    }

    suspend fun startCountdown(currency: String, amountProvider: () -> Double?) {
        val activeQuote = quote
        if (activeQuote == null) {
            refreshSecondsRemaining = 25L
            return
        }

        var remaining = minOf(25L, activeQuote.expiresInSeconds)
        refreshSecondsRemaining = remaining
        while (true) {
            delay(1000)
            remaining -= 1
            refreshSecondsRemaining = remaining.coerceAtLeast(0L)
            if (remaining <= 0L) {
                val activeAmount = amountProvider()
                if (activeAmount == null || activeAmount <= 0.0) {
                    clear()
                    return
                }

                val result = HomeApiClient.getWithdrawalQuote(currency, activeAmount)
                if (result.isSuccess) {
                    quote = result.data
                    error = null
                } else if (quote == null) {
                    error = result.errorMessage ?: "Unable to refresh withdrawal fee."
                }
                remaining = minOf(25L, quote?.expiresInSeconds ?: 25L)
                refreshSecondsRemaining = remaining
            }
        }
    }
}

@Composable
fun rememberWithdrawalQuoteState(
    selectedCurrency: String,
    amountInput: String
): WithdrawalQuoteState {
    val state = remember { WithdrawalQuoteState() }

    LaunchedEffect(selectedCurrency, amountInput) {
        val validAmount = amountInput.toDoubleOrNull()
        if (validAmount == null || validAmount <= 0.0) {
            state.clear()
            return@LaunchedEffect
        }
        state.load(selectedCurrency, validAmount)
    }

    LaunchedEffect(selectedCurrency, amountInput, state.quote?.quoteId, state.quote != null) {
        state.startCountdown(selectedCurrency) { amountInput.toDoubleOrNull() }
    }

    return state
}
