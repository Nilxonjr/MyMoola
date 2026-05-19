//// MyMoola.Infrastructure/Services/CurrencyExchangeService.cs
//using Microsoft.Extensions.Caching.Memory;
//using Microsoft.Extensions.Logging;
//using Microsoft.Extensions.Options;
//using MyMoola.Application.Common.Interfaces;
//using MyMoola.Domain.Entities;
//using MyMoola.Domain.Enums;
//using MyMoola.Domain.Exceptions;
//using MyMoola.Infrastructure.Settings;

//namespace MyMoola.Infrastructure.Services;

//public sealed class CurrencyExchangeService(
//    IExchangeRateRepository repository,
//    IMemoryCache cache,
//    IOptions<ExchangeRateSettings> settings,
//    ILogger<CurrencyExchangeService> logger)
//    : ICurrencyExchangeService
//{
//    private static readonly TimeSpan CacheTtl = TimeSpan.FromSeconds(30);

//    public async Task<ExchangeRate> GetRateAsync(Currency currency, CancellationToken ct = default)
//    {
//        var cacheKey = $"exchange_rate:{currency}";

//        if (cache.TryGetValue(cacheKey, out ExchangeRate? cached) && cached is not null)
//            return cached;

//        var rate = await repository.GetLatestAsync(currency, ct);

//        if (rate is null)
//        {
//            logger.LogError(
//                "No exchange rate found for {Currency}. Background job may not have run yet.",
//                currency);
//            throw new NotFoundException(nameof(ExchangeRate), currency.ToString());
//        }

//        var stalenessThreshold = TimeSpan.FromMinutes(settings.Value.RateStalenessThresholdMinutes);
//        var age = DateTimeOffset.UtcNow - rate.FetchedAt;

//        if (age > stalenessThreshold)
//            logger.LogWarning(
//                "Exchange rate for {Currency} is {Age} minutes old. Background job may be failing.",
//                currency, Math.Round(age.TotalMinutes, 1));

//        cache.Set(cacheKey, rate, CacheTtl);
//        return rate;
//    }
//}