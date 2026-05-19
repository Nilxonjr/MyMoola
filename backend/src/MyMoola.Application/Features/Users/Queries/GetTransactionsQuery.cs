using MediatR;

namespace MyMoola.Application.Features.Users.Queries;

public sealed record GetTransactionsQuery(int Page, int PageSize)
    : IRequest<GetTransactionsResponse>;

public sealed record GetTransactionsResponse(
    IReadOnlyList<TransactionDto> Items,
    int Page,
    int PageSize,
    int TotalCount,
    int TotalPages);

public sealed record TransactionDto(
    Guid Id,
    string ReferenceCode,
    string Type,
    string Status,
    Guid? InitiatorUserId,
    Guid? CounterpartyUserId,
    string? InteractedPhone,
    string Currency,
    decimal Amount,
    decimal FeeAmount,
    decimal? KesAmount,
    decimal? ExchangeRateSnapshot,
    decimal? MarketRateSnapshot,
    string? OnChainTxHash,
    int OnChainConfirmations,
    string? MpesaReference,
    DateTimeOffset CreatedAt,
    DateTimeOffset? CompletedAt);