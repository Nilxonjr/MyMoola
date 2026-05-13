// MyMoola.Infrastructure/Services/CoinGeckoRateFetcher.cs
using System.Text.Json;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Infrastructure.Settings;

namespace MyMoola.Infrastructure.Services;

/// <summary>
/// Internal fetcher used only by ExchangeRateRefreshJob as fallback.
/// Single call returns all currencies with USD and KES rates natively.
/// </summary>
public sealed class CoinGeckoRateFetcher(
    HttpClient httpClient,
    IOptions<ExchangeRateSettings> settings,
    ILogger<CoinGeckoRateFetcher> logger)
{
    private static readonly Dictionary<Currency, string> GeckoIds = new()
    {
        { Currency.BTC,  "bitcoin"  },
        { Currency.ETH,  "ethereum" },
        { Currency.USDC, "usd-coin" }
    };

    public async Task<IReadOnlyList<ExchangeRate>> FetchAllAsync(CancellationToken ct)
    {
        var ids = string.Join(",", GeckoIds.Values);
        var url = $"/api/v3/simple/price?ids={ids}&vs_currencies=usd,kes";
        var apiKey = settings.Value.CoinGeckoApiKey;

        var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.Add("x-cg-demo-api-key", apiKey);

        var response = await httpClient.SendAsync(request, ct);
        response.EnsureSuccessStatusCode();

        var json = await response.Content.ReadAsStringAsync(ct);
        using var doc = JsonDocument.Parse(json);

        var spreadPercent = settings.Value.SpreadPercent;
        var rates = new List<ExchangeRate>();

        foreach (var (currency, geckoId) in GeckoIds)
        {
            if (!doc.RootElement.TryGetProperty(geckoId, out var priceObj))
            {
                logger.LogWarning("CoinGecko did not return price for {GeckoId}", geckoId);
                continue;
            }

            var usdRate = priceObj.GetProperty("usd").GetDecimal();
            var kesRate = priceObj.GetProperty("kes").GetDecimal();

            rates.Add(ExchangeRate.Create(
                currency: currency,
                rateKes: kesRate,
                rateUsd: usdRate,
                spreadPercent: spreadPercent,
                source: "coingecko"));
        }

        logger.LogInformation(
            "CoinGecko batch fetch complete. Currencies={Count}", rates.Count);

        return rates;
    }
}