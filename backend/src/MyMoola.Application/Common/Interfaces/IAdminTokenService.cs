using MyMoola.Domain.Entities;

namespace MyMoola.Application.Common.Interfaces;

public interface IAdminTokenService
{
    string GenerateToken(AdminUser admin);
}