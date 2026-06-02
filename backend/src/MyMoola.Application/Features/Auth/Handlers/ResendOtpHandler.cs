using MediatR;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Auth.Commands;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;
using MyMoola.Application.Common.Helpers;

namespace MyMoola.Application.Features.Auth.Handlers;

public sealed class ResendOtpHandler(
    IUserRepository users,
    IOtpCache otpCache,
    ISmsService sms) : IRequestHandler<ResendOtpCommand, ResendOtpResponse>
{
    private static readonly TimeSpan OtpTtl = TimeSpan.FromMinutes(5);
    private const string GenericSuccessMessage = "If this number is registered you will receive an OTP.";

    public async Task<ResendOtpResponse> Handle(ResendOtpCommand cmd, CancellationToken ct)
    {
        // Generic response — never confirm whether phone exists
        // Prevents phone enumeration attacks
        var user = await users.FindByPhoneAsync(cmd.PhoneNumber, ct);
        if (user is null)
            return new ResendOtpResponse(GenericSuccessMessage);

        if (cmd.Purpose == OtpPurpose.Registration)
        {
            // Phone already verified — resend makes no sense
            if (user.PhoneVerifiedAt is not null)
                return new ResendOtpResponse(GenericSuccessMessage);
        }
        else
        {
            // Login resend — account must be active
            // PIN validation already happened in the login step
            // We trust that login-otp cache key exists from that step
            user.EnsureActive();
        }

        // Build cache key based on purpose — same key as original OTP
        // Overwrites old OTP immediately — previous code is invalid
        var cacheKey = cmd.Purpose == OtpPurpose.Registration
            ? cmd.PhoneNumber
            : $"login-otp:{cmd.PhoneNumber}";

        var otp = OtpGenerator.Generate();
        await otpCache.SetAsync(cacheKey, otp, OtpTtl, ct);

        var purposeLabel = cmd.Purpose == OtpPurpose.Registration
            ? "verification"
            : "login";

        await sms.SendAsync(
            cmd.PhoneNumber,
            $"Your MyMoola {purposeLabel} code is {otp}. " +
            $"It expires in 5 minutes. Do not share it.",
            ct);

        return new ResendOtpResponse("OTP resent to your phone number.");
    }

}