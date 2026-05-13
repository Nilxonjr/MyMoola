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

    public async Task AddAsync(ExchangeRate rate, CancellationToken ct = default)
        => await db.ExchangeRates.AddAsync(rate, ct);
}