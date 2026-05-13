namespace MyMoola.Application.Common.Interfaces;

public interface ILedgerService
{
    Task CreditAsync(Guid walletId, decimal amount, Guid transactionId, CancellationToken ct = default);
    Task DebitAsync(Guid walletId, decimal amount, Guid transactionId, CancellationToken ct = default);
    Task LockAsync(Guid walletId, decimal amount, Guid transactionId, CancellationToken ct = default);
    Task UnlockAsync(Guid walletId, decimal amount, Guid transactionId, CancellationToken ct = default);
    Task UnlockAndDebitAsync(Guid walletId, decimal amount, Guid transactionId, CancellationToken ct = default);
}