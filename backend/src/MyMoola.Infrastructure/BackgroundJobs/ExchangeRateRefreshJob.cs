// MyMoola.Infrastructure/BackgroundJobs/ExchangeRateRefreshJob.cs
using Microsoft.Extensions.Caching.Memory;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Enums;
using MyMoola.Infrastructure.Services;
using MyMoola.Infrastructure.Settings;

namespace MyMoola.Infrastructure.BackgroundJobs;

public sealed class ExchangeRateRefreshJob(
    IServiceScopeFactory scopeFactory,
    IMemoryCache cache,
    IOptions<ExchangeRateSettings> settings,
    ILogger<ExchangeRateRefreshJob> logger)
    : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        await RefreshAsync(stoppingToken);

        var interval = TimeSpan.FromMinutes(settings.Value.RateRefreshIntervalMinutes);
        using var timer = new PeriodicTimer(interval);

        while (await timer.WaitForNextTickAsync(stoppingToken))
            await RefreshAsync(stoppingToken);
    }

    private async Task RefreshAsync(CancellationToken ct)
    {
        logger.LogInformation("Exchange rate refresh starting.");

        try
        {
            await using var scope = scopeFactory.CreateAsyncScope();

            var binance = scope.ServiceProvider.GetRequiredService<BinanceRateFetcher>();
            var coinGecko = scope.ServiceProvider.GetRequiredService<CoinGeckoRateFetcher>();
            var repository = scope.ServiceProvider.GetRequiredService<IExchangeRateRepository>();
            var uow = scope.ServiceProvider.GetRequiredService<IUnitOfWork>();

            IReadOnlyList<MyMoola.Domain.Entities.ExchangeRate> rates;

            try
            {
                rates = await binance.FetchAllAsync(ct);
            }
            catch (Exception ex) when (ex is not OperationCanceledException || ct.IsCancellationRequested is false)
            {
                logger.LogWarning(ex, "Binance batch fetch failed. Falling back to CoinGecko.");
                rates = await coinGecko.FetchAllAsync(ct);
            }

            if (rates.Count == 0)
            {
                logger.LogError("No rates returned from any provider. Skipping persist.");
                return;
            }

            await repository.AddRangeAsync(rates, ct);
            await uow.SaveChangesAsync(ct);

            foreach (var rate in rates)
                cache.Remove($"exchange_rate:{rate.Currency}");

            logger.LogInformation(
                "Exchange rate refresh complete. Currencies={Count} Source={Source}",
                rates.Count, rates[0].Source);
        }
        catch (OperationCanceledException) when (ct.IsCancellationRequested)
        {
            // Application is shutting down — exit cleanly
            logger.LogInformation("Exchange rate refresh cancelled — application shutting down.");
        }
        catch (Exception ex)
        {
            // Any other failure — log and survive to next tick
            logger.LogError(ex,
                "Exchange rate refresh failed. Will retry in {Minutes} minutes.",
                settings.Value.RateRefreshIntervalMinutes);
        }
    }
}