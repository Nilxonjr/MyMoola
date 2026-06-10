// MyMoola.Domain/ValueObjects/ExchangeRateQuote.cs
using MyMoola.Domain.Enums;

namespace MyMoola.Domain.ValueObjects;

/// <summary>
/// A locked rate valid for a short window.
/// Written to Redis by GetQuoteHandler.
/// Consumed and invalidated by BuyCommandHandler/SellCommandHandler.
/// </summary>
public sealed record ExchangeRateQuote(
    Guid QuoteId,
    Currency Currency,
    decimal RateKes,
    decimal RateUsd,
    decimal BuyRateKes,
    decimal SellRateKes,
    decimal SpreadPercent,
    string Source,
    DateTimeOffset ExpiresAt);