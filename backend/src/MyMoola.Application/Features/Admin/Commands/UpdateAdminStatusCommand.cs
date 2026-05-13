using MediatR;

namespace MyMoola.Application.Features.Admin.Commands;

public sealed record UpdateAdminStatusCommand(
    Guid AdminId,
    bool Activate) : IRequest;