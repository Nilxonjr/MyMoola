using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Common.Interfaces;

public interface ICurrencyExchangeService
{
    /// <summary>
    /// Returns the latest rate from DB. Never makes outbound HTTP calls.
    /// Throws if no rate exists — background job must have failed.
    /// </summary>
    Task<ExchangeRate> GetRateAsync(Currency currency, CancellationToken ct = default);
}