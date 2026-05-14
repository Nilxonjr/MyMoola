using MyMoola.Domain.Common;

namespace MyMoola.Domain.Entities;

public sealed class RefreshToken : BaseEntity
{
    public Guid UserId { get; private set; }
    public string TokenHash { get; private set; } = null!;
    public DateTimeOffset ExpiresAt { get; private set; }
    public DateTimeOffset? RevokedAt { get; private set; }
    public bool IsRevoked => RevokedAt.HasValue;
    public bool IsExpired => DateTimeOffset.UtcNow >= ExpiresAt;
    public bool IsActive => !IsRevoked && !IsExpired;
    public uint Version { get; private set; }

    // EF Core
    private RefreshToken() { }

    public static RefreshToken Create(Guid userId, string tokenHash, int expiryDays = 30)
    {
        return new RefreshToken
        {
            UserId = userId,
            TokenHash = tokenHash,
            ExpiresAt = DateTimeOffset.UtcNow.AddDays(expiryDays),
        };
    }

    public void Revoke()
    {
        if (IsRevoked)
            return;

        RevokedAt = DateTimeOffset.UtcNow;
    }
}