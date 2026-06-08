// MyMoola.Application/Common/Interfaces/ITransactionRepository.cs
using MyMoola.Domain.Entities;

namespace MyMoola.Application.Common.Interfaces;

public interface ITransactionRepository
{
    Task AddAsync(Transaction transaction, CancellationToken ct = default);

    Task<Transaction?> FindByIdAsync(Guid id, CancellationToken ct = default);
    Task<(IReadOnlyList<Transaction> Items, int TotalCount)> GetPagedByUserIdAsync(
        Guid userId, int page, int pageSize, CancellationToken ct = default);

    Task<Transaction?> FindByOnChainTxHashAsync(string txHash, CancellationToken ct = default);
}