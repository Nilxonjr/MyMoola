using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;
using MyMoola.Application.Features.Users.Queries;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Users.Handlers;

public sealed class GetBalanceHandler(
    ICurrentUserService currentUser,
    IWalletRepository wallets,
    ICurrencyExchangeService exchangeService,
    ILogger<GetBalanceHandler> logger)
    : IRequestHandler<GetBalanceQuery, GetBalanceResponse>
{
    public async Task<GetBalanceResponse> Handle(GetBalanceQuery request, CancellationToken ct)
    {
        if (currentUser.UserId is null)
            throw new UnauthorizedException();

        var userId = currentUser.UserId.Value;

        var userWallets = await wallets.GetByUserIdAsync(userId, ct);

        // Fetch rates concurrently for all wallets the user holds
        var rateMap = new Dictionary<Currency, ExchangeRate>();
        foreach (var wallet in userWallets)
        {
            var rate = await exchangeService.GetRateAsync(wallet.Currency, ct);
            rateMap[rate.Currency] = rate;
        }

        var walletDtos = userWallets.Select(w =>
        {
            var rate = rateMap[w.Currency];
            var fiatRate = request.DisplayCurrency == Domain.Enums.DisplayCurrency.KES
                ? rate.RateKes
                : rate.RateUsd;

            return new WalletBalanceDto(
                Currency: w.Currency.ToString(),
                Available: w.Balance,
                Locked: w.LockedBalance,
                Total: w.TotalBalance,
                FiatEquivalent: Math.Round(w.TotalBalance * fiatRate, 2),
                Rate: fiatRate);
        }).ToList();

        var total = walletDtos.Sum(w => w.FiatEquivalent);

        logger.LogInformation(
            "Balance fetched. UserId={UserId} DisplayCurrency={Display} Total={Total}",
            userId, request.DisplayCurrency, total);

        return new GetBalanceResponse(
            DisplayCurrency: request.DisplayCurrency.ToString(),
            TotalFiatEquivalent: total,
            Wallets: walletDtos);
    }
}