using MediatR;

namespace MyMoola.Application.Features.Auth.Commands;

/// <summary>
/// Authenticates an existing user with their phone number and PIN.
/// Locks the account for 30 minutes after 5 consecutive failed attempts.
/// </summary>
public sealed record LoginUserCommand(
    string PhoneNumber,
    string Pin) : IRequest<LoginInitiatedResponse>;

public sealed record LoginInitiatedResponse(string Message);