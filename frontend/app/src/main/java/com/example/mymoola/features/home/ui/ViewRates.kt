package com.example.mymoola.features.home.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mymoola.BackIconButton
import com.example.mymoola.features.home.data.HomeApiClient
import com.example.mymoola.ui.theme.MyMoolaTheme
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private data class RatePoint(
    val timestampLabel: String,
    val kesRate: Double
)

@Composable
fun ViewRatesScreen(
    onBackClick: () -> Unit
) {
    val currencies = listOf("BTC", "ETH", "USDC")
    var selectedCurrency by remember { mutableStateOf(currencies.first()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var generatedAt by remember { mutableStateOf<String?>(null) }
    var pointsByCurrency by remember { mutableStateOf<Map<String, List<RatePoint>>>(emptyMap()) }

    fun formatTimestampLabel(raw: String): String {
        val datePart = raw.substringBefore("T", missingDelimiterValue = raw)
        return if (datePart.length >= 5) datePart.takeLast(5) else datePart
    }

    suspend fun loadRates() {
        isLoading = true
        errorMessage = null
        val result = HomeApiClient.getRatesHistory(
            currencies = currencies,
            range = "7d",
            interval = "day"
        )
        isLoading = false

        if (result.isSuccess) {
            val data = result.data
            generatedAt = data?.generatedAt
            pointsByCurrency = data?.series
                ?.associate { series ->
                    series.currency.uppercase(Locale.US) to series.points.map { point ->
                        RatePoint(
                            timestampLabel = formatTimestampLabel(point.timestamp),
                            kesRate = point.kesRate
                        )
                    }
                }
                .orEmpty()
        } else {
            errorMessage = result.errorMessage ?: "Failed to load rates."
            pointsByCurrency = emptyMap()
        }
    }

    LaunchedEffect(Unit) {
        loadRates()
    }

    val points = pointsByCurrency[selectedCurrency].orEmpty()
    val latest = points.lastOrNull()?.kesRate
    val numberFormatter = remember {
        DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
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
                text = "View Rates",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            currencies.forEach { currency ->
                val isSelected = currency == selectedCurrency
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                    modifier = Modifier
                        .clickable { selectedCurrency = currency }
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFF0F172A) else Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(10.dp)
                        )
                ) {
                    Text(
                        text = currency,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = if (isSelected) Color.White else Color(0xFF334155),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF0A7C6A))
                }
            }
            !errorMessage.isNullOrBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFDC2626)
                    )
                }
            }
            points.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No rate data available yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF64748B)
                    )
                }
            }
            else -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White
                ) {
                    RatesLineChart(
                        points = points,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (latest != null) {
                "1 $selectedCurrency = KES ${numberFormatter.format(latest)}"
            } else {
                "No current rate available."
            },
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF0F172A),
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Last updated: ${generatedAt ?: "unknown"}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64748B)
        )
    }
}

@Composable
private fun RatesLineChart(
    points: List<RatePoint>,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return

    val min = points.minOf { it.kesRate }
    val max = points.maxOf { it.kesRate }
    val span = (max - min).takeIf { it > 0.0 } ?: 1.0
    val lineColor = Color(0xFF0A7C6A)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val startX = 10f
            val endX = size.width - 10f
            val topY = 8f
            val bottomY = size.height - 8f
            val xStep = (endX - startX) / (points.size - 1).coerceAtLeast(1)

            drawLine(
                color = Color(0xFFE2E8F0),
                start = Offset(startX, bottomY),
                end = Offset(endX, bottomY),
                strokeWidth = 2f
            )

            val path = Path()
            points.forEachIndexed { index, point ->
                val x = startX + (index * xStep)
                val ratio = ((point.kesRate - min) / span).toFloat()
                val y = bottomY - ratio * (bottomY - topY)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = lineColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
            )

            points.forEachIndexed { index, point ->
                val x = startX + (index * xStep)
                val ratio = ((point.kesRate - min) / span).toFloat()
                val y = bottomY - ratio * (bottomY - topY)
                drawCircle(color = Color.White, radius = 6f, center = Offset(x, y))
                drawCircle(color = lineColor, radius = 4f, center = Offset(x, y))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = points.first().timestampLabel, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
            Text(text = "7 days", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
            Text(text = points.last().timestampLabel, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ViewRatesScreenPreview() {
    MyMoolaTheme {
        ViewRatesScreen(onBackClick = {})
    }
}
