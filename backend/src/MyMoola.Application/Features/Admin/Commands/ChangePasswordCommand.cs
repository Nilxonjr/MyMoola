using MediatR;

namespace MyMoola.Application.Features.Admin.Commands;

public sealed record ChangePasswordCommand(
    string CurrentPassword,
    string NewPassword) : IRequest;