using System.Net.Http.Json;
using System.Numerics;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Interfaces;
using MyMoola.Domain.Enums;
using MyMoola.Infrastructure.Settings;
using Nethereum.HdWallet;
using Nethereum.Web3;
using Nethereum.Web3.Accounts;

namespace MyMoola.Infrastructure.Services;

public sealed class BlockchainService(
    IOptions<HdWalletOptions> walletOptions,
    IOptions<AlchemyOptions> alchemyOptions,
    HttpClient httpClient,
    ILogger<BlockchainService> logger) : IBlockchainService
{
    private static readonly Dictionary<Currency, string> Erc20Contracts = new()
    {
        [Currency.USDC] = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238",
        [Currency.BTC] = "0x29f2D40B0605204364af54EC677bD022dA425d03"
    };

    private const string Erc20Abi = """
        [
          {
            "name": "transfer",
            "type": "function",
            "inputs": [
              { "name": "to", "type": "address" },
              { "name": "value", "type": "uint256" }
            ],
            "outputs": [{ "name": "", "type": "bool" }]
          },
          {
            "name": "balanceOf",
            "type": "function",
            "inputs": [{ "name": "account", "type": "address" }],
            "outputs": [{ "name": "", "type": "uint256" }]
          }
        ]
        """;

    private readonly HdWalletOptions _wallet = walletOptions.Value;
    private readonly AlchemyOptions _alchemy = alchemyOptions.Value;

    // -------------------------------------------------------------------------
    // Address derivation
    // -------------------------------------------------------------------------

    public Task<string> GetEthAddressAsync(int derivationIndex, CancellationToken ct = default)
    {
        var hdWallet = new Wallet(_wallet.HdWalletSeedPhrase, null);
        var account = hdWallet.GetAccount(derivationIndex);
        return Task.FromResult(account.Address);
    }

    // -------------------------------------------------------------------------
    // Balance queries
    // -------------------------------------------------------------------------

    public async Task<decimal> GetBalanceAsync(string address, Currency currency, CancellationToken ct = default)
    {
        var web3 = BuildWeb3();

        if (currency == Currency.ETH)
        {
            var wei = await web3.Eth.GetBalance.SendRequestAsync(address);
            return Web3.Convert.FromWei(wei.Value);
        }

        if (Erc20Contracts.TryGetValue(currency, out var contractAddress))
        {
            var contract = web3.Eth.GetContract(Erc20Abi, contractAddress);
            var balanceOf = contract.GetFunction("balanceOf");
            var raw = await balanceOf.CallAsync<BigInteger>(address);
            // USDC and WBTC use 6 decimals on Sepolia
            return (decimal)raw / 1_000_000m;
        }

        throw new InvalidOperationException($"Currency {currency} is not supported for balance queries.");
    }

    // -------------------------------------------------------------------------
    // Confirmation count — Fix B: +1 because inclusion in a block = 1 confirmation
    // -------------------------------------------------------------------------

    public async Task<int> GetConfirmationsAsync(string txHash, CancellationToken ct = default)
    {
        var web3 = BuildWeb3();
        var receipt = await web3.Eth.Transactions.GetTransactionReceipt.SendRequestAsync(txHash);
        if (receipt is null) return 0;

        var currentBlock = await web3.Eth.Blocks.GetBlockNumber.SendRequestAsync();
        var confirmations = (int)(currentBlock.Value - receipt.BlockNumber.Value) + 1;
        return Math.Max(1, confirmations);
    }

    // -------------------------------------------------------------------------
    // Broadcast withdrawal
    // Fix A: BigInteger from decimal string — no long cast, no overflow
    // Fix C: Use TransactionManager for remote signing via Alchemy
    // -------------------------------------------------------------------------

    public async Task<string> BroadcastWithdrawalAsync(
        string toAddress,
        decimal amount,
        Currency currency,
        int fromIndex,
        CancellationToken ct = default)
    {
        var account = DeriveAccount(fromIndex);
        var web3 = BuildWeb3(account);

        if (currency == Currency.ETH)
        {
            // Fix C: TransactionManager handles gas estimation and remote signing
            var wei = Web3.Convert.ToWei(amount);
            var txHash = await web3.Eth.GetEtherTransferService()
                .TransferEtherAsync(toAddress, amount);

            logger.LogInformation("ETH withdrawal broadcast. TxHash={TxHash}", txHash);
            return txHash;
        }

        if (Erc20Contracts.TryGetValue(currency, out var contractAddress))
        {
            var contract = web3.Eth.GetContract(Erc20Abi, contractAddress);
            var transfer = contract.GetFunction("transfer");

            var gas = new Nethereum.Hex.HexTypes.HexBigInteger(50_000);

            // Fix A: convert decimal to BigInteger via string — no long cast
            var tokenUnits = DecimalToTokenUnits(amount, decimals: 6);

            // Fix C: SendTransactionAndWaitForReceiptAsync uses TransactionManager
            //var receipt = await transfer.SendTransactionAndWaitForReceiptAsync(
            //    from: account.Address,
            //    receiptRequestCancellationToken: ct,
            //    functionInput: new object[] { toAddress, tokenUnits });

            var receipt = await transfer.SendTransactionAndWaitForReceiptAsync(
                from: account.Address,
                gas: gas,
                value: null,
                receiptRequestCancellationToken: ct,
                functionInput: new object[] { toAddress, tokenUnits });

            logger.LogInformation(
                    "ERC-20 transfer gas used: {GasUsed} of {GasLimit}",
                    receipt.GasUsed.Value,
                    50_000);

            logger.LogInformation(
                "{Currency} withdrawal broadcast. TxHash={TxHash}",
                currency, receipt.TransactionHash);

            return receipt.TransactionHash;
        }

        throw new InvalidOperationException($"Currency {currency} is not supported for withdrawals.");
    }

    // -------------------------------------------------------------------------
    // Broadcast sweep
    // -------------------------------------------------------------------------

    public async Task<string> BroadcastSweepAsync(
        string fromAddress,
        string toAddress,
        decimal amount,
        Currency currency,
        int fromIndex,
        CancellationToken ct = default)
    {
        logger.LogInformation(
            "Sweeping {Amount} {Currency} from {From} to {To}",
            amount, currency, fromAddress, toAddress);

        return await BroadcastWithdrawalAsync(toAddress, amount, currency, fromIndex, ct);
    }

    // -------------------------------------------------------------------------
    // Webhook registration
    // -------------------------------------------------------------------------

    public async Task RegisterWebhookAddressAsync(string address, CancellationToken ct = default)
    {
        var url = "https://dashboard.alchemy.com/api/update-webhook-addresses";

        var payload = new
        {
            webhook_id = _alchemy.WebhookId,
            addresses_to_add = new[] { address },
            addresses_to_remove = Array.Empty<string>()
        };

        using var request = new HttpRequestMessage(HttpMethod.Patch, url);
        request.Headers.Add("X-Alchemy-Token", _alchemy.AuthToken);
        request.Content = JsonContent.Create(payload);

        var response = await httpClient.SendAsync(request, ct);

        if (!response.IsSuccessStatusCode)
        {
            var body = await response.Content.ReadAsStringAsync(ct);
            logger.LogWarning(
                "Failed to register webhook address {Address}. Status={Status} Body={Body}",
                address, response.StatusCode, body);
        }
        else
        {
            logger.LogInformation("Registered webhook address {Address}", address);
        }
    }

    public async Task<bool> TransactionExistsOnChainAsync(
    string txHash, CancellationToken ct = default)
    {
        var web3 = BuildWeb3();
        var receipt = await web3.Eth.Transactions
            .GetTransactionReceipt.SendRequestAsync(txHash);
        return receipt is not null;
    }

    public async Task<bool> TransactionSucceededAsync(
        string txHash, CancellationToken ct = default)
    {
        var web3 = BuildWeb3();
        var receipt = await web3.Eth.Transactions
            .GetTransactionReceipt.SendRequestAsync(txHash);
        if (receipt is null) return false;
        // Status 1 = success, Status 0 = reverted
        return receipt.Status.Value == 1;
    }
    public async Task<decimal> GetHotWalletBalanceAsync(Currency currency, CancellationToken ct = default)
    {
        var hotWalletAddress = await GetEthAddressAsync(
            BlockchainConstants.HotWalletDerivationIndex, ct);
        return await GetBalanceAsync(hotWalletAddress, currency, ct);
    }
    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Account DeriveAccount(int index)
    {
        var hdWallet = new Wallet(_wallet.HdWalletSeedPhrase, null);
        return hdWallet.GetAccount(index);
    }

    private Web3 BuildWeb3(Account? account = null)
    {
        var rpcUrl = $"{_alchemy.BaseUrl.TrimEnd('/')}/{_alchemy.ApiKey}";
        return account is null ? new Web3(rpcUrl) : new Web3(account, rpcUrl);
    }

    /// <summary>
    /// Converts a decimal amount to token units (BigInteger) without casting
    /// through long. Safe for any token decimal precision.
    /// e.g. 10.5 USDC with 6 decimals → 10_500_000
    /// </summary>
    private static BigInteger DecimalToTokenUnits(decimal amount, int decimals)
    {
        var multiplier = (decimal)Math.Pow(10, decimals);
        var units = decimal.Truncate(amount * multiplier);
        return BigInteger.Parse(units.ToString("F0"));
    }

    public async Task<decimal> GetEstimatedGasCostAsync(Currency currency, CancellationToken ct = default)
    {
        var web3 = BuildWeb3();

        // Requesting 10 blocks of history
        var feeHistory = await web3.Eth.FeeHistory.SendRequestAsync(
            new Nethereum.Hex.HexTypes.HexBigInteger(10),
            Nethereum.RPC.Eth.DTOs.BlockParameter.CreateLatest(),
            new[] { 75.0m });

        // 1. Core Base Fee: Find the absolute peak across the last 10 blocks
        var baseFees = feeHistory.BaseFeePerGas.Select(x => (decimal)x.Value).ToList();
        var maxBaseFeeWei = baseFees.Max();

        // 2. Priority Fee: Extract the 75th percentile tip across the 10 blocks safely
        // Since we only passed one percentile value (75.0m), it will live at index [0] of each row
        var tips = feeHistory.Reward is not null
            ? feeHistory.Reward
                .Where(r => r != null && r.Length > 0)
                .Select(r => (decimal)r[0].Value)
                .OrderBy(x => x)
                .ToList()
            : new List<decimal>();

        var priorityFeeWei = tips.Count > 0
            ? tips[tips.Count / 2]  // median of 75th percentile tips across 10 blocks
            : 1_500_000_000m;

        // Cap the priority fee to prevent testnet bot spikes from overfunding the wallet
        const decimal MaxTestnetPriorityFeeWei = 3_000_000_000m; // 3 Gwei cap
        if (priorityFeeWei > MaxTestnetPriorityFeeWei)
        {
            priorityFeeWei = MaxTestnetPriorityFeeWei;
        }

        // 3. Absolute Worst-Case Pricing Envelope (Historical Max * 1.5x for 3-4 block growth headroom)
        var maxGasPriceWei = (maxBaseFeeWei * 1.5m) + priorityFeeWei;

        // Use 65,000 for ERC-20 sweeps to absorb internal contract state adjustments safely
        var gasLimit = currency == Currency.ETH ? 21_000m : 65_000m;

        var gasCostWei = maxGasPriceWei * gasLimit;

        // Return exact total ETH value required to fund the address for the pre-flight check
        return gasCostWei / 1_000_000_000_000_000_000m;
    }

    public async Task<decimal> GetCurrentGasPriceAsync(CancellationToken ct = default)
    {
        var web3 = BuildWeb3();

        var feeHistory = await web3.Eth.FeeHistory.SendRequestAsync(
            new Nethereum.Hex.HexTypes.HexBigInteger(1),
            Nethereum.RPC.Eth.DTOs.BlockParameter.CreateLatest(),
            new[] { 50.0m });

        var baseFeeWei = decimal.Parse(feeHistory.BaseFeePerGas[^1].Value.ToString());
        var rawPriorityFeeWei = feeHistory.Reward is not null && feeHistory.Reward.Length > 0
    ? decimal.Parse(feeHistory.Reward[0][0].Value.ToString())
    : 1_500_000_000m;

        // Cap priority fee at 3 Gwei — Sepolia validators set artificially high tips
        // On mainnet/Base this cap will rarely be hit
        const decimal MaxPriorityFeeWei = 3_000_000_000m;
        var priorityFeeWei = Math.Min(rawPriorityFeeWei, MaxPriorityFeeWei);

        logger.LogInformation(
            "EIP1559 fees. BaseFeeWei={BaseFee} RawPriorityFeeWei={RawPriority} " +
            "CappedPriorityFeeWei={CappedPriority}",
            baseFeeWei, rawPriorityFeeWei, priorityFeeWei);

        return (baseFeeWei * 1.2m) + priorityFeeWei;
    }
}

