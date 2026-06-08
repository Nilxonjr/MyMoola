using MyMoola.Domain.Common;
using MyMoola.Domain.Enums;

namespace MyMoola.Domain.Entities;

public sealed class DepositAddress : BaseEntity
{
    private DepositAddress() { }

    public Guid UserId { get; private set; }
    public Chain Chain { get; private set; }
    public string Address { get; private set; } = null!;
    public string DerivationPath { get; private set; } = null!;
    public int DerivationIndex { get; private set; }
    public bool IsActive { get; private set; } = true;
    public DateTimeOffset? LastUsedAt { get; private set; }
    public DateTimeOffset? LastCheckedAt { get; private set; }

    public string? PendingGasFundingTxHash { get; private set; }

    public static DepositAddress Create(
        Guid userId,
        Chain chain,
        string address,
        string derivationPath,
        int derivationIndex)
    {
        return new DepositAddress
        {
            UserId = userId,
            Chain = chain,
            Address = address,
            DerivationPath = derivationPath,
            DerivationIndex = derivationIndex,
            IsActive = true
        };
    }

    public void MarkUsed()
    {
        LastUsedAt = DateTimeOffset.UtcNow;
    }

    public void MarkChecked()
    {
        LastCheckedAt = DateTimeOffset.UtcNow;
    }

    public void Deactivate()
    {
        IsActive = false;
    }

    public void SetGasFundingTxHash(string txHash)
    {
        PendingGasFundingTxHash = txHash;
    }

    public void ClearGasFundingTxHash()
    {
        PendingGasFundingTxHash = null;
    }

}