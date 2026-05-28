// MyMoola.Application/Features/Transactions/OutboxHandlers/StkPushOutboxHandler.cs
using System.Text.Json;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Common.Constants;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;
using MyMoola.Application.Features.Transactions.Handlers;

namespace MyMoola.Application.Features.Transactions.OutboxHandlers;

/// <summary>
/// Picks up StkPush outbox messages and calls Safaricom.
/// IDEMPOTENT — checks whether CheckoutRequestId is already set
/// before firing. If it is, Safaricom was already called — skip.
/// </summary>
public sealed class StkPushOutboxHandler(
    IMpesaService mpesa,
    IMpesaTransactionRepository mpesaTransactions,
    IUnitOfWork uow,
    ILogger<StkPushOutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.StkPush;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<StkPushPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize StkPushPayload. MessageId={message.Id}");

        // IDEMPOTENCY — if CheckoutRequestId is already set, Safaricom was
        // already called successfully. Do not fire again.
        var mpesaTx = await mpesaTransactions.FindByIdAsync(
            payload.MpesaTransactionId, ct)
            ?? throw new NotFoundException(
                nameof(MpesaTransaction), payload.MpesaTransactionId);

        if (mpesaTx.CheckoutRequestId is not null)
        {
            logger.LogWarning(
                "STK Push already initiated. Skipping. " +
                "MpesaTransactionId={Id} CheckoutRequestId={CheckoutRequestId}",
                payload.MpesaTransactionId, mpesaTx.CheckoutRequestId);
            return;
        }

        // Call Safaricom
        var result = await mpesa.InitiateStkPushAsync(
            phoneNumber: payload.PhoneNumber,
            amountKes: payload.AmountKes,
            accountReference: payload.ReferenceCode,
            transactionDesc: $"Buy {payload.Currency}",
            ct: ct);

        // Update MpesaTransaction with Safaricom correlation IDs
        mpesaTx.SetCheckoutIds(result.CheckoutRequestId, result.MerchantRequestId);
        await uow.SaveChangesAsync(ct);

        logger.LogInformation(
            "STK Push fired. MpesaTransactionId={Id} CheckoutRequestId={CheckoutRequestId}",
            payload.MpesaTransactionId, result.CheckoutRequestId);
    }
}