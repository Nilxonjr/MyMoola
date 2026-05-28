// MyMoola.Application/Features/Transactions/Commands/GetQuoteCommand.cs
using MediatR;
using MyMoola.Domain.Enums;
using MyMoola.Domain.ValueObjects;

namespace MyMoola.Application.Features.Transactions.Commands;

public sealed record GetQuoteCommand(Currency Currency) : IRequest<GetQuoteResponse>;

public sealed record GetQuoteResponse(
    Guid QuoteId,
    Currency Currency,
    decimal RateKes,
    decimal BuyRateKes,
    decimal SellRateKes,
    decimal SpreadPercent,
    DateTimeOffset ExpiresAt);