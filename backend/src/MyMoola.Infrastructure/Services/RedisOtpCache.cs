// MyMoola.Infrastructure/Services/RedisOtpCache.cs
using StackExchange.Redis;
using MyMoola.Application.Common.Interfaces;

namespace MyMoola.Infrastructure.Services;

public sealed class RedisOtpCache(IConnectionMultiplexer redis) : IOtpCache
{
    private static string Key(string phoneNumber) => $"otp:{phoneNumber}";

    private IDatabase Db => redis.GetDatabase();

    public async Task SetAsync(string phoneNumber, string otp, TimeSpan ttl, CancellationToken ct = default)
        => await Db.StringSetAsync(Key(phoneNumber), otp, ttl);

    public async Task<string?> GetAsync(string phoneNumber, CancellationToken ct = default)
    {
        var value = await Db.StringGetAsync(Key(phoneNumber));
        return value.HasValue ? value.ToString() : null;
    }

    public async Task RemoveAsync(string phoneNumber, CancellationToken ct = default)
        => await Db.KeyDeleteAsync(Key(phoneNumber));
}