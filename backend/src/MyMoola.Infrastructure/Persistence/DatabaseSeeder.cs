// MyMoola.Infrastructure/Persistence/DatabaseSeeder.cs
using BCrypt.Net;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;

namespace MyMoola.Infrastructure.Persistence;

public sealed class DatabaseSeeder(
    ISystemControlRepository systemControls,
    IWalletRepository wallets,
    IAdminRepository admins,
    IEmailService emailService,
    IConfiguration configuration,
    IUnitOfWork uow,
    IUserRepository users,
    IDepositAddressRepository depositAddresses,
    ILogger<DatabaseSeeder> logger)
{
    public async Task SeedAsync(CancellationToken ct = default)
    {
        await SeedSuperAdminAsync(ct);
        await SeedSystemControlsAsync(ct);
        await SeedSystemUsersAsync(ct);
        await SeedSystemWalletsAsync(ct);
        await SeedSystemDepositAddressesAsync(ct);
    }

    private async Task SeedSystemUsersAsync(CancellationToken ct)
    {
        var systemUsers = new[]
        {
            (SystemWallets.TreasuryAccountUserId,      "Treasury Account",       "+000000000001"),
            (SystemWallets.RevenueAccountUserId,       "Revenue Account",        "+000000000002"),
            (SystemWallets.SpreadRevenueAccountUserId, "Spread Revenue Account", "+000000000003"),
            (SystemWallets.SettlementAccountUserId,    "Settlement Account",     "+000000000004"),
            (SystemWallets.SuspenseAccountUserId,      "Suspense Account",       "+000000000005"),
            (SystemWallets.HotWalletAccountUserId,     "Hot Wallet Account",     "+000000000006"),
        };

        foreach (var (id, name, phoneNumber) in systemUsers)
        {
            var existing = await users.FindByIdAsync(id, ct);
            if (existing is not null)
            {
                logger.LogInformation(
                    "System user already exists. Skipping. Name={Name}", name);
                continue;
            }

            var user = User.CreateSystem(id, name, phoneNumber);
            await users.AddAsync(user, ct);
            await uow.SaveChangesAsync(ct);

            logger.LogInformation(
                "System user seeded. Name={Name} Id={Id}", name, id);
        }
    }

    private async Task SeedSystemWalletsAsync(CancellationToken ct)
    {
        // Treasury — KES + all crypto
        await SeedWalletIfMissingAsync(
            SystemWallets.TreasuryAccountUserId, Currency.KES, ct);

        foreach (var currency in CryptoCurrencies)
            await SeedWalletIfMissingAsync(
                SystemWallets.TreasuryAccountUserId, currency, ct);

        // Revenue — KES only (fee income from buys and sells)
        await SeedWalletIfMissingAsync(
            SystemWallets.RevenueAccountUserId, Currency.KES, ct);

        // SpreadRevenue — KES (sells) + all crypto (buys)
        await SeedWalletIfMissingAsync(
            SystemWallets.SpreadRevenueAccountUserId, Currency.KES, ct);

        foreach (var currency in CryptoCurrencies)
            await SeedWalletIfMissingAsync(
                SystemWallets.SpreadRevenueAccountUserId, currency, ct);

        // Settlement — KES only (M-Pesa in, B2C out)
        await SeedWalletIfMissingAsync(
            SystemWallets.SettlementAccountUserId, Currency.KES, ct);

        // Suspense — KES only (B2C floor residuals)
        await SeedWalletIfMissingAsync(
            SystemWallets.SuspenseAccountUserId, Currency.KES, ct);

        // HotWallet — all crypto only (never holds KES)
        foreach (var currency in CryptoCurrencies)
            await SeedWalletIfMissingAsync(
                SystemWallets.HotWalletAccountUserId, currency, ct);

        await uow.SaveChangesAsync(ct);
    }

    private async Task SeedWalletIfMissingAsync(
        Guid userId, Currency currency, CancellationToken ct)
    {
        var existing = await wallets.FindByUserAndCurrencyAsync(userId, currency, ct);
        if (existing is not null)
        {
            logger.LogInformation(
                "System wallet already exists. Skipping. UserId={UserId} Currency={Currency}",
                userId, currency);
            return;
        }

        var wallet = Wallet.Create(userId, currency);
        await wallets.AddAsync(wallet, ct);

        logger.LogInformation(
            "System wallet seeded. UserId={UserId} Currency={Currency}",
            userId, currency);
    }

    private static readonly Currency[] CryptoCurrencies =
        [Currency.BTC, Currency.ETH, Currency.USDC];

    // --- unchanged methods below ---

    private async Task SeedSuperAdminAsync(CancellationToken ct)
    {
        if (await admins.AnyAsync(ct))
        {
            logger.LogInformation("SuperAdmin already exists. Skipping.");
            return;
        }

        var tempPassword = configuration["ADMIN_TEMP_PASSWORD"]
            ?? Environment.GetEnvironmentVariable("ADMIN_TEMP_PASSWORD");

        if (string.IsNullOrWhiteSpace(tempPassword))
        {
            logger.LogWarning(
                "No SuperAdmin exists and ADMIN_TEMP_PASSWORD is not set. " +
                "Skipping SuperAdmin seed. Set the variable and restart.");
            return;
        }

        var adminEmail = configuration["ADMIN_EMAIL"]
            ?? Environment.GetEnvironmentVariable("ADMIN_EMAIL")
            ?? throw new InvalidOperationException("ADMIN_EMAIL is not set.");

        var passwordHash = BCrypt.Net.BCrypt.HashPassword(tempPassword, workFactor: 12);

        var admin = AdminUser.Create(
            name: "Super Admin",
            email: adminEmail,
            passwordHash: passwordHash,
            role: AdminRole.SuperAdmin);

        await admins.AddAsync(admin, ct);
        await uow.SaveChangesAsync(ct);

        await emailService.SendAsync(
            to: admin.Email,
            subject: "MyMoola SuperAdmin Account Created",
            body: $"""
                Your SuperAdmin account has been created.
                Email: {admin.Email}
                Temporary Password: {tempPassword}
                Please log in and change your password immediately.
                """,
            ct);

        logger.LogInformation(
            "SuperAdmin seeded successfully. Email={Email}", admin.Email);
    }

    private async Task SeedSystemControlsAsync(CancellationToken ct)
    {
        var allKeys = new[]
        {
            SystemControlKeys.BtcBuyEnabled,
            SystemControlKeys.EthBuyEnabled,
            SystemControlKeys.UsdcBuyEnabled,
            SystemControlKeys.BtcSellEnabled,
            SystemControlKeys.EthSellEnabled,
            SystemControlKeys.UsdcSellEnabled,
            SystemControlKeys.BtcWithdrawEnabled,
            SystemControlKeys.EthWithdrawEnabled,
            SystemControlKeys.UsdcWithdrawEnabled,
            SystemControlKeys.BtcDepositEnabled,
            SystemControlKeys.EthDepositEnabled,
            SystemControlKeys.UsdcDepositEnabled,
            SystemControlKeys.GlobalMaintenance,
        };

        foreach (var key in allKeys)
        {
            if (await systemControls.ExistsByKeyAsync(key, ct))
            {
                logger.LogInformation(
                    "System control already exists. Skipping. Key={Key}", key);
                continue;
            }

            SystemControl control;

            if (key == SystemControlKeys.GlobalMaintenance)
            {
                control = SystemControl.Seed(key);
                control.Disable("System initializing", disabledBy: null);
            }
            else
            {
                control = SystemControl.Seed(key);
            }

            await systemControls.AddAsync(control, ct);
            await uow.SaveChangesAsync(ct);

            logger.LogInformation("System control seeded. Key={Key}", key);
        }
    }

    private async Task SeedSystemDepositAddressesAsync(CancellationToken ct)
    {
        var hotWalletAddress = configuration["Crypto__HotWalletAddress"]
            ?? throw new InvalidOperationException(
                "Crypto__HotWalletAddress is not set. " +
                "Derive from seed phrase at index 0 and set in environment variables.");

        var treasuryAddress = configuration["Crypto__TreasuryAddress"]
            ?? throw new InvalidOperationException(
                "Crypto__TreasuryAddress is not set. " +
                "Derive from seed phrase at index 1 and set in environment variables.");

        await SeedDepositAddressIfMissingAsync(
            userId: SystemWallets.HotWalletAccountUserId,
            address: hotWalletAddress,
            derivationPath: "m/44'/60'/0'/0/0",
            derivationIndex: BlockchainConstants.HotWalletDerivationIndex,
            ct: ct);

        await SeedDepositAddressIfMissingAsync(
            userId: SystemWallets.TreasuryAccountUserId,
            address: treasuryAddress,
            derivationPath: "m/44'/60'/0'/0/1",
            derivationIndex: BlockchainConstants.TreasuryDerivationIndex,
            ct: ct);

        await uow.SaveChangesAsync(ct);
    }

    private async Task SeedDepositAddressIfMissingAsync(
        Guid userId,
        string address,
        string derivationPath,
        int derivationIndex,
        CancellationToken ct)
    {
        var existing = await depositAddresses.FindByUserAndChainAsync(
            userId, Chain.Ethereum, ct);

        if (existing is not null)
        {
            logger.LogInformation(
                "Deposit address already exists. Skipping. UserId={UserId}", userId);
            return;
        }

        var depositAddress = DepositAddress.Create(
            userId: userId,
            chain: Chain.Ethereum,
            address: address,
            derivationPath: derivationPath,
            derivationIndex: derivationIndex);

        await depositAddresses.AddAsync(depositAddress, ct);

        logger.LogInformation(
            "System deposit address seeded. UserId={UserId} Address={Address} Index={Index}",
            userId, address, derivationIndex);
    }
}