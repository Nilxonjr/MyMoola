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
    IWalletRepository wallets,
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
        // Ignore zero or negative value transfers
        var currency = MapAssetToCurrency(activity.Asset);
        if (currency is null || activity.Value <= 0) return;

        // Only process INCOMING transfers — toAddress must be a registered deposit address
        // Outgoing sweeps and withdrawals FROM our addresses are ignored here
        var depositAddress = await depositAddresses
            .FindByAddressAsync(activity.ToAddress, ct);

        if (depositAddress is null) return;

        // Ignore transfers FROM our own system addresses — these are sweeps or internal moves
        var fromAddress = activity.FromAddress.ToLowerInvariant();
        var hotWalletAddress = await depositAddresses
            .FindByUserAndChainAsync(SystemWallets.HotWalletAccountUserId, Chain.Ethereum, ct);

        var isFromSystemWallet =
            (hotWalletAddress is not null &&
             hotWalletAddress.Address.ToLowerInvariant() == fromAddress);

        var isToSystemWallet = (hotWalletAddress is not null && hotWalletAddress.Address.ToLowerInvariant()
            == activity.ToAddress.ToLowerInvariant());

        if (isFromSystemWallet || isToSystemWallet)
        {
            logger.LogDebug(
                "Ignoring internal transfer. From={From} To={To} TxHash={TxHash}",
                activity.FromAddress, activity.ToAddress, activity.Hash);
            return;
        }

        // Idempotency — if we already have this tx hash skip
        var existing = await transactions
            .FindByOnChainTxHashAsync(activity.Hash, ct);

        if (existing is not null) return;

        var transaction = Transaction.CreateDeposit(
            userId: depositAddress.UserId,
            currency: currency.Value,
            amount: activity.Value,
            txHash: activity.Hash,
            referenceCode: ReferenceCodeGenerator.Generate("DEP"));

        await transactions.AddAsync(transaction, ct);

        depositAddress.MarkUsed();

        await uow.SaveChangesAsync(ct);

        logger.LogInformation(
            "Deposit detected. Awaiting confirmations. " +
            "TransactionId={TransactionId} TxHash={TxHash} " +
            "Currency={Currency} Amount={Amount} UserId={UserId}",
            transaction.Id, activity.Hash,
            currency.Value, activity.Value, depositAddress.UserId);
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