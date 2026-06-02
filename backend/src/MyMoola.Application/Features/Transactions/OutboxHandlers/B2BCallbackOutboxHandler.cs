// MyMoola.Application/Features/Transactions/OutboxHandlers/B2BCallbackOutboxHandler.cs
using System.Data;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Transactions.Handlers;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Transactions.OutboxHandlers;

/// <summary>
/// Processes confirmed or failed B2B callbacks.
///
/// SUCCESS — 7-entry double-entry ledger:
///   USDC: UnlockAndDebit user → Credit treasury
///   KES:  Debit treasury KES → Credit revenue + spread + settlement + suspense
///
/// FAILURE — UnlockAsync returns crypto. Transaction marked Failed.
///
/// IDEMPOTENT — checks Transaction.Status before executing.
/// </summary>
public sealed class B2BCallbackOutboxHandler(
    ITransactionRepository transactions,
    IMpesaTransactionRepository mpesaTransactions,
    ILedgerService ledger,
    IUnitOfWork uow,
    ILogger<B2BCallbackOutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.B2BCallback;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<B2BCallbackOutboxPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize B2BCallbackOutboxPayload. MessageId={message.Id}");

        var mpesaTx = await mpesaTransactions
            .FindByConversationIDAsync(payload.ConversationID, ct)
            ?? throw new NotFoundException(
                nameof(MpesaTransaction), payload.ConversationID);

        var transaction = await transactions.FindByIdAsync(mpesaTx.TransactionId, ct)
            ?? throw new NotFoundException(
                nameof(Transaction), mpesaTx.TransactionId);

        // IDEMPOTENCY
        if (transaction.Status is TransactionStatus.Completed
                                or TransactionStatus.Failed)
        {
            logger.LogWarning(
                "B2B callback for terminal transaction. Skipping. " +
                "TransactionId={TransactionId} Status={Status}",
                transaction.Id, transaction.Status);
            return;
        }

        if (payload.ResultCode == 0)
            await HandleSuccessAsync(payload, mpesaTx, transaction, ct);
        else
            await HandleFailureAsync(payload, mpesaTx, transaction, ct);
    }

    private async Task HandleSuccessAsync(
        B2BCallbackOutboxPayload payload,
        MpesaTransaction mpesaTx,
        Transaction transaction,
        CancellationToken ct)
    {
        logger.LogInformation(
            "B2B callback success. TransactionId={TransactionId} Receipt={Receipt}",
            transaction.Id, payload.MpesaReceiptNumber);

        await using var dbTransaction = await uow.BeginTransactionAsync(
            IsolationLevel.Serializable, ct);

        try
        {
            // --- USDC leg ---

            // 1. UnlockAndDebit user — crypto permanently removed
            await ledger.UnlockAndDebitAsync(
                payload.UserWalletId,
                payload.CryptoAmount,
                transaction.Id,
                ct);

            // 2. Credit treasury USDC
            await ledger.CreditAsync(
                payload.TreasuryWalletId,
                payload.CryptoAmount,
                transaction.Id,
                ct);

            // --- KES leg ---

            // 3. Debit treasury KES — funds the merchant payment
            await ledger.DebitAsync(
                payload.TreasuryKesWalletId,
                payload.GrossKes,
                transaction.Id,
                ct);

            // 4. Credit revenue — platform fee
            await ledger.CreditAsync(
                payload.RevenueWalletId,
                payload.PlatformFeeKes,
                transaction.Id,
                ct);

            // 5. Credit spread revenue
            await ledger.CreditAsync(
                payload.SpreadKesWalletId,
                payload.SpreadKes,
                transaction.Id,
                ct);

            // 6. Credit settlement — exact merchant amount
            await ledger.CreditAsync(
                payload.SettlementWalletId,
                payload.MerchantAmountKes,
                transaction.Id,
                ct);

            // 7. Credit suspense — rounding residual
            if (payload.ResidualKes > 0)
                await ledger.CreditAsync(
                    payload.SuspenseWalletId,
                    payload.ResidualKes,
                    transaction.Id,
                    ct);

            mpesaTx.Confirm(payload.MpesaReceiptNumber!, payload.RawCallbackJson);
            transaction.SetMpesaReference(payload.MpesaReceiptNumber!);
            transaction.MarkCompleted();

            await uow.SaveChangesAsync(ct);
            await dbTransaction.CommitAsync(ct);

            logger.LogInformation(
                "Merchant payment completed. TransactionId={TransactionId} " +
                "CryptoAmount={CryptoAmount} MerchantAmount={MerchantAmount}",
                transaction.Id, payload.CryptoAmount, payload.MerchantAmountKes);
        }
        catch (Exception ex)
        {
            await dbTransaction.RollbackAsync(ct);

            logger.LogError(ex,
                "Merchant payment ledger failed. TransactionId={TransactionId}",
                transaction.Id);

            throw;
        }
    }

    private async Task HandleFailureAsync(
        B2BCallbackOutboxPayload payload,
        MpesaTransaction mpesaTx,
        Transaction transaction,
        CancellationToken ct)
    {
        logger.LogWarning(
            "B2B callback failure. TransactionId={TransactionId} " +
            "ResultCode={ResultCode} ResultDesc={ResultDesc}",
            transaction.Id, payload.ResultCode, payload.ResultDesc);

        await using var dbTransaction = await uow.BeginTransactionAsync(
            IsolationLevel.Serializable, ct);

        try
        {
            await ledger.UnlockAsync(
                payload.UserWalletId,
                payload.CryptoAmount,
                transaction.Id,
                ct);

            mpesaTx.Fail(payload.RawCallbackJson);
            transaction.MarkFailed();

            await uow.SaveChangesAsync(ct);
            await dbTransaction.CommitAsync(ct);
        }
        catch (Exception ex)
        {
            await dbTransaction.RollbackAsync(ct);
            logger.LogError(ex,
                "B2B failure handling failed. TransactionId={TransactionId}",
                transaction.Id);
            throw;
        }
    }
}