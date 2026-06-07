namespace MyMoola.Infrastructure.Settings;

public sealed class HdWalletOptions
{
    public const string Section = "Crypto";

    /// <summary>
    /// BIP-39 seed phrase. Stored ONLY in environment variable Crypto__HdWalletSeedPhrase.
    /// Never logged. Never stored in database. Never committed to source control.
    /// </summary>
    public string HdWalletSeedPhrase { get; init; } = string.Empty;

    /// <summary>
    /// Derivation index for the platform hot wallet. Default 0.
    /// User deposit addresses start at index 1.
    /// </summary>
    public int HotWalletDerivationIndex { get; init; } = 0;
    public string HotWalletAddress { get; init; } = string.Empty;
    public string TreasuryAddress { get; init; } = string.Empty;
}