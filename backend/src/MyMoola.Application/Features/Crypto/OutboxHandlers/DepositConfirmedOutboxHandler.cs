using System.Data;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Crypto.DTOs;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Crypto.OutboxHandlers;

/// <summary>
/// Executes when a deposit reaches the confirmation threshold.
///
/// Ledger — two entries, your convention (credit = add, debit = subtract):
///   1. CREDIT  HotWallet [currency]     +amount   platform physically holds it
///   2. DEBIT   HotWallet [currency]     -amount   HotWallet backs the user balance
///   3. CREDIT  User Wallet [currency]   +amount   user is now owed this crypto
///
/// Net: HotWallet zero, User Wallet up.
///
/// IDEMPOTENT — checks Transaction.Status before executing.
/// </summary>
public sealed class DepositConfirmedOutboxHandler(
    ITransactionRepository transactions,
    IWalletRepository wallets,
    ILedgerService ledger,
    IOutboxService outbox,
    IUnitOfWork uow,
    ILogger<DepositConfirmedOutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.DepositConfirmed;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<DepositConfirmedOutboxPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize DepositConfirmedOutboxPayload. MessageId={message.Id}");

        var transaction = await transactions.FindByIdAsync(payload.TransactionId, ct)
            ?? throw new NotFoundException(nameof(Transaction), payload.TransactionId);

        // IDEMPOTENCY — already completed means ledger already ran
        if (transaction.Status is TransactionStatus.Completed)
        {
            logger.LogWarning(
                "DepositConfirmed for already completed transaction. Skipping. " +
                "TransactionId={TransactionId}", transaction.Id);
            return;
        }

        var userWallet = await wallets.FindByUserAndCurrencyAsync(
            payload.UserId, payload.Currency, ct)
            ?? throw new NotFoundException(
                $"User wallet not found. UserId={payload.UserId} Currency={payload.Currency}");

        var hotWallet = await wallets.FindByUserAndCurrencyAsync(
            SystemWallets.HotWalletAccountUserId, payload.Currency, ct)
            ?? throw new NotFoundException(
                $"HotWallet not found for currency {payload.Currency}");

        await using var dbTransaction = await uow.BeginTransactionAsync(
            IsolationLevel.Serializable, ct);

        try
        {
            // 1. CREDIT HotWallet — platform received on-chain
            await ledger.CreditAsync(
                hotWallet.Id,
                payload.Amount,
                transaction.Id,
                ct);

            // 2. DEBIT HotWallet — backs the user balance
            await ledger.DebitAsync(
                hotWallet.Id,
                payload.Amount,
                transaction.Id,
                ct);

            // 3. CREDIT User Wallet — user is now owed this crypto
            await ledger.CreditAsync(
                userWallet.Id,
                payload.Amount,
                transaction.Id,
                ct);

            transaction.MarkCompleted();

            // Enqueue address sweep — consolidate deposit address funds to hot wallet
            await outbox.EnqueueAsync(
                OutboxMessageTypes.AddressSweep,
                new AddressSweepOutboxPayload(
                    TransactionId: transaction.Id,
                    DepositAddressId: payload.DepositAddressId,
                    Currency: payload.Currency,
                    Amount: payload.Amount,
                    TxHash: payload.TxHash),
                ct);

            await uow.SaveChangesAsync(ct);
            await dbTransaction.CommitAsync(ct);

            logger.LogInformation(
                "Deposit confirmed and credited. " +
                "TransactionId={TransactionId} UserId={UserId} " +
                "Currency={Currency} Amount={Amount}",
                transaction.Id, payload.UserId,
                payload.Currency, payload.Amount);
        }
        catch (Exception ex)
        {
            await dbTransaction.RollbackAsync(ct);
            logger.LogError(ex,
                "Deposit confirmation ledger failed. TransactionId={TransactionId}",
                transaction.Id);
            throw;
        }
    }
}