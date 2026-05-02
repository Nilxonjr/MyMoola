using MyMoola.Domain.Enums;

namespace MyMoola.Domain.Entities;

public sealed class IdempotencyKey
{
    public string Key { get; private set; } = null!;
    public string ResponseBody { get; private set; } = null!;
    public int StatusCode { get; private set; }
    public DateTimeOffset CreatedAt { get; private set; }
    public DateTimeOffset ExpiresAt { get; private set; }

    private IdempotencyKey() { }

    public static IdempotencyKey Create(string key, string responseBody, int statusCode)
    {
        return new IdempotencyKey
        {
            Key = key,
            ResponseBody = responseBody,
            StatusCode = statusCode,
            CreatedAt = DateTimeOffset.UtcNow,
            ExpiresAt = DateTimeOffset.UtcNow.AddHours(24)
        };
    }

    public bool IsExpired => DateTimeOffset.UtcNow > ExpiresAt;
}
