using MyMoola.Domain.Common;

namespace MyMoola.Domain.Entities;

public sealed class SystemControl : BaseEntity
{
    public string ControlKey { get; private set; } = null!;
    public bool IsEnabled { get; private set; } = true;
    public string? Reason { get; private set; }
    public Guid? DisabledBy { get; private set; }
    public DateTimeOffset? DisabledAt { get; private set; }

    private SystemControl() { }

    public static SystemControl Seed(string controlKey)
    {
        return new SystemControl
        {
            ControlKey = controlKey,
            IsEnabled = true
        };
    }

    public void Disable(string reason, Guid? disabledBy = null)
    {
        IsEnabled = false;
        Reason = reason;
        DisabledBy = disabledBy;
        DisabledAt = DateTimeOffset.UtcNow;
    }

    public void Enable()
    {
        IsEnabled = true;
        Reason = null;
        DisabledBy = null;
        DisabledAt = null;
    }
}
