using MyMoola.Domain.Common;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Events;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Domain.Entities;

public sealed class Wallet : BaseEntity
{
    public Guid UserId { get; private set; }
    public Currency Currency { get; private set; }
    public decimal Balance { get; private set; }
    public decimal LockedBalance { get; private set; }
    public byte[]? RowVersion { get; private set; }

    // EF Core
    private Wallet() { }

    public static Wallet Create(Guid userId, Currency currency)
    {
        return new Wallet
        {
            UserId = userId,
            Currency = currency,
            Balance = 0,
            LockedBalance = 0
        };
    }

    public void Credit(decimal amount, Guid transactionId)
    {
        if (amount <= 0)
            throw new ArgumentException("Credit amount must be positive.", nameof(amount));

        var balanceBefore = Balance;
        Balance += amount;

        AddDomainEvent(new WalletCreditedEvent(Id, transactionId, amount, balanceBefore, Balance));
    }

    public void Debit(decimal amount, Guid transactionId)
    {
        if (amount <= 0)
            throw new ArgumentException("Debit amount must be positive.", nameof(amount));

        if (amount > Balance)
            throw new InsufficientBalanceException();

        var balanceBefore = Balance;
        Balance -= amount;

        AddDomainEvent(new WalletDebitedEvent(Id, transactionId, amount, balanceBefore, Balance));
    }

    public void Lock(decimal amount, Guid transactionId)
    {
        if (amount <= 0)
            throw new ArgumentException("Lock amount must be positive.", nameof(amount));

        if (amount > Balance)
            throw new InsufficientBalanceException("Insufficient balance to lock.");

        Balance -= amount;
        LockedBalance += amount;
        AddDomainEvent(new WalletLockedEvent(Id, transactionId, amount));
    }

    public void Unlock(decimal amount, Guid transactionId)
    {
        if (amount <= 0)
            throw new ArgumentException("Unlock amount must be positive.", nameof(amount));

        if (amount > LockedBalance)
            throw new InvalidOperationException("Cannot unlock more than the locked balance.");

        LockedBalance -= amount;
        Balance += amount;

        AddDomainEvent(new WalletUnlockedEvent(Id, transactionId, amount));
    }

    public void UnlockAndDebit(decimal amount, Guid transactionId)
    {
        if (amount <= 0)
            throw new ArgumentException("Amount must be positive.", nameof(amount));

        if (amount > LockedBalance)
            throw new InvalidOperationException("Cannot debit more than the locked balance.");

        var balanceBefore = Balance;
        LockedBalance -= amount;

        AddDomainEvent(new WalletDebitedEvent(Id, transactionId, amount, balanceBefore, Balance));
    }

    public decimal TotalBalance => Balance + LockedBalance;
}
