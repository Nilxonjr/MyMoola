using MyMoola.Domain.Common;
using MyMoola.Domain.Enums;

namespace MyMoola.Domain.Entities;

public sealed class TreasuryPosition : BaseEntity
{
    public Currency Currency { get; private set; }
    public decimal Balance { get; private set; }
    public decimal KesReserve { get; private set; }
    public decimal CoverageRatio { get; private set; }
    public bool BuyHalted { get; private set; }
    public DateTimeOffset? LastRebalancedAt { get; private set; }
    public DateTimeOffset? LastSyncedAt { get; private set; }

    private TreasuryPosition() { }

    public static TreasuryPosition Seed(Currency currency)
    {
        return new TreasuryPosition
        {
            Currency = currency,
            Balance = 0,
            KesReserve = 0,
            CoverageRatio = 0,
            BuyHalted = false
        };
    }

    public void UpdateBalance(decimal onChainBalance, decimal userWalletsTotal)
    {
        Balance = onChainBalance;
        CoverageRatio = userWalletsTotal == 0 ? 0 : Balance / userWalletsTotal;
        LastSyncedAt = DateTimeOffset.UtcNow;

        BuyHalted = CoverageRatio < 1.02m;
    }

    public void AddKesReserve(decimal amount)
    {
        KesReserve += amount;
    }

    public void DeductKesReserve(decimal amount)
    {
        KesReserve -= amount;
    }

    public void RecordRebalance()
    {
        LastRebalancedAt = DateTimeOffset.UtcNow;
    }

    public void OverrideBuyHalt(bool halted)
    {
        BuyHalted = halted;
    }
}
