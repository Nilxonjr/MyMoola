// MyMoola.Infrastructure/Settings/ExchangeRateSettings.cs
namespace MyMoola.Infrastructure.Settings;

public sealed class ExchangeRateSettings
{
    public const string Section = "ExternalApis";

    public string CoinGeckoApiKey { get; init; } = null!;
    public BinanceSettings Binance { get; init; } = null!;
    public CoinGeckoSettings CoinGecko { get; init; } = null!;
    public string ForexBaseUrl { get; init; } = null!;
    public double RateRefreshIntervalMinutes { get; init; } = 1.5;
    public double RateStalenessThresholdMinutes { get; init; } = 5;
    public decimal SpreadPercent { get; init; } = 1.5m;
}

public sealed class BinanceSettings
{
    public string BaseUrl { get; init; } = null!;
    public int TimeoutSeconds { get; init; } = 5;
}

public sealed class CoinGeckoSettings
{
    public string BaseUrl { get; init; } = null!;
    public int TimeoutSeconds { get; init; } = 8;
}