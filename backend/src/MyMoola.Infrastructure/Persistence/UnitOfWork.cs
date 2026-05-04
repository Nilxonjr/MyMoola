using MyMoola.Application.Common.Interfaces;
using MyMoola.Infrastructure.Persistence;

namespace MyMoola.Infrastructure.Persistence;

public sealed class UnitOfWork(AppDbContext db) : IUnitOfWork
{
    public Task<int> SaveChangesAsync(CancellationToken ct = default)
        => db.SaveChangesAsync(ct);
}