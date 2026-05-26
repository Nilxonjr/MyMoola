namespace MyMoola.Application.Common.Interfaces;

public interface IRealtimeNotifier
{
    Task NotifyWalletCreditedAsync(
        Guid userId,
        decimal amount,
        string currency,
        decimal availableBalance,
        Guid transactionId,
        CancellationToken ct = default);
}