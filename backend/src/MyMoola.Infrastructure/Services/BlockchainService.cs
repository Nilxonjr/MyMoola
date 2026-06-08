using System.Net.Http.Json;
using System.Numerics;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
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

            // Fix A: convert decimal to BigInteger via string — no long cast
            var tokenUnits = DecimalToTokenUnits(amount, decimals: 6);

            // Fix C: SendTransactionAndWaitForReceiptAsync uses TransactionManager
            var receipt = await transfer.SendTransactionAndWaitForReceiptAsync(
                from: account.Address,
                receiptRequestCancellationToken: ct,
                functionInput: new object[] { toAddress, tokenUnits });

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
            addresses_to_add = new[] { address }
        };

        // Alchemy management API requires auth token — different from API key
        using var request = new HttpRequestMessage(HttpMethod.Post, url);
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
        var block = await web3.Eth.Blocks
            .GetBlockWithTransactionsByNumber
            .SendRequestAsync(Nethereum.RPC.Eth.DTOs.BlockParameter.CreateLatest());

        // Base fee is in Wei
        var baseFeeWei = (decimal)block.BaseFeePerGas.Value;

        // Add 20% tip buffer on top of base fee
        var gasPriceWei = baseFeeWei * 1.2m;

        var gasLimit = currency == Currency.ETH ? 21_000m : 65_000m;

        var gasCostWei = gasPriceWei * gasLimit;
        return gasCostWei / 1_000_000_000_000_000_000m; // convert Wei to ETH
    }
}