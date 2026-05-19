using MyMoola.Domain.Entities;

namespace MyMoola.Application.Common.Interfaces;

public interface IUserRepository
{
    Task<User?> FindByPhoneAsync(string phoneNumber, CancellationToken ct = default);
    Task<User?> FindByIdAsync(Guid id, CancellationToken ct = default);
    Task<IReadOnlyList<User>> GetByIdsAsync(IEnumerable<Guid> userIds, CancellationToken ct = default);
    Task<bool> ExistsByPhoneAsync(string phoneNumber, CancellationToken ct = default);
    Task AddAsync(User user, CancellationToken ct = default);
}