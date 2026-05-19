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
    ILogger<DatabaseSeeder> logger)
{
    public async Task SeedAsync(CancellationToken ct = default)
    {
        await SeedSuperAdminAsync(ct);
        await SeedSystemControlsAsync(ct);
        await SeedSystemUsersAsync(ct);
        await SeedSystemWalletsAsync(ct);
    }
    private async Task SeedSystemUsersAsync(CancellationToken ct)
    {
        var systemUsers = new[]
        {
            (SystemWallets.OperationalBufferUserId, "Operational Buffer", "+000000000001"),
            (SystemWallets.PlatformFeeUserId, "Platform Fee", "+000000000002")
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

            logger.LogInformation("System user seeded. Name={Name} Id={Id}", name, id);
        }
    }

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

    private async Task SeedSystemWalletsAsync(CancellationToken ct)
    {
        var currencies = new[] { Currency.BTC, Currency.ETH, Currency.USDC };

        foreach (var currency in currencies)
        {
            var existingBuffer = await wallets.FindByUserAndCurrencyAsync(
                SystemWallets.OperationalBufferUserId, currency, ct);

            if (existingBuffer is null)
            {
                var buffer = Wallet.Create(SystemWallets.OperationalBufferUserId, currency);
                await wallets.AddAsync(buffer, ct);
                logger.LogInformation(
                    "Operational buffer wallet seeded. Currency={Currency}", currency);
            }

            var existingFee = await wallets.FindByUserAndCurrencyAsync(
                SystemWallets.PlatformFeeUserId, currency, ct);

            if (existingFee is null)
            {
                var fee = Wallet.Create(SystemWallets.PlatformFeeUserId, currency);
                await wallets.AddAsync(fee, ct);
                logger.LogInformation(
                    "Platform fee wallet seeded. Currency={Currency}", currency);
            }
        }

        await uow.SaveChangesAsync(ct);
    }
}