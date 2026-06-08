namespace MyMoola.Infrastructure.Settings;

public sealed class AlchemyOptions
{
    public const string Section = "Alchemy";

    public string ApiKey { get; init; } = string.Empty;

    /// <summary>
    /// Base RPC URL e.g. https://eth-sepolia.g.alchemy.com/v2/
    /// </summary>
    public string BaseUrl { get; init; } = string.Empty;

    /// <summary>
    /// HMAC-SHA256 signing key used to validate incoming Alchemy webhook payloads.
    /// </summary>
    public string WebhookSigningKey { get; init; } = string.Empty;

    /// <summary>
    /// Alchemy webhook ID used when registering new deposit addresses.
    /// </summary>
    public string WebhookId { get; init; } = string.Empty;

    public string AuthToken { get; init; } = string.Empty;

    /// <summary>
    /// Confirmations required before a deposit is credited. Default 12.
    /// </summary>
    public int DepositConfirmationThreshold { get; init; } = 12;

    /// <summary>
    /// Confirmations required before a withdrawal is considered settled. Default 12.
    /// </summary>
    public int WithdrawalConfirmationThreshold { get; init; } = 12;
}