// MyMoola.Application/Common/Interfaces/IWalletRepository.cs
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Common.Interfaces;

public interface IWalletRepository
{
    Task AddRangeAsync(IEnumerable<Wallet> wallets, CancellationToken ct = default);
    Task<Wallet?> FindByUserAndCurrencyAsync(Guid userId, Currency currency, CancellationToken ct = default);
    Task<Wallet?> GetByIdAsync(Guid id, CancellationToken ct = default);
    Task<IReadOnlyList<Wallet>> GetByUserIdAsync(Guid userId, CancellationToken ct = default); // added
}