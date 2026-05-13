using MyMoola.Domain.Common;
using MyMoola.Domain.Enums;

namespace MyMoola.Domain.Entities;

public sealed class ExchangeRate : BaseEntity
{
    public Currency Currency { get; private set; }
    public decimal RateKes { get; private set; }
    public decimal RateUsd { get; private set; }
    public decimal BuyRateKes { get; private set; }
    public decimal SellRateKes { get; private set; }
    public decimal SpreadPercent { get; private set; }
    public string Source { get; private set; } = null!;   // binance | coingecko | manual_override
    public DateTimeOffset FetchedAt { get; private set; }

    private ExchangeRate() { }

    public static ExchangeRate Create(
        Currency currency,
        decimal rateKes,
        decimal rateUsd,
        decimal spreadPercent,
        string source)
    {
        var spread = spreadPercent / 100;

        return new ExchangeRate
        {
            Currency = currency,
            RateKes = rateKes,
            RateUsd = rateUsd,
            BuyRateKes = rateKes * (1 + spread),
            SellRateKes = rateKes * (1 - spread),
            SpreadPercent = spreadPercent,
            Source = source,
            FetchedAt = DateTimeOffset.UtcNow
        };
    }
}
