// MyMoola.Application/Features/Transactions/Handlers/ProcessB2BCallbackHandler.cs
using System.Text.Json;
using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Transactions.Commands;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Transactions.Handlers;

public sealed class ProcessB2BCallbackHandler(
    IMpesaTransactionRepository mpesaTransactions,
    ITransactionRepository transactions,
    IWalletRepository wallets,
    IOutboxService outbox,
    IUnitOfWork uow,
    ILogger<ProcessB2BCallbackHandler> logger) : IRequestHandler<ProcessB2BCallbackCommand>
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        PropertyNameCaseInsensitive = true

    };

    public async Task Handle(
        ProcessB2BCallbackCommand request,
        CancellationToken ct)
    {
        var result = request.Callback.Result;
        var rawJson = JsonSerializer.Serialize(request.Callback, JsonOptions);

        logger.LogInformation(
            "B2B callback received. ConversationID={ID} ResultCode={Code}",
            result.ConversationID, result.ResultCode);

        logger.LogInformation(
            "B2B  payload is: {rawjson}", rawJson);

        var mpesaTx = await mpesaTransactions
            .FindByConversationIDAsync(result.ConversationID, ct)
            ?? throw new NotFoundException(
                nameof(Domain.Entities.MpesaTransaction), result.ConversationID);

        var transaction = await transactions.FindByIdAsync(mpesaTx.TransactionId, ct)
            ?? throw new NotFoundException(
                nameof(Domain.Entities.Transaction), mpesaTx.TransactionId);

        // Extract receipt from result parameters
        string? receiptNumber = null;
        string? receiverName = null;
        if (result.ResultCode == 0 && result.ResultParameters is not null)
        {
            receiptNumber = result.ResultParameters.ResultParameter
                .FirstOrDefault(p => p.Key == "TransactionReceipt")
                ?.Value.ToString();

            receiverName = result.ResultParameters?.ResultParameter
                    .FirstOrDefault(p => p.Key == "ReceiverPartyPublicName")
                    ?.Value.ToString()
                    ?? result.ResultParameters?.ResultParameter
                        .FirstOrDefault(p => p.Key == "CreditPartyName")
                        ?.Value.ToString();
        }

        if (string.IsNullOrWhiteSpace(transaction.Metadata))
            throw new InvalidOperationException(
                $"Merchant payment metadata missing. TransactionId={transaction.Id}");

        // Read metadata stored at payment initiation
        var meta = JsonSerializer.Deserialize<MerchantPaymentMeta>(
            transaction.Metadata!, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Merchant payment metadata missing. TransactionId={transaction.Id}");

        var existingMeta = string.IsNullOrWhiteSpace(transaction.Metadata)
                ? new Dictionary<string, object?>()
                : JsonSerializer.Deserialize<Dictionary<string, object?>>(
                    transaction.Metadata, JsonOptions)
                  ?? new Dictionary<string, object?>();

        existingMeta["receiverName"] = receiverName;

        transaction.SetMetadata(JsonSerializer.Serialize(existingMeta, JsonOptions));

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
            OutboxMessageTypes.B2BCallback,
            new B2BCallbackOutboxPayload(
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
                MerchantAmountKes: meta.MerchantAmountKes,
                ResidualKes: meta.ResidualKes),
            ct);

        await uow.SaveChangesAsync(ct);

        logger.LogInformation(
            "B2B callback enqueued. TransactionId={TransactionId} ResultCode={Code}",
            transaction.Id, result.ResultCode);
    }
}

internal sealed record MerchantPaymentMeta(
    int MerchantAmountKes,
    decimal GrossKes,
    decimal PlatformFeeKes,
    decimal SpreadKes,
    decimal ResidualKes,
    string MerchantType,
    string MerchantNumber,
    string AccountReference);