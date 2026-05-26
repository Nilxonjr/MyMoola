// MyMoola.Application/Features/Transactions/Handlers/BuyCommandHandler.cs
using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Helpers;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Transactions.Commands;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Transactions.Handlers;

public sealed class BuyCommandHandler(
    ICurrentUserService currentUser,
    IIdempotencyContext idempotencyContext,
    IUserRepository users,
    IWalletRepository wallets,
    ITransactionRepository transactions,
    IMpesaTransactionRepository mpesaTransactions,
    ISystemControlRepository systemControls,
    IOutboxService outbox,
    IUnitOfWork uow,
    IExchangeRateQuoteService quoteService,
    ILogger<BuyCommandHandler> logger) : IRequestHandler<BuyCommand, BuyResponse>
{
    public async Task<BuyResponse> Handle(BuyCommand command, CancellationToken ct)
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

        // 3. PIN lock check before BCrypt — avoid expensive hash on locked account
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

        var buyControlKey = command.Currency switch
        {
            Currency.BTC => SystemControlKeys.BtcBuyEnabled,
            Currency.ETH => SystemControlKeys.EthBuyEnabled,
            Currency.USDC => SystemControlKeys.UsdcBuyEnabled,
            _ => throw new ArgumentOutOfRangeException()
        };

        var buyControl = await systemControls.FindByKeyAsync(buyControlKey, ct);
        if (buyControl is not null && !buyControl.IsEnabled)
            throw new OperationDisabledException(buyControlKey);

        // 7. Load exchange rate — latest record for this currency
        var quote = await quoteService.GetQuoteAsync(command.QuoteId, ct)
            ?? throw new InvalidOperationException(
                "Quote has expired. Please request a new rate and try again.");

        // Ensure quote currency matches command currency
        if (quote.Currency != command.Currency)
            throw new InvalidOperationException(
                "Quote currency does not match requested currency.");

        // 8. Fee calculation — all arithmetic in one place
        var fees = FeeCalculator.CalculateBuy(
        grossKes: command.GrossKes,
        marketRate: quote.RateKes,
        buyRate: quote.BuyRateKes);

        // 9. Load user crypto wallet
        var userWallet = await wallets
            .FindByUserAndCurrencyAsync(userId, command.Currency, ct)
            ?? throw new NotFoundException(nameof(Wallet), $"{userId}/{command.Currency}");

        // 10. Load system wallets — all needed for ledger entries
        var treasuryWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.TreasuryAccountUserId, command.Currency, ct)
            ?? throw new NotFoundException("TreasuryWallet", command.Currency);

        var revenueWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.RevenueAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("RevenueWallet", Currency.KES);

        var spreadWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.SpreadRevenueAccountUserId, command.Currency, ct)
            ?? throw new NotFoundException("SpreadRevenueWallet", command.Currency);

        var settlementWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.SettlementAccountUserId, Currency.KES, ct)
            ?? throw new NotFoundException("SettlementWallet", Currency.KES);

        // 11. Create transaction record
        var referenceCode = ReferenceCodeGenerator.Generate();

        var transaction = Transaction.Create(
            referenceCode: referenceCode,
            type: TransactionType.Buy,
            currency: command.Currency,
            amount: fees.UserCrypto,
            idempotencyKey: idempotencyKey,
            initiatorUserId: userId,
            feeAmount: fees.PlatformFeeKes);

        transaction.SetExchangeRates(
            exchangeRate: quote.BuyRateKes,
            marketRate: quote.RateKes,
            kesAmount: command.GrossKes);

        await transactions.AddAsync(transaction, ct);

        // 12. Create MpesaTransaction — no CheckoutRequestId yet,
        //     STK push has not been fired. Outbox handler sets it after Safaricom responds.
        var mpesaTx = MpesaTransaction.Create(
            transactionId: transaction.Id,
            phoneNumber: user.PhoneNumberValue, // caller encrypts before storing
            amountKes: command.GrossKes,
            direction: "inbound");

        await mpesaTransactions.AddAsync(mpesaTx, ct);

        // 13. Enqueue STK push outbox message — written atomically with the
        //     transaction records in the same SaveChangesAsync below.
        //     OutboxProcessor fires the actual Safaricom call asynchronously.
        await outbox.EnqueueAsync(
            OutboxMessageTypes.StkPush,
            new StkPushPayload(
                MpesaTransactionId: mpesaTx.Id,
                TransactionId: transaction.Id,
                PhoneNumber: user.PhoneNumberValue,
                AmountKes: (int)command.GrossKes,
                ReferenceCode: referenceCode,
                Currency: command.Currency,
                UserCrypto: fees.UserCrypto,
                GrossCrypto: fees.GrossCrypto,
                SpreadCrypto: fees.SpreadCrypto,
                PlatformFeeKes: fees.PlatformFeeKes,
                UserWalletId: userWallet.Id,
                TreasuryWalletId: treasuryWallet.Id,
                RevenueWalletId: revenueWallet.Id,
                SpreadWalletId: spreadWallet.Id,
                SettlementWalletId: settlementWallet.Id),
            ct);

        // 14. Single atomic commit:
        //     Transaction + MpesaTransaction + OutboxMessage land together.
        //     If this fails nothing was sent to Safaricom — safe to retry.
        await uow.SaveChangesAsync(ct);

        await quoteService.ConsumeQuoteAsync(command.QuoteId, ct);

        logger.LogInformation(
            "Buy initiated. TransactionId={TransactionId} UserId={UserId} " +
            "GrossKes={GrossKes} Currency={Currency} UserCrypto={UserCrypto}",
            transaction.Id, userId, command.GrossKes,
            command.Currency, fees.UserCrypto);

        return new BuyResponse(
            TransactionId: transaction.Id,
            ReferenceCode: referenceCode,
            Message: "Payment initiated. Enter your M-Pesa PIN when prompted.");
    }
}

// Payload written to outbox — everything the STK push handler needs,
// including all wallet IDs so the callback handler can execute ledger entries
// without re-querying configuration.
public sealed record StkPushPayload(
    Guid MpesaTransactionId,
    Guid TransactionId,
    string PhoneNumber,
    int AmountKes,
    string ReferenceCode,
    Currency Currency,
    decimal UserCrypto,
    decimal GrossCrypto,
    decimal SpreadCrypto,
    decimal PlatformFeeKes,
    Guid UserWalletId,
    Guid TreasuryWalletId,
    Guid RevenueWalletId,
    Guid SpreadWalletId,
    Guid SettlementWalletId);