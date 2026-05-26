// MyMoola.Infrastructure/Persistence/Repositories/OutboxRepository.cs
using System.Data;
using Microsoft.EntityFrameworkCore;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;

namespace MyMoola.Infrastructure.Persistence.Repositories;

public sealed class OutboxRepository(AppDbContext db) : IOutboxRepository
{
    public async Task AddAsync(OutboxMessage message, CancellationToken ct = default)
        => await db.OutboxMessages.AddAsync(message, ct);

    public async Task<OutboxMessage?> FindByIdAsync(Guid id, CancellationToken ct = default)
        => await db.OutboxMessages.FirstOrDefaultAsync(m => m.Id == id, ct);

    public async Task<IReadOnlyList<OutboxMessage>> ClaimPendingAsync(
        int batchSize,
        TimeSpan lockDuration,
        CancellationToken ct = default)
    {
        var now = DateTimeOffset.UtcNow;
        var lockUntil = now.Add(lockDuration);

        // Atomic SELECT FOR UPDATE SKIP LOCKED — safe under horizontal scaling.
        // Two processor instances cannot claim the same row simultaneously.

        var messages = await db.OutboxMessages
        .FromSql(
            $"""
            SELECT * FROM "outbox_messages"
            WHERE "status" = {OutboxMessageStatus.Pending}
              AND ("locked_until" IS NULL OR "locked_until" < {now})
            ORDER BY "created_at"
            LIMIT {batchSize}
            FOR UPDATE SKIP LOCKED
            """)
        .ToListAsync(ct);

        if (messages.Count == 0)
            return messages;

        foreach (var message in messages)
            message.Lock(lockUntil);

        await db.SaveChangesAsync(ct);

        return messages;
    }
}