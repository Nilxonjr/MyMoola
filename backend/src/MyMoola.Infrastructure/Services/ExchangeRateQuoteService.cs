// MyMoola.Infrastructure/Services/ExchangeRateQuoteService.cs
using System.Text.Json;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Enums;
using MyMoola.Domain.ValueObjects;
using StackExchange.Redis;

namespace MyMoola.Infrastructure.Services;

public sealed class ExchangeRateQuoteService(
    ICurrencyExchangeService exchangeService,
    IConnectionMultiplexer redis,
    ILogger<ExchangeRateQuoteService> logger) : IExchangeRateQuoteService
{
    // Quote valid for 30 seconds — enough for user to confirm
    private static readonly TimeSpan QuoteTtl = TimeSpan.FromSeconds(30);

    private static readonly TimeSpan WithdrawalQuoteTtl = TimeSpan.FromSeconds(30);

    private IDatabase Db => redis.GetDatabase();

    public async Task<ExchangeRateQuote> CreateQuoteAsync(
        Currency currency,
        CancellationToken ct = default)
    {
        // Use cached rate from CurrencyExchangeService — 120s cache
        // Fresh enough for quoting, avoids hammering external APIs
        var rate = await exchangeService.GetRateAsync(currency, ct);

        var quote = new ExchangeRateQuote(
            QuoteId: Guid.NewGuid(),
            Currency: currency,
            RateKes: rate.RateKes,
            RateUsd: rate.RateUsd,
            BuyRateKes: rate.BuyRateKes,
            SellRateKes: rate.SellRateKes,
            SpreadPercent: rate.SpreadPercent,
            Source: rate.Source,
            ExpiresAt: DateTimeOffset.UtcNow.Add(QuoteTtl));

        var key = QuoteKey(quote.QuoteId);
        var json = JsonSerializer.Serialize(quote);

        await Db.StringSetAsync(key, json, QuoteTtl);

        logger.LogInformation(
            "Quote created. QuoteId={QuoteId} Currency={Currency} " +
            "BuyRate={BuyRate} ExpiresAt={ExpiresAt}",
            quote.QuoteId, currency, rate.BuyRateKes, quote.ExpiresAt);

        return quote;
    }

    public async Task<ExchangeRateQuote?> GetQuoteAsync(
        Guid quoteId,
        CancellationToken ct = default)
    {
        var value = await Db.StringGetAsync(QuoteKey(quoteId));

        if (!value.HasValue)
            return null;

        var quote = JsonSerializer.Deserialize<ExchangeRateQuote>(value.ToString());

        if (quote is null || DateTimeOffset.UtcNow > quote.ExpiresAt)
            return null;

        return quote;
    }

    public async Task<WithdrawalQuote> CreateWithdrawalQuoteAsync(
    Currency currency,
    decimal feeAmount,
    decimal Amount,
    CancellationToken ct = default)
    {
        var quote = new WithdrawalQuote(
            QuoteId: Guid.NewGuid(),
            Currency: currency,
            FeeAmount: feeAmount,
            Amount: Amount,
            ExpiresAt: DateTimeOffset.UtcNow.Add(WithdrawalQuoteTtl));

        var key = WithdrawalQuoteKey(quote.QuoteId);
        await Db.StringSetAsync(key, JsonSerializer.Serialize(quote), WithdrawalQuoteTtl);

        logger.LogInformation(
            "Withdrawal quote created. QuoteId={QuoteId} Currency={Currency} Fee={Fee}",
            quote.QuoteId, currency, feeAmount);

        return quote;
    }

    public async Task<WithdrawalQuote?> GetWithdrawalQuoteAsync(
        Guid quoteId,
        CancellationToken ct = default)
    {
        var value = await Db.StringGetAsync(WithdrawalQuoteKey(quoteId));
        if (!value.HasValue) return null;

        var quote = JsonSerializer.Deserialize<WithdrawalQuote>(value.ToString());
        if (quote is null || DateTimeOffset.UtcNow > quote.ExpiresAt) return null;

        return quote;
    }

    public async Task ConsumeWithdrawalQuoteAsync(
        Guid quoteId,
        CancellationToken ct = default)
        => await Db.KeyDeleteAsync(WithdrawalQuoteKey(quoteId));

    private static string WithdrawalQuoteKey(Guid quoteId) => $"withdrawal_quote:{quoteId}";

    public async Task ConsumeQuoteAsync(Guid quoteId, CancellationToken ct = default)
        => await Db.KeyDeleteAsync(QuoteKey(quoteId));

    private static string QuoteKey(Guid quoteId) => $"quote:{quoteId}";

}