// MyMoola.Infrastructure/BackgroundServices/OutboxProcessor.cs
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Infrastructure.Settings;

namespace MyMoola.Infrastructure.BackgroundJobs;

public sealed class OutboxProcessor(
    IServiceScopeFactory scopeFactory,
    IOptions<OutboxProcessorOptions> options,
    ILogger<OutboxProcessor> logger) : BackgroundService
{
    private readonly OutboxProcessorOptions _options = options.Value;

    protected override async Task ExecuteAsync(CancellationToken ct)
    {
        logger.LogInformation("OutboxProcessor started.");

        while (!ct.IsCancellationRequested)
        {
            try
            {
                await ProcessBatchAsync(ct);
            }
            catch (Exception ex) when (ex is not OperationCanceledException)
            {
                logger.LogError(ex, "OutboxProcessor batch failed.");
            }

            await Task.Delay(
                TimeSpan.FromSeconds(_options.PollingIntervalSeconds), ct);
        }

        logger.LogInformation("OutboxProcessor stopped.");
    }

    private async Task ProcessBatchAsync(CancellationToken ct)
    {
        await using var scope = scopeFactory.CreateAsyncScope();

        var outbox = scope.ServiceProvider
            .GetRequiredService<IOutboxRepository>();

        var uow = scope.ServiceProvider
            .GetRequiredService<IUnitOfWork>();

        var handlers = scope.ServiceProvider
            .GetRequiredService<IEnumerable<IOutboxMessageHandler>>()
            .ToDictionary(h => h.Type);

        var lockDuration = TimeSpan.FromSeconds(_options.LockDurationSeconds);

        var messages = await outbox.ClaimPendingAsync(
            _options.BatchSize, lockDuration, ct);

        if (messages.Count == 0)
            return;

        logger.LogInformation(
            "OutboxProcessor claimed {Count} messages.", messages.Count);

        foreach (var message in messages)
        {
            if (!handlers.TryGetValue(message.Type, out var handler))
            {
                message.MarkFailed($"No handler registered for type '{message.Type}'.");
                await uow.SaveChangesAsync(ct);

                logger.LogWarning(
                    "No handler for OutboxMessage. Type={Type} Id={Id}",
                    message.Type, message.Id);
                continue;
            }

            try
            {
                await handler.ExecuteAsync(message, ct);
                message.MarkProcessed();
                await uow.SaveChangesAsync(ct);

                logger.LogInformation(
                    "OutboxMessage processed. Type={Type} Id={Id}",
                    message.Type, message.Id);
            }
            catch (Exception ex) when (ex is not OperationCanceledException)
            {
                message.MarkFailed(ex.Message);
                await uow.SaveChangesAsync(ct);

                logger.LogError(ex,
                    "OutboxMessage failed. Type={Type} Id={Id} RetryCount={RetryCount}",
                    message.Type, message.Id, message.RetryCount);
            }
        }
    }
}