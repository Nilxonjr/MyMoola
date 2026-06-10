using System.Data;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Crypto.DTOs;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Crypto.OutboxHandlers;

/// <summary>
/// Executes ledger entries once withdrawal is confirmed on-chain.
///
/// Ledger — journey pattern (credit=add, debit=subtract):
///   1. UnlockAndDebit  User Wallet      amount     ← user gives up crypto
///   2. Credit          HotWallet        amount     ← hotwallet receives from user
///   3. Debit           HotWallet        feeAmount  ← fee leaves hotwallet
///   4. Credit          RevenueAccount   feeAmount  ← revenue receives fee
///   5. Debit           HotWallet        netAmount  ← net amount left on-chain
///
/// IDEMPOTENT — checks Transaction.Status before executing.
/// </summary>
public sealed class WithdrawalConfirmedOutboxHandler(
    ITransactionRepository transactions,
    ILedgerService ledger,
    IUnitOfWork uow,
    ILogger<WithdrawalConfirmedOutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.WithdrawalConfirmed;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<WithdrawalConfirmedOutboxPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize WithdrawalConfirmedOutboxPayload. MessageId={message.Id}");

        var transaction = await transactions.FindByIdAsync(payload.TransactionId, ct)
            ?? throw new NotFoundException(nameof(Transaction), payload.TransactionId);

        // IDEMPOTENCY
        if (transaction.Status is TransactionStatus.Completed)
        {
            logger.LogWarning(
                "Withdrawal already completed. Skipping. " +
                "TransactionId={TransactionId}", transaction.Id);
            return;
        }

        await using var dbTransaction = await uow.BeginTransactionAsync(
            IsolationLevel.Serializable, ct);

        try
        {
            // 1. UnlockAndDebit user — crypto permanently removed
            await ledger.UnlockAndDebitAsync(
                payload.UserWalletId,
                payload.Amount,
                transaction.Id,
                ct);

            // 2. Credit HotWallet — receives from user
            await ledger.CreditAsync(
                payload.HotWalletId,
                payload.Amount,
                transaction.Id,
                ct);

            // 3. Debit HotWallet — fee leaves
            await ledger.DebitAsync(
                payload.HotWalletId,
                payload.FeeAmount,
                transaction.Id,
                ct);

            // 4. Credit RevenueAccount — fee received
            await ledger.CreditAsync(
                payload.RevenueWalletId,
                payload.FeeAmount,
                transaction.Id,
                ct);

            // 5. Debit HotWallet — net amount left on-chain
            await ledger.DebitAsync(
                payload.HotWalletId,
                payload.NetAmount,
                transaction.Id,
                ct);

            transaction.MarkCompleted();

            await uow.SaveChangesAsync(ct);
            await dbTransaction.CommitAsync(ct);

            logger.LogInformation(
                "Withdrawal completed. TransactionId={TransactionId} " +
                "Currency={Currency} Amount={Amount} Fee={Fee} " +
                "Net={Net} TxHash={TxHash}",
                transaction.Id, payload.Currency,
                payload.Amount, payload.FeeAmount,
                payload.NetAmount, payload.TxHash);
        }
        catch (Exception ex)
        {
            await dbTransaction.RollbackAsync(ct);
            logger.LogError(ex,
                "Withdrawal ledger failed. TransactionId={TransactionId}",
                transaction.Id);
            throw;
        }
    }
}