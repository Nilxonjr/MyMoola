using MyMoola.Domain.Common;

namespace MyMoola.Domain.Entities;

public sealed class AuditLog : BaseEntity
{
    public Guid? ActorId { get; private set; }
    public string ActorType { get; private set; } = null!;  // user | admin | system | bot
    public string Action { get; private set; } = null!;
    public string TargetEntity { get; private set; } = null!;
    public Guid TargetId { get; private set; }
    public string? IpAddress { get; private set; }
    public string? BeforeState { get; private set; }
    public string? AfterState { get; private set; }

    // EF Core
    private AuditLog() { }

    public static AuditLog Create(
    string actorType,
    string action,
    string targetEntity,
    Guid targetId,
    Guid? actorId = null,
    string? ipAddress = null,
    string? beforeState = null,
    string? afterState = null)
    {
        var log = new AuditLog
        {
            ActorId = actorId,
            ActorType = actorType,
            Action = action,
            TargetEntity = targetEntity,
            TargetId = targetId,
            IpAddress = ipAddress,
            BeforeState = beforeState,
            AfterState = afterState
        };

        log.SetCreatedAt(DateTimeOffset.UtcNow);
        log.SetUpdatedAt(DateTimeOffset.UtcNow);

        return log;
    }
}
