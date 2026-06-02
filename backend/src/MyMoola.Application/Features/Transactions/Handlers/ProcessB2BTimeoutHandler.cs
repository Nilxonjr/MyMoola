// MyMoola.Application/Features/Transactions/Handlers/ProcessB2BTimeoutHandler.cs
using System.Text.Json;
using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Transactions.Commands;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Transactions.Handlers;

public sealed class ProcessB2BTimeoutHandler(
    IMpesaTransactionRepository mpesaTransactions,
    ITransactionRepository transactions,
    IWalletRepository wallets,
    IOutboxService outbox,
    IUnitOfWork uow,
    ILogger<ProcessB2BTimeoutHandler> logger) : IRequestHandler<ProcessB2BTimeoutCommand>
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task Handle(
        ProcessB2BTimeoutCommand request,
        CancellationToken ct)
    {
        var result = request.Callback.Result;
        var rawJson = JsonSerializer.Serialize(request.Callback, JsonOptions);

        logger.LogWarning(
            "B2B timeout received. ConversationID={ID}",
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
            OutboxMessageTypes.B2BTimeout,
            new B2BTimeoutOutboxPayload(
                ConversationID: result.ConversationID,
                RawCallbackJson: rawJson,
                UserWalletId: userWallet.Id,
                CryptoAmount: transaction.Amount),
            ct);

        await uow.SaveChangesAsync(ct);
    }
}