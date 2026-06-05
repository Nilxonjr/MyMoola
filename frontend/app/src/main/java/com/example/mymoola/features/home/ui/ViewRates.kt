package com.example.mymoola.features.home.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.example.mymoola.BackIconButton
import com.example.mymoola.features.home.data.HomeApiClient
import com.example.mymoola.ui.theme.MyMoolaTheme
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class RatePoint(
    val timestampLabel: String,
    val timestampRaw: String,
    val kesRate: Double
)

private data class SelectedChartPoint(
    val point: RatePoint,
    val xPx: Float,
    val yPx: Float
)

@Composable
fun ViewRatesScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val currencies = listOf("BTC", "ETH", "USDC")
    var selectedCurrency by remember { mutableStateOf(currencies.first()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var generatedAt by remember { mutableStateOf<String?>(null) }
    var pointsByCurrency by remember { mutableStateOf<Map<String, List<RatePoint>>>(emptyMap()) }
    var selectedPoint by remember { mutableStateOf<SelectedChartPoint?>(null) }

    fun formatTimestampLabel(raw: String): String {
        return runCatching {
            OffsetDateTime.parse(raw)
                .toLocalDate()
                .format(DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault()))
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

    suspend fun loadRates() {
        isLoading = true
        errorMessage = null
        val result = HomeApiClient.getRatesHistory(
            currencies = currencies,
            range = "24h",
            interval = "hour"
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
                            timestampRaw = point.timestamp,
                            kesRate = point.kesRate
                        )
                    }
                }
                .orEmpty()
            selectedPoint = null
        } else {
            errorMessage = result.errorMessage ?: "Failed to load rates."
            pointsByCurrency = emptyMap()
            selectedPoint = null
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
            .pointerInput(selectedPoint) {
                detectTapGestures {
                    if (selectedPoint != null) {
                        selectedPoint = null
                    }
                }
            }
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
                val iconResName = when (currency) {
                    "USDC" -> "usdc_logo"
                    "BTC" -> "bitcoin_logo"
                    "ETH" -> "ethereum_logo"
                    else -> "onb_wallet_manage"
                }
                val iconResId = remember(iconResName) {
                    context.resources.getIdentifier(
                        iconResName,
                        "drawable",
                        context.packageName
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFF8FAFC),
                    modifier = Modifier
                        .clickable { selectedCurrency = currency }
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(10.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (iconResId != 0) {
                            Image(
                                painter = painterResource(id = iconResId),
                                contentDescription = "$currency logo",
                                modifier = Modifier.height(16.dp)
                            )
                        }
                        Text(
                            text = currency,
                            color = if (isSelected) Color.White else Color(0xFF334155),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
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
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    val density = LocalDensity.current
                    Surface(
                        modifier = Modifier
                            .fillMaxSize(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White
                    ) {
                        RatesLineChart(
                            points = points,
                            onPointSelected = { selectedPoint = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 16.dp)
                        )
                    }

                    selectedPoint?.let { selected ->
                        val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
                        val estimatedCardWidthPx = with(density) { 220.dp.toPx() }
                        val chartWidthPx = with(density) { maxWidth.toPx() }
                        val cardCenterTargetX = selected.xPx + with(density) { 16.dp.toPx() }
                        val clampedX = (cardCenterTargetX - estimatedCardWidthPx / 2f)
                            .coerceIn(8f, minOf(chartWidthPx - estimatedCardWidthPx - 8f, screenWidthPx - estimatedCardWidthPx - 8f))
                        val rawY = selected.yPx - with(density) { 90.dp.toPx() }
                        val clampedY = rawY.coerceAtLeast(8f)

                        Surface(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        x = clampedX.toInt(),
                                        y = clampedY.toInt()
                                    )
                                },
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 1.dp,
                            shadowElevation = 4.dp,
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = formatLocalDateTime(selected.point.timestampRaw),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF334155),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "KES ${numberFormatter.format(selected.point.kesRate)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
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
        if (selectedPoint != null) {
            Text(
                text = "Selected: ${formatLocalDateTime(selectedPoint?.point?.timestampRaw.orEmpty())} • KES ${numberFormatter.format(selectedPoint?.point?.kesRate ?: 0.0)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF334155)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = "Last updated: ${generatedAt?.let(::formatLocalDateTime) ?: "unknown"}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64748B)
        )
    }
}

@Composable
private fun RatesLineChart(
    points: List<RatePoint>,
    onPointSelected: (SelectedChartPoint) -> Unit,
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
                .pointerInput(points) {
                    detectTapGestures { tapOffset ->
                        val topY = 8f
                        val bottomY = size.height - 8f
                        val startX = 10f
                        val endX = size.width - 10f
                        if (tapOffset.x < startX || tapOffset.x > endX) return@detectTapGestures
                        val xStep = (endX - startX) / (points.size - 1).coerceAtLeast(1)
                        val index = ((tapOffset.x - startX) / xStep)
                            .toInt()
                            .coerceIn(0, points.lastIndex)
                        val selected = points[index]
                        val ratio = ((selected.kesRate - min) / span).toFloat()
                        val y = bottomY - ratio * (bottomY - topY)
                        val x = startX + (index * xStep)
                        onPointSelected(
                            SelectedChartPoint(
                                point = selected,
                                xPx = x,
                                yPx = y
                            )
                        )
                    }
                }
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
            Text(text = "24 hours", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B))
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
