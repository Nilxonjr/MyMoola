using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Crypto.Queries;
using MyMoola.Application.Interfaces;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Crypto.Handlers;

/// <summary>
/// Returns a locked withdrawal quote containing the gas fee
/// in the withdrawal currency. Valid for 30 seconds.
///
/// Fee calculation:
///   Gas units × current gas price (Wei) → ETH cost
///   ETH cost × ETH/USD rate → USD cost
///   USD cost / withdrawal currency USD rate → fee in withdrawal currency
///
/// Gas limits:
///   ETH:          50,000 units (native transfer upper bound)
///   USDC/BTC:    100,000 units (ERC-20 transfer upper bound)
/// </summary>
public sealed class GetWithdrawalQuoteQueryHandler(
    ICurrentUserService currentUser,
    ICurrencyExchangeService exchangeService,
    IExchangeRateQuoteService quoteService,
    ILogger<GetWithdrawalQuoteQueryHandler> logger,
    IBlockchainService blockchain) : IRequestHandler<GetWithdrawalQuoteQuery, GetWithdrawalQuoteResponse>
{
    // Maximum gas limits — user is charged worst case
    private const int EthGasLimit = 50_000;
    private const int Erc20GasLimit = 100_000;

    public async Task<GetWithdrawalQuoteResponse> Handle(
        GetWithdrawalQuoteQuery request,
        CancellationToken ct)
    {
        if (currentUser.UserId is null)
            throw new UnauthorizedException();

        // Get current gas price in Wei
        var gasPriceWei = await blockchain.GetCurrentGasPriceAsync(ct);

        // Get gas limit for this currency
        var gasLimit = request.Currency == Currency.ETH
            ? EthGasLimit
            : Erc20GasLimit;

        // Gas cost in ETH
        var gasCostEth = gasPriceWei * gasLimit / 1_000_000_000_000_000_000m;

        // Convert gas cost to withdrawal currency
        var feeAmount = await ConvertEthCostToCurrencyAsync(
            gasCostEth, request.Currency, ct);

        // Create and store quote in Redis
        var quote = await quoteService.CreateWithdrawalQuoteAsync(
            currency: request.Currency,
            feeAmount: feeAmount,
            Amount: request.Amount,
            ct: ct);


        return new GetWithdrawalQuoteResponse(
            QuoteId: quote.QuoteId,
            Currency: request.Currency.ToString(),
            FeeAmount: feeAmount,
            ExpiresInSeconds: 30);
    }

    private async Task<decimal> ConvertEthCostToCurrencyAsync(
        decimal gasCostEth,
        Currency currency,
        CancellationToken ct)
    {
        if (currency == Currency.ETH)
            return gasCostEth;

        // Get ETH/USD rate
        var ethRate = await exchangeService.GetRateAsync(Currency.ETH, ct);

        // Gas cost in USD
        var gasCostUsd = gasCostEth * ethRate.RateUsd;
        logger.LogInformation(
            "Fee conversion. GasCostEth={GasCostEth} EthRateUsd={EthRateUsd} GasCostUsd={GasCostUsd}",
            gasCostEth, ethRate.RateUsd, gasCostUsd);
        if (currency == Currency.USDC)
        {
            // USDC ≈ $1 — fee is direct USD equivalent
            return Math.Round(gasCostUsd, 6);
        }

        // BTC — convert USD cost to BTC
        var btcRate = await exchangeService.GetRateAsync(Currency.BTC, ct);


        return Math.Round(gasCostUsd / btcRate.RateUsd, 8);
    }
}