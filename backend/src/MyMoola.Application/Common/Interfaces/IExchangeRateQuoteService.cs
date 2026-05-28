// MyMoola.Application/Common/Interfaces/IExchangeRateQuoteService.cs
using MyMoola.Domain.Enums;
using MyMoola.Domain.ValueObjects;

namespace MyMoola.Application.Common.Interfaces;

public interface IExchangeRateQuoteService
{
    /// <summary>
    /// Fetches latest rate, locks it in Redis for QuoteTtlSeconds, returns QuoteId.
    /// </summary>
    Task<ExchangeRateQuote> CreateQuoteAsync(
        Currency currency,
        CancellationToken ct = default);

    /// <summary>
    /// Retrieves and validates quote. Returns null if expired or not found.
    /// Does NOT consume — call ConsumeAsync after successful transaction creation.
    /// </summary>
    Task<ExchangeRateQuote?> GetQuoteAsync(
        Guid quoteId,
        CancellationToken ct = default);

    /// <summary>
    /// Deletes quote from Redis — called after transaction committed.
    /// Prevents reuse of the same quote.
    /// </summary>
    Task ConsumeQuoteAsync(Guid quoteId, CancellationToken ct = default);
}