// MyMoola.Application/Features/Transactions/Handlers/ProcessB2CTimeoutHandler.cs
using System.Text.Json;
using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Transactions.Commands;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Transactions.Handlers;

/// <summary>
/// Handles B2C queue timeout — Safaricom could not process in time.
/// Enqueues timeout message for unlock handler.
/// </summary>
public sealed class ProcessB2CTimeoutHandler(
    IMpesaTransactionRepository mpesaTransactions,
    ITransactionRepository transactions,
    IWalletRepository wallets,
    IOutboxService outbox,
    IUnitOfWork uow,
    ILogger<ProcessB2CTimeoutHandler> logger) : IRequestHandler<ProcessB2CTimeoutCommand>
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task Handle(
        ProcessB2CTimeoutCommand request,
        CancellationToken ct)
    {
        var result = request.Callback.Result;
        var rawJson = JsonSerializer.Serialize(request.Callback, JsonOptions);

        logger.LogWarning(
            "B2C timeout received. ConversationID={ID}",
            result.ConversationID);

        var mpesaTx = await mpesaTransactions
            .FindByConversationIDAsync(result.ConversationID, ct)
            ?? throw new NotFoundException(
                nameof(Domain.Entities.MpesaTransaction), result.ConversationID);

        var transaction = await transactions.FindByIdAsync(mpesaTx.TransactionId, ct)
            ?? throw new NotFoundException(
                nameof(Domain.Entities.Transaction), mpesaTx.TransactionId);

        var userWallet = await wallets
            .FindByUserAndCurrencyAsync(
                transaction.InitiatorUserId!.Value, transaction.Currency, ct)
            ?? throw new NotFoundException("UserWallet", transaction.Currency);

        await outbox.EnqueueAsync(
            OutboxMessageTypes.B2CTimeout,
            new B2CTimeoutOutboxPayload(
                ConversationID: result.ConversationID,
                RawCallbackJson: rawJson,
                UserWalletId: userWallet.Id,
                CryptoAmount: transaction.Amount),
            ct);

        await uow.SaveChangesAsync(ct);
    }
}