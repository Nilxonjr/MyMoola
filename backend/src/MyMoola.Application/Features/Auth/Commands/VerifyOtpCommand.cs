using MediatR;

namespace MyMoola.Application.Features.Auth.Commands;

public enum OtpPurpose
{
    Registration,
    Login
}

/// <summary>
/// Verifies the OTP sent during registration.
/// On success, the phone is marked verified, default wallets are provisioned,
/// and a JWT is returned.
/// </summary>
public sealed record VerifyOtpCommand(
    string PhoneNumber,
    string Otp,
    OtpPurpose Purpose) : IRequest<AuthTokenResponse>;

/// <summary>
/// Returned by both <see cref="VerifyOtpCommand"/> and <see cref="LoginUserCommand"/>.
/// </summary>
public sealed record AuthTokenResponse(string AccessToken, string TokenType = "Bearer");