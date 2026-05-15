using MyMoola.Domain.Entities;

namespace MyMoola.Application.Common.Interfaces;

public interface IRefreshTokenRepository
{
    Task AddAsync(RefreshToken token, CancellationToken ct = default);
    Task<RefreshToken?> FindByTokenHashAsync(string tokenHash, CancellationToken ct = default);

    Task RevokeAllForUserAsync(Guid userId, CancellationToken ct = default);
    Task<IReadOnlyList<RefreshToken>> FindActiveByUserIdAsync(Guid userId, CancellationToken ct = default);
}