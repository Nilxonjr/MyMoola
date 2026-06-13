using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Events;

namespace MyMoola.Application.Features.Wallet.Handlers;

/// <summary>
/// Handles WalletCreditedEvent by enqueuing a real-time notification
/// to the outbox. Runs inside AppDbContext.SaveChangesAsync before commit —
/// the outbox message lands in the same atomic transaction as the wallet
/// balance update and ledger entry.
/// OutboxProcessor fires SignalRNotificationOutboxHandler asynchronously.
/// </summary>
public sealed class WalletNotificationHandler(
    IOutboxService outbox,
    ILogger<WalletNotificationHandler> logger) : INotificationHandler<WalletCreditedEvent>
{
    public async Task Handle(WalletCreditedEvent evt, CancellationToken ct)
    {
        await outbox.EnqueueAsync(
            OutboxMessageTypes.WalletCredited,
            new WalletCreditedOutboxPayload(
                UserId: evt.UserId,
                TransactionId: evt.TransactionId,
                Amount: evt.Amount,
                Currency: evt.Currency.ToString(),
                AvailableBalance: evt.AvailableBalanceAfter),
            ct);

        logger.LogDebug(
            "WalletCredited notification enqueued. UserId={UserId} TransactionId={TransactionId}",
            evt.UserId, evt.TransactionId);
    }
}

public sealed record WalletCreditedOutboxPayload(
    Guid UserId,
    Guid TransactionId,
    decimal Amount,
    string Currency,
    decimal AvailableBalance);