using MediatR;
using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Admin.Commands;

public sealed record CreateAdminCommand(
    string Name,
    string Email,
    AdminRole Role) : IRequest<CreateAdminResponse>;

public sealed record CreateAdminResponse(
    Guid AdminId,
    string Name,
    string Email,
    string Role);