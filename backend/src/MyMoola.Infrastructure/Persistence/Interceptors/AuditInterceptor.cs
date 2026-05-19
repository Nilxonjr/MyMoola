using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Diagnostics;
using Microsoft.EntityFrameworkCore.ChangeTracking;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Common;
using MyMoola.Domain.Entities;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace MyMoola.Infrastructure.Persistence.Interceptors;

/// <summary>
/// Automatically creates AuditLog entries for changes to auditable entities.
/// Runs inside SaveChangesAsync before the commit — audit entries land in the same atomic transaction.
/// </summary>
public sealed class AuditInterceptor : SaveChangesInterceptor
{
    // Properties excluded because they are noise — change on every save, carry no audit value.
    private static readonly HashSet<string> _excludedProperties =
    [
        nameof(BaseEntity.CreatedAt),
        nameof(BaseEntity.UpdatedAt),
        nameof(BaseEntity.Id)
    ];

    // Properties excluded because they are sensitive — must never appear in audit JSON.
    // Extend this list as new secret/hash fields are added to any auditable entity.
    private static readonly HashSet<string> _sensitiveProperties =
    [
        "PasswordHash",
        "PinHash",
        "Secret",
        "ApiKey",
        "PrivateKey"
    ];

    // Only these types trigger automatic audit entries.
    // Uses ClrType (not GetType()) to survive EF Core proxy generation — EF can wrap
    // entities in Castle.Proxies.UserProxy at runtime; ClrType always returns the real type.
    private static readonly HashSet<Type> _auditableTypes =
    [
        typeof(User),
        typeof(AdminUser),
        typeof(Transaction),
        typeof(SystemControl),
        typeof(TreasuryPosition),
        typeof(MpesaTransaction)
    ];

    // Centralised serializer options — consistent handling of enums, DateTimeOffset,
    // and camelCase across all audit JSON produced by this interceptor.
    private static readonly JsonSerializerOptions _jsonOptions = new()
    {
        WriteIndented = false,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        Converters = { new JsonStringEnumConverter(JsonNamingPolicy.CamelCase) }
    };

    private readonly ICurrentUserService _currentUser;
    private readonly ICurrentAdminService _currentAdmin;

    public AuditInterceptor(
        ICurrentUserService currentUser,
        ICurrentAdminService currentAdmin)
    {
        _currentUser = currentUser;
        _currentAdmin = currentAdmin;
    }

    public override async ValueTask<InterceptionResult<int>> SavingChangesAsync(
        DbContextEventData eventData,
        InterceptionResult<int> result,
        CancellationToken ct = default)
    {
        if (eventData.Context is not null)
            CreateAuditLogs(eventData.Context);

        return await base.SavingChangesAsync(eventData, result, ct);
    }

    // -------------------------------------------------------------------------
    // Core logic
    // -------------------------------------------------------------------------

    private void CreateAuditLogs(DbContext context)
    {
        // Snapshot all auditable entries BEFORE we add new AuditLog entities,
        // so we never accidentally audit the AuditLog entries themselves.
        // entry.Metadata.ClrType is used instead of entry.Entity.GetType() —
        // the latter breaks when EF generates runtime proxy types.
        var auditableEntries = context.ChangeTracker
            .Entries()
            .Where(e =>
                _auditableTypes.Contains(e.Metadata.ClrType) &&
                e.State is EntityState.Added or EntityState.Modified or EntityState.Deleted)
            .ToList(); // materialise before we mutate the change tracker

        if (auditableEntries.Count == 0)
            return;

        var actorId = ResolveActorId();
        var actorType = ResolveActorType();

        var auditLogs = auditableEntries
            .Select(entry => BuildAuditLog(entry, actorId, actorType))
            .Where(log => log is not null)
            .ToList();

        if (auditLogs.Count > 0)
            context.Set<AuditLog>().AddRange(auditLogs!);
    }

