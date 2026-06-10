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
using MyMoola.Infrastructure.Settings;
using MyMoola.Infrastructure.Persistence;

namespace MyMoola.Infrastructure.BackgroundJobs;

/// <summary>
/// Runs every 10 seconds. Polls confirmation count on all Processing
/// withdrawal transactions.
///
/// On threshold met + succeeded   → enqueues WithdrawalConfirmed
/// On threshold met + reverted    → enqueues WithdrawalFailed
/// On dropped from mempool        → enqueues WithdrawalFailed
/// </summary>
public sealed class WithdrawalConfirmationPollerJob(
    IServiceScopeFactory scopeFactory,
    ILogger<WithdrawalConfirmationPollerJob> logger) : BackgroundService
{
    private static readonly TimeSpan Interval = TimeSpan.FromSeconds(10);

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
                logger.LogError(ex, "WithdrawalConfirmationPollerJob error.");
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
        var options = scope.ServiceProvider
            .GetRequiredService<IOptions<AlchemyOptions>>().Value;

        var pending = await db.Transactions
            .Where(t => t.Type == TransactionType.Withdrawal
                     && t.Status == TransactionStatus.Processing
                     && t.OnChainTxHash != null)
            .ToListAsync(ct);

        if (pending.Count == 0) return;

        logger.LogInformation(
            "WithdrawalConfirmationPoller checking {Count} pending withdrawals.",
            pending.Count);

        foreach (var transaction in pending)
        {
            try
            {
                var confirmations = await blockchain
                    .GetConfirmationsAsync(transaction.OnChainTxHash!, ct);

                transaction.UpdateConfirmations(confirmations);

                // Not yet mined
                if (confirmations == 0)
                {
                    // If not mined after 10 minutes consider dropped
                    if (transaction.UpdatedAt < DateTimeOffset.UtcNow.AddMinutes(-10))
                    {
                        var existsOnChain = await blockchain
                            .TransactionExistsOnChainAsync(
                                transaction.OnChainTxHash!, ct);

                        if (!existsOnChain)
                        {
                            var walletForFail = await wallets
                                .FindByUserAndCurrencyAsync(
                                    transaction.InitiatorUserId!.Value,
                                    transaction.Currency, ct);

                            await outbox.EnqueueAsync(
                                OutboxMessageTypes.WithdrawalFailed,
                                new WithdrawalFailedOutboxPayload(
                                    TransactionId: transaction.Id,
                                    UserWalletId: walletForFail!.Id,
                                    Amount: transaction.Amount,
                                    Reason: "Transaction dropped from mempool."),
                                ct);

                            await uow.SaveChangesAsync(ct);

                            logger.LogWarning(
                                "Withdrawal dropped from mempool. " +
                                "TransactionId={TransactionId}", transaction.Id);
                            continue;
                        }
                    }

                    await uow.SaveChangesAsync(ct);
                    continue;
                }

                // Below threshold — still waiting
                if (confirmations < options.WithdrawalConfirmationThreshold)
                {
                    logger.LogDebug(
                        "Withdrawal not yet confirmed. " +
                        "TransactionId={TransactionId} " +
                        "Confirmations={Confirmations}/{Threshold}",
                        transaction.Id, confirmations,
                        options.WithdrawalConfirmationThreshold);

                    await uow.SaveChangesAsync(ct);
                    continue;
                }

                // Threshold met — verify not reverted
                var succeeded = await blockchain
                    .TransactionSucceededAsync(transaction.OnChainTxHash!, ct);

                if (!succeeded)
                {
                    var walletForFail = await wallets
                        .FindByUserAndCurrencyAsync(
                            transaction.InitiatorUserId!.Value,
                            transaction.Currency, ct);

                    await outbox.EnqueueAsync(
                        OutboxMessageTypes.WithdrawalFailed,
                        new WithdrawalFailedOutboxPayload(
                            TransactionId: transaction.Id,
                            UserWalletId: walletForFail!.Id,
                            Amount: transaction.Amount,
                            Reason: "Transaction reverted on-chain."),
                        ct);

                    await uow.SaveChangesAsync(ct);

                    logger.LogWarning(
                        "Withdrawal reverted on-chain. " +
                        "TransactionId={TransactionId}", transaction.Id);
                    continue;
                }

                // Confirmed and succeeded
                transaction.MarkConfirmed();

                var userWallet = await wallets.FindByUserAndCurrencyAsync(
                    transaction.InitiatorUserId!.Value, transaction.Currency, ct);

                var hotWallet = await wallets.FindByUserAndCurrencyAsync(
                    SystemWallets.HotWalletAccountUserId, transaction.Currency, ct);

                var revenueWallet = await wallets.FindByUserAndCurrencyAsync(
                    SystemWallets.RevenueAccountUserId, transaction.Currency, ct);

                if (userWallet is null || hotWallet is null || revenueWallet is null)
                {
                    logger.LogWarning(
                        "Wallets not found for confirmed withdrawal. " +
                        "TransactionId={TransactionId}", transaction.Id);
                    await uow.SaveChangesAsync(ct);
                    continue;
                }

                await outbox.EnqueueAsync(
                    OutboxMessageTypes.WithdrawalConfirmed,
                    new WithdrawalConfirmedOutboxPayload(
                        TransactionId: transaction.Id,
                        UserId: transaction.InitiatorUserId!.Value,
                        UserWalletId: userWallet.Id,
                        HotWalletId: hotWallet.Id,
                        RevenueWalletId: revenueWallet.Id,
                        Currency: transaction.Currency,
                        Amount: transaction.Amount,
                        FeeAmount: transaction.FeeAmount,
                        NetAmount: transaction.Amount - transaction.FeeAmount,
                        TxHash: transaction.OnChainTxHash!),
                    ct);

                await uow.SaveChangesAsync(ct);

                logger.LogInformation(
                    "WithdrawalConfirmed enqueued. " +
                    "TransactionId={TransactionId} " +
                    "Confirmations={Confirmations}",
                    transaction.Id, confirmations);
            }
            catch (Exception ex)
            {
                logger.LogError(ex,
                    "Error polling withdrawal. " +
                    "TransactionId={TransactionId}", transaction.Id);
            }
        }
    }
}