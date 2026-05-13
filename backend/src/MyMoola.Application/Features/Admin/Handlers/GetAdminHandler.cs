using MediatR;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Admin.Queries;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Admin.Handlers;

public sealed class GetAdminHandler(
    IAdminRepository admins) : IRequestHandler<GetAdminQuery, AdminDto>
{
    public async Task<AdminDto> Handle(
        GetAdminQuery query,
        CancellationToken ct)
    {
        var admin = await admins.FindByIdAsync(query.AdminId, ct)
            ?? throw new NotFoundException(nameof(AdminUser), query.AdminId);

        return new AdminDto(
            Id: admin.Id,
            Name: admin.Name,
            Email: admin.Email,
            Role: admin.Role.ToString(),
            IsActive: admin.IsActive,
            MustChangePassword: admin.MustChangePassword,
            LastLoginAt: admin.LastLoginAt,
            CreatedAt: admin.CreatedAt);
    }
}