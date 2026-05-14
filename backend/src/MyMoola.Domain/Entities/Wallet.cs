using MyMoola.Domain.Common;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Events;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Domain.Entities;

public sealed class Wallet : BaseEntity
{
    private Wallet() { }

    public Guid UserId { get; private set; }
    public Currency Currency { get; private set; }
    public decimal Balance { get; private set; }
    public decimal LockedBalance { get; private set; }
    public uint Version { get; private set; }
    public decimal TotalBalance => Balance + LockedBalance;

    public static Wallet Create(Guid userId, Currency currency)
    {
        return new Wallet
        {
            UserId = userId,
            Currency = currency,
            Balance = 0m,
            LockedBalance = 0m
        };
    }

    public void Credit(decimal amount, Guid transactionId)
    {
        if (amount <= 0)
            throw new InvalidOperationException("Credit amount must be greater than zero.");

        var availableBefore = Balance;
        var lockedBefore = LockedBalance;
        Balance += amount;

        AddDomainEvent(new WalletCreditedEvent(
            WalletId: Id,
            TransactionId: transactionId,
            Amount: amount,
            AvailableBalanceBefore: availableBefore,
            AvailableBalanceAfter: Balance,
            LockedBalanceBefore: lockedBefore,
            LockedBalanceAfter: LockedBalance,
            Currency: Currency));
    }

    public void Debit(decimal amount, Guid transactionId)
    {
        if (amount <= 0)
            throw new InvalidOperationException("Debit amount must be greater than zero.");

        if (Balance < amount)
            throw new InsufficientBalanceException();

        var availableBefore = Balance;
        var lockedBefore = LockedBalance;
        Balance -= amount;

        AddDomainEvent(new WalletDebitedEvent(
            WalletId: Id,
            TransactionId: transactionId,
            Amount: amount,
            AvailableBalanceBefore: availableBefore,
            AvailableBalanceAfter: Balance,
            LockedBalanceBefore: lockedBefore,
            LockedBalanceAfter: LockedBalance,
            Currency: Currency));
    }

    public void Lock(decimal amount, Guid transactionId)
    {
        if (amount <= 0)
            throw new InvalidOperationException("Lock amount must be greater than zero.");

        if (Balance < amount)
            throw new InsufficientBalanceException();

        var availableBefore = Balance;
        var lockedBefore = LockedBalance;
        Balance -= amount;
        LockedBalance += amount;

        AddDomainEvent(new WalletLockedEvent(
            WalletId: Id,
            TransactionId: transactionId,
            Amount: amount,
            AvailableBalanceBefore: availableBefore,
            AvailableBalanceAfter: Balance,
            LockedBalanceBefore: lockedBefore,
            LockedBalanceAfter: LockedBalance,
            Currency: Currency));
    }

    public void Unlock(decimal amount, Guid transactionId)
    {
        if (amount <= 0)
            throw new InvalidOperationException("Unlock amount must be greater than zero.");

        if (LockedBalance < amount)
            throw new InvalidOperationException("Unlock amount exceeds locked balance.");

        var availableBefore = Balance;
        var lockedBefore = LockedBalance;
        LockedBalance -= amount;
        Balance += amount;

        AddDomainEvent(new WalletUnlockedEvent(
            WalletId: Id,
            TransactionId: transactionId,
            Amount: amount,
            AvailableBalanceBefore: availableBefore,
            AvailableBalanceAfter: Balance,
            LockedBalanceBefore: lockedBefore,
            LockedBalanceAfter: LockedBalance,
            Currency: Currency));
    }

    public void UnlockAndDebit(decimal amount, Guid transactionId)
    {
        if (amount <= 0)
            throw new InvalidOperationException("Amount must be greater than zero.");

        if (LockedBalance < amount)
            throw new InvalidOperationException("UnlockAndDebit amount exceeds locked balance.");

        var availableBefore = Balance;
        var lockedBefore = LockedBalance;
        LockedBalance -= amount;

        AddDomainEvent(new WalletDebitedEvent(
            WalletId: Id,
            TransactionId: transactionId,
            Amount: amount,
            AvailableBalanceBefore: availableBefore,
            AvailableBalanceAfter: Balance,
            LockedBalanceBefore: lockedBefore,
            LockedBalanceAfter: LockedBalance,
            Currency: Currency));
    }
}