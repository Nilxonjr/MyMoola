using MediatR;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Admin.Queries;

namespace MyMoola.Application.Features.Admin.Handlers;

public sealed class ListAdminsHandler(
    IAdminRepository admins) : IRequestHandler<ListAdminsQuery, ListAdminsResponse>
{
    public async Task<ListAdminsResponse> Handle(
        ListAdminsQuery query,
        CancellationToken ct)
    {
        var list = await admins.ListAsync(ct);

        var dtos = list
            .Select(a => new AdminDto(
                Id: a.Id,
                Name: a.Name,
                Email: a.Email,
                Role: a.Role.ToString(),
                IsActive: a.IsActive,
                MustChangePassword: a.MustChangePassword,
                LastLoginAt: a.LastLoginAt,
                CreatedAt: a.CreatedAt))
            .ToList();

        return new ListAdminsResponse(dtos);
    }
}