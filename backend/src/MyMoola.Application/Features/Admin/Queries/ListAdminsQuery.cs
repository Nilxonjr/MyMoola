using MediatR;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Admin.Queries;

public sealed record ListAdminsQuery : IRequest<ListAdminsResponse>;

public sealed record AdminDto(
    Guid Id,
    string Name,
    string Email,
    string Role,
    bool IsActive,
    bool MustChangePassword,
    DateTimeOffset? LastLoginAt,
    DateTimeOffset CreatedAt);

public sealed record ListAdminsResponse(
    IReadOnlyList<AdminDto> Admins);