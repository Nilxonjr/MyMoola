using System.Text.Json;
using Microsoft.Extensions.Logging;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using Microsoft.Extensions.Http;

namespace MyMoola.Infrastructure.Services;

public sealed class BinanceCurrencyExchangeService(
    HttpClient httpClient,
    IHttpClientFactory httpClientFactory,
    ILogger<BinanceCurrencyExchangeService> logger)
{
    private const string ForexUrl = "https://open.er-api.com/v6/latest/USD";

    private static readonly Dictionary<Currency, string> UsdtSymbols = new()
    {
        { Currency.BTC,  "BTCUSDT"  },
        { Currency.ETH,  "ETHUSDT"  },
        { Currency.USDC, "USDCUSDT" }
    };

 
    private const decimal DefaultSpreadPercent = 1.5m;

    public async Task<ExchangeRate> FetchAsync(Currency currency, CancellationToken ct)
    {
        if (!UsdtSymbols.TryGetValue(currency, out var symbol))
            throw new InvalidOperationException($"Unsupported currency: {currency}");

        var usdRate = await FetchPriceAsync(symbol, ct);
        var usdToKes = await FetchUsdToKesAsync(ct);
        var kesRate = usdRate * usdToKes;

        logger.LogInformation(
            "Binance rates fetched. Currency={Currency} USD={Usd} KES={Kes}",
            currency, usdRate, kesRate);

        return ExchangeRate.Create(
            currency: currency,
            rateKes: kesRate,
            rateUsd: usdRate,
            spreadPercent: DefaultSpreadPercent,
            source: "binance");
    }

    private async Task<decimal> FetchPriceAsync(string symbol, CancellationToken ct)
    {
        var url = $"https://api.binance.com/api/v3/ticker/price?symbol={symbol}";
        var response = await httpClient.GetAsync(url, ct);
        response.EnsureSuccessStatusCode();

        var json = await response.Content.ReadAsStringAsync(ct);
        using var doc = JsonDocument.Parse(json);

        var priceStr = doc.RootElement.GetProperty("price").GetString()
            ?? throw new InvalidOperationException($"Binance returned null price for {symbol}.");

        return decimal.Parse(priceStr, System.Globalization.CultureInfo.InvariantCulture);
    }
    private async Task<decimal> FetchUsdToKesAsync(CancellationToken ct)
    {
        using var client = httpClientFactory.CreateClient();
        var response = await client.GetAsync(ForexUrl, ct);
        response.EnsureSuccessStatusCode();

        var json = await response.Content.ReadAsStringAsync(ct);
        using var doc = JsonDocument.Parse(json);

        return doc.RootElement
            .GetProperty("rates")
            .GetProperty("KES")
            .GetDecimal();
    }
}