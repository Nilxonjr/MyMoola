// MyMoola.Application/Common/Services/FeeCalculator.cs
namespace MyMoola.Application.Common.Helpers;

/// <summary>
/// All fee arithmetic for buy and sell flows.
///
/// Buy flow:
///   User pays gross KES via STK Push.
///   Platform fee (1.5%) deducted from gross KES → RevenueAccount [KES]
///   Net KES converted to crypto at BuyRate.
///   Spread is the difference between gross crypto at MarketRate
///   and net crypto at BuyRate → SpreadRevenueAccount [crypto]
///
/// Sell flow:
///   User sells crypto, receives KES.
///   Gross KES = crypto amount × MarketRate (fee base — independent of spread)
///   Platform fee (1.5%) deducted from gross KES → RevenueAccount [KES]
///   Spread (0.5%) deducted from gross KES → SpreadRevenueAccount [KES]
///   Remaining KES floored to integer → sent via B2C
///   Residual (sub-integer KES) → SuspenseAccount [KES]
///
/// Both fees always applied to gross KES market value independently.
/// They never compound on each other.
/// </summary>
public static class FeeCalculator
{
    public const decimal PlatformFeeRate = 0.015m;  // 1.5%
    public const decimal SpreadRate = 0.005m;  // 0.5%

    // -------------------------------------------------------------------------
    // Buy
    // -------------------------------------------------------------------------

    public static BuyFeeResult CalculateBuy(
        decimal grossKes,
        decimal marketRate,
        decimal buyRate)
    {
        // Fee on gross KES — independent of spread
        var platformFeeKes = decimal.Round(
            grossKes * PlatformFeeRate,
            4,
            MidpointRounding.AwayFromZero);

        var netKes = grossKes - platformFeeKes;

        // Gross crypto released from treasury at MarketRate
        var grossCrypto = decimal.Round(
            netKes / marketRate,
            8,
            MidpointRounding.ToEven);

        // Net crypto user receives at BuyRate (worse rate = platform keeps difference)
        var userCrypto = decimal.Round(
            netKes / buyRate,
            8,
            MidpointRounding.ToEven);

        // Spread retained in crypto — explicit, not implied
        var spreadCrypto = decimal.Round(
            grossCrypto - userCrypto,
            8,
            MidpointRounding.ToEven);

        return new BuyFeeResult(
            GrossKes: grossKes,
            PlatformFeeKes: platformFeeKes,
            NetKes: netKes,
            GrossCrypto: grossCrypto,
            UserCrypto: userCrypto,
            SpreadCrypto: spreadCrypto);
    }

    // -------------------------------------------------------------------------
    // Sell
    // -------------------------------------------------------------------------

    public static SellFeeResult CalculateSell(
        decimal cryptoAmount,
        decimal marketRate)
    {
        // Gross KES at MarketRate — this is the fee base for both fees
        var grossKes = decimal.Round(
            cryptoAmount * marketRate,
            4,
            MidpointRounding.ToEven);

        // Platform fee on gross KES — independent of spread
        var platformFeeKes = decimal.Round(
            grossKes * PlatformFeeRate,
            4,
            MidpointRounding.AwayFromZero);

        // Spread on gross KES — independent of fee
        var spreadKes = decimal.Round(
            grossKes * SpreadRate,
            4,
            MidpointRounding.AwayFromZero);

        // Target payout before flooring
        var targetPayoutKes = grossKes - platformFeeKes - spreadKes;

        // Safaricom B2C requires integer KES — floor always (never round up)
        var b2cAmountKes = (int)Math.Floor(targetPayoutKes);

        // Residual stays in SuspenseAccount — never lost, never charged to user
        var residualKes = decimal.Round(
            targetPayoutKes - b2cAmountKes,
            4,
            MidpointRounding.ToEven);

        return new SellFeeResult(
            CryptoAmount: cryptoAmount,
            GrossKes: grossKes,
            PlatformFeeKes: platformFeeKes,
            SpreadKes: spreadKes,
            TargetPayoutKes: targetPayoutKes,
            B2CAmountKes: b2cAmountKes,
            ResidualKes: residualKes);
    }
}

// -------------------------------------------------------------------------
// Results
// -------------------------------------------------------------------------

/// <summary>
/// All values needed to execute the buy ledger entries.
/// Proof: GrossKes = PlatformFeeKes + NetKes
///        GrossCrypto = UserCrypto + SpreadCrypto
/// </summary>
public sealed record BuyFeeResult(
    decimal GrossKes,
    decimal PlatformFeeKes,
    decimal NetKes,
    decimal GrossCrypto,
    decimal UserCrypto,
    decimal SpreadCrypto);

/// <summary>
/// All values needed to execute the sell ledger entries.
/// Proof: GrossKes = PlatformFeeKes + SpreadKes + B2CAmountKes + ResidualKes
/// </summary>
public sealed record SellFeeResult(
    decimal CryptoAmount,
    decimal GrossKes,
    decimal PlatformFeeKes,
    decimal SpreadKes,
    decimal TargetPayoutKes,
    int B2CAmountKes,
    decimal ResidualKes);