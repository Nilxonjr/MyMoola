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
/// Handles on-chain withdrawal failure — dropped or reverted transaction.
/// Unlocks user funds — crypto returned to available balance.
/// IDEMPOTENT — checks Transaction.Status before executing.
/// </summary>
public sealed class WithdrawalFailedOutboxHandler(
    ITransactionRepository transactions,
    ILedgerService ledger,
    IUnitOfWork uow,
    ILogger<WithdrawalFailedOutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.WithdrawalFailed;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<WithdrawalFailedOutboxPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize WithdrawalFailedOutboxPayload. MessageId={message.Id}");

        var transaction = await transactions.FindByIdAsync(payload.TransactionId, ct)
            ?? throw new NotFoundException(nameof(Transaction), payload.TransactionId);

        // IDEMPOTENCY
        if (transaction.Status is TransactionStatus.Failed)
        {
            logger.LogWarning(
                "Withdrawal already failed. Skipping. " +
                "TransactionId={TransactionId}", transaction.Id);
            return;
        }

        await using var dbTransaction = await uow.BeginTransactionAsync(
            IsolationLevel.Serializable, ct);

        try
        {
            // Unlock user funds — crypto returned to available balance
            await ledger.UnlockAsync(
                payload.UserWalletId,
                payload.Amount,
                transaction.Id,
                ct);

            transaction.MarkFailed();

            await uow.SaveChangesAsync(ct);
            await dbTransaction.CommitAsync(ct);

            logger.LogWarning(
                "Withdrawal failed. Funds unlocked. " +
                "TransactionId={TransactionId} Reason={Reason}",
                transaction.Id, payload.Reason);
        }
        catch (Exception ex)
        {
            await dbTransaction.RollbackAsync(ct);
            logger.LogError(ex,
                "Withdrawal failure handling failed. " +
                "TransactionId={TransactionId}", transaction.Id);
            throw;
        }
    }
}