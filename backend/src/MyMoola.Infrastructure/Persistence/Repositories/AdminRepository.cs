using Microsoft.EntityFrameworkCore;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Infrastructure.Persistence;

namespace MyMoola.Infrastructure.Persistence.Repositories;

public sealed class AdminRepository(AppDbContext db) : IAdminRepository
{
    public async Task<AdminUser?> FindByIdAsync(Guid id, CancellationToken ct = default)
        => await db.AdminUsers
            .FirstOrDefaultAsync(a => a.Id == id, ct);

    public async Task<AdminUser?> FindByEmailAsync(string email, CancellationToken ct = default)
        => await db.AdminUsers
            .FirstOrDefaultAsync(a => a.Email == email, ct);

    public async Task<bool> ExistsByEmailAsync(string email, CancellationToken ct = default)
        => await db.AdminUsers
            .AnyAsync(a => a.Email == email, ct);

    public async Task<bool> AnyAsync(CancellationToken ct = default)
        => await db.AdminUsers.AnyAsync(ct);

    public async Task AddAsync(AdminUser admin, CancellationToken ct = default)
        => await db.AdminUsers.AddAsync(admin, ct);

    public async Task<IReadOnlyList<AdminUser>> ListAsync(CancellationToken ct = default)
        => await db.AdminUsers
            .OrderBy(a => a.CreatedAt)
            .ToListAsync(ct);
}