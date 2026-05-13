using MediatR;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Users.Queries;

public sealed record GetBalanceQuery(DisplayCurrency DisplayCurrency) : IRequest<GetBalanceResponse>;

public sealed record GetBalanceResponse(
    string DisplayCurrency,
    decimal TotalFiatEquivalent,
    IReadOnlyList<WalletBalanceDto> Wallets);

public sealed record WalletBalanceDto(
    string Currency,
    decimal Available,
    decimal Locked,
    decimal Total,
    decimal FiatEquivalent,
    decimal Rate);