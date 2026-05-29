// MyMoola.Application/Features/Transactions/Handlers/ProcessB2CCallbackHandler.cs
using System.Text.Json;
using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Transactions.Commands;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Transactions.Handlers;

/// <summary>
/// Writes B2C callback to outbox atomically and returns immediately.
/// All ledger execution happens in B2CCallbackOutboxHandler.
/// </summary>
public sealed class ProcessB2CCallbackHandler(
    IMpesaTransactionRepository mpesaTransactions,
    ITransactionRepository transactions,
    IWalletRepository wallets,
    IOutboxService outbox,
    IUnitOfWork uow,
    ILogger<ProcessB2CCallbackHandler> logger) : IRequestHandler<ProcessB2CCallbackCommand>
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task Handle(
        ProcessB2CCallbackCommand request,
        CancellationToken ct)
    {
        var result = request.Callback.Result;
        var rawJson = JsonSerializer.Serialize(request.Callback, JsonOptions);

        logger.LogInformation(
            "B2C callback received. ConversationID={ID} ResultCode={Code}",
            result.ConversationID, result.ResultCode);

        // Load MpesaTransaction by ConversationID
        var mpesaTx = await mpesaTransactions
            .FindByConversationIDAsync(result.ConversationID, ct)
            ?? throw new NotFoundException(
                nameof(Domain.Entities.MpesaTransaction), result.ConversationID);

        // Load Transaction
        var transaction = await transactions.FindByIdAsync(mpesaTx.TransactionId, ct)
            ?? throw new NotFoundException(
                nameof(Domain.Entities.Transaction), mpesaTx.TransactionId);

        // Extract receipt — only present on ResultCode 0
        string? receiptNumber = null;
        if (result.ResultCode == 0 && result.ResultParameters is not null)
        {
            receiptNumber = result.ResultParameters.ResultParameter
                .FirstOrDefault(p => p.Key == "TransactionReceipt")
                ?.Value.GetString();
        }

        // Read metadata stored at sell initiation — never recalculate
        var meta = JsonSerializer.Deserialize<SellTransactionMeta>(
            transaction.Metadata!, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Sell metadata missing. TransactionId={transaction.Id}");

        // Resolve wallet IDs from SystemWallets constants
        var currency = transaction.Currency;

        var userWallet = await wallets
            .FindByUserAndCurrencyAsync(
                transaction.InitiatorUserId!.Value, currency, ct)
            ?? throw new NotFoundException("UserWallet", currency);

        var treasuryWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.TreasuryAccountUserId, currency, ct)
            ?? throw new NotFoundException("TreasuryWallet", currency);

        var treasuryKesWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.TreasuryAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("TreasuryKesWallet", Currency.KES);

        var revenueWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.RevenueAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("RevenueWallet", Currency.KES);

        var spreadKesWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.SpreadRevenueAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("SpreadRevenueWallet", Currency.KES);

        var settlementWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.SettlementAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("SettlementWallet", Currency.KES);

        var suspenseWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.SuspenseAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("SuspenseWallet", Currency.KES);

        await outbox.EnqueueAsync(
            OutboxMessageTypes.B2CCallback,
            new B2CCallbackOutboxPayload(
                ConversationID: result.ConversationID,
                ResultCode: result.ResultCode,
                ResultDesc: result.ResultDesc,
                MpesaReceiptNumber: receiptNumber,
                RawCallbackJson: rawJson,
                UserWalletId: userWallet.Id,
                TreasuryWalletId: treasuryWallet.Id,
                TreasuryKesWalletId: treasuryKesWallet.Id,
                RevenueWalletId: revenueWallet.Id,
                SpreadKesWalletId: spreadKesWallet.Id,
                SettlementWalletId: settlementWallet.Id,
                SuspenseWalletId: suspenseWallet.Id,
                CryptoAmount: transaction.Amount,
                GrossKes: meta.GrossKes,
                PlatformFeeKes: meta.PlatformFeeKes,
                SpreadKes: meta.SpreadKes,
                B2CAmountKes: meta.B2CAmountKes,
                ResidualKes: meta.ResidualKes),
            ct);

        await uow.SaveChangesAsync(ct);

        logger.LogInformation(
            "B2C callback enqueued. TransactionId={TransactionId} ResultCode={Code}",
            transaction.Id, result.ResultCode);
    }
}

internal sealed record SellTransactionMeta(
    decimal GrossKes,
    decimal SpreadKes,
    decimal PlatformFeeKes,
    int B2CAmountKes,
    decimal ResidualKes);