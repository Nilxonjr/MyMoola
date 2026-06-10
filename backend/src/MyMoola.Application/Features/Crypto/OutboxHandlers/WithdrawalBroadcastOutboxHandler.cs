using System.Text.Json;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Crypto.DTOs;
using MyMoola.Application.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Crypto.OutboxHandlers;

/// <summary>
/// Broadcasts the withdrawal transaction on-chain from the hot wallet.
/// Stores the tx hash on the transaction and marks it Processing.
/// Confirmation polling is handled by WithdrawalConfirmationPollerJob.
///
/// IDEMPOTENT — if OnChainTxHash already set, already broadcast, skip.
/// </summary>
public sealed class WithdrawalBroadcastOutboxHandler(
    ITransactionRepository transactions,
    IBlockchainService blockchain,
    IUnitOfWork uow,
    ILogger<WithdrawalBroadcastOutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.WithdrawalBroadcast;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<WithdrawalBroadcastOutboxPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize WithdrawalBroadcastOutboxPayload. MessageId={message.Id}");

        var transaction = await transactions.FindByIdAsync(payload.TransactionId, ct)
            ?? throw new NotFoundException(nameof(Transaction), payload.TransactionId);

        // IDEMPOTENCY — already broadcast, poller will handle confirmation
        if (transaction.OnChainTxHash is not null)
        {
            logger.LogWarning(
                "Withdrawal already broadcast. Skipping. " +
                "TransactionId={TransactionId} TxHash={TxHash}",
                transaction.Id, transaction.OnChainTxHash);
            return;
        }

        logger.LogInformation(
            "Broadcasting withdrawal. TransactionId={TransactionId} " +
            "Currency={Currency} NetAmount={NetAmount} ToAddress={ToAddress}",
            transaction.Id, payload.Currency,
            payload.NetAmount, payload.ToAddress);

        // Broadcast net amount on-chain from hot wallet
        var txHash = await blockchain.BroadcastWithdrawalAsync(
            toAddress: payload.ToAddress,
            amount: payload.NetAmount,
            currency: payload.Currency,
            fromIndex: BlockchainConstants.HotWalletDerivationIndex,
            ct: ct);

        transaction.SetOnChainTxHash(txHash);
        transaction.MarkProcessing();

        await uow.SaveChangesAsync(ct);

        logger.LogInformation(
            "Withdrawal broadcast. TransactionId={TransactionId} TxHash={TxHash}",
            transaction.Id, txHash);
    }
}