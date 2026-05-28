// MyMoola.Application/Features/Transactions/OutboxHandlers/StkCallbackOutboxHandler.cs
using System.Data;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Transactions.OutboxHandlers;

/// <summary>
/// Processes confirmed or failed STK Push callbacks.
///
/// SUCCESS — executes 8-entry double-entry ledger atomically:
///   KES leg:  Settlement credited (M-Pesa in) → Revenue credited (fee)
///             → Settlement debited (net) → Treasury KES credited (net)
///   USDC leg: Treasury USDC debited (gross) → User credited (net)
///             → SpreadRevenue credited (spread)
///   Transaction marked Completed.
///
/// FAILURE — MpesaTransaction marked failed. Transaction marked Failed.
///           No ledger entries written.
///
/// IDEMPOTENT — checks Transaction.Status before executing.
///              Completed or Failed transactions are skipped.
/// </summary>
public sealed class StkCallbackOutboxHandler(
    ITransactionRepository transactions,
    IMpesaTransactionRepository mpesaTransactions,
    ILedgerService ledger,
    IUnitOfWork uow,
    ILogger<StkCallbackOutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.StkCallback;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<StkCallbackOutboxPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize StkCallbackOutboxPayload. MessageId={message.Id}");

        // Load MpesaTransaction by CheckoutRequestId
        var mpesaTx = await mpesaTransactions
            .FindByCheckoutRequestIDAsync(payload.CheckoutRequestId, ct)
            ?? throw new NotFoundException(
                nameof(MpesaTransaction), payload.CheckoutRequestId);

        // Load Transaction
        var transaction = await transactions.FindByIdAsync(mpesaTx.TransactionId, ct)
            ?? throw new NotFoundException(
                nameof(Transaction), mpesaTx.TransactionId);

        // IDEMPOTENCY — if already terminal, skip entirely
        if (transaction.Status is Domain.Enums.TransactionStatus.Completed
                                or Domain.Enums.TransactionStatus.Failed)
        {
            logger.LogWarning(
                "STK callback received for terminal transaction. Skipping. " +
                "TransactionId={TransactionId} Status={Status}",
                transaction.Id, transaction.Status);
            return;
        }

        // Safaricom ResultCode 0 = success, anything else = failure
        if (payload.ResultCode == 0)
            await HandleSuccessAsync(payload, mpesaTx, transaction, ct);
        else
            await HandleFailureAsync(payload, mpesaTx, transaction, ct);
    }

    // -------------------------------------------------------------------------
    // Success path
    // -------------------------------------------------------------------------

    private async Task HandleSuccessAsync(
        StkCallbackOutboxPayload payload,
        MpesaTransaction mpesaTx,
        Transaction transaction,
        CancellationToken ct)
    {
        logger.LogInformation(
            "STK callback success. TransactionId={TransactionId} Receipt={Receipt}",
            transaction.Id, payload.MpesaReceiptNumber);

        // All 8 ledger entries + status updates in one Serializable transaction.
        // LedgerService detects the ambient transaction and participates
        // without committing — this handler owns the single CommitAsync.
        await using var dbTransaction = await uow.BeginTransactionAsync(
            IsolationLevel.Serializable, ct);

        try
        {
            // --- KES leg ---

            // 1. Settlement receives M-Pesa inbound
            await ledger.CreditAsync(
                payload.SettlementWalletId,
                payload.GrossKes,
                transaction.Id,
                ct);

            // 2. Settlement pays fee to Revenue
            await ledger.DebitAsync(
                payload.SettlementWalletId,
                payload.PlatformFeeKes,
                transaction.Id,
                ct);

            // 3. Revenue receives platform fee
            await ledger.CreditAsync(
                payload.RevenueWalletId,
                payload.PlatformFeeKes,
                transaction.Id,
                ct);

            // 4. Settlement pays net KES for conversion
            await ledger.DebitAsync(
                payload.SettlementWalletId,
                payload.NetKes,
                transaction.Id,
                ct);

            // 5. Treasury KES receives net — funds crypto release
            await ledger.CreditAsync(
                payload.TreasuryKesWalletId,
                payload.NetKes,
                transaction.Id,
                ct);

            // --- USDC leg ---

            // 6. Treasury USDC releases gross crypto at MarketRate
            await ledger.DebitAsync(
                payload.TreasuryWalletId,
                payload.GrossCrypto,
                transaction.Id,
                ct);

            // 7. User receives net crypto at BuyRate
            await ledger.CreditAsync(
                payload.UserWalletId,
                payload.UserCrypto,
                transaction.Id,
                ct);

            // 8. SpreadRevenue retains spread crypto
            await ledger.CreditAsync(
                payload.SpreadWalletId,
                payload.SpreadCrypto,
                transaction.Id,
                ct);

            // Update MpesaTransaction and Transaction status
            mpesaTx.Confirm(payload.MpesaReceiptNumber!, payload.RawCallbackJson);
            transaction.SetMpesaReference(payload.MpesaReceiptNumber!);
            transaction.MarkCompleted();

            await uow.SaveChangesAsync(ct);
            await dbTransaction.CommitAsync(ct);

            logger.LogInformation(
                "Buy completed. TransactionId={TransactionId} " +
                "UserCrypto={UserCrypto} GrossKes={GrossKes}",
                transaction.Id, payload.UserCrypto, payload.GrossKes);
        }
        catch (Exception ex)
        {
            await dbTransaction.RollbackAsync(ct);

            logger.LogError(ex,
                "Buy ledger failed. TransactionId={TransactionId}",
                transaction.Id);

            throw;
        }
    }

    // -------------------------------------------------------------------------
    // Failure path
    // -------------------------------------------------------------------------

    private async Task HandleFailureAsync(
        StkCallbackOutboxPayload payload,
        MpesaTransaction mpesaTx,
        Transaction transaction,
        CancellationToken ct)
    {
        logger.LogWarning(
            "STK callback failure. TransactionId={TransactionId} " +
            "ResultCode={ResultCode} ResultDesc={ResultDesc}",
            transaction.Id, payload.ResultCode, payload.ResultDesc);

        mpesaTx.Fail(payload.RawCallbackJson);
        transaction.MarkFailed();

        await uow.SaveChangesAsync(ct);
    }
}

/// <summary>
/// Written to outbox by MpesaCallbackController.
/// Carries everything the handler needs — no re-querying of wallet config.
/// Wallet IDs are resolved once in BuyCommandHandler and flow through
/// StkPushPayload → stored on MpesaTransaction → recalled here.
/// </summary>
public sealed record StkCallbackOutboxPayload(
    string CheckoutRequestId,
    int ResultCode,
    string ResultDesc,
    string? MpesaReceiptNumber,
    string RawCallbackJson,
    // KES leg wallets
    Guid SettlementWalletId,
    Guid TreasuryKesWalletId,
    Guid RevenueWalletId,
    // Crypto leg wallets
    Guid TreasuryWalletId,
    Guid UserWalletId,
    Guid SpreadWalletId,
    // Amounts from original buy calculation
    decimal GrossKes,
    decimal PlatformFeeKes,
    decimal NetKes,
    decimal GrossCrypto,
    decimal UserCrypto,
    decimal SpreadCrypto);