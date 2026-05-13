using MediatR;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Auth.Commands;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Auth.Handlers;

public sealed class LoginUserHandler(
    IUserRepository users,
    IUnitOfWork uow,
    IOtpCache otpCache,
    ISmsService sms) : IRequestHandler<LoginUserCommand, LoginInitiatedResponse>
{
    private static readonly TimeSpan OtpTtl = TimeSpan.FromMinutes(5);
    public async Task<LoginInitiatedResponse> Handle(LoginUserCommand cmd, CancellationToken ct)
    {
        // Generic error — never reveal whether the phone number exists in the system
        var user = await users.FindByPhoneAsync(cmd.PhoneNumber, ct)
            ?? throw new InvalidCredentialsException();

        // Check account status before PIN — order matters for security
        user.EnsureActive();

        if (user.IsPinLocked)
            throw new PinLockedException(user.PinLockedUntil!.Value);

        // BCrypt.Verify is constant-time — safe against timing attacks
        var pinValid = BCrypt.Net.BCrypt.Verify(cmd.Pin, user.PinHash);

        if (!pinValid)
        {
            user.RecordFailedPinAttempt();
            await uow.SaveChangesAsync(ct);

            // Re-check after persisting — this attempt may have just triggered the lock
            if (user.IsPinLocked)
                throw new PinLockedException(user.PinLockedUntil!.Value);

            throw new InvalidCredentialsException();
        }

        //user.RecordSuccessfulLogin();
        //await uow.SaveChangesAsync(ct);

        //var token = tokens.GenerateToken(user.Id, user.PhoneNumberValue, user.FullName);
        //return new AuthTokenResponse(token);

        var otp = GenerateOtp();
        await otpCache.SetAsync($"login-otp:{cmd.PhoneNumber}", otp, OtpTtl, ct);

        await sms.SendAsync(
            cmd.PhoneNumber,
            $"Your MyMoola login code is {otp}. It expires in 5 minutes. Do not share it.",
            ct);

        return new LoginInitiatedResponse("OTP sent to your phone number.");
    }

    private static string GenerateOtp()
    {
        var bytes = new byte[4];
        System.Security.Cryptography.RandomNumberGenerator.Fill(bytes);
        var number = Math.Abs(BitConverter.ToInt32(bytes, 0)) % 1_000_000;
        return number.ToString("D6");
    }
}