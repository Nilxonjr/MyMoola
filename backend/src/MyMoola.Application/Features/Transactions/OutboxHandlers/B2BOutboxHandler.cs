// MyMoola.Application/Features/Transactions/OutboxHandlers/B2BOutboxHandler.cs
using System.Text.Json;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Transactions.Commands;
using MyMoola.Application.Features.Transactions.Handlers;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Transactions.OutboxHandlers;

/// <summary>
/// Picks up B2BPayment outbox messages and calls the correct
/// Safaricom API based on MerchantType.
/// IDEMPOTENT — checks ConversationId before firing.
/// </summary>
public sealed class B2BOutboxHandler(
    IMpesaService mpesa,
    IMpesaTransactionRepository mpesaTransactions,
    IUnitOfWork uow,
    ILogger<B2BOutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.B2BPayment;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<B2BPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize B2BPayload. MessageId={message.Id}");

        // IDEMPOTENCY — if ConversationId already set, Safaricom was already called
        var mpesaTx = await mpesaTransactions.FindByIdAsync(
            payload.MpesaTransactionId, ct)
            ?? throw new NotFoundException(
                nameof(MpesaTransaction), payload.MpesaTransactionId);

        if (mpesaTx.ConversationId is not null)
        {
            logger.LogWarning(
                "B2B already initiated. Skipping. " +
                "MpesaTransactionId={Id} ConversationId={ConversationId}",
                payload.MpesaTransactionId, mpesaTx.ConversationId);
            return;
        }

        // Dispatch to correct Safaricom API based on merchant type
        var result = payload.MerchantType switch
        {
            MerchantPaymentType.Paybill => await mpesa.InitiateB2BPaybillAsync(
                paybillNumber: payload.MerchantNumber,
                accountNumber: payload.AccountReference,
                amountKes: payload.AmountKes,
                remarks: $"PAY {payload.ReferenceCode}",
                ct: ct),

            MerchantPaymentType.Till => await mpesa.InitiateB2BTillAsync(
                tillNumber: payload.MerchantNumber,
                amountKes: payload.AmountKes,
                remarks: $"PAY {payload.ReferenceCode}",
                ct: ct),

            _ => throw new ArgumentOutOfRangeException(nameof(payload.MerchantType))
        };

        mpesaTx.SetConversationIds(
            result.ConversationId,
            result.OriginatorConversationId);

        await uow.SaveChangesAsync(ct);

        logger.LogInformation(
            "B2B fired. MpesaTransactionId={Id} MerchantType={Type} " +
            "ConversationId={ConversationId}",
            payload.MpesaTransactionId, payload.MerchantType,
            result.ConversationId);
    }
}