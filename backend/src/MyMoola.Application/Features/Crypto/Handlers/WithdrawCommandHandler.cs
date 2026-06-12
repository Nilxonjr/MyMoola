using System.Data;
using MediatR;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Helpers;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Crypto.Commands;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;
using MyMoola.Application.Features.Crypto.DTOs;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Interfaces;
namespace MyMoola.Application.Features.Crypto.Handlers;

public sealed class WithdrawCommandHandler(
    ICurrentUserService currentUser,
    IUserRepository users,
    IWalletRepository wallets,
    ITransactionRepository transactions,
    IDepositAddressRepository depositAddresses,
    ISystemControlRepository systemControls,
    IExchangeRateQuoteService quoteService,
    ILedgerService ledger,
    IIdempotencyContext idempotencyContext,
    IOutboxService outbox,
    IBlockchainService blockchain,
    ILogger<WithdrawCommandHandler> logger,
    IUnitOfWork uow) : IRequestHandler<WithdrawCommand, WithdrawResponse>
{
    public async Task<WithdrawResponse> Handle(
        WithdrawCommand command,
        CancellationToken ct)
    {
        if (currentUser.UserId is null)
            throw new UnauthorizedException();

        var idempotencyKey = idempotencyContext.IdempotencyKey
            ?? throw new InvalidOperationException("Idempotency key is required.");

        var userId = currentUser.UserId.Value;

        // 1. Load user
        var user = await users.FindByIdAsync(userId, ct)
            ?? throw new NotFoundException(nameof(User), userId);

        // 2. PIN lock check
        if (user.IsPinLocked)
            throw new PinLockedException(user.PinLockedUntil!.Value);

        // 3. Verify PIN
        if (!BCrypt.Net.BCrypt.Verify(command.Pin, user.PinHash))
        {
            user.RecordFailedPinAttempt();
            await uow.SaveChangesAsync(ct);
            if (user.IsPinLocked)
                throw new PinLockedException(user.PinLockedUntil!.Value);
            throw new InvalidCredentialsException();
        }

        // 4. Account active
        user.EnsureActive();

        // 5. System controls
        var maintenance = await systemControls
            .FindByKeyAsync(SystemControlKeys.GlobalMaintenance, ct);
        if (maintenance is not null && maintenance.IsEnabled)
            throw new OperationDisabledException(SystemControlKeys.GlobalMaintenance);

        var withdrawControlKey = command.Currency switch
        {
            Currency.BTC => SystemControlKeys.BtcWithdrawEnabled,
            Currency.ETH => SystemControlKeys.EthWithdrawEnabled,
            Currency.USDC => SystemControlKeys.UsdcWithdrawEnabled,
            _ => throw new ArgumentOutOfRangeException(nameof(command.Currency))
        };

        var withdrawControl = await systemControls
            .FindByKeyAsync(withdrawControlKey, ct);
        if (withdrawControl is not null && !withdrawControl.IsEnabled)
            throw new OperationDisabledException(withdrawControlKey);

        // 6. Check if toAddress is a MyMoola deposit address
        var internalAddress = await depositAddresses
            .FindByAddressAsync(command.ToAddress.ToLowerInvariant(), ct);

        if (internalAddress is not null)
        {
            // Cannot send to own deposit address
            if (internalAddress.UserId == userId)
                throw new InvalidOperationException(
                    "Cannot withdraw to your own deposit address.");

            // Internal transfer — no fee, no on-chain transaction
            return await HandleInternalTransferAsync(
                command, userId, internalAddress.UserId, idempotencyKey, ct);
        }

        // 7. External withdrawal — validate quote
        var quote = await quoteService.GetWithdrawalQuoteAsync(command.QuoteId, ct)
            ?? throw new InvalidOperationException(
                "Quote has expired. Please request a new rate and try again.");

        if (quote.Currency != command.Currency)
            throw new InvalidOperationException(
                "Quote currency does not match withdrawal currency.");
        
        logger.LogInformation(
            "Quote amounts is {} while received amount is {}",
            quote.Amount, command.Amount);

        if (quote.Amount != command.Amount)
            throw new InvalidOperationException(
                "Quote amount does not match withdrawal amount.");

        var feeAmount = quote.FeeAmount;
        var netAmount = command.Amount - feeAmount;

        if (netAmount <= 0)
            throw new InvalidOperationException(
                "Withdrawal amount is too small to cover the fee.");

        // 8. Load user wallet
        var userWallet = await wallets
            .FindByUserAndCurrencyAsync(userId, command.Currency, ct)
            ?? throw new NotFoundException(
                $"Wallet not found. UserId={userId} Currency={command.Currency}");

        if (userWallet.Balance < command.Amount)
            throw new InsufficientBalanceException();

        // 9. Load hot wallet
        var hotWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.HotWalletAccountUserId, command.Currency, ct)
            ?? throw new NotFoundException(
                $"HotWallet not found for currency {command.Currency}");

        // Check actual on-chain hot wallet balance
        var onChainHotWalletBalance = await blockchain
            .GetHotWalletBalanceAsync(command.Currency, ct);

        if (onChainHotWalletBalance < netAmount)
            throw new InsufficientBalanceException(
                "Platform hot wallet has insufficient funds. Try again later.");

        // 10. Load revenue wallet
        var revenueWallet = await wallets
            .FindByUserAndCurrencyAsync(
                SystemWallets.RevenueAccountUserId, command.Currency, ct)
            ?? throw new NotFoundException(
                $"RevenueWallet not found for currency {command.Currency}");

        // 11. Consume quote — prevent reuse
        await quoteService.ConsumeWithdrawalQuoteAsync(command.QuoteId, ct);

        // 12. Create transaction
        var referenceCode = ReferenceCodeGenerator.Generate("WDR");
        var transaction = Transaction.CreateWithdrawal(
            userId: userId,
            currency: command.Currency,
            amount: command.Amount,
            feeAmount: feeAmount,
            toAddress: command.ToAddress,
            idempotencyKey: idempotencyKey,
            referenceCode: referenceCode);

        await transactions.AddAsync(transaction, ct);

        // 13. Lock funds and enqueue broadcast atomically
        await using var dbTransaction = await uow.BeginTransactionAsync(
            IsolationLevel.Serializable, ct);

        try
        {
            await ledger.LockAsync(
                userWallet.Id,
                command.Amount,
                transaction.Id,
                ct);

            await outbox.EnqueueAsync(
                OutboxMessageTypes.WithdrawalBroadcast,
                new WithdrawalBroadcastOutboxPayload(
                    TransactionId: transaction.Id,
                    UserId: userId,
                    Currency: command.Currency,
                    Amount: command.Amount,
                    FeeAmount: feeAmount,
                    NetAmount: netAmount,
                    ToAddress: command.ToAddress,
                    UserWalletId: userWallet.Id,
                    HotWalletId: hotWallet.Id,
                    RevenueWalletId: revenueWallet.Id),
                ct);

            await uow.SaveChangesAsync(ct);
            await dbTransaction.CommitAsync(ct);
        }
        catch (Exception)
        {
            await dbTransaction.RollbackAsync(ct);
            throw;
        }

        return new WithdrawResponse(
            TransactionId: transaction.Id,
            ReferenceCode: referenceCode,
            Amount: command.Amount,
            FeeAmount: feeAmount,
            NetAmount: netAmount,
            Currency: command.Currency.ToString(),
            ToAddress: command.ToAddress,
            Status: TransactionStatus.Pending.ToString());
    }

    // -------------------------------------------------------------------------
    // Internal transfer — no fee, no on-chain transaction
    // -------------------------------------------------------------------------

    private async Task<WithdrawResponse> HandleInternalTransferAsync(
        WithdrawCommand command,
        Guid senderUserId,
        Guid receiverUserId,
        string idempotencyKey,
        CancellationToken ct)
    {
        var senderWallet = await wallets
            .FindByUserAndCurrencyAsync(senderUserId, command.Currency, ct)
            ?? throw new NotFoundException(
                $"Sender wallet not found. UserId={senderUserId} Currency={command.Currency}");

        if (senderWallet.Balance < command.Amount)
            throw new InsufficientBalanceException();

        var receiverWallet = await wallets
            .FindByUserAndCurrencyAsync(receiverUserId, command.Currency, ct)
            ?? throw new NotFoundException(
                $"Receiver wallet not found. UserId={receiverUserId} Currency={command.Currency}");

        var referenceCode = ReferenceCodeGenerator.Generate("INT");

        var transaction = Transaction.CreateWithdrawal(
            userId: senderUserId,
            currency: command.Currency,
            amount: command.Amount,
            feeAmount: 0,
            idempotencyKey: idempotencyKey,
            toAddress: command.ToAddress,
            referenceCode: referenceCode);

        await transactions.AddAsync(transaction, ct);

        await using var dbTransaction = await uow.BeginTransactionAsync(
            IsolationLevel.Serializable, ct);

        try
        {
            // Debit sender
            await ledger.DebitAsync(
                senderWallet.Id,
                command.Amount,
                transaction.Id,
                ct);

            // Credit receiver
            await ledger.CreditAsync(
                receiverWallet.Id,
                command.Amount,
                transaction.Id,
                ct);

            transaction.MarkCompleted();

            await uow.SaveChangesAsync(ct);
            await dbTransaction.CommitAsync(ct);
        }
        catch (Exception ex)
        {
            await dbTransaction.RollbackAsync(ct);
            throw;
        }

        return new WithdrawResponse(
            TransactionId: transaction.Id,
            ReferenceCode: referenceCode,
            Amount: command.Amount,
            FeeAmount: 0,
            NetAmount: command.Amount,
            Currency: command.Currency.ToString(),
            ToAddress: command.ToAddress,
            Status: TransactionStatus.Completed.ToString());
    }
}