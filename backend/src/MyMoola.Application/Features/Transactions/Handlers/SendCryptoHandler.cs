using System.Data;
using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Helpers;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Transactions.Commands;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Transactions.Handlers;

public sealed class SendCryptoHandler(
    ICurrentUserService currentUser,
    IUserRepository users,
    IWalletRepository wallets,
    ITransactionRepository transactions,
    ISystemControlRepository systemControls,
    ILedgerService ledger,
    IUnitOfWork uow,
    ILogger<SendCryptoHandler> logger) : IRequestHandler<SendCryptoCommand, SendCryptoResponse>
{
    public async Task<SendCryptoResponse> Handle(
        SendCryptoCommand command,
        CancellationToken ct)
    {
        // 1. Read sender userId from JWT claims
        if (currentUser.UserId is null)
            throw new UnauthorizedException();

        var senderId = currentUser.UserId.Value;

        // 2. Load sender
        var sender = await users.FindByIdAsync(senderId, ct)
            ?? throw new NotFoundException(nameof(User), senderId);

        // 3. Check PIN lock before verifying — no point attempting BCrypt if locked
        if (sender.IsPinLocked)
            throw new PinLockedException(sender.PinLockedUntil!.Value);

        // 4. Verify PIN
        if (!BCrypt.Net.BCrypt.Verify(command.Pin, sender.PinHash))
        {
            // Record failure and persist immediately — must survive even if
            // subsequent operations fail. Intentionally outside outer transaction.
            sender.RecordFailedPinAttempt();
            await uow.SaveChangesAsync(ct);

            // Check again — this attempt may have triggered the lock
            if (sender.IsPinLocked)
                throw new PinLockedException(sender.PinLockedUntil!.Value);

            throw new InvalidCredentialsException();
        }

        // 5. Load recipient by phone
        var recipient = await users.FindByPhoneAsync(command.RecipientPhone, ct)
            ?? throw new NotFoundException(nameof(User), command.RecipientPhone);

        // 6. Sender cannot send to themselves
        if (sender.Id == recipient.Id)
            throw new InvalidOperationException("You cannot send crypto to yourself.");

        // 7. Ensure both accounts are active
        sender.EnsureActive();
        recipient.EnsureActive();

        // 8. Check global maintenance
        var maintenance = await systemControls
            .FindByKeyAsync(SystemControlKeys.GlobalMaintenance, ct);

        if (maintenance is not null && !maintenance.IsEnabled)
            throw new OperationDisabledException(SystemControlKeys.GlobalMaintenance);

        // 9. Load sender wallet
        var senderWallet = await wallets
            .FindByUserAndCurrencyAsync(sender.Id, command.Currency, ct)
            ?? throw new NotFoundException(nameof(Wallet), $"{sender.Id}/{command.Currency}");

        // 10. Load recipient wallet
        var recipientWallet = await wallets
            .FindByUserAndCurrencyAsync(recipient.Id, command.Currency, ct)
            ?? throw new NotFoundException(nameof(Wallet), $"{recipient.Id}/{command.Currency}");

        // 11. Check sender balance — early exit before any DB writes
        if (senderWallet.Balance < command.Amount)
            throw new InsufficientBalanceException();

        // 12. Generate reference code
        var referenceCode = ReferenceCodeGenerator.Generate();

        // 13. Create transaction record — status starts as Pending
        var transaction = Transaction.Create(
            referenceCode: referenceCode,
            type: Domain.Enums.TransactionType.Send,
            currency: command.Currency,
            amount: command.Amount,
            idempotencyKey: Guid.NewGuid().ToString(),
            initiatorUserId: sender.Id,
            counterpartyUserId: recipient.Id,
            feeAmount: 0);

        await transactions.AddAsync(transaction, ct);

        // 14. Open outer Serializable transaction — Transaction INSERT, debit,
        //     credit, and MarkCompleted must all land atomically.
        //     LedgerService detects this ambient transaction and participates
        //     without committing — this handler owns the single CommitAsync.
        await using var dbTransaction = await uow.BeginTransactionAsync(
            IsolationLevel.Serializable, ct);

        try
        {
            // Save Transaction row first — LedgerEntry FK requires it to exist.
            // INSERT is held inside the Serializable transaction, visible to
            // subsequent operations on the same connection.
            await uow.SaveChangesAsync(ct);

            // 15. Debit sender
            await ledger.DebitAsync(senderWallet.Id, command.Amount, transaction.Id, ct);

            // 16. Credit recipient
            await ledger.CreditAsync(recipientWallet.Id, command.Amount, transaction.Id, ct);

            // 17. Mark transaction completed
            transaction.MarkCompleted();
            await uow.SaveChangesAsync(ct);

            // 18. Single commit — everything lands atomically:
            //     Transaction INSERT + sender debit + sender ledger entry +
            //     recipient credit + recipient ledger entry + Transaction UPDATE
            await dbTransaction.CommitAsync(ct);

            logger.LogInformation(
                "Send completed. TransactionId={TransactionId} Sender={SenderId} " +
                "Recipient={RecipientId} Amount={Amount} Currency={Currency}",
                transaction.Id, sender.Id, recipient.Id, command.Amount, command.Currency);
        }
        catch (Exception ex)
        {
            await dbTransaction.RollbackAsync(ct);

            logger.LogError(ex,
                "Send failed. TransactionId={TransactionId} Sender={SenderId} Recipient={RecipientId}",
                transaction.Id, sender.Id, recipient.Id);

            throw;
        }

        return new SendCryptoResponse(
            TransactionId: transaction.Id,
            ReferenceCode: referenceCode,
            Message: "Transfer completed successfully.");
    }
}