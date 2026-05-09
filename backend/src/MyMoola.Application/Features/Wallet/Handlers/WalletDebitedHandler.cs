using MediatR;
using MyMoola.Application.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Events;

namespace MyMoola.Application.Features.Wallet.Handlers;

public sealed class WalletDebitedHandler(
    ILedgerEntryRepository ledgerEntries) : INotificationHandler<WalletDebitedEvent>
{
    public Task Handle(WalletDebitedEvent evt, CancellationToken ct)
    {
        var entry = LedgerEntry.Create(
            transactionId: evt.TransactionId,
            walletId: evt.WalletId,
            currency: evt.Currency,
            entryType: LedgerEntryType.Debit,
            amount: evt.Amount,
            availableBalanceBefore: evt.AvailableBalanceBefore,
            availableBalanceAfter: evt.AvailableBalanceAfter,
            lockedBalanceBefore: evt.LockedBalanceBefore,
            lockedBalanceAfter: evt.LockedBalanceAfter);

        ledgerEntries.Add(entry);

        return Task.CompletedTask;
    }
}