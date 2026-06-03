// MyMoola.Application/Features/Transactions/Handlers/PayMerchantCommandHandler.cs
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

public sealed class PayMerchantCommandHandler(
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
    ILogger<PayMerchantCommandHandler> logger)
    : IRequestHandler<PayMerchantCommand, PayMerchantResponse>
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task<PayMerchantResponse> Handle(
        PayMerchantCommand command,
        CancellationToken ct)
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

        // 6. System controls — reuse sell enabled control
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

        // 8. Fee calculation
        // User specifies exact merchant amount — fees added on top in crypto
        // grossKes = merchantAmount + platform fee + spread
        // This ensures merchant always receives exactly amountKes
        // In PayMerchantCommandHandler

        var spreadRate = quote.SpreadPercent / 100m;

        // Work backwards from merchant amount
        // Merchant receives amountKes exactly
        // Platform takes fees on top — user pays more crypto to cover them
        var grossKes = decimal.Round(
            command.AmountKes / (1m - FeeCalculator.PlatformFeeRate - spreadRate),
            4,
            MidpointRounding.AwayFromZero);

        var platformFeeKes = decimal.Round(
            grossKes * FeeCalculator.PlatformFeeRate,
            4,
            MidpointRounding.AwayFromZero);

        var spreadKes = decimal.Round(
            grossKes * spreadRate,
            4,
            MidpointRounding.AwayFromZero);

        // Merchant receives exactly this — integer for Safaricom
        var merchantAmountKes = (int)command.AmountKes;

        // Residual from rounding
        var residualKes = decimal.Round(
            grossKes - platformFeeKes - spreadKes - merchantAmountKes,
            4,
            MidpointRounding.ToEven);

        // Crypto user pays — total KES needed converted at sell rate
        var cryptoAmount = decimal.Round(
            grossKes / quote.SellRateKes,
            8,
            MidpointRounding.ToEven);

        // 9. Load user crypto wallet
        var userWallet = await wallets
            .FindByUserAndCurrencyAsync(userId, command.Currency, ct)
            ?? throw new NotFoundException(nameof(Wallet), $"{userId}/{command.Currency}");

        // 10. Check balance
        if (userWallet.Balance < cryptoAmount)
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
            ?? throw new NotFoundException("SpreadRevenueWallet", Currency.KES);

        var settlementWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.SettlementAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("SettlementWallet", Currency.KES);

        var suspenseWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.SuspenseAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("SuspenseWallet", Currency.KES);

        // 12. Resolve merchant details
        var (merchantNumber, accountRef) = command.MerchantType switch
        {
            MerchantPaymentType.Paybill => (command.PaybillNumber!, command.AccountNumber!),
            MerchantPaymentType.Till => (command.TillNumber!, string.Empty),
            MerchantPaymentType.Pochi => (command.PhoneNumber!, string.Empty),
            MerchantPaymentType.SendMoney => (command.PhoneNumber!, string.Empty),
            _ => throw new ArgumentOutOfRangeException(nameof(command.MerchantType))
        };

        // 13. Create transaction record
        var referenceCode = ReferenceCodeGenerator.Generate("PAY");

        var transaction = Transaction.Create(
            referenceCode: referenceCode,
            type: TransactionType.MerchantPayment,
            currency: command.Currency,
            amount: cryptoAmount,
            idempotencyKey: idempotencyKey,
            initiatorUserId: userId,
            feeAmount: platformFeeKes);

        transaction.SetExchangeRates(
            exchangeRate: quote.SellRateKes,
            marketRate: quote.RateKes,
            kesAmount: grossKes);

        // Store all merchant payment amounts — callback handler reads these directly
        transaction.SetMetadata(JsonSerializer.Serialize(new
        {
            MerchantAmountKes = merchantAmountKes,  // int — what Safaricom sends to merchant
            GrossKes = grossKes,           // decimal — total KES from treasury
            PlatformFeeKes = platformFeeKes,     // decimal — revenue P&L
            SpreadKes = spreadKes,          // decimal — spread P&L
            ResidualKes = residualKes,        // decimal — suspense account
            MerchantType = command.MerchantType.ToString(),
            MerchantNumber = merchantNumber,
            AccountReference = accountRef
        }, JsonOptions));

        await transactions.AddAsync(transaction, ct);

        // 14. Create MpesaTransaction
        var mpesaTx = MpesaTransaction.Create(
            transactionId: transaction.Id,
            phoneNumber: merchantNumber,
            amountKes: command.AmountKes,
            direction: "outbound");

        await mpesaTransactions.AddAsync(mpesaTx, ct);

        // 15. Open Serializable transaction
        await using var dbTransaction = await uow.BeginTransactionAsync(
            IsolationLevel.Serializable, ct);

        try
        {
            // Save Transaction + MpesaTransaction
            await uow.SaveChangesAsync(ct);

            // Lock user crypto immediately — prevents double spend
            await ledger.LockAsync(
                userWallet.Id, cryptoAmount, transaction.Id, ct);

            transaction.MarkProcessing();

            var outboxType = command.MerchantType switch
            {
                MerchantPaymentType.Paybill => OutboxMessageTypes.B2BPayment,
                MerchantPaymentType.Till => OutboxMessageTypes.B2BPayment,
                MerchantPaymentType.Pochi => OutboxMessageTypes.PochiPayment,
                MerchantPaymentType.SendMoney => OutboxMessageTypes.PochiPayment,
                _ => throw new ArgumentOutOfRangeException(nameof(command.MerchantType))
            };

            await outbox.EnqueueAsync(
                outboxType,
                new B2BPayload(
                    MpesaTransactionId: mpesaTx.Id,
                    TransactionId: transaction.Id,
                    MerchantType: command.MerchantType,
                    MerchantNumber: merchantNumber,
                    AccountReference: accountRef,
                    AmountKes: (int)command.AmountKes,
                    ReferenceCode: referenceCode,
                    Currency: command.Currency,
                    CryptoAmount: cryptoAmount,
                    GrossKes: grossKes,
                    PlatformFeeKes: platformFeeKes,
                    SpreadKes: spreadKes,
                    UserWalletId: userWallet.Id,
                    TreasuryWalletId: treasuryWallet.Id,
                    TreasuryKesWalletId: treasuryKesWallet.Id,
                    RevenueWalletId: revenueWallet.Id,
                    SpreadKesWalletId: spreadKesWallet.Id,
                    SettlementWalletId: settlementWallet.Id,
                    SuspenseWalletId: suspenseWallet.Id),
                ct);

            await uow.SaveChangesAsync(ct);
            await dbTransaction.CommitAsync(ct);

            logger.LogInformation(
                "Merchant payment initiated. TransactionId={TransactionId} " +
                "UserId={UserId} MerchantType={MerchantType} " +
                "MerchantNumber={MerchantNumber} AmountKes={AmountKes} " +
                "CryptoAmount={CryptoAmount} Currency={Currency}",
                transaction.Id, userId, command.MerchantType,
                merchantNumber, command.AmountKes,
                cryptoAmount, command.Currency);
        }
        catch (Exception ex)
        {
            await dbTransaction.RollbackAsync(ct);

            logger.LogError(ex,
                "Merchant payment initiation failed. TransactionId={TransactionId}",
                transaction.Id);

            throw;
        }

        await quoteService.ConsumeQuoteAsync(command.QuoteId, ct);

        return new PayMerchantResponse(
            TransactionId: transaction.Id,
            ReferenceCode: referenceCode,
            Message: "Payment initiated. Merchant will receive funds shortly.");
    }
}

