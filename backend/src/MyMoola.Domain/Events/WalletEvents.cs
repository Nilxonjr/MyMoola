namespace MyMoola.Domain.Events;
using MyMoola.Domain.Common;

public sealed record WalletCreditedEvent(
    Guid WalletId,
    Guid TransactionId,
    decimal Amount,
    decimal BalanceBefore,
    decimal BalanceAfter) : IDomainEvent;

public sealed record WalletDebitedEvent(
    Guid WalletId,
    Guid TransactionId,
    decimal Amount,
    decimal BalanceBefore,
    decimal BalanceAfter) : IDomainEvent;

public sealed record WalletLockedEvent(
    Guid WalletId,
    Guid TransactionId,
    decimal Amount) : IDomainEvent;

public sealed record WalletUnlockedEvent(
    Guid WalletId,
    Guid TransactionId,
    decimal Amount) : IDomainEvent;
