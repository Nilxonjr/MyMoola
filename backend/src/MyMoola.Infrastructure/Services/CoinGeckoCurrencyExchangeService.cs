using System.Text.Json;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;

namespace MyMoola.Infrastructure.Services;

public sealed class CoinGeckoCurrencyExchangeService(
    HttpClient httpClient,
    IConfiguration configuration,
    ILogger<CoinGeckoCurrencyExchangeService> logger)
{
    private static readonly Dictionary<Currency, string> GeckoIds = new()
    {
        { Currency.BTC,  "bitcoin"  },
        { Currency.ETH,  "ethereum" },
        { Currency.USDC, "usd-coin" }
    };

    private const decimal DefaultSpreadPercent = 1.5m;

    public async Task<ExchangeRate> FetchAsync(Currency currency, CancellationToken ct)
    {
        if (!GeckoIds.TryGetValue(currency, out var geckoId))
            throw new InvalidOperationException($"Unsupported currency: {currency}");

        var apiKey = configuration["ExternalApis:CoinGeckoApiKey"]
            ?? throw new InvalidOperationException("CoinGecko API key is not configured.");

        var url = $"https://api.coingecko.com/api/v3/simple/price" +
                  $"?ids={geckoId}&vs_currencies=usd,kes";

        var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.Add("x-cg-demo-api-key", apiKey);

        var response = await httpClient.SendAsync(request, ct);
        response.EnsureSuccessStatusCode();

        var json = await response.Content.ReadAsStringAsync(ct);
        using var doc = JsonDocument.Parse(json);

        var priceObj = doc.RootElement.GetProperty(geckoId);
        var usdRate = priceObj.GetProperty("usd").GetDecimal();
        var kesRate = priceObj.GetProperty("kes").GetDecimal();

        logger.LogInformation(
            "CoinGecko rates fetched. Currency={Currency} USD={Usd} KES={Kes}",
            currency, usdRate, kesRate);

        return ExchangeRate.Create(
            currency: currency,
            rateKes: kesRate,
            rateUsd: usdRate,
            spreadPercent: DefaultSpreadPercent,
            source: "coingecko");
    }
}