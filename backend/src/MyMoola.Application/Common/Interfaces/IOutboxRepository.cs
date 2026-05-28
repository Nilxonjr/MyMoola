// MyMoola.Application/Common/Interfaces/IOutboxRepository.cs
using MyMoola.Domain.Entities;

namespace MyMoola.Application.Common.Interfaces;

public interface IOutboxRepository
{
    Task AddAsync(OutboxMessage message, CancellationToken ct = default);
    Task<OutboxMessage?> FindByIdAsync(Guid id, CancellationToken ct = default);

    /// <summary>
    /// Atomically claims a batch of pending messages using
    /// SELECT FOR UPDATE SKIP LOCKED — safe under horizontal scaling.
    /// Runs inside its own transaction — no outer UoW required.
    /// </summary>
    Task<IReadOnlyList<OutboxMessage>> ClaimPendingAsync(
        int batchSize,
        TimeSpan lockDuration,
        CancellationToken ct = default);
}