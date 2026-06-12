using MyMoola.Domain.Enums;

namespace MyMoola.Application.Interfaces;

public interface IBlockchainService
{
    /// <summary>
    /// Derives the Ethereum address for a given BIP-44 derivation index.
    /// Path: m/44'/60'/0'/0/{index}
    /// </summary>
    Task<string> GetEthAddressAsync(int derivationIndex, CancellationToken ct = default);

    /// <summary>
    /// Returns the on-chain balance of an address for a given currency.
    /// ETH: native balance. USDC/WBTC: ERC-20 balance.
    /// </summary>
    Task<decimal> GetBalanceAsync(string address, Currency currency, CancellationToken ct = default);

    /// <summary>
    /// Returns the current confirmation count for a transaction hash.
    /// Returns 0 if not yet mined.
    /// </summary>
    Task<int> GetConfirmationsAsync(string txHash, CancellationToken ct = default);

    /// <summary>
    /// Signs and broadcasts a withdrawal from the platform hot wallet.
    /// ETH: native transfer. USDC/WBTC: ERC-20 transfer.
    /// Returns the on-chain transaction hash.
    /// </summary>
    Task<string> BroadcastWithdrawalAsync(
        string toAddress,
        decimal amount,
        Currency currency,
        int fromIndex,
        CancellationToken ct = default);

    /// <summary>
    /// Signs and broadcasts a sweep from a deposit address to the hot wallet.
    /// Returns the on-chain transaction hash.
    /// </summary>
    Task<string> BroadcastSweepAsync(
        string fromAddress,
        string toAddress,
        decimal amount,
        Currency currency,
        int fromIndex,
        CancellationToken ct = default);

    /// <summary>
    /// Registers a deposit address with the Alchemy webhook.
    /// </summary>
    Task RegisterWebhookAddressAsync(string address, CancellationToken ct = default);

    /// <summary>
    /// Estimates the gas cost in ETH for a transfer of the given currency.
    /// ETH: 21,000 gas. ERC-20: ~65,000 gas. Multiplied by current base fee.
    /// </summary>
    Task<decimal> GetEstimatedGasCostAsync(Currency currency, CancellationToken ct = default);

    Task<decimal> GetCurrentGasPriceAsync(CancellationToken ct = default);

    Task<bool> TransactionExistsOnChainAsync(string txHash, CancellationToken ct = default);
    Task<bool> TransactionSucceededAsync(string txHash, CancellationToken ct = default);

    Task<decimal> GetHotWalletBalanceAsync(Currency currency, CancellationToken ct = default);
}