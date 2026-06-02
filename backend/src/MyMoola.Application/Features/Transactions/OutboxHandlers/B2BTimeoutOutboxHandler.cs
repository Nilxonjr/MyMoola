// MyMoola.Application/Features/Transactions/OutboxHandlers/B2BTimeoutOutboxHandler.cs
using System.Data;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Transactions.Handlers;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Transactions.OutboxHandlers;

/// <summary>
/// Handles B2B queue timeout.
/// Queries Safaricom status before unlocking — prevents double payout
/// if Safaricom processes after timeout notification.
/// </summary>
public sealed class B2BTimeoutOutboxHandler(
    ITransactionRepository transactions,
    IMpesaTransactionRepository mpesaTransactions,
    IMpesaService mpesa,
    ILedgerService ledger,
    IUnitOfWork uow,
    ILogger<B2BTimeoutOutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.B2BTimeout;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<B2BTimeoutOutboxPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize B2BTimeoutOutboxPayload. MessageId={message.Id}");

        var mpesaTx = await mpesaTransactions
            .FindByConversationIDAsync(payload.ConversationID, ct)
            ?? throw new NotFoundException(
                nameof(MpesaTransaction), payload.ConversationID);

        var transaction = await transactions.FindByIdAsync(mpesaTx.TransactionId, ct)
            ?? throw new NotFoundException(
                nameof(Transaction), mpesaTx.TransactionId);

        if (transaction.Status is TransactionStatus.Completed
                                or TransactionStatus.Failed)
        {
            logger.LogWarning(
                "B2B timeout for terminal transaction. Skipping. " +
                "TransactionId={TransactionId}",
                transaction.Id);
            return;
        }

        // Query Safaricom before unlocking — defensive against double payout
        var status = await mpesa.QueryB2CStatusAsync(payload.ConversationID, ct);

        if (status.ResultCode == 0)
        {
            logger.LogWarning(
                "B2B timeout but Safaricom confirms success. " +
                "Waiting for result callback. TransactionId={TransactionId}",
                transaction.Id);
            return;
        }

        await using var dbTransaction = await uow.BeginTransactionAsync(
            IsolationLevel.Serializable, ct);

        try
        {
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
                "B2B timeout confirmed failed. Crypto unlocked. " +
                "TransactionId={TransactionId}",
                transaction.Id);
        }
        catch (Exception ex)
        {
            await dbTransaction.RollbackAsync(ct);
            logger.LogError(ex,
                "B2B timeout handling failed. TransactionId={TransactionId}",
                transaction.Id);
            throw;
        }
    }
}