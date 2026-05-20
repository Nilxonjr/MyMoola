// MyMoola.Infrastructure/Persistence/Repositories/ExchangeRateRepository.cs
using Microsoft.EntityFrameworkCore;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;

namespace MyMoola.Infrastructure.Persistence.Repositories;

public sealed class ExchangeRateRepository(AppDbContext db) : IExchangeRateRepository
{
    public async Task<ExchangeRate?> GetLatestAsync(Currency currency, CancellationToken ct = default)
        => await db.ExchangeRates
            .Where(r => r.Currency == currency)
            .OrderByDescending(r => r.FetchedAt)
            .FirstOrDefaultAsync(ct);

    public async Task<IReadOnlyList<ExchangeRate>> GetLatestAllAsync(CancellationToken ct = default)
    {
        // One row per currency — latest per currency using a subquery
        var currencies = Enum.GetValues<Currency>();
        var result = new List<ExchangeRate>();

        foreach (var currency in currencies)
        {
            var latest = await db.ExchangeRates
                .Where(r => r.Currency == currency)
                .OrderByDescending(r => r.FetchedAt)
                .FirstOrDefaultAsync(ct);

            if (latest is not null)
                result.Add(latest);
        }

        return result;
    }

    public async Task<IReadOnlyList<ExchangeRate>> GetHistoryAsync(
        IReadOnlyCollection<Currency> currencies,
        DateTimeOffset fromInclusive,
        DateTimeOffset toInclusive,
        CancellationToken ct = default)
        => await db.ExchangeRates
            .AsNoTracking()
            .Where(r =>
                currencies.Contains(r.Currency) &&
                r.FetchedAt >= fromInclusive &&
                r.FetchedAt <= toInclusive)
            .OrderBy(r => r.Currency)
            .ThenBy(r => r.FetchedAt)
            .ToListAsync(ct);

    public async Task AddAsync(ExchangeRate rate, CancellationToken ct = default)
        => await db.ExchangeRates.AddAsync(rate, ct);

    public async Task AddRangeAsync(IEnumerable<ExchangeRate> rates, CancellationToken ct = default)
        => await db.ExchangeRates.AddRangeAsync(rates, ct);
}
