// MyMoola.Application/Common/Interfaces/ICurrencyExchangeService.cs
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Common.Interfaces;

public interface ICurrencyExchangeService
{
    /// <summary>
    /// Returns the latest persisted exchange rate for the given currency.
    /// Fetches from external provider and persists if no rate exists or rate is stale.
    /// </summary>
    Task<ExchangeRate> GetRateAsync(Currency currency, CancellationToken ct = default);
}