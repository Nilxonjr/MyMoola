using System.Text.Json;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Crypto.DTOs;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Crypto.OutboxHandlers;

public sealed class DepositDetectedOutboxHandler(
    ITransactionRepository transactions,
    ILogger<DepositDetectedOutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.DepositDetected;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<DepositDetectedOutboxPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize DepositDetectedOutboxPayload. MessageId={message.Id}");

        var transaction = await transactions.FindByIdAsync(payload.TransactionId, ct)
            ?? throw new NotFoundException(nameof(Transaction), payload.TransactionId);

        // Nothing to do financially yet — funds not confirmed on-chain.
        // DepositConfirmationPollerJob will push DepositConfirmed when threshold is met.
        logger.LogInformation(
            "Deposit detected. Awaiting confirmations. " +
            "TransactionId={TransactionId} TxHash={TxHash} " +
            "Currency={Currency} Amount={Amount} UserId={UserId}",
            transaction.Id, payload.TxHash,
            payload.Currency, payload.Amount, payload.UserId);
    }
}