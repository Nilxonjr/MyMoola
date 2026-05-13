using MyMoola.Domain.Entities;

namespace MyMoola.Application.Common.Interfaces;

public interface IAuditLogRepository
{
    void Add(AuditLog auditLog);
}