using MediatR;

namespace MyMoola.Application.Features.Admin.Commands;

public sealed record LoginAdminCommand(
    string Email,
    string Password) : IRequest<LoginAdminResponse>;

public sealed record LoginAdminResponse(
    string AccessToken,
    string Role,
    bool MustChangePassword);