namespace MyMoola.Application.Common.Constants;

/// <summary>
/// Well-known GUIDs for system-owned virtual accounts.
/// Each maps to a User row + one Wallet row per applicable currency.
///
/// TreasuryAccount     — crypto float (USDC/BTC/ETH) + KES working capital
/// RevenueAccount      — 1.5% service fee income, KES only
/// SpreadRevenueAccount— 0.5% spread margin, KES (sells) + crypto (buys)
/// SettlementAccount   — M-Pesa inbound / B2C outbound, KES only
/// SuspenseAccount     — B2C floor residuals, KES only
/// </summary>
public static class SystemWallets
{
    public static readonly Guid TreasuryAccountUserId =
        new("00000000-0000-0000-0000-000000000001");

    public static readonly Guid RevenueAccountUserId =
        new("00000000-0000-0000-0000-000000000002");

    public static readonly Guid SpreadRevenueAccountUserId =
        new("00000000-0000-0000-0000-000000000003");

    public static readonly Guid SettlementAccountUserId =
        new("00000000-0000-0000-0000-000000000004");

    public static readonly Guid SuspenseAccountUserId =
        new("00000000-0000-0000-0000-000000000005");
}