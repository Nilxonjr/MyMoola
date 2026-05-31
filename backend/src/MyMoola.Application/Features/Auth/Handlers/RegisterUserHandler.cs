using MediatR;
using MyMoola.Application.Common.Helpers;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Auth.Commands;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;
using MyMoola.Domain.ValueObjects;

namespace MyMoola.Application.Features.Auth.Handlers;

public sealed class RegisterUserHandler(
    IUserRepository users,
    IUnitOfWork uow,
    IOtpCache otpCache,
    ISmsService sms) : IRequestHandler<RegisterUserCommand, RegisterUserResponse>
{
    private static readonly TimeSpan OtpTtl = TimeSpan.FromMinutes(5);

    public async Task<RegisterUserResponse> Handle(
        RegisterUserCommand cmd,
        CancellationToken ct)
    {
        // Fail fast — check uniqueness before any heavy work
        if (await users.ExistsByPhoneAsync(cmd.PhoneNumber, ct))
            throw new ConflictException(nameof(User));

        // BCrypt cost 12 — strong enough for PIN auth, acceptable latency on mobile
        var pinHash = BCrypt.Net.BCrypt.HashPassword(cmd.Pin, workFactor: 12);

        var phoneNumber = new PhoneNumber(cmd.PhoneNumber);
        var user = User.Create(
            phoneNumber,
            pinHash,
            cmd.FullName);

        await users.AddAsync(user, ct);

        // Persist first — never cache or send SMS if the DB write fails
        await uow.SaveChangesAsync(ct);

        var otp = OtpGenerator.Generate();
        await otpCache.SetAsync(cmd.PhoneNumber, otp, OtpTtl, ct);

        await sms.SendAsync(
            cmd.PhoneNumber,
            $"Your MyMoola verification code is {otp}. It expires in 5 minutes. Do not share it.",
            ct);

        return new RegisterUserResponse(user.Id, "OTP sent to your phone number.");
    }

}
