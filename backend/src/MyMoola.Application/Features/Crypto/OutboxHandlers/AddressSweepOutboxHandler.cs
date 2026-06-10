using System.Text.Json;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Crypto.DTOs;
using MyMoola.Application.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Crypto.OutboxHandlers;

/// <summary>
/// Sweeps funds from deposit address back to the platform hot wallet.
///
/// ETH sweep:
///   Dynamically estimates gas, sweeps (totalBalance - gasCost).
///
/// ERC-20 sweep (USDC, WBTC):
///   Step 1 — Check deposit address ETH balance for gas
///   Step 2 — If insufficient, fund from hot wallet
///             PendingGasFundingTxHash stored on DepositAddress — idempotent on retry
///   Step 3 — Wait for funding confirmation
///   Step 4 — Sweep token balance
///
/// Private keys derived on demand — never stored.
/// </summary>
public sealed class AddressSweepOutboxHandler(
    IDepositAddressRepository depositAddresses,
    IBlockchainService blockchain,
    IUnitOfWork uow,
    ILogger<AddressSweepOutboxHandler> logger) : IOutboxMessageHandler
{
    public string Type => OutboxMessageTypes.AddressSweep;

    private static readonly TimeSpan GasFundingPollInterval = TimeSpan.FromSeconds(15);
    private const int GasFundingMaxAttempts = 20; // 5 minutes total

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default)
    {
        var payload = JsonSerializer.Deserialize<AddressSweepOutboxPayload>(
            message.Payload, JsonOptions)
            ?? throw new InvalidOperationException(
                $"Failed to deserialize AddressSweepOutboxPayload. MessageId={message.Id}");

        var depositAddress = await depositAddresses
            .FindByIdAsync(payload.DepositAddressId, ct)
            ?? throw new NotFoundException(nameof(DepositAddress), payload.DepositAddressId);

        var hotWalletIndex = 0;
        var hotWalletAddress = await blockchain.GetEthAddressAsync(hotWalletIndex, ct);

        if (payload.Currency == Currency.ETH)
            await SweepEthAsync(depositAddress, hotWalletAddress, ct);
        else
            await SweepErc20Async(depositAddress, hotWalletAddress, hotWalletIndex, payload, ct);
    }

    // -------------------------------------------------------------------------
    // ETH sweep — deduct gas dynamically, sweep remainder
    // -------------------------------------------------------------------------

    private async Task SweepEthAsync(
        DepositAddress depositAddress,
        string hotWalletAddress,
        CancellationToken ct)
    {
        var totalBalance = await blockchain
            .GetBalanceAsync(depositAddress.Address, Currency.ETH, ct);

        var estimatedGas = await blockchain
            .GetEstimatedGasCostAsync(Currency.ETH, ct);

        var sweepAmount = totalBalance - estimatedGas;

        if (sweepAmount <= 0.0001m)
        {
            logger.LogWarning(
                "ETH balance too low to cover gas. Skipping sweep. " +
                "Address={Address} Balance={Balance} EstimatedGas={Gas}",
                depositAddress.Address, totalBalance, estimatedGas);
            return;
        }

        var sweepTxHash = await blockchain.BroadcastSweepAsync(
            fromAddress: depositAddress.Address,
            toAddress: hotWalletAddress,
            amount: sweepAmount,
            currency: Currency.ETH,
            fromIndex: depositAddress.DerivationIndex,
            ct: ct);

        logger.LogInformation(
            "ETH sweep broadcast. TxHash={TxHash} Amount={Amount} Address={Address}",
            sweepTxHash, sweepAmount, depositAddress.Address);
    }

    // -------------------------------------------------------------------------
    // ERC-20 sweep — ensure gas funded idempotently, then sweep tokens
    // -------------------------------------------------------------------------

    private async Task SweepErc20Async(
        DepositAddress depositAddress,
        string hotWalletAddress,
        int hotWalletIndex,
        AddressSweepOutboxPayload payload,
        CancellationToken ct)
    {
        // If we already broadcast a gas funding tx (from a previous attempt),
        // skip straight to waiting — do not broadcast again
        if (depositAddress.PendingGasFundingTxHash is null)
        {
            var ethBalance = await blockchain
                .GetBalanceAsync(depositAddress.Address, Currency.ETH, ct);

            var estimatedGas = await blockchain
                .GetEstimatedGasCostAsync(payload.Currency, ct);

            if (ethBalance < estimatedGas)
            {
                var fundingAmount = estimatedGas - ethBalance;

                logger.LogInformation(
                    "Funding gas for ERC-20 sweep. " +
                    "Address={Address} EthBalance={Balance} Required={Required} Funding={Funding}",
                    depositAddress.Address, ethBalance, estimatedGas, fundingAmount);

                var fundingTxHash = await blockchain.BroadcastWithdrawalAsync(
                    toAddress: depositAddress.Address,
                    amount: fundingAmount,
                    currency: Currency.ETH,
                    fromIndex: hotWalletIndex,
                    ct: ct);

                // Persist funding tx hash before waiting — retry-safe
                depositAddress.SetGasFundingTxHash(fundingTxHash);
                await uow.SaveChangesAsync(ct);

                logger.LogInformation(
                    "Gas funding broadcast. TxHash={TxHash} Address={Address}",
                    fundingTxHash, depositAddress.Address);

                await WaitForConfirmationAsync(fundingTxHash, ct);
            }
        }
        else
        {
            // Resume from previous attempt — wait for already-broadcast funding tx
            logger.LogInformation(
                "Resuming gas funding wait from previous attempt. " +
                "TxHash={TxHash} Address={Address}",
                depositAddress.PendingGasFundingTxHash, depositAddress.Address);

            await WaitForConfirmationAsync(depositAddress.PendingGasFundingTxHash, ct);
        }

        var tokenBalance = await blockchain
            .GetBalanceAsync(depositAddress.Address, payload.Currency, ct);

        if (tokenBalance <= 1)
        {
            logger.LogWarning(
                "No token balance to sweep. Address={Address} Currency={Currency}",
                depositAddress.Address, payload.Currency);
            depositAddress.ClearGasFundingTxHash();
            await uow.SaveChangesAsync(ct);
            return;
        }

        var sweepTxHash = await blockchain.BroadcastSweepAsync(
            fromAddress: depositAddress.Address,
            toAddress: hotWalletAddress,
            amount: tokenBalance,
            currency: payload.Currency,
            fromIndex: depositAddress.DerivationIndex,
            ct: ct);

        // Clear gas funding hash — sweep succeeded
        depositAddress.ClearGasFundingTxHash();
        await uow.SaveChangesAsync(ct);

        logger.LogInformation(
            "ERC-20 sweep broadcast. TxHash={TxHash} " +
            "Currency={Currency} Amount={Amount} Address={Address}",
            sweepTxHash, payload.Currency, payload.Amount,
            depositAddress.Address);
    }

    // -------------------------------------------------------------------------
    // Wait for a tx to get at least 1 confirmation
    // -------------------------------------------------------------------------

    private async Task WaitForConfirmationAsync(string txHash, CancellationToken ct)
    {
        for (var attempt = 0; attempt < GasFundingMaxAttempts; attempt++)
        {
            await Task.Delay(GasFundingPollInterval, ct);

            var confirmations = await blockchain.GetConfirmationsAsync(txHash, ct);
            if (confirmations >= 1)
            {
                logger.LogInformation(
                    "Transaction confirmed. TxHash={TxHash}", txHash);
                return;
            }

            logger.LogDebug(
                "Waiting for confirmation. TxHash={TxHash} Attempt={Attempt}/{Max}",
                txHash, attempt + 1, GasFundingMaxAttempts);
        }

        // Throw — outbox will retry. PendingGasFundingTxHash is already persisted
        // so next retry resumes waiting instead of broadcasting again
        throw new InvalidOperationException(
            $"Transaction not confirmed after {GasFundingMaxAttempts} attempts. " +
            $"TxHash={txHash}");
    }
}