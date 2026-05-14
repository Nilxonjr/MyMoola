using MyMoola.Domain.Common;
using MyMoola.Domain.Enums;

namespace MyMoola.Domain.Entities;

public sealed class AdminUser : BaseEntity
{
    public string Name { get; private set; } = null!;
    public string Email { get; private set; } = null!;
    public string PasswordHash { get; private set; } = null!;
    public AdminRole Role { get; private set; }
    public bool IsActive { get; private set; } = true;
    public bool MustChangePassword { get; private set; } = true;
    public DateTimeOffset? LastLoginAt { get; private set; }
    public Guid? CreatedBy { get; private set; }
    public uint Version { get; private set; }

    private AdminUser() { }

    public static AdminUser Create(
        string name,
        string email,
        string passwordHash,
        AdminRole role,
        Guid? createdBy = null)
    {
        return new AdminUser
        {
            Name = name,
            Email = email,
            PasswordHash = passwordHash,
            Role = role,
            CreatedBy = createdBy,
            MustChangePassword = true
        };
    }

    public void RecordLogin()
    {
        LastLoginAt = DateTimeOffset.UtcNow;
    }

    public void ChangePassword(string newPasswordHash)
    {
        PasswordHash = newPasswordHash;
        MustChangePassword = false;
    }

    public void ForcePasswordChange()
    {
        MustChangePassword = true;
    }

    public void Deactivate()
    {
        IsActive = false;
    }

    public void Activate()
    {
        IsActive = true;
    }
}