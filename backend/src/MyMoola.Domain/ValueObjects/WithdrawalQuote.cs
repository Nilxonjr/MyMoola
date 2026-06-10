using MyMoola.Domain.Enums;

namespace MyMoola.Domain.ValueObjects;

/// <summary>
/// A locked withdrawal quote containing the gas fee in the withdrawal currency.
/// Valid for 30 seconds. Stored in Redis and consumed by WithdrawCommandHandler.
/// </summary>
public sealed record WithdrawalQuote(
    Guid QuoteId,
    Currency Currency,
    decimal FeeAmount,
    decimal Amount,
    DateTimeOffset ExpiresAt);