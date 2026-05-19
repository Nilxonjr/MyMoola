using MyMoola.Domain.Entities;

namespace MyMoola.Application.Common.Interfaces;

public interface ISystemControlRepository
{
    Task<SystemControl?> FindByKeyAsync(string controlKey, CancellationToken ct = default);
    Task<bool> ExistsByKeyAsync(string controlKey, CancellationToken ct = default);

    Task AddAsync(SystemControl control, CancellationToken ct = default);

}