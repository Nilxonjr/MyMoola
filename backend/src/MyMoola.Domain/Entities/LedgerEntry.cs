using MyMoola.Domain.Common;
using MyMoola.Domain.Enums;

namespace MyMoola.Domain.Entities;

public sealed class LedgerEntry : BaseEntity
{
    private LedgerEntry() { }

    public Guid TransactionId { get; private set; }
    public Guid WalletId { get; private set; }
    public Currency Currency { get; private set; }
    public LedgerEntryType EntryType { get; private set; }
    public decimal Amount { get; private set; }
    public decimal AvailableBalanceBefore { get; private set; }
    public decimal AvailableBalanceAfter { get; private set; }
    public decimal LockedBalanceBefore { get; private set; }
    public decimal LockedBalanceAfter { get; private set; }

    /// <summary>
    /// Cross-entry continuity (AvailableAfter[n] == AvailableBefore[n+1]) is
    /// enforced by the reconciliation job — see tracked item #8.
    /// </summary>
    public static LedgerEntry Create(
        Guid transactionId,
        Guid walletId,
        Currency currency,
        LedgerEntryType entryType,
        decimal amount,
        decimal availableBalanceBefore,
        decimal availableBalanceAfter,
        decimal lockedBalanceBefore,
        decimal lockedBalanceAfter)
    {
        if (amount <= 0)
            throw new InvalidOperationException(
                "Ledger entry amount must be greater than zero.");

        return new LedgerEntry
        {
            TransactionId = transactionId,
            WalletId = walletId,
            Currency = currency,
            EntryType = entryType,
            Amount = amount,
            AvailableBalanceBefore = availableBalanceBefore,
            AvailableBalanceAfter = availableBalanceAfter,
            LockedBalanceBefore = lockedBalanceBefore,
            LockedBalanceAfter = lockedBalanceAfter
        };
    }
}