using MediatR;
using MyMoola.Application.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Events;

namespace MyMoola.Application.Features.Wallet.Handlers;

public sealed class WalletLockedHandler(
    ILedgerEntryRepository ledgerEntries) : INotificationHandler<WalletLockedEvent>
{
    public Task Handle(WalletLockedEvent evt, CancellationToken ct)
    {
        var entry = LedgerEntry.Create(
            transactionId: evt.TransactionId,
            walletId: evt.WalletId,
            currency: evt.Currency,
            entryType: LedgerEntryType.Lock,
            amount: evt.Amount,
            availableBalanceBefore: evt.AvailableBalanceBefore,
            availableBalanceAfter: evt.AvailableBalanceAfter,
            lockedBalanceBefore: evt.LockedBalanceBefore,
            lockedBalanceAfter: evt.LockedBalanceAfter);

        ledgerEntries.Add(entry);

        return Task.CompletedTask;
    }
}