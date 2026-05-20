using MediatR;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Rates.Queries;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Rates.Handlers;

public sealed class GetRateHistoryHandler(
    IExchangeRateRepository exchangeRates)
    : IRequestHandler<GetRateHistoryQuery, GetRateHistoryResponse>
{
    private static readonly IReadOnlyDictionary<string, TimeSpan> RangeMap =
        new Dictionary<string, TimeSpan>(StringComparer.OrdinalIgnoreCase)
        {
            ["24h"] = TimeSpan.FromHours(24),
            ["7d"] = TimeSpan.FromDays(7),
            ["30d"] = TimeSpan.FromDays(30)
        };

    public async Task<GetRateHistoryResponse> Handle(GetRateHistoryQuery request, CancellationToken ct)
    {
        var currencies = ParseCurrencies(request.Currencies);
        var rangeValue = request.Range?.Trim();
        if (string.IsNullOrWhiteSpace(rangeValue) || !RangeMap.TryGetValue(rangeValue, out var span))
            throw new InvalidOperationException("Range must be one of: 24h, 7d, 30d.");

        var interval = NormalizeInterval(request.Interval);

        var now = DateTimeOffset.UtcNow;
        var from = now.Subtract(span);
        var raw = await exchangeRates.GetHistoryAsync(currencies, from, now, ct);

        var series = raw
            .GroupBy(r => r.Currency)
            .OrderBy(g => g.Key.ToString())
            .Select(group =>
            {
                var points = BucketPoints(group, interval)
                    .Select(x => new RatePointDto(
                        Timestamp: x.Timestamp,
                        KesRate: x.KesRate))
                    .ToList();

                return new RateSeriesDto(
                    Currency: group.Key.ToString(),
                    Points: points);
            })
            .ToList();

        return new GetRateHistoryResponse(
            GeneratedAt: now,
            Range: rangeValue.ToLowerInvariant(),
            Interval: interval,
            Series: series);
    }

    private static IReadOnlyList<Currency> ParseCurrencies(string rawCurrencies)
    {
        if (string.IsNullOrWhiteSpace(rawCurrencies))
            throw new InvalidOperationException("At least one currency is required.");

        var parsed = rawCurrencies
            .Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .Select(value =>
            {
                var ok = Enum.TryParse<Currency>(value, ignoreCase: true, out var currency);
                if (!ok)
                    throw new InvalidOperationException($"Unsupported currency: {value}.");
                return currency;
            })
            .ToList();

        if (parsed.Count == 0)
            throw new InvalidOperationException("At least one currency is required.");

        return parsed;
    }

    private static string NormalizeInterval(string rawInterval)
    {
        var normalized = rawInterval?.Trim().ToLowerInvariant();
        return normalized switch
        {
            "hour" => "hour",
            "day" => "day",
            _ => "day"
        };
    }

    private static IReadOnlyList<(DateTimeOffset Timestamp, decimal KesRate)> BucketPoints(
        IEnumerable<Domain.Entities.ExchangeRate> items,
        string interval)
    {
        if (interval == "hour")
        {
            return items
                .GroupBy(x => new DateTimeOffset(
                    x.FetchedAt.Year,
                    x.FetchedAt.Month,
                    x.FetchedAt.Day,
                    x.FetchedAt.Hour,
                    0,
                    0,
                    TimeSpan.Zero))
                .Select(g =>
                {
                    var latest = g.OrderByDescending(x => x.FetchedAt).First();
                    return (Timestamp: g.Key, KesRate: latest.RateKes);
                })
                .OrderBy(x => x.Timestamp)
                .ToList();
        }

        return items
            .GroupBy(x => new DateTimeOffset(
                x.FetchedAt.Year,
                x.FetchedAt.Month,
                x.FetchedAt.Day,
                0,
                0,
                0,
                TimeSpan.Zero))
            .Select(g =>
            {
                var latest = g.OrderByDescending(x => x.FetchedAt).First();
                return (Timestamp: g.Key, KesRate: latest.RateKes);
            })
            .OrderBy(x => x.Timestamp)
            .ToList();
    }
}
