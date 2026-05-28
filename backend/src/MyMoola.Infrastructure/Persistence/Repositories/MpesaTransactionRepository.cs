// MyMoola.Infrastructure/Persistence/Repositories/MpesaTransactionRepository.cs
using Microsoft.EntityFrameworkCore;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;

namespace MyMoola.Infrastructure.Persistence.Repositories;

public sealed class MpesaTransactionRepository(AppDbContext db) : IMpesaTransactionRepository
{
    public async Task AddAsync(MpesaTransaction transaction, CancellationToken ct = default)
        => await db.MpesaTransactions.AddAsync(transaction, ct);

    public async Task<MpesaTransaction?> FindByIdAsync(
        Guid id, CancellationToken ct = default)
        => await db.MpesaTransactions
            .FirstOrDefaultAsync(m => m.Id == id, ct);

    public async Task<MpesaTransaction?> FindByCheckoutRequestIDAsync(
        string checkoutRequestID, CancellationToken ct = default)
        => await db.MpesaTransactions
            .FirstOrDefaultAsync(m => m.CheckoutRequestId == checkoutRequestID, ct);

    public async Task<MpesaTransaction?> FindByConversationIDAsync(
        string conversationID, CancellationToken ct = default)
        => await db.MpesaTransactions
            .FirstOrDefaultAsync(m => m.ConversationId == conversationID, ct);
}