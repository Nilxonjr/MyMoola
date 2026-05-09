using MyMoola.Domain.Entities;

namespace MyMoola.Application.Common.Interfaces;

public interface ITransactionRepository
{
    Task AddAsync(Transaction transaction, CancellationToken ct = default);
}