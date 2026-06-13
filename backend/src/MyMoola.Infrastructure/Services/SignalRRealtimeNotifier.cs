using Microsoft.AspNetCore.SignalR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;

namespace MyMoola.Infrastructure.Services;

public sealed class SignalRRealtimeNotifier<THub>(
    IHubContext<THub> hubContext,
    ILogger<SignalRRealtimeNotifier<THub>> logger) : IRealtimeNotifier
    where THub : Hub
{
    public async Task NotifyWalletCreditedAsync(
        Guid userId,
        decimal amount,
        string currency,
        decimal availableBalance,
        Guid transactionId,
        CancellationToken ct = default)
    {
        await hubContext.Clients
            .User(userId.ToString())
            .SendAsync("WalletCredited", new
            {
                transactionId,
                amount,
                currency,
                availableBalance,
                timestamp = DateTimeOffset.UtcNow
            }, ct);

        logger.LogDebug(
            "SignalR WalletCredited sent. UserId={UserId} Amount={Amount} Currency={Currency}",
            userId, amount, currency);
    }
}