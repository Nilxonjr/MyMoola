using MediatR;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Auth.Commands;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Enums;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Auth.Handlers;

public sealed class VerifyOtpHandler(
    IUserRepository users,
    IWalletRepository wallets,
    IUnitOfWork uow,
    IOtpCache otpCache,
    ITokenService tokens) : IRequestHandler<VerifyOtpCommand, AuthTokenResponse>
{
    // Every new user gets these three wallets provisioned on verification
    private static readonly Currency[] DefaultCurrencies =
        [Currency.BTC, Currency.ETH, Currency.USDC];

    public async Task<AuthTokenResponse> Handle(VerifyOtpCommand cmd, CancellationToken ct)
    {   
        // Build cache key based on purpose — prevents login OTP working for registration and vice versa
        var cacheKey = cmd.Purpose == OtpPurpose.Registration
            ? cmd.PhoneNumber
            : $"login-otp:{cmd.PhoneNumber}";

        // Validate OTP before hitting the DB — fail fast on the cheap path
        var cached = await otpCache.GetAsync(cacheKey, ct);
        if (cached is null || cached != cmd.Otp)
            throw new InvalidOtpException();

        var user = await users.FindByPhoneAsync(cmd.PhoneNumber, ct)
            ?? throw new NotFoundException(nameof(User), cmd.PhoneNumber);

        user.VerifyPhone();

        // Provision default wallets in the same transaction as phone verification
        //var defaultWallets = DefaultCurrencies
        //    .Select(c => Wallet.Create(user.Id, c))
        //    .ToList();

        //await wallets.AddRangeAsync(defaultWallets, ct);

        if (cmd.Purpose == OtpPurpose.Registration)
        {
            user.VerifyPhone();

            var defaultWallets = DefaultCurrencies
                .Select(c => Wallet.Create(user.Id, c))
                .ToList();

            await wallets.AddRangeAsync(defaultWallets, ct);
        }
        else
        {
            // Login — record successful login
            user.RecordSuccessfulLogin();
        }

        // Single atomic commit — user update + wallet creation together or not at all
        await uow.SaveChangesAsync(ct);

        // Invalidate OTP immediately after use — one-time only, prevent replay
        await otpCache.RemoveAsync(cacheKey, ct);

        var token = tokens.GenerateToken(user.Id, user.PhoneNumberValue, user.FullName);
        return new AuthTokenResponse(token);
    }
}