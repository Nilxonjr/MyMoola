using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Crypto.DTOs;
using MyMoola.Application.Interfaces;
using MyMoola.Domain.Enums;
using MyMoola.Infrastructure.Persistence;
using MyMoola.Infrastructure.Settings;

namespace MyMoola.Infrastructure.BackgroundJobs;

/// <summary>
/// Runs every 2 minutes. Queries Alchemy for confirmation counts on all
/// Processing deposit transactions. When threshold is met:
///   - Updates OnChainConfirmations
///   - Marks transaction Confirmed (prevents re-processing)
///   - Enqueues DepositConfirmed outbox message
/// </summary>
public sealed class DepositConfirmationPollerJob(
    IServiceScopeFactory scopeFactory,
    ILogger<DepositConfirmationPollerJob> logger) : BackgroundService
{
    private static readonly TimeSpan Interval = TimeSpan.FromMinutes(2);

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await PollAsync(stoppingToken);
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "DepositConfirmationPollerJob error.");
            }

            await Task.Delay(Interval, stoppingToken);
        }
    }

    private async Task PollAsync(CancellationToken ct)
    {
        using var scope = scopeFactory.CreateScope();

        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        var blockchain = scope.ServiceProvider.GetRequiredService<IBlockchainService>();
        var wallets = scope.ServiceProvider.GetRequiredService<IWalletRepository>();
        var outbox = scope.ServiceProvider.GetRequiredService<IOutboxService>();
        var uow = scope.ServiceProvider.GetRequiredService<IUnitOfWork>();
        var options = scope.ServiceProvider.GetRequiredService<IOptions<AlchemyOptions>>().Value;

        // No AsNoTracking — we mutate status and confirmations on this entity
        var pending = await db.Transactions
            .Where(t => t.Type == TransactionType.Deposit
                     && t.Status == TransactionStatus.Processing
                     && t.OnChainTxHash != null)
            .ToListAsync(ct);

        if (pending.Count == 0) return;

        logger.LogInformation(
            "DepositConfirmationPoller checking {Count} pending deposits.", pending.Count);

        foreach (var transaction in pending)
        {
            try
            {
                var confirmations = await blockchain
                    .GetConfirmationsAsync(transaction.OnChainTxHash!, ct);

                // Always update confirmation count so the record reflects reality
                transaction.UpdateConfirmations(confirmations);

                if (confirmations < options.DepositConfirmationThreshold)
                {
                    logger.LogDebug(
                        "Deposit not yet confirmed. TransactionId={TransactionId} " +
                        "Confirmations={Confirmations}/{Threshold}",
                        transaction.Id, confirmations,
                        options.DepositConfirmationThreshold);

                    await uow.SaveChangesAsync(ct);
                    continue;
                }

                // Threshold met — transition to Confirmed so poller never picks it up again
                transaction.MarkConfirmed();

                var userWallet = await wallets.FindByUserAndCurrencyAsync(
                    transaction.InitiatorUserId!.Value, transaction.Currency, ct);

                var hotWallet = await wallets.FindByUserAndCurrencyAsync(
                    SystemWallets.HotWalletAccountUserId, transaction.Currency, ct);

                if (userWallet is null || hotWallet is null)
                {
                    logger.LogWarning(
                        "Wallets not found for confirmed deposit. Skipping. " +
                        "TransactionId={TransactionId}", transaction.Id);
                    await uow.SaveChangesAsync(ct);
                    continue;
                }

                var depositAddress = await db.DepositAddresses
                    .FirstOrDefaultAsync(d =>
                        d.UserId == transaction.InitiatorUserId!.Value && d.IsActive, ct);

                if (depositAddress is null)
                {
                    logger.LogWarning(
                        "Deposit address not found. Skipping. " +
                        "TransactionId={TransactionId}", transaction.Id);
                    await uow.SaveChangesAsync(ct);
                    continue;
                }

                await outbox.EnqueueAsync(
                    OutboxMessageTypes.DepositConfirmed,
                    new DepositConfirmedOutboxPayload(
                        TransactionId: transaction.Id,
                        UserId: transaction.InitiatorUserId!.Value,
                        DepositAddressId: depositAddress.Id,
                        Currency: transaction.Currency,
                        Amount: transaction.Amount,
                        TxHash: transaction.OnChainTxHash!,
                        UserWalletId: userWallet.Id,
                        HotWalletId: hotWallet.Id),
                    ct);

                // Status update + outbox message saved atomically
                await uow.SaveChangesAsync(ct);

                logger.LogInformation(
                    "DepositConfirmed enqueued. TransactionId={TransactionId} " +
                    "Confirmations={Confirmations}",
                    transaction.Id, confirmations);
            }
            catch (Exception ex)
            {
                logger.LogError(ex,
                    "Error polling deposit. TransactionId={TransactionId}",
                    transaction.Id);
            }
        }
    }
}