// MyMoola.Application/Features/Transactions/OutboxHandlers/B2CCallbackOutboxHandler.cs
using System.Data;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Transactions.Commands;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Transactions.OutboxHandlers;

/// <summary>
/// Processes confirmed or failed B2C callbacks.
///
/// SUCCESS — executes 6-entry double-entry ledger atomically:
///   USDC leg: UnlockAndDebit user → Credit treasury
///   KES leg:  Debit treasury KES → Credit revenue + spread + settlement(B2C out) + suspense(residual)
///
/// FAILURE — UnlockAsync returns crypto to user. Transaction marked Failed.
///
/// IDEMPOTENT — checks Transaction.Status before executing.
/// </summary>
public sealed class B2CCallbackOutboxHandler(
    ITransactionRepository transactions,
    IMpesaTransactionRepository mpesaTransactions,
    ILedgerService ledger,
    IUnitOfWork uow,
    ILogger<B2CCallbackOutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.B2CCallback;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<B2CCallbackOutboxPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize B2CCallbackOutboxPayload. MessageId={message.Id}");

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
                "B2C callback received for terminal transaction. Skipping. " +
                "TransactionId={TransactionId} Status={Status}",
                transaction.Id, transaction.Status);
            return;
        }

        if (payload.ResultCode == 0)
            await HandleSuccessAsync(payload, mpesaTx, transaction, ct);
        else
            await HandleFailureAsync(payload, mpesaTx, transaction, ct);
    }

    // -------------------------------------------------------------------------
    // Success path — 6 ledger entries
    // -------------------------------------------------------------------------

    private async Task HandleSuccessAsync(
        B2CCallbackOutboxPayload payload,
        MpesaTransaction mpesaTx,
        Transaction transaction,
        CancellationToken ct)
    {
        logger.LogInformation(
            "B2C callback success. TransactionId={TransactionId} Receipt={Receipt}",
            transaction.Id, payload.MpesaReceiptNumber);

        await using var dbTransaction = await uow.BeginTransactionAsync(
            IsolationLevel.Serializable, ct);

        try
        {
            // --- USDC leg ---

            // 1. UnlockAndDebit user USDC — removes from locked balance permanently
            await ledger.UnlockAndDebitAsync(
                payload.UserWalletId,
                payload.CryptoAmount,
                transaction.Id,
                ct);

            // 2. Credit treasury USDC — platform receives crypto
            await ledger.CreditAsync(
                payload.TreasuryWalletId,
                payload.CryptoAmount,
                transaction.Id,
                ct);

            // --- KES leg ---

            // 3. Debit treasury KES — funds the payout
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

            // 5. Credit spread revenue — exchange margin
            await ledger.CreditAsync(
                payload.SpreadKesWalletId,
                payload.SpreadKes,
                transaction.Id,
                ct);

            // 6. Credit settlement — B2C amount sent to user
            await ledger.CreditAsync(
                payload.SettlementWalletId,
                payload.B2CAmountKes,
                transaction.Id,
                ct);

            // Settlement pays out B2C amount
            await ledger.DebitAsync(
                payload.SettlementWalletId,
                payload.B2CAmountKes,
                transaction.Id,
                ct);

            // 7. Credit suspense — rounding residual, never lost
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
                "Sell completed. TransactionId={TransactionId} " +
                "CryptoAmount={CryptoAmount} GrossKes={GrossKes} B2CAmount={B2C}",
                transaction.Id, payload.CryptoAmount,
                payload.GrossKes, payload.B2CAmountKes);
        }
        catch (Exception ex)
        {
            await dbTransaction.RollbackAsync(ct);

            logger.LogError(ex,
                "Sell ledger failed. TransactionId={TransactionId}",
                transaction.Id);

            throw;
        }
    }

    // -------------------------------------------------------------------------
    // Failure path — unlock crypto, mark failed
    // -------------------------------------------------------------------------

    private async Task HandleFailureAsync(
        B2CCallbackOutboxPayload payload,
        MpesaTransaction mpesaTx,
        Transaction transaction,
        CancellationToken ct)
    {
        logger.LogWarning(
            "B2C callback failure. TransactionId={TransactionId} " +
            "ResultCode={ResultCode} ResultDesc={ResultDesc}",
            transaction.Id, payload.ResultCode, payload.ResultDesc);

        await using var dbTransaction = await uow.BeginTransactionAsync(
            IsolationLevel.Serializable, ct);

        try
        {
            // Return crypto to user available balance
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
                "Sell failure handling failed. TransactionId={TransactionId}",
                transaction.Id);
            throw;
        }
    }
}