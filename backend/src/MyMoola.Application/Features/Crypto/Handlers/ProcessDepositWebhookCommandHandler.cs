using System.Text.Json;
using MediatR;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Application.Common.Helpers;
using MyMoola.Application.Features.Crypto.DTOs;
using Microsoft.Extensions.Logging;

namespace MyMoola.Application.Features.Crypto.Commands;

public sealed class ProcessDepositWebhookCommandHandler(
    IDepositAddressRepository depositAddresses,
    ITransactionRepository transactions,
    IOutboxService outbox,
    ILogger<ProcessDepositWebhookCommandHandler> logger,
    IUnitOfWork uow) : IRequestHandler<ProcessDepositWebhookCommand>
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true
    };

    public async Task Handle(ProcessDepositWebhookCommand request, CancellationToken ct)
    {
        logger.LogInformation("Alchemy raw payload: {Payload}", request.RawPayload);

        var payload = JsonSerializer.Deserialize<AlchemyWebhookPayload>(
            request.RawPayload, JsonOptions)
            ?? throw new InvalidOperationException("Failed to deserialize Alchemy webhook payload.");

        foreach (var activity in payload.Event.Activity)
        {
            try
            {
                await ProcessActivityAsync(activity, ct);
            }
            catch (Exception ex)
            {
                logger.LogError(ex,
                "Failed to process activity. TxHash={TxHash}", activity.Hash);
                _ = ex;
            }
        }
    }

    private async Task ProcessActivityAsync(AlchemyActivity activity, CancellationToken ct)
    {
        // Ignore zero-value or unrecognised asset transfers
        var currency = MapAssetToCurrency(activity.Asset);
        if (currency is null || activity.Value <= 0) return;

        var depositAddress = await depositAddresses
            .FindByAddressAsync(activity.ToAddress, ct);

        // Not one of our deposit addresses — ignore
        if (depositAddress is null) return;
        if (depositAddress is null) return;

        // Idempotency — if we already have this tx hash, skip
        var existing = await transactions
            .FindByOnChainTxHashAsync(activity.Hash, ct);

        if (existing is not null) return;
        var referenceCode = ReferenceCodeGenerator.Generate("DEP");

        var transaction = Transaction.CreateDeposit(
            referenceCode: referenceCode,
            userId: depositAddress.UserId,
            currency: currency.Value,
            amount: activity.Value,
            txHash: activity.Hash);

        await transactions.AddAsync(transaction, ct);

        await outbox.EnqueueAsync(
            OutboxMessageTypes.DepositDetected,
            new DepositDetectedOutboxPayload(
                TransactionId: transaction.Id,
                UserId: depositAddress.UserId,
                Currency: currency.Value,
                Amount: activity.Value,
                TxHash: activity.Hash,
                DepositAddressId: depositAddress.Id),
            ct);

        depositAddress.MarkUsed();

        // Each activity saved atomically — failure here only affects this activity
        await uow.SaveChangesAsync(ct);
    }

    private static Currency? MapAssetToCurrency(string asset) => asset.ToUpperInvariant() switch
    {
        "ETH" => Currency.ETH,
        "USDC" => Currency.USDC,
        "WBTC" => Currency.BTC,
        _ => null
    };
}

public sealed record AlchemyWebhookPayload(
    string WebhookId,
    string Id,
    string Type,
    AlchemyEvent Event);

public sealed record AlchemyEvent(
    string Network,
    List<AlchemyActivity> Activity);

public sealed record AlchemyActivity(
    string FromAddress,
    string ToAddress,
    decimal Value,
    string Asset,
    string Hash,
    string Category);