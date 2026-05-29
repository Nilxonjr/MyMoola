// MyMoola.Application/Features/Transactions/OutboxHandlers/B2COutboxHandler.cs
using System.Text.Json;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Transactions.Commands;
using MyMoola.Application.Features.Transactions.Handlers;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Transactions.OutboxHandlers;

/// <summary>
/// Picks up B2C outbox messages and calls Safaricom.
/// IDEMPOTENT — checks whether ConversationId is already set
/// before firing. If set, Safaricom was already called — skip.
/// </summary>
public sealed class B2COutboxHandler(
    IMpesaService mpesa,
    IMpesaTransactionRepository mpesaTransactions,
    IUnitOfWork uow,
    ILogger<B2COutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.B2C;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<B2CPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize B2CPayload. MessageId={message.Id}");

        // IDEMPOTENCY — if ConversationId already set, Safaricom was already called
        var mpesaTx = await mpesaTransactions.FindByIdAsync(
            payload.MpesaTransactionId, ct)
            ?? throw new NotFoundException(
                nameof(MpesaTransaction), payload.MpesaTransactionId);

        if (mpesaTx.ConversationId is not null)
        {
            logger.LogWarning(
                "B2C already initiated. Skipping. " +
                "MpesaTransactionId={Id} ConversationId={ConversationId}",
                payload.MpesaTransactionId, mpesaTx.ConversationId);
            return;
        }

        // Call Safaricom B2C
        var result = await mpesa.InitiateB2CAsync(
            phoneNumber: payload.PhoneNumber,
            amountKes: payload.B2CAmountKes,
            remarks: $"Sell {payload.Currency} {payload.ReferenceCode}",
            ct: ct);

        // Store Safaricom correlation IDs
        mpesaTx.SetConversationIds(
            result.ConversationId,
            result.OriginatorConversationId);

        await uow.SaveChangesAsync(ct);

        logger.LogInformation(
            "B2C fired. MpesaTransactionId={Id} ConversationId={ConversationId}",
            payload.MpesaTransactionId, result.ConversationId);
    }
}