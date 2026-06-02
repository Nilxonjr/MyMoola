// MyMoola.Application/Features/Transactions/OutboxHandlers/PochiOutboxHandler.cs
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
/// Handles Pochi la Biashara and SendMoney outbox messages.
/// Uses B2C API — callbacks arrive at B2CCallbackUrl.
/// Separate from B2BOutboxHandler to keep callback routing clean.
/// IDEMPOTENT — checks ConversationId before firing.
/// </summary>
public sealed class PochiOutboxHandler(
    IMpesaService mpesa,
    IMpesaTransactionRepository mpesaTransactions,
    IUnitOfWork uow,
    ILogger<PochiOutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.PochiPayment;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        PropertyNameCaseInsensitive = true
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<B2BPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize B2BPayload for Pochi. MessageId={message.Id}");

        // IDEMPOTENCY
        var mpesaTx = await mpesaTransactions.FindByIdAsync(
            payload.MpesaTransactionId, ct)
            ?? throw new NotFoundException(
                nameof(MpesaTransaction), payload.MpesaTransactionId);

        if (mpesaTx.ConversationId is not null)
        {
            logger.LogWarning(
                "Pochi already initiated. Skipping. " +
                "MpesaTransactionId={Id} ConversationId={ConversationId}",
                payload.MpesaTransactionId, mpesaTx.ConversationId);
            return;
        }

        B2BResult result;

        if (payload.MerchantType == MerchantPaymentType.Pochi)
        {
            var pochiResult = await mpesa.InitiatePochiAsync(
                phoneNumber: payload.MerchantNumber,
                amountKes: payload.AmountKes,
                remarks: $"PAY {payload.ReferenceCode}",
                ct: ct);

            result = new B2BResult(
                pochiResult.ConversationId,
                pochiResult.OriginatorConversationId);
        }
        else
        {
            var sendResult = await mpesa.InitiateSendMoneyAsync(
                phoneNumber: payload.MerchantNumber,
                amountKes: payload.AmountKes,
                remarks: $"PAY {payload.ReferenceCode}",
                ct: ct);

            result = new B2BResult(
                sendResult.ConversationId,
                sendResult.OriginatorConversationId);
        }

        mpesaTx.SetConversationIds(
            result.ConversationId,
            result.OriginatorConversationId);

        await uow.SaveChangesAsync(ct);

        logger.LogInformation(
            "Pochi/SendMoney fired. MpesaTransactionId={Id} " +
            "MerchantType={Type} ConversationId={ConversationId}",
            payload.MpesaTransactionId,
            payload.MerchantType,
            result.ConversationId);
    }
}