using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Infrastructure.Persistence;

namespace MyMoola.Infrastructure.Persistence.Repositories;

public sealed class AuditLogRepository(AppDbContext db) : IAuditLogRepository
{
    public void Add(AuditLog auditLog)
        => db.AuditLogs.Add(auditLog);
}