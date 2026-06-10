using MediatR;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Crypto.Queries;

public sealed record GetWithdrawalQuoteQuery(
    Currency Currency,
    decimal Amount) : IRequest<GetWithdrawalQuoteResponse>;

public sealed record GetWithdrawalQuoteResponse(
    Guid QuoteId,
    string Currency,
    decimal FeeAmount,
    decimal ExpiresInSeconds);