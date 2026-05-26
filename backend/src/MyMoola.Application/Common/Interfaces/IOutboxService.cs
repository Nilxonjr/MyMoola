// MyMoola.Application/Common/Interfaces/IOutboxService.cs
using MyMoola.Application.Common.Constants;

namespace MyMoola.Application.Common.Interfaces;

/// <summary>
/// Writes an OutboxMessage to the database within the current ambient
/// DbContext transaction. Caller owns SaveChangesAsync — do not call it here.
/// </summary>
public interface IOutboxService
{
    Task EnqueueAsync<TPayload>(
        string type,
        TPayload payload,
        CancellationToken ct = default) where TPayload : class;
}