// MyMoola.Infrastructure/ExchangeRates/ResilientCurrencyExchangeService.cs
using Microsoft.Extensions.Caching.Memory;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Infrastructure.Persistence.Repositories;
using MyMoola.Infrastructure.Services;

namespace MyMoola.Infrastructure.Services;

/// <summary>
/// Tries Binance first. Falls back to CoinGecko on failure.
/// Caches the result for 60 seconds — serves cached rate for all
/// requests within that window so external APIs are not hammered.
/// Persists every fresh fetch to exchange_rates table.
/// </summary>
public sealed class ResilientCurrencyExchangeService(
    BinanceCurrencyExchangeService binance,
    CoinGeckoCurrencyExchangeService coinGecko,
    IExchangeRateRepository repository,
    IUnitOfWork uow,
    IMemoryCache cache,
    ILogger<ResilientCurrencyExchangeService> logger)
    : ICurrencyExchangeService
{
    private static readonly TimeSpan CacheTtl = TimeSpan.FromSeconds(60);
    private static readonly TimeSpan StalenessAge = TimeSpan.FromMinutes(5);

    public async Task<ExchangeRate> GetRateAsync(Currency currency, CancellationToken ct = default)
    {
        var cacheKey = $"exchange_rate:{currency}";

        if (cache.TryGetValue(cacheKey, out ExchangeRate? cached) && cached is not null)
            return cached;

        // Check DB — use persisted rate if fresh enough
        var persisted = await repository.GetLatestAsync(currency, ct);
        if (persisted is not null && DateTimeOffset.UtcNow - persisted.FetchedAt < StalenessAge)
        {
            cache.Set(cacheKey, persisted, CacheTtl);
            return persisted;
        }

        // Fetch fresh from external provider
        ExchangeRate fresh;
        try
        {
            fresh = await binance.FetchAsync(currency, ct);
            logger.LogInformation("Exchange rate fetched from Binance. Currency={Currency}", currency);
        }
        catch (Exception ex)
        {
            logger.LogWarning(ex,
                "Binance rate fetch failed for {Currency}. Falling back to CoinGecko.", currency);
            fresh = await coinGecko.FetchAsync(currency, ct);
            logger.LogInformation("Exchange rate fetched from CoinGecko (fallback). Currency={Currency}", currency);
        }

        // Persist and cache
        await repository.AddAsync(fresh, ct);
        await uow.SaveChangesAsync(ct);

        cache.Set(cacheKey, fresh, CacheTtl);
        return fresh;
    }
}