// MyMoola.Application/Features/Transactions/Handlers/ProcessStkCallbackHandler.cs
using System.Text.Json;
using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Transactions.Commands;
using MyMoola.Application.Features.Transactions.OutboxHandlers;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;
using MyMoola.Application.Features.Transactions.DTOs;


namespace MyMoola.Application.Features.Transactions.Handlers;

/// <summary>
/// Writes STK callback to outbox atomically and returns immediately.
/// All ledger execution happens in StkCallbackOutboxHandler.
/// Handler resolves wallet IDs from SystemWallets constants —
/// no business logic in controller.
/// </summary>
public sealed class ProcessStkCallbackHandler(
    IMpesaTransactionRepository mpesaTransactions,
    ITransactionRepository transactions,
    IWalletRepository wallets,
    IOutboxService outbox,
    IUnitOfWork uow,
    ILogger<ProcessStkCallbackHandler> logger) : IRequestHandler<ProcessStkCallbackCommand>
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task Handle(
        ProcessStkCallbackCommand request,
        CancellationToken ct)
    {
        var data = request.Callback.Body.StkCallback;
        var rawJson = JsonSerializer.Serialize(request.Callback, JsonOptions);

        logger.LogInformation(
            "STK callback received. CheckoutRequestId={Id} ResultCode={Code}",
            data.CheckoutRequestID, data.ResultCode);

        // Load MpesaTransaction by CheckoutRequestId
        var mpesaTx = await mpesaTransactions
            .FindByCheckoutRequestIDAsync(data.CheckoutRequestID, ct)
            ?? throw new NotFoundException(
                nameof(Domain.Entities.MpesaTransaction), data.CheckoutRequestID);

        // Load Transaction — carries amounts and exchange rate snapshot
        var transaction = await transactions.FindByIdAsync(mpesaTx.TransactionId, ct)
            ?? throw new NotFoundException(
                nameof(Domain.Entities.Transaction), mpesaTx.TransactionId);

        // Extract receipt — only present on ResultCode 0
        string? receiptNumber = null;
        if (data.ResultCode == 0 && data.CallbackMetadata is not null)
        {
            receiptNumber = data.CallbackMetadata.Item
                    .FirstOrDefault(i => i.Name == "MpesaReceiptNumber")
                    ?.Value?.GetString();
        }

        // Resolve wallet IDs from SystemWallets constants — deterministic, no config needed
        var currency = transaction.Currency;

        var settlementWallet = await wallets
            .FindByUserAndCurrencyAsync(SystemWallets.SettlementAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("SettlementWallet", Currency.KES);

        var treasuryKesWallet = await wallets
            .FindByUserAndCurrencyAsync(SystemWallets.TreasuryAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("TreasuryKesWallet", Currency.KES);

        var revenueWallet = await wallets
            .FindByUserAndCurrencyAsync(SystemWallets.RevenueAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("RevenueWallet", Currency.KES);

        var treasuryWallet = await wallets
            .FindByUserAndCurrencyAsync(SystemWallets.TreasuryAccountUserId, currency, ct)
            ?? throw new NotFoundException("TreasuryWallet", currency);

        var userWallet = await wallets
            .FindByUserAndCurrencyAsync(transaction.InitiatorUserId!.Value, currency, ct)
            ?? throw new NotFoundException("UserWallet", currency);

        var spreadWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.SpreadRevenueAccountUserId, currency, ct)
            ?? throw new NotFoundException("SpreadRevenueWallet", currency);

        // Reconstruct amounts from Transaction entity —
        // these were computed in BuyCommandHandler and persisted.
        // KesAmount = gross KES paid by user
        // FeeAmount = platform fee KES
        // Amount    = user crypto received
        // ExchangeRateSnapshot = BuyRate
        // MarketRateSnapshot   = MarketRate
        var grossKes = transaction.KesAmount!.Value;
        var platformFeeKes = transaction.FeeAmount;
        var netKes = grossKes - platformFeeKes;
        var userCrypto = transaction.Amount;
        var grossCrypto = decimal.Round(
            netKes / transaction.MarketRateSnapshot!.Value, 8, MidpointRounding.ToEven);
        var spreadCrypto = decimal.Round(
            grossCrypto - userCrypto, 8, MidpointRounding.ToEven);

        // Write to outbox — atomic with nothing else, controller already returned 200
        await outbox.EnqueueAsync(
            OutboxMessageTypes.StkCallback,
            new StkCallbackOutboxPayload(
                CheckoutRequestId: data.CheckoutRequestID,
                ResultCode: data.ResultCode,
                ResultDesc: data.ResultDesc,
                MpesaReceiptNumber: receiptNumber,
                RawCallbackJson: rawJson,
                SettlementWalletId: settlementWallet.Id,
                TreasuryKesWalletId: treasuryKesWallet.Id,
                RevenueWalletId: revenueWallet.Id,
                TreasuryWalletId: treasuryWallet.Id,
                UserWalletId: userWallet.Id,
                SpreadWalletId: spreadWallet.Id,
                GrossKes: grossKes,
                PlatformFeeKes: platformFeeKes,
                NetKes: netKes,
                GrossCrypto: grossCrypto,
                UserCrypto: userCrypto,
                SpreadCrypto: spreadCrypto),
            ct);

        await uow.SaveChangesAsync(ct);

        logger.LogInformation(
            "STK callback enqueued. TransactionId={TransactionId} ResultCode={Code}",
            transaction.Id, data.ResultCode);
    }
}