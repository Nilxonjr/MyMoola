using System.Text.Json;
using Microsoft.EntityFrameworkCore.Storage;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Common.Models;
using StackExchange.Redis;

namespace MyMoola.Infrastructure.Services;

public sealed class RedisIdempotencyService(
    IConnectionMultiplexer redis) : IIdempotencyService
{
    private readonly StackExchange.Redis.IDatabase _db = redis.GetDatabase(0);

    public async Task<CachedResponse?> GetAsync(
        string key,
        CancellationToken ct = default)
    {
        var value = await _db.StringGetAsync(key);

        if (value.IsNullOrEmpty)
            return null;

        return JsonSerializer.Deserialize<CachedResponse>(value!);
    }

    public async Task SetAsync(
        string key,
        CachedResponse response,
        TimeSpan ttl,
        CancellationToken ct = default)
    {
        var json = JsonSerializer.Serialize(response);
        await _db.StringSetAsync(key, json, ttl);
    }

    public async Task<bool> TryAcquireLockAsync(
        string lockKey,
        TimeSpan ttl,
        CancellationToken ct = default)
        => await _db.StringSetAsync(lockKey, "1", ttl, When.NotExists);

    public async Task ReleaseLockAsync(
        string lockKey,
        CancellationToken ct = default)
        => await _db.KeyDeleteAsync(lockKey);
}