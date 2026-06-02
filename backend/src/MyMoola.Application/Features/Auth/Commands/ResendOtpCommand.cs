using MediatR;
using MyMoola.Application.Features.Auth.Commands;

namespace MyMoola.Application.Features.Auth.Commands;

/// <summary>
/// Resends an OTP to the user's phone number.
/// For Registration — only works if phone is not yet verified.
/// For Login — only works if PIN was already validated in this session.
/// New OTP overwrites the old one — previous OTP is immediately invalid.
/// </summary>
public sealed record ResendOtpCommand(
    string PhoneNumber,
    OtpPurpose Purpose) : IRequest<ResendOtpResponse>;

public sealed record ResendOtpResponse(string Message);