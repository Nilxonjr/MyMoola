using System.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Storage;
using MyMoola.Application.Common.Interfaces;

namespace MyMoola.Infrastructure.Persistence;

public sealed class UnitOfWork(AppDbContext db) : IUnitOfWork
{
    public Task<int> SaveChangesAsync(CancellationToken ct = default)
        => db.SaveChangesAsync(ct);

    public async Task<IAppTransaction> BeginTransactionAsync(
        IsolationLevel isolationLevel,
        CancellationToken ct = default)
    {
        var efTransaction = await db.Database
            .BeginTransactionAsync(isolationLevel, ct);

        return new EfAppTransaction(efTransaction);
    }
}

internal sealed class EfAppTransaction(IDbContextTransaction inner) : IAppTransaction
{
    public Task CommitAsync(CancellationToken ct = default)
        => inner.CommitAsync(ct);

    public Task RollbackAsync(CancellationToken ct = default)
        => inner.RollbackAsync(ct);

    public ValueTask DisposeAsync()
        => inner.DisposeAsync();
}