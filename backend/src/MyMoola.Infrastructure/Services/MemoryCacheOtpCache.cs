using Microsoft.Extensions.Caching.Memory;
using MyMoola.Application.Common.Interfaces;

namespace MyMoola.Infrastructure.Services;

public sealed class MemoryCacheOtpCache(IMemoryCache cache) : IOtpCache
{
    // Namespace the key to avoid collisions with other cache entries
    private static string Key(string phoneNumber) => $"otp:{phoneNumber}";

    public Task SetAsync(string phoneNumber, string otp, TimeSpan ttl, CancellationToken ct = default)
    {
        cache.Set(Key(phoneNumber), otp, new MemoryCacheEntryOptions
        {
            AbsoluteExpirationRelativeToNow = ttl,
            Priority = CacheItemPriority.High,
        });

        return Task.CompletedTask;
    }

    public Task<string?> GetAsync(string phoneNumber, CancellationToken ct = default)
    {
        cache.TryGetValue<string>(Key(phoneNumber), out var otp);
        return Task.FromResult(otp);
    }

    public Task RemoveAsync(string phoneNumber, CancellationToken ct = default)
    {
        cache.Remove(Key(phoneNumber));
        return Task.CompletedTask;
    }
}