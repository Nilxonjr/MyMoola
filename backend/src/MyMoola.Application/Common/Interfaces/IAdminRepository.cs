using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Common.Interfaces;

public interface IAdminRepository
{
    Task<AdminUser?> FindByIdAsync(Guid id, CancellationToken ct = default);
    Task<AdminUser?> FindByEmailAsync(string email, CancellationToken ct = default);
    Task<bool> ExistsByEmailAsync(string email, CancellationToken ct = default);
    Task<bool> AnyAsync(CancellationToken ct = default);
    Task AddAsync(AdminUser admin, CancellationToken ct = default);
    Task<IReadOnlyList<AdminUser>> ListAsync(CancellationToken ct = default);
}