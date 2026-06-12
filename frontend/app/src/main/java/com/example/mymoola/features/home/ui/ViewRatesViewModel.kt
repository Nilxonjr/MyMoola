package com.example.mymoola.features.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymoola.features.home.data.HomeApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class RatePoint(
    val timestampLabel: String,
    val timestampRaw: String,
    val timestampEpochMs: Long,
    val kesRate: Double
)

data class SelectedChartPoint(
    val point: RatePoint,
    val xPx: Float,
    val yPx: Float
)

data class ViewRatesUiState(
    val selectedCurrency: String = "BTC",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val generatedAt: String? = null,
    val pointsByCurrency: Map<String, List<RatePoint>> = emptyMap(),
    val selectedPoint: SelectedChartPoint? = null
)

internal object ViewRatesFormatting {
    fun formatTimestampLabel(raw: String): String {
        return runCatching {
            OffsetDateTime.parse(raw)
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        }.getOrDefault(raw)
    }

    fun formatLocalDateTime(raw: String): String {
        return runCatching {
            OffsetDateTime.parse(raw)
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
                .toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.getDefault()))
        }.getOrDefault(raw)
    }

    fun parseTimestampEpochMs(raw: String): Long {
        return runCatching {
            OffsetDateTime.parse(raw).toInstant().toEpochMilli()
        }.getOrDefault(Long.MIN_VALUE)
    }

    fun mapSeries(series: List<HomeApiClient.RateHistorySeries>): Map<String, List<RatePoint>> {
        return series.associate { item ->
            item.currency.uppercase(Locale.US) to item.points
                .map { point ->
                    val epochMs = parseTimestampEpochMs(point.timestamp)
                    RatePoint(
                        timestampLabel = formatTimestampLabel(point.timestamp),
                        timestampRaw = point.timestamp,
                        timestampEpochMs = epochMs,
                        kesRate = point.kesRate
                    )
                }
                .sortedBy { it.timestampEpochMs }
        }
    }
}

class ViewRatesViewModel : ViewModel() {
    private val currencies = listOf("BTC", "ETH", "USDC")

    private val _uiState = MutableStateFlow(ViewRatesUiState())
    val uiState: StateFlow<ViewRatesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectCurrency(currency: String) {
        updateState { it.copy(selectedCurrency = currency, selectedPoint = null) }
    }

    fun selectPoint(point: SelectedChartPoint) {
        updateState { it.copy(selectedPoint = point) }
    }

    fun clearSelectedPoint() {
        updateState { it.copy(selectedPoint = null) }
    }

    fun refresh() {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, errorMessage = null) }
            val result = HomeApiClient.getRatesHistory(
                currencies = currencies,
                range = "24h",
                interval = "hour"
            )

            if (result.isSuccess) {
                val data = result.data
                updateState {
                    it.copy(
                        isLoading = false,
                        generatedAt = data?.generatedAt,
                        pointsByCurrency = ViewRatesFormatting.mapSeries(data?.series.orEmpty()),
                        selectedPoint = null,
                        errorMessage = null
                    )
                }
            } else {
                updateState {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.errorMessage ?: "Failed to load rates.",
                        pointsByCurrency = emptyMap(),
                        selectedPoint = null
                    )
                }
            }
        }
    }

    private fun updateState(transform: (ViewRatesUiState) -> ViewRatesUiState) {
        _uiState.value = transform(_uiState.value)
    }
}
