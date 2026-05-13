using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;

namespace MyMoola.Application.Common.Services;

public sealed class AuditLogService(
    IAuditLogRepository auditLogRepository) : IAuditLogService
{
    public void Log(
        string actorType,
        string action,
        string targetEntity,
        Guid targetId,
        Guid? actorId = null,
        string? ipAddress = null,
        string? beforeState = null,
        string? afterState = null)
    {
        var auditLog = AuditLog.Create(
            actorType: actorType,
            action: action,
            targetEntity: targetEntity,
            targetId: targetId,
            actorId: actorId,
            ipAddress: ipAddress,
            beforeState: beforeState,
            afterState: afterState);

        auditLogRepository.Add(auditLog);
    }
}