// Outbox payload — everything B2B handler needs
public sealed record B2BPayload(
    Guid MpesaTransactionId,
    Guid TransactionId,
    MerchantPaymentType MerchantType,
    string MerchantNumber,
    string AccountReference,
    int AmountKes,
    string ReferenceCode,
    Currency Currency,
    decimal CryptoAmount,
    decimal GrossKes,
    decimal PlatformFeeKes,
    decimal SpreadKes,
    Guid UserWalletId,
    Guid TreasuryWalletId,
    Guid TreasuryKesWalletId,
    Guid RevenueWalletId,
    Guid SpreadKesWalletId,
    Guid SettlementWalletId,
    Guid SuspenseWalletId);

// Callback outbox payload
public sealed record B2BCallbackOutboxPayload(
    string ConversationID,
    int ResultCode,
    string ResultDesc,
    string? MpesaReceiptNumber,
    string RawCallbackJson,
    Guid UserWalletId,
    Guid TreasuryWalletId,
    Guid TreasuryKesWalletId,
    Guid RevenueWalletId,
    Guid SpreadKesWalletId,
    Guid SettlementWalletId,
    Guid SuspenseWalletId,
    decimal CryptoAmount,
    decimal GrossKes,
    decimal PlatformFeeKes,
    decimal SpreadKes,
    int MerchantAmountKes,
    decimal ResidualKes);

// Timeout outbox payload
public sealed record B2BTimeoutOutboxPayload(
    string ConversationID,
    string RawCallbackJson,
    Guid UserWalletId,
    decimal CryptoAmount);