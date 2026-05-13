namespace MyMoola.Application.Common.Interfaces;

public interface ICurrentAdminService
{
    Guid? AdminId { get; }
    string? Role { get; }
    bool MustChangePassword { get; }
}