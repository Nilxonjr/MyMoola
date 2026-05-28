// MyMoola.Infrastructure/Services/MpesaTokenCache.cs
namespace MyMoola.Infrastructure.Services;

/// <summary>
/// Thread-safe in-memory cache for the Safaricom OAuth token.
/// Token lifetime from Safaricom is 3600 seconds — we expire at 3300
/// to avoid using a token that expires mid-request.
/// Registered as Singleton — must survive across scoped service lifetimes.
/// </summary>
public sealed class MpesaTokenCache
{
    private string? _token;
    private DateTimeOffset _expiresAt = DateTimeOffset.MinValue;
    private readonly SemaphoreSlim _lock = new(1, 1);

    public bool TryGet(out string? token)
    {
        if (_token is not null && DateTimeOffset.UtcNow < _expiresAt)
        {
            token = _token;
            return true;
        }

        token = null;
        return false;
    }

    public async Task<string> GetOrFetchAsync(
        Func<Task<(string token, int expiresInSeconds)>> fetch,
        CancellationToken ct = default)
    {
        if (TryGet(out var cached))
            return cached!;

        await _lock.WaitAsync(ct);
        try
        {
            // Double-check after acquiring lock — another thread may have
            // already refreshed the token while we were waiting
            if (TryGet(out cached))
                return cached!;

            var (token, expiresIn) = await fetch();
            _token = token;
            // Expire 300 seconds early to avoid edge cases at boundary
            _expiresAt = DateTimeOffset.UtcNow.AddSeconds(expiresIn - 300);
            return _token;
        }
        finally
        {
            _lock.Release();
        }
    }
}