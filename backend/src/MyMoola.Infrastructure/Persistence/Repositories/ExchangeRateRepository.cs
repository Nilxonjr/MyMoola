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

    public async Task AddAsync(ExchangeRate rate, CancellationToken ct = default)
        => await db.ExchangeRates.AddAsync(rate, ct);

    public async Task AddRangeAsync(IEnumerable<ExchangeRate> rates, CancellationToken ct = default)
        => await db.ExchangeRates.AddRangeAsync(rates, ct);
}