// MyMoola.Infrastructure/Services/CurrencyExchangeService.cs
using System.Text.Json;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;
using MyMoola.Infrastructure.Settings;
using StackExchange.Redis;

namespace MyMoola.Infrastructure.Services;

public sealed class CurrencyExchangeService(
    IExchangeRateRepository repository,
    IConnectionMultiplexer redis,
    IOptions<ExchangeRateSettings> settings,
    ILogger<CurrencyExchangeService> logger)
    : ICurrencyExchangeService
{
    private static readonly TimeSpan CacheTtl = TimeSpan.FromSeconds(120);
    private IDatabase Db => redis.GetDatabase();

    public async Task<ExchangeRate> GetRateAsync(Currency currency, CancellationToken ct = default)
    {
        var cacheKey = $"exchange_rate:{currency}";

        // Check Redis first
        var cached = await Db.StringGetAsync(cacheKey);
        if (cached.HasValue)
        {
            var cachedRate = JsonSerializer.Deserialize<ExchangeRateCacheDto>(cached.ToString());
            if (cachedRate is not null)
                return cachedRate.ToExchangeRate();
        }

        var rate = await repository.GetLatestAsync(currency, ct);

        if (rate is null)
        {
            logger.LogError(
                "No exchange rate found for {Currency}. Background job may not have run yet.",
                currency);
            throw new NotFoundException(nameof(ExchangeRate), currency.ToString());
        }

        var stalenessThreshold = TimeSpan.FromMinutes(settings.Value.RateStalenessThresholdMinutes);
        var age = DateTimeOffset.UtcNow - rate.FetchedAt;

        if (age > stalenessThreshold)
            logger.LogWarning(
                "Exchange rate for {Currency} is {Age} minutes old. Background job may be failing.",
                currency, Math.Round(age.TotalMinutes, 1));

        // Cache in Redis
        var dto = ExchangeRateCacheDto.FromExchangeRate(rate);
        await Db.StringSetAsync(cacheKey, JsonSerializer.Serialize(dto), CacheTtl);

        return rate;
    }
}

/// <summary>
/// Lightweight DTO for Redis serialization — ExchangeRate entity
/// has private setters so cannot be deserialized directly.
/// </summary>
internal sealed record ExchangeRateCacheDto(
    Guid Id,
    string Currency,
    decimal RateKes,
    decimal RateUsd,
    decimal BuyRateKes,
    decimal SellRateKes,
    decimal SpreadPercent,
    string Source,
    DateTimeOffset FetchedAt)
{
    public static ExchangeRateCacheDto FromExchangeRate(ExchangeRate rate) => new(
        rate.Id,
        rate.Currency.ToString(),
        rate.RateKes,
        rate.RateUsd,
        rate.BuyRateKes,
        rate.SellRateKes,
        rate.SpreadPercent,
        rate.Source,
        rate.FetchedAt);

    public ExchangeRate ToExchangeRate() => ExchangeRate.Restore(
    Enum.Parse<Currency>(Currency),
    RateKes,
    RateUsd,
    SpreadPercent,
    Source,
    FetchedAt);
}