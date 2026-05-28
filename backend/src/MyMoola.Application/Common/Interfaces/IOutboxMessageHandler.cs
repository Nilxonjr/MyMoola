// MyMoola.Application/Common/Interfaces/IOutboxMessageHandler.cs
using MyMoola.Domain.Entities;

namespace MyMoola.Application.Common.Interfaces;

/// <summary>
/// Implement one handler per OutboxMessageType.
/// OutboxProcessor resolves the correct handler by Type string and calls ExecuteAsync.
/// </summary>
public interface IOutboxMessageHandler
{
    string Type { get; }
    Task ExecuteAsync(OutboxMessage message, CancellationToken ct = default);
}