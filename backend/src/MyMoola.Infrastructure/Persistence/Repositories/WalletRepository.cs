// MyMoola.Infrastructure/Persistence/Repositories/WalletRepository.cs
using Microsoft.EntityFrameworkCore;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;

namespace MyMoola.Infrastructure.Persistence.Repositories;

public sealed class WalletRepository(AppDbContext db) : IWalletRepository
{
    public async Task AddRangeAsync(IEnumerable<Wallet> wallets, CancellationToken ct = default)
        => await db.Wallets.AddRangeAsync(wallets, ct);

    public async Task<Wallet?> FindByUserAndCurrencyAsync(
        Guid userId, Currency currency, CancellationToken ct = default)
        => await db.Wallets
            .FirstOrDefaultAsync(w => w.UserId == userId && w.Currency == currency, ct);

    public async Task<Wallet?> GetByIdAsync(Guid id, CancellationToken ct = default)
        => await db.Wallets
            .FirstOrDefaultAsync(w => w.Id == id, ct);

    public async Task<IReadOnlyList<Wallet>> GetByUserIdAsync(Guid userId, CancellationToken ct = default)
        => await db.Wallets
            .Where(w => w.UserId == userId)
            .AsNoTracking()
            .ToListAsync(ct);
}