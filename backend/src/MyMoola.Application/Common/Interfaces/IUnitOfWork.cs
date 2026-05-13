using System.Data;

namespace MyMoola.Application.Common.Interfaces;

public interface IAppTransaction : IAsyncDisposable
{
    Task CommitAsync(CancellationToken ct = default);
    Task RollbackAsync(CancellationToken ct = default);
}

public interface IUnitOfWork
{
    Task<int> SaveChangesAsync(CancellationToken ct = default);
    Task<IAppTransaction> BeginTransactionAsync(
        IsolationLevel isolationLevel,
        CancellationToken ct = default);
}