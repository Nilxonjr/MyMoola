// MyMoola.Application/Features/Transactions/OutboxHandlers/B2CTimeoutOutboxHandler.cs
using System.Data;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Transactions.Commands;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;
using MyMoola.Domain.Enums;


namespace MyMoola.Application.Features.Transactions.OutboxHandlers;

/// <summary>
/// Handles B2C timeout — Safaricom queue timed out before processing.
/// Returns locked crypto to user available balance.
/// </summary>
public sealed class B2CTimeoutOutboxHandler(
    ITransactionRepository transactions,
    IMpesaTransactionRepository mpesaTransactions,
    ILedgerService ledger,
    IUnitOfWork uow,
    ILogger<B2CTimeoutOutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.B2CTimeout;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<B2CTimeoutOutboxPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize B2CTimeoutOutboxPayload. MessageId={message.Id}");

        var mpesaTx = await mpesaTransactions
            .FindByConversationIDAsync(payload.ConversationID, ct)
            ?? throw new NotFoundException(
                nameof(MpesaTransaction), payload.ConversationID);

        var transaction = await transactions.FindByIdAsync(mpesaTx.TransactionId, ct)
            ?? throw new NotFoundException(
                nameof(Transaction), mpesaTx.TransactionId);

        // IDEMPOTENCY
        if (transaction.Status is TransactionStatus.Completed
                                or TransactionStatus.Failed)
        {
            logger.LogWarning(
                "B2C timeout for terminal transaction. Skipping. " +
                "TransactionId={TransactionId}",
                transaction.Id);
            return;
        }

        await using var dbTransaction = await uow.BeginTransactionAsync(
            IsolationLevel.Serializable, ct);

        try
        {
            // Return crypto to user
            await ledger.UnlockAsync(
                payload.UserWalletId,
                payload.CryptoAmount,
                transaction.Id,
                ct);

            mpesaTx.Timeout();
            transaction.MarkFailed();

            await uow.SaveChangesAsync(ct);
            await dbTransaction.CommitAsync(ct);

            logger.LogWarning(
                "B2C timeout handled. TransactionId={TransactionId} " +
                "Crypto returned to user.",
                transaction.Id);
        }
        catch (Exception ex)
        {
            await dbTransaction.RollbackAsync(ct);
            logger.LogError(ex,
                "B2C timeout handling failed. TransactionId={TransactionId}",
                transaction.Id);
            throw;
        }
    }
}