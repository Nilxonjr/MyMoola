using MediatR;

namespace MyMoola.Application.Features.Auth.Commands;

/// <summary>
/// Initiates user registration with a phone number, PIN, and full name.
/// On success, an OTP is dispatched to the provided phone number via SMS.
/// </summary>
public sealed record RegisterUserCommand(
    string PhoneNumber,
    string Pin,
    string FullName) : IRequest<RegisterUserResponse>;

public sealed record RegisterUserResponse(Guid UserId, string Message);
