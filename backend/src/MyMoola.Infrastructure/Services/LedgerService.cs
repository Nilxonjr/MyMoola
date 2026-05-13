using System.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Exceptions;
using MyMoola.Infrastructure.Persistence;

namespace MyMoola.Infrastructure.Services;

public sealed class LedgerService(
    AppDbContext db,
    ILogger<LedgerService> logger) : ILedgerService
{
    public Task CreditAsync(Guid walletId, decimal amount, Guid transactionId, CancellationToken ct = default)
        => ExecuteAsync(walletId, transactionId, ct, wallet => wallet.Credit(amount, transactionId));

    public Task DebitAsync(Guid walletId, decimal amount, Guid transactionId, CancellationToken ct = default)
        => ExecuteAsync(walletId, transactionId, ct, wallet => wallet.Debit(amount, transactionId));

    public Task LockAsync(Guid walletId, decimal amount, Guid transactionId, CancellationToken ct = default)
        => ExecuteAsync(walletId, transactionId, ct, wallet => wallet.Lock(amount, transactionId));

    public Task UnlockAsync(Guid walletId, decimal amount, Guid transactionId, CancellationToken ct = default)
        => ExecuteAsync(walletId, transactionId, ct, wallet => wallet.Unlock(amount, transactionId));

    public Task UnlockAndDebitAsync(Guid walletId, decimal amount, Guid transactionId, CancellationToken ct = default)
        => ExecuteAsync(walletId, transactionId, ct, wallet => wallet.UnlockAndDebit(amount, transactionId));

    private async Task ExecuteAsync(
        Guid walletId,
        Guid transactionId,
        CancellationToken ct,
        Action<Domain.Entities.Wallet> domainAction)
    {
        var ownsTransaction = db.Database.CurrentTransaction is null;

        if (ownsTransaction)
        {
            await using var transaction = await db.Database
                .BeginTransactionAsync(IsolationLevel.Serializable, ct);
            try
            {
                await AttemptAsync(walletId, domainAction, ct);
                await transaction.CommitAsync(ct);
            }
            catch (DbUpdateConcurrencyException ex)
            {
                await transaction.RollbackAsync(ct);

                logger.LogWarning(
                    "Concurrency conflict on wallet {WalletId} for transaction {TransactionId}. Retrying once.",
                    walletId, transactionId);

                foreach (var entry in ex.Entries)
                    entry.State = EntityState.Detached;

                await using var retryTransaction = await db.Database
                    .BeginTransactionAsync(IsolationLevel.Serializable, ct);
                try
                {
                    await AttemptAsync(walletId, domainAction, ct);
                    await retryTransaction.CommitAsync(ct);
                }
                catch (DbUpdateConcurrencyException)
                {
                    await retryTransaction.RollbackAsync(ct);
                    logger.LogError(
                        "Concurrency conflict on wallet {WalletId} for transaction {TransactionId} failed after retry.",
                        walletId, transactionId);
                    throw new ConcurrencyException();
                }
                catch (Exception)
                {
                    await retryTransaction.RollbackAsync(ct);
                    throw;
                }
            }
            catch (Exception)
            {
                await transaction.RollbackAsync(ct);
                throw;
            }
        }
        else
        {
            // Outer transaction owned by handler — participate without committing
            // Retry is not applicable here — handler owns rollback on failure
            await AttemptAsync(walletId, domainAction, ct);
        }
    }

    private async Task AttemptAsync(
        Guid walletId,
        Action<Domain.Entities.Wallet> domainAction,
        CancellationToken ct)
    {
        var wallet = await db.Wallets
            .FirstOrDefaultAsync(w => w.Id == walletId, ct)
            ?? throw new NotFoundException(nameof(Domain.Entities.Wallet), walletId);

        domainAction(wallet);

        await db.SaveChangesAsync(ct);
    }
}