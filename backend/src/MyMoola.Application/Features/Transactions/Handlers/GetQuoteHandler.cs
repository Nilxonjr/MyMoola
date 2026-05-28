// MyMoola.Application/Features/Transactions/Handlers/GetQuoteHandler.cs
using MediatR;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Transactions.Commands;

namespace MyMoola.Application.Features.Transactions.Handlers;

public sealed class GetQuoteHandler(
    IExchangeRateQuoteService quoteService) : IRequestHandler<GetQuoteCommand, GetQuoteResponse>
{
    public async Task<GetQuoteResponse> Handle(
        GetQuoteCommand request,
        CancellationToken ct)
    {
        var quote = await quoteService.CreateQuoteAsync(request.Currency, ct);

        return new GetQuoteResponse(
            QuoteId: quote.QuoteId,
            Currency: quote.Currency,
            RateKes: quote.RateKes,
            BuyRateKes: quote.BuyRateKes,
            SellRateKes: quote.SellRateKes,
            SpreadPercent: quote.SpreadPercent,
            ExpiresAt: quote.ExpiresAt);
    }
}