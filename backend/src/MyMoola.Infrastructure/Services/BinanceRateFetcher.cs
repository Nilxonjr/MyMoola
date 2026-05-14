// MyMoola.Infrastructure/Services/BinanceRateFetcher.cs
using System.Globalization;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Infrastructure.Settings;

namespace MyMoola.Infrastructure.Services;

/// <summary>
/// Internal fetcher used only by ExchangeRateRefreshJob.
/// Makes a single batch call to Binance for all supported currencies.
/// </summary>
public sealed class BinanceRateFetcher(
    HttpClient httpClient,
    IHttpClientFactory httpClientFactory,
    IOptions<ExchangeRateSettings> settings,
    ILogger<BinanceRateFetcher> logger)
{
    private static readonly Dictionary<Currency, string> UsdtSymbols = new()
    {
        { Currency.BTC,  "BTCUSDT"  },
        { Currency.ETH,  "ETHUSDT"  },
        { Currency.USDC, "USDCUSDT" }
    };

    public async Task<IReadOnlyList<ExchangeRate>> FetchAllAsync(CancellationToken ct)
    {
        // Single batch call — returns all symbols at once
        var symbols = string.Join(",", UsdtSymbols.Values.Select(s => $"\"{s}\""));
        var url = $"/api/v3/ticker/price?symbols=[{Uri.EscapeDataString($"[{string.Join(",", UsdtSymbols.Values.Select(s => $"\"{s}\""))}]")}]";

        var response = await httpClient.GetAsync(
            $"/api/v3/ticker/price?symbols={Uri.EscapeDataString($"[{string.Join(",", UsdtSymbols.Values.Select(s => $"\"{s}\""))}]")}",
            ct);
        response.EnsureSuccessStatusCode();

        var json = await response.Content.ReadAsStringAsync(ct);
        using var doc = JsonDocument.Parse(json);

        // Build symbol -> price map
        var priceMap = new Dictionary<string, decimal>();
        foreach (var element in doc.RootElement.EnumerateArray())
        {
            var symbol = element.GetProperty("symbol").GetString()!;
            var price = decimal.Parse(
                element.GetProperty("price").GetString()!,
                CultureInfo.InvariantCulture);
            priceMap[symbol] = price;
        }

        // Fetch USD/KES from forex provider
        var usdToKes = await FetchUsdToKesAsync(ct);

        var spreadPercent = settings.Value.SpreadPercent;
        var rates = new List<ExchangeRate>();

        foreach (var (currency, symbol) in UsdtSymbols)
        {
            if (!priceMap.TryGetValue(symbol, out var usdRate))
            {
                logger.LogWarning("Binance did not return price for {Symbol}", symbol);
                continue;
            }

            var kesRate = usdRate * usdToKes;
            rates.Add(ExchangeRate.Create(
                currency: currency,
                rateKes: kesRate,
                rateUsd: usdRate,
                spreadPercent: spreadPercent,
                source: "binance"));
        }

        logger.LogInformation(
            "Binance batch fetch complete. Currencies={Count} UsdToKes={UsdToKes}",
            rates.Count, usdToKes);

        return rates;
    }

    private async Task<decimal> FetchUsdToKesAsync(CancellationToken ct)
    {
        using var client = httpClientFactory.CreateClient();
        var response = await client.GetAsync(
            $"{settings.Value.ForexBaseUrl}/v6/latest/USD", ct);
        response.EnsureSuccessStatusCode();

        var json = await response.Content.ReadAsStringAsync(ct);
        using var doc = JsonDocument.Parse(json);

        return doc.RootElement
            .GetProperty("rates")
            .GetProperty("KES")
            .GetDecimal();
    }
}