namespace MyMoola.Application.Common.Interfaces;

public interface IAuditLogService
{
    void Log(
        string actorType,
        string action,
        string targetEntity,
        Guid targetId,
        Guid? actorId = null,
        string? ipAddress = null,
        string? beforeState = null,
        string? afterState = null);
}