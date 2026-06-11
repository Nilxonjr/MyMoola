package com.example.mymoola.features.home.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.mymoola.BackIconButton
import com.example.mymoola.R
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
    val pageBackground = Color(0xFFF8FAFC)
    val panelBackground = Color.White
    val panelBorder = Color(0xFFE2E8F0)
    val brandDark = Color(0xFF0F172A)
    val brandAccent = MaterialTheme.colorScheme.primary
    val mutedText = Color(0xFF64748B)
    val currencies = listOf("BTC", "ETH", "USDC")
    val numberFormatter = remember {
        DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
    }
    val compactFormatter = remember {
        DecimalFormat("#,##0.######", DecimalFormatSymbols(Locale.US))
    }
    val scrollState = rememberScrollState()

    var selectedCurrency by remember { mutableStateOf(currencies.first()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var generatedAt by remember { mutableStateOf<String?>(null) }
    var pointsByCurrency by remember { mutableStateOf<Map<String, List<RatePoint>>>(emptyMap()) }
    var selectedPoint by remember { mutableStateOf<SelectedChartPoint?>(null) }
    var refreshNonce by remember { mutableStateOf(0) }

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

    LaunchedEffect(refreshNonce) {
        loadRates()
    }

    val points = pointsByCurrency[selectedCurrency].orEmpty()
    val latest = points.lastOrNull()?.kesRate
    val earliest = points.firstOrNull()?.kesRate
    val delta = if (latest != null && earliest != null) latest - earliest else null
    val selectedRate = selectedPoint?.point?.kesRate ?: latest
    val selectedTimestamp = selectedPoint?.point?.timestampRaw ?: generatedAt
    val selectedLabel = if (selectedPoint != null) "Selected point" else "Current rate"
    val selectedTimestampLabel = if (selectedPoint != null) "Point time" else "Last updated"
    val chartHelperText = if (selectedPoint != null) {
        "Tap anywhere outside the chart point to clear the selection."
    } else {
        "Tap a point on the chart to inspect its exact rate."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .pointerInput(selectedPoint) {
                detectTapGestures {
                    if (selectedPoint != null) {
                        selectedPoint = null
                    }
                }
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            BackIconButton(onClick = onBackClick)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "View Rates",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = brandDark
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, panelBorder),
            color = panelBackground
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Market snapshot",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = brandDark
                        )
                        Text(
                            text = "Prices are shown in Kenyan shillings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedText
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = brandAccent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "24H",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = brandAccent
                        )
                    }
                }

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
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) brandAccent else Color(0xFFF8FAFC),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) brandAccent else panelBorder
                            ),
                            modifier = Modifier.clickable {
                                selectedCurrency = currency
                                selectedPoint = null
                            }
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
                                        modifier = Modifier.size(16.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                Text(
                                    text = currency,
                                    color = if (isSelected) Color.White else brandDark,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, panelBorder),
                    color = Color(0xFFF8FAFC)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = selectedLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = mutedText
                        )
                        Text(
                            text = if (selectedRate != null) {
                                "1 $selectedCurrency = KES ${numberFormatter.format(selectedRate)}"
                            } else {
                                "No current rate available."
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = brandDark,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$selectedTimestampLabel: ${selectedTimestamp?.let(::formatLocalDateTime) ?: "unknown"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedText
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricPill(
                                modifier = Modifier.weight(1f),
                                label = "24h open",
                                value = earliest?.let { "KES ${numberFormatter.format(it)}" } ?: "-"
                            )
                            MetricPill(
                                modifier = Modifier.weight(1f),
                                label = "24h change",
                                value = delta?.let { signedKes(it, numberFormatter) } ?: "-"
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, panelBorder),
                    color = panelBackground
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = brandAccent)
                        Text(
                            text = "Loading latest rates...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = brandDark
                        )
                        Text(
                            text = "Fetching the last 24 hours for BTC, ETH, and USDC.",
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedText
                        )
                    }
                }
            }

            !errorMessage.isNullOrBlank() -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    color = Color(0xFFFEF2F2)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Unable to load rates",
                            style = MaterialTheme.typography.titleMedium,
                            color = brandDark,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = { refreshNonce += 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = brandAccent)
                        ) {
                            Text("Try Again")
                        }
                    }
                }
            }

            points.isEmpty() -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, panelBorder),
                    color = panelBackground
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "No rate data available yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = brandDark,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "This environment has no history for $selectedCurrency yet. Try again later or switch to another asset.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = mutedText
                        )
                        OutlinedButton(
                            onClick = { refreshNonce += 1 },
                            border = BorderStroke(1.dp, panelBorder)
                        ) {
                            Text("Refresh")
                        }
                    }
                }
            }

            else -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, panelBorder),
                    color = panelBackground
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "$selectedCurrency price movement",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = brandDark
                            )
                            Text(
                                text = chartHelperText,
                                style = MaterialTheme.typography.bodySmall,
                                color = mutedText
                            )
                        }

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            val density = LocalDensity.current
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, panelBorder)
                            ) {
                                RatesLineChart(
                                    points = points,
                                    selectedRawTimestamp = selectedPoint?.point?.timestampRaw,
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
                                    .coerceIn(
                                        8f,
                                        minOf(chartWidthPx - estimatedCardWidthPx - 8f, screenWidthPx - estimatedCardWidthPx - 8f)
                                    )
                                val rawY = selected.yPx - with(density) { 94.dp.toPx() }
                                val clampedY = rawY.coerceAtLeast(8f)

                                Surface(
                                    modifier = Modifier.offset {
                                        IntOffset(
                                            x = clampedX.toInt(),
                                            y = clampedY.toInt()
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    tonalElevation = 1.dp,
                                    shadowElevation = 4.dp,
                                    color = Color.White,
                                    border = BorderStroke(1.dp, panelBorder)
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
                                            color = brandDark,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF64748B)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A)
            )
        }
    }
}

@Composable
private fun RatesLineChart(
    points: List<RatePoint>,
    selectedRawTimestamp: String?,
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
                style = Stroke(width = 4f)
            )

            points.forEachIndexed { index, point ->
                val x = startX + (index * xStep)
                val ratio = ((point.kesRate - min) / span).toFloat()
                val y = bottomY - ratio * (bottomY - topY)
                val isSelected = point.timestampRaw == selectedRawTimestamp
                drawCircle(color = Color.White, radius = if (isSelected) 8f else 6f, center = Offset(x, y))
                drawCircle(color = lineColor, radius = if (isSelected) 5f else 4f, center = Offset(x, y))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = points.first().timestampLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B)
            )
            Text(
                text = "24 hours",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B)
            )
            Text(
                text = points.last().timestampLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B)
            )
        }
    }
}

private fun signedKes(value: Double, formatter: DecimalFormat): String {
    return when {
        value > 0 -> "+ KES ${formatter.format(value)}"
        value < 0 -> "- KES ${formatter.format(kotlin.math.abs(value))}"
        else -> "KES ${formatter.format(value)}"
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ViewRatesScreenPreview() {
    MyMoolaTheme {
        ViewRatesScreen(onBackClick = {})
    }
}
