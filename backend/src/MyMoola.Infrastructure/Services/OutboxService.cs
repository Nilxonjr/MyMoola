// MyMoola.Infrastructure/Services/OutboxService.cs
using System.Text.Json;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;

namespace MyMoola.Infrastructure.Services;

public sealed class OutboxService(IOutboxRepository outbox) : IOutboxService
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public async Task EnqueueAsync<TPayload>(
        string type,
        TPayload payload,
        CancellationToken ct = default) where TPayload : class
    {
        var json = JsonSerializer.Serialize(payload, JsonOptions);
        var message = OutboxMessage.Create(type, json);
        await outbox.AddAsync(message, ct);
    }
}