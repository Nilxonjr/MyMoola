using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Common.Interfaces;

public interface IDepositAddressRepository
{
    Task AddAsync(DepositAddress address, CancellationToken ct = default);
    Task<DepositAddress?> FindByUserAndChainAsync(Guid userId, Chain chain, CancellationToken ct = default);
    Task<DepositAddress?> FindByAddressAsync(string address, CancellationToken ct = default);
    Task<IReadOnlyList<DepositAddress>> GetAllActiveAsync(CancellationToken ct = default);

    /// <summary>
    /// Allocates the next derivation index from the deposit_address_index_seq
    /// PostgreSQL sequence. Atomic and safe under concurrent requests.
    /// </summary>
    Task<int> GetNextDerivationIndexAsync(CancellationToken ct = default);
    Task<DepositAddress?> FindByIdAsync(Guid id, CancellationToken ct = default);
}