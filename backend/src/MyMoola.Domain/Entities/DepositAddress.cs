using MyMoola.Domain.Common;
using MyMoola.Domain.Enums;

namespace MyMoola.Domain.Entities;

public sealed class DepositAddress : BaseEntity
{
    public Guid UserId { get; private set; }
    public Currency Currency { get; private set; }
    public string Address { get; private set; } = null!;
    public string DerivationPath { get; private set; } = null!;
    public bool IsActive { get; private set; } = true;
    public DateTimeOffset? LastUsedAt { get; private set; }

    private DepositAddress() { }

    public static DepositAddress Create(Guid userId, Currency currency, string address, string derivationPath)
    {
        return new DepositAddress
        {
            UserId = userId,
            Currency = currency,
            Address = address,
            DerivationPath = derivationPath
        };
    }

    public void MarkUsed()
    {
        LastUsedAt = DateTimeOffset.UtcNow;
    }

    public void Deactivate()
    {
        IsActive = false;
    }
}
