// MyMoola.Infrastructure/Persistence/Repositories/TransactionRepository.cs
using Microsoft.EntityFrameworkCore;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;

namespace MyMoola.Infrastructure.Persistence.Repositories;

public sealed class TransactionRepository(AppDbContext db) : ITransactionRepository
{
    public async Task AddAsync(Transaction transaction, CancellationToken ct = default)
        => await db.Transactions.AddAsync(transaction, ct);

    public async Task<Transaction?> FindByIdAsync(Guid id, CancellationToken ct = default)
        => await db.Transactions.FirstOrDefaultAsync(t => t.Id == id, ct);

    public async Task<(IReadOnlyList<Transaction> Items, int TotalCount)> GetPagedByUserIdAsync(
        Guid userId, int page, int pageSize, CancellationToken ct = default)
    {
        var query = db.Transactions
            .Where(t => t.InitiatorUserId == userId || t.CounterpartyUserId == userId)
            .OrderByDescending(t => t.CreatedAt)
            .AsNoTracking();

        var totalCount = await query.CountAsync(ct);

        var items = await query
            .Skip((page - 1) * pageSize)
            .Take(pageSize)
            .ToListAsync(ct);

        return (items, totalCount);
    }

    public async Task<Transaction?> FindByOnChainTxHashAsync(
    string txHash, CancellationToken ct = default)
    => await db.Transactions
        .FirstOrDefaultAsync(t => t.OnChainTxHash == txHash, ct);
}