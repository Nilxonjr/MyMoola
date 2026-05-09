using MediatR;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Auth.Commands;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Auth.Handlers;

public sealed class RefreshTokenHandler(
    IRefreshTokenRepository refreshTokens,
    IUserRepository users,
    IUnitOfWork uow,
    ITokenService tokens) : IRequestHandler<RefreshTokenCommand, AuthTokenResponse>
{
    public async Task<AuthTokenResponse> Handle(RefreshTokenCommand cmd, CancellationToken ct)
    {
        // Hash the incoming raw token to look up in DB
        var hash = tokens.HashRefreshToken(cmd.RefreshToken);
        var token = await refreshTokens.FindByTokenHashAsync(hash, ct)
            ?? throw new UnauthorizedException("Invalid refresh token.");

        // Token found but already revoked — possible theft, kill all sessions
        if (token.IsRevoked)
        {
            var activeTokens = await refreshTokens.FindActiveByUserIdAsync(token.UserId, ct);
            foreach (var active in activeTokens)
                active.Revoke();

            await uow.SaveChangesAsync(ct);
            throw new UnauthorizedException("Session invalidated. Please log in again.");
        }

        // Token found but expired
        if (token.IsExpired)
            throw new UnauthorizedException("Refresh token has expired. Please log in again.");

        // Load user and verify account is still active
        var user = await users.FindByIdAsync(token.UserId, ct)
            ?? throw new UnauthorizedException("User no longer exists.");

        user.EnsureActive();

        // Rotate — revoke old token, issue new one
        token.Revoke();

        var rawNewToken = tokens.GenerateRefreshToken();
        var hashedNewToken = tokens.HashRefreshToken(rawNewToken);
        var newToken = Domain.Entities.RefreshToken.Create(user.Id, hashedNewToken);

        await refreshTokens.AddAsync(newToken, ct);
        await uow.SaveChangesAsync(ct);

        var accessToken = tokens.GenerateToken(user.Id, user.PhoneNumberValue, user.FullName);

        return new AuthTokenResponse(accessToken, rawNewToken);
    }
}