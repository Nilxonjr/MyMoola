namespace MyMoola.Application.Common.Constants;

/// <summary>
/// Well-known GUIDs for system-owned wallets.
/// These are not real users — they are virtual accounts owned by the platform.
/// OperationalBuffer — holds crypto buffer for liquidity
/// PlatformFee — receives platform fees from transactions
/// </summary>
public static class SystemWallets
{
    // Operational buffer wallet user IDs — one per currency
    public static readonly Guid OperationalBufferUserId =
        new("00000000-0000-0000-0000-000000000001");

    // Platform fee wallet user ID — one per currency
    public static readonly Guid PlatformFeeUserId =
        new("00000000-0000-0000-0000-000000000002");
}