    private AuditLog? BuildAuditLog(EntityEntry entry, Guid? actorId, string actorType)
    {
        // ClrType gives us the real entity name even under EF proxy wrapping.
        var entityName = entry.Metadata.ClrType.Name;

        // IDs in MyMoola are always application-generated (Guid.NewGuid() in BaseEntity),
        // so CurrentValue is guaranteed to be set by the time the interceptor fires.
        var targetId = (Guid)entry.Property(nameof(BaseEntity.Id)).CurrentValue!;
        var action = BuildActionName(entityName, entry.State);

        string? beforeState = null;
        string? afterState = null;

        switch (entry.State)
        {
            case EntityState.Added:
                afterState = CaptureValues(entry.CurrentValues, entry.Properties);
                break;

            // EntityState.Deleted will rarely fire in MyMoola — most deletes are soft
            // (MarkDeleted() sets AccountStatus = Deleted, captured as Modified instead).
            // Kept for hard-delete edge cases in admin operations.
            case EntityState.Deleted:
                beforeState = CaptureValues(entry.OriginalValues, entry.Properties);
                break;

            case EntityState.Modified:
                // Only capture properties that actually changed — keeps JSON small and readable.
                // Both _excludedProperties (noise) and _sensitiveProperties (secrets) are filtered out.
                var changedProperties = entry.Properties
                    .Where(p =>
                        !_excludedProperties.Contains(p.Metadata.Name) &&
                        !_sensitiveProperties.Contains(p.Metadata.Name) &&
                        !Equals(p.OriginalValue, p.CurrentValue))
                    .ToList();

                // Nothing meaningful changed — skip this entry entirely.
                if (changedProperties.Count == 0)
                    return null;

                beforeState = CaptureDictionary(
                    changedProperties.ToDictionary(p => p.Metadata.Name, p => p.OriginalValue));

                afterState = CaptureDictionary(
                    changedProperties.ToDictionary(p => p.Metadata.Name, p => p.CurrentValue));
                break;

            default:
                return null;
        }

        return AuditLog.Create(
            actorType: actorType,
            action: action,
            targetEntity: entityName,
            targetId: targetId,
            actorId: actorId,
            beforeState: beforeState,
            afterState: afterState);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /// <summary>
    /// Captures a filtered snapshot of all non-excluded, non-sensitive properties
    /// from an EF values object. Used for Added (CurrentValues) and Deleted (OriginalValues).
    /// </summary>
    private static string? CaptureValues(
        PropertyValues values,
        IEnumerable<PropertyEntry> properties)
    {
        var dict = properties
            .Where(p =>
                !_excludedProperties.Contains(p.Metadata.Name) &&
                !_sensitiveProperties.Contains(p.Metadata.Name))
            .ToDictionary(p => p.Metadata.Name, p => values[p.Metadata.Name]);

        return CaptureDictionary(dict);
    }

    private static string? CaptureDictionary(Dictionary<string, object?> dict)
    {
        if (dict.Count == 0)
            return null;

        return JsonSerializer.Serialize(dict, _jsonOptions);
    }

    /// <summary>
    /// Action naming convention: "{camelCaseEntityName}.{state}"
    /// e.g. "user.modified", "adminUser.added", "treasuryPosition.deleted"
    /// </summary>
    private static string BuildActionName(string entityName, EntityState state)
    {
        var camel = char.ToLowerInvariant(entityName[0]) + entityName[1..];
        var stateLabel = state switch
        {
            EntityState.Added => "added",
            EntityState.Modified => "modified",
            EntityState.Deleted => "deleted",
            _ => "unknown"
        };
        return $"{camel}.{stateLabel}";
    }

    /// <summary>
    /// Admin takes priority over user. Falls back to null (system/bot action).
    /// </summary>
    private Guid? ResolveActorId() =>
        _currentAdmin.AdminId ?? _currentUser.UserId;

    /// <summary>
    /// Derives actorType string from which service resolved the actor.
    /// </summary>
    private string ResolveActorType()
    {
        if (_currentAdmin.AdminId.HasValue && !string.IsNullOrEmpty(_currentAdmin.Role))
            return "admin";
        if (_currentUser.UserId.HasValue) return "user";
        return "system";
    }
}