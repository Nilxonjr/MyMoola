using MyMoola.Domain.Common;
using MyMoola.Domain.Enums;
namespace MyMoola.Domain.Entities;

public sealed class LedgerEntry : BaseEntity
{
    public Guid TransactionId { get; private set; }
    public Guid WalletId { get; private set; }
    public LedgerEntryType EntryType { get; private set; }  // credit | debit | lock | unlock
    public decimal Amount { get; private set; }
    public decimal BalanceBefore { get; private set; }
    public decimal BalanceAfter { get; private set; }

    // EF Core
    private LedgerEntry() { }

    public static LedgerEntry Create(
        Guid transactionId,
        Guid walletId,
        LedgerEntryType entryType,
        decimal amount,
        decimal balanceBefore,
        decimal balanceAfter)
    {
        if (amount <= 0)
            throw new ArgumentException("Ledger entry amount must be positive.", nameof(amount));

        return new LedgerEntry
        {
            TransactionId = transactionId,
            WalletId = walletId,
            EntryType = entryType,
            Amount = amount,
            BalanceBefore = balanceBefore,
            BalanceAfter = balanceAfter
        };
    }
}
