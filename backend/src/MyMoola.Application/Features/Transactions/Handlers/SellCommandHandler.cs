// MyMoola.Application/Features/Transactions/Handlers/SellCommandHandler.cs
using System.Data;
using System.Text.Json;
using MediatR;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Helpers;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Common.Options;
using MyMoola.Application.Features.Transactions.Commands;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Transactions.Handlers;

public sealed class SellCommandHandler(
    ICurrentUserService currentUser,
    IIdempotencyContext idempotencyContext,
    IUserRepository users,
    IWalletRepository wallets,
    ITransactionRepository transactions,
    IMpesaTransactionRepository mpesaTransactions,
    ISystemControlRepository systemControls,
    IExchangeRateQuoteService quoteService,
    ILedgerService ledger,
    IOutboxService outbox,
    IUnitOfWork uow,
    IOptions<TestingOptions> testing,
    ILogger<SellCommandHandler> logger) : IRequestHandler<SellCommand, SellResponse>
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task<SellResponse> Handle(SellCommand command, CancellationToken ct)
    {
        // 1. Resolve caller
        if (currentUser.UserId is null)
            throw new UnauthorizedException();

        var userId = currentUser.UserId.Value;

        var idempotencyKey = idempotencyContext.IdempotencyKey
            ?? throw new InvalidOperationException("Idempotency key is required.");

        // 2. Load user
        var user = await users.FindByIdAsync(userId, ct)
            ?? throw new NotFoundException(nameof(User), userId);

        // 3. PIN lock check before BCrypt
        if (user.IsPinLocked)
            throw new PinLockedException(user.PinLockedUntil!.Value);

        // 4. Verify PIN
        if (!BCrypt.Net.BCrypt.Verify(command.Pin, user.PinHash))
        {
            user.RecordFailedPinAttempt();
            await uow.SaveChangesAsync(ct);

            if (user.IsPinLocked)
                throw new PinLockedException(user.PinLockedUntil!.Value);

            throw new InvalidCredentialsException();
        }

        // 5. Account must be active
        user.EnsureActive();

        // 6. System controls
        var maintenance = await systemControls
            .FindByKeyAsync(SystemControlKeys.GlobalMaintenance, ct);

        if (maintenance is not null && maintenance.IsEnabled)
            throw new OperationDisabledException(SystemControlKeys.GlobalMaintenance);

        var sellControlKey = command.Currency switch
        {
            Currency.BTC => SystemControlKeys.BtcSellEnabled,
            Currency.ETH => SystemControlKeys.EthSellEnabled,
            Currency.USDC => SystemControlKeys.UsdcSellEnabled,
            _ => throw new ArgumentOutOfRangeException(nameof(command.Currency))
        };

        var sellControl = await systemControls.FindByKeyAsync(sellControlKey, ct);
        if (sellControl is not null && !sellControl.IsEnabled)
            throw new OperationDisabledException(sellControlKey);

        // 7. Validate and load quote
        var quote = await quoteService.GetQuoteAsync(command.QuoteId, ct)
            ?? throw new InvalidOperationException(
                "Quote has expired. Please request a new rate and try again.");

        if (quote.Currency != command.Currency)
            throw new InvalidOperationException(
                "Quote currency does not match requested currency.");

        // 8. Fee calculation using locked rate
        var fees = FeeCalculator.CalculateSell(
            cryptoAmount: command.CryptoAmount,
            marketRate: quote.RateKes,
            spreadPercent: quote.SpreadPercent);

        // 9. Load user crypto wallet
        var userWallet = await wallets
            .FindByUserAndCurrencyAsync(userId, command.Currency, ct)
            ?? throw new NotFoundException(nameof(Wallet), $"{userId}/{command.Currency}");

        // 10. Check balance — early exit before any DB writes
        if (userWallet.Balance < command.CryptoAmount)
            throw new InsufficientBalanceException();

        // 11. Load system wallets
        var treasuryWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.TreasuryAccountUserId, command.Currency, ct)
            ?? throw new NotFoundException("TreasuryWallet", command.Currency);

        var treasuryKesWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.TreasuryAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("TreasuryKesWallet", Currency.KES);

        var revenueWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.RevenueAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("RevenueWallet", Currency.KES);

        var spreadKesWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.SpreadRevenueAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("SpreadRevenueKesWallet", Currency.KES);

        var settlementWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.SettlementAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("SettlementWallet", Currency.KES);

        var suspenseWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.SuspenseAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("SuspenseWallet", Currency.KES);

        // 12. Phone number — use test override if configured
        var phoneForMpesa = !string.IsNullOrWhiteSpace(
            testing.Value.StkPushPhoneOverride)
            ? testing.Value.StkPushPhoneOverride
            : user.PhoneNumberValue;

        // 13. Create transaction record
        var referenceCode = ReferenceCodeGenerator.Generate("SELL");

        var transaction = Transaction.Create(
            referenceCode: referenceCode,
            type: TransactionType.Sell,
            currency: command.Currency,
            amount: command.CryptoAmount,
            idempotencyKey: idempotencyKey,
            initiatorUserId: userId,
            feeAmount: fees.PlatformFeeKes);

        transaction.SetExchangeRates(
            exchangeRate: quote.SellRateKes,
            marketRate: quote.RateKes,
            kesAmount: fees.GrossKes);

        // Store sell-specific amounts in metadata —
        // callback handler reads these directly, never recalculates
        transaction.SetMetadata(JsonSerializer.Serialize(new
        {
            fees.GrossKes,
            fees.SpreadKes,
            fees.PlatformFeeKes,
            fees.B2CAmountKes,
            fees.ResidualKes
        }, JsonOptions));

        await transactions.AddAsync(transaction, ct);

        // 14. Create MpesaTransaction — outbound direction
        var mpesaTx = MpesaTransaction.Create(
            transactionId: transaction.Id,
            phoneNumber: phoneForMpesa,
            amountKes: fees.GrossKes,
            direction: "outbound");

        await mpesaTransactions.AddAsync(mpesaTx, ct);

        // 15. Open Serializable transaction — lock, status, outbox atomic
        await using var dbTransaction = await uow.BeginTransactionAsync(
            IsolationLevel.Serializable, ct);

        try
        {
            // Save Transaction + MpesaTransaction rows first —
            // LedgerEntry FK requires Transaction to exist
            await uow.SaveChangesAsync(ct);

            // Lock user crypto immediately — prevents double spend
            // LedgerService participates in ambient transaction
            await ledger.LockAsync(userWallet.Id, command.CryptoAmount, transaction.Id, ct);

            // Mark processing — crypto is now locked
            transaction.MarkProcessing();

            // Enqueue B2C outbox — written atomically with lock
            await outbox.EnqueueAsync(
                OutboxMessageTypes.B2C,
                new B2CPayload(
                    MpesaTransactionId: mpesaTx.Id,
                    TransactionId: transaction.Id,
                    PhoneNumber: phoneForMpesa,
                    B2CAmountKes: fees.B2CAmountKes,
                    ReferenceCode: referenceCode,
                    Currency: command.Currency,
                    CryptoAmount: command.CryptoAmount,
                    GrossKes: fees.GrossKes,
                    PlatformFeeKes: fees.PlatformFeeKes,
                    SpreadKes: fees.SpreadKes,
                    ResidualKes: fees.ResidualKes,
                    UserWalletId: userWallet.Id,
                    TreasuryWalletId: treasuryWallet.Id,
                    TreasuryKesWalletId: treasuryKesWallet.Id,
                    RevenueWalletId: revenueWallet.Id,
                    SpreadKesWalletId: spreadKesWallet.Id,
                    SettlementWalletId: settlementWallet.Id,
                    SuspenseWalletId: suspenseWallet.Id),
                ct);

            // Lock + MarkProcessing + OutboxMessage — one atomic commit
            await uow.SaveChangesAsync(ct);
            await dbTransaction.CommitAsync(ct);

            logger.LogInformation(
                "Sell initiated. TransactionId={TransactionId} UserId={UserId} " +
                "CryptoAmount={CryptoAmount} Currency={Currency} " +
                "GrossKes={GrossKes} B2CAmount={B2CAmount}",
                transaction.Id, userId, command.CryptoAmount,
                command.Currency, fees.GrossKes, fees.B2CAmountKes);
        }
        catch (Exception ex)
        {
            await dbTransaction.RollbackAsync(ct);

            logger.LogError(ex,
                "Sell initiation failed. TransactionId={TransactionId}",
                transaction.Id);

            throw;
        }

        await quoteService.ConsumeQuoteAsync(command.QuoteId, ct);

        return new SellResponse(
            TransactionId: transaction.Id,
            ReferenceCode: referenceCode,
            Message: "Sell initiated. M-Pesa payment will arrive shortly.");
    }
}

// Payload written to outbox — everything B2C handler needs
public sealed record B2CPayload(
    Guid MpesaTransactionId,
    Guid TransactionId,
    string PhoneNumber,
    int B2CAmountKes,
    string ReferenceCode,
    Currency Currency,
    decimal CryptoAmount,
    decimal GrossKes,
    decimal PlatformFeeKes,
    decimal SpreadKes,
    decimal ResidualKes,
    Guid UserWalletId,
    Guid TreasuryWalletId,
    Guid TreasuryKesWalletId,
    Guid RevenueWalletId,
    Guid SpreadKesWalletId,
    Guid SettlementWalletId,
    Guid SuspenseWalletId);