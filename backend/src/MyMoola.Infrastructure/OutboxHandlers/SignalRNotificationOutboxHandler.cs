using System.Text.Json;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Wallet.Handlers;
using MyMoola.Domain.Entities;

namespace MyMoola.Infrastructure.OutboxHandlers;

/// <summary>
/// Processes WalletCredited outbox messages.
/// Calls IRealtimeNotifier which pushes via SignalR to the user's device.
/// Runs in OutboxProcessor background service — completely decoupled from
/// the HTTP request that triggered the wallet credit.
/// If SignalR fails, OutboxProcessor retries based on RetryCount policy.
/// </summary>
public sealed class SignalRNotificationOutboxHandler(
    IRealtimeNotifier notifier,
    ILogger<SignalRNotificationOutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.WalletCredited;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<WalletCreditedOutboxPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize WalletCreditedOutboxPayload. MessageId={message.Id}");

        await notifier.NotifyWalletCreditedAsync(
            userId: payload.UserId,
            amount: payload.Amount,
            currency: payload.Currency,
            availableBalance: payload.AvailableBalance,
            transactionId: payload.TransactionId,
            ct: ct);

        logger.LogInformation(
            "SignalR notification delivered. UserId={UserId} TransactionId={TransactionId}",
            payload.UserId, payload.TransactionId);
    }
}