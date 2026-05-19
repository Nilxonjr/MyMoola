using Microsoft.EntityFrameworkCore;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Infrastructure.Persistence;

namespace MyMoola.Infrastructure.Persistence.Repositories;

public sealed class SystemControlRepository(AppDbContext db) : ISystemControlRepository
{
    public async Task<SystemControl?> FindByKeyAsync(string controlKey, CancellationToken ct = default)
        => await db.SystemControls
            .FirstOrDefaultAsync(sc => sc.ControlKey == controlKey, ct);

    public async Task<bool> ExistsByKeyAsync(string controlKey, CancellationToken ct = default)
    => await db.SystemControls.AnyAsync(sc => sc.ControlKey == controlKey, ct);

    public async Task AddAsync(SystemControl control, CancellationToken ct = default)
        => await db.SystemControls.AddAsync(control, ct);
}