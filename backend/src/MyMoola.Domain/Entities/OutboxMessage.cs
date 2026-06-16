// MyMoola.Domain/Entities/OutboxMessage.cs
using MyMoola.Domain.Common;
using MyMoola.Domain.Enums;

namespace MyMoola.Domain.Entities;

public sealed class OutboxMessage : BaseEntity
{
    public string Type { get; private set; } = default!;
    public string Payload { get; private set; } = default!;
    public OutboxMessageStatus Status { get; private set; }
    public int RetryCount { get; private set; }
    public string? Error { get; private set; }
    public DateTimeOffset? ProcessedAt { get; private set; }
    public DateTimeOffset? LockedUntil { get; private set; }
    public DateTimeOffset? LastAttemptedAt { get; private set; }

    public static OutboxMessage Create(string type, string payload)
        => new()
        {
            Type = type,
            Payload = payload,
            Status = OutboxMessageStatus.Pending,
            RetryCount = 0
        };

    public void MarkProcessed()
    {
        Status = OutboxMessageStatus.Processed;
        ProcessedAt = DateTimeOffset.UtcNow;
        LastAttemptedAt = DateTimeOffset.UtcNow;
        LockedUntil = null;
        Error = null;
    }

    public void MarkFailed(string error)
    {
        RetryCount++;
        LastAttemptedAt = DateTimeOffset.UtcNow;
        Error = error[..Math.Min(2000, error.Length)];
        LockedUntil = null;
        Status = RetryCount >= 4
            ? OutboxMessageStatus.DeadLettered
            : OutboxMessageStatus.Pending;
    }

    public void Lock(DateTimeOffset until)
        => LockedUntil = until;
}