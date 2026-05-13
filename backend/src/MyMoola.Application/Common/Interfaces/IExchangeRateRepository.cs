// MyMoola.Application/Common/Interfaces/IExchangeRateRepository.cs
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Common.Interfaces;

public interface IExchangeRateRepository
{
    Task<ExchangeRate?> GetLatestAsync(Currency currency, CancellationToken ct = default);
    Task<IReadOnlyList<ExchangeRate>> GetLatestAllAsync(CancellationToken ct = default);
    Task AddAsync(ExchangeRate rate, CancellationToken ct = default);
    Task AddRangeAsync(IEnumerable<ExchangeRate> rates, CancellationToken ct = default);
}