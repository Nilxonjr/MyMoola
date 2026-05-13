using MyMoola.Domain.Common;
using MyMoola.Domain.Enums;

namespace MyMoola.Domain.Events;

public sealed record WalletCreditedEvent(
    Guid WalletId,
    Guid TransactionId,
    decimal Amount,
    decimal AvailableBalanceBefore,
    decimal AvailableBalanceAfter,
    decimal LockedBalanceBefore,
    decimal LockedBalanceAfter,
    Currency Currency) : IDomainEvent;

public sealed record WalletDebitedEvent(
    Guid WalletId,
    Guid TransactionId,
    decimal Amount,
    decimal AvailableBalanceBefore,
    decimal AvailableBalanceAfter,
    decimal LockedBalanceBefore,
    decimal LockedBalanceAfter,
    Currency Currency) : IDomainEvent;

public sealed record WalletLockedEvent(
    Guid WalletId,
    Guid TransactionId,
    decimal Amount,
    decimal AvailableBalanceBefore,
    decimal AvailableBalanceAfter,
    decimal LockedBalanceBefore,
    decimal LockedBalanceAfter,
    Currency Currency) : IDomainEvent;

public sealed record WalletUnlockedEvent(
    Guid WalletId,
    Guid TransactionId,
    decimal Amount,
    decimal AvailableBalanceBefore,
    decimal AvailableBalanceAfter,
    decimal LockedBalanceBefore,
    decimal LockedBalanceAfter,
    Currency Currency) : IDomainEvent;