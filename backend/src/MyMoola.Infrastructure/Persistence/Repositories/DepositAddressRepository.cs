using Microsoft.EntityFrameworkCore;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;

namespace MyMoola.Infrastructure.Persistence.Repositories;

public sealed class DepositAddressRepository(AppDbContext db) : IDepositAddressRepository
{
    public async Task AddAsync(DepositAddress address, CancellationToken ct = default)
        => await db.DepositAddresses.AddAsync(address, ct);

    public async Task<DepositAddress?> FindByUserAndChainAsync(
        Guid userId, Chain chain, CancellationToken ct = default)
        => await db.DepositAddresses
            .FirstOrDefaultAsync(d => d.UserId == userId && d.Chain == chain && d.IsActive, ct);

    public async Task<DepositAddress?> FindByAddressAsync(string address, CancellationToken ct = default)
    => await db.DepositAddresses
        .FirstOrDefaultAsync(d => d.Address.ToLower() == address.ToLower(), ct);

    public async Task<IReadOnlyList<DepositAddress>> GetAllActiveAsync(CancellationToken ct = default)
        => await db.DepositAddresses
            .Where(d => d.IsActive)
            .AsNoTracking()
            .ToListAsync(ct);

    public async Task<int> GetNextDerivationIndexAsync(CancellationToken ct = default)
    {
        var connection = db.Database.GetDbConnection();

        if (connection.State != System.Data.ConnectionState.Open)
            await connection.OpenAsync(ct);

        using var command = connection.CreateCommand();
        command.CommandText = "SELECT nextval('deposit_address_index_seq')";

        var result = await command.ExecuteScalarAsync(ct);
        return Convert.ToInt32(result);
    }
    public async Task<DepositAddress?> FindByIdAsync(Guid id, CancellationToken ct = default)
    => await db.DepositAddresses.FirstOrDefaultAsync(d => d.Id == id, ct);
}