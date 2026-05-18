using Microsoft.EntityFrameworkCore;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Infrastructure.Persistence;

namespace MyMoola.Infrastructure.Persistence.Repositories;

public sealed class UserRepository(AppDbContext db) : IUserRepository
{
    public async Task<User?> FindByPhoneAsync(string phoneNumber, CancellationToken ct = default)
    => await db.Users
        .FirstOrDefaultAsync(u => u.PhoneNumberValue == phoneNumber, ct);


    public async Task<User?> FindByIdAsync(Guid id, CancellationToken ct = default)
        => await db.Users
            .FirstOrDefaultAsync(u => u.Id == id, ct);

    public async Task<IReadOnlyList<User>> GetByIdsAsync(IEnumerable<Guid> userIds, CancellationToken ct = default)
    {
        var ids = userIds.Distinct().ToList();
        if (ids.Count == 0)
            return [];

        return await db.Users
            .Where(u => ids.Contains(u.Id))
            .ToListAsync(ct);
    }

    public async Task<bool> ExistsByPhoneAsync(string phoneNumber, CancellationToken ct = default)
        => await db.Users
            .AnyAsync(u => u.PhoneNumberValue == phoneNumber, ct);


    public async Task AddAsync(User user, CancellationToken ct = default)
        => await db.Users.AddAsync(user, ct);
}
