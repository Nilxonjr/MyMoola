using MyMoola.Application.Common.Models;

namespace MyMoola.Application.Common.Interfaces;

public interface IIdempotencyService
{
    Task<CachedResponse?> GetAsync(string userScopedKey, CancellationToken ct = default);
    Task SetAsync(string userScopedKey, CachedResponse response, TimeSpan ttl, CancellationToken ct = default);
    Task<bool> TryAcquireLockAsync(string lockKey, TimeSpan ttl, CancellationToken ct = default);
    Task ReleaseLockAsync(string lockKey, CancellationToken ct = default);
}