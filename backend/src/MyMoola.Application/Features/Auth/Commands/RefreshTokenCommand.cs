using MediatR;

namespace MyMoola.Application.Features.Auth.Commands;

/// <summary>
/// Exchanges a valid refresh token for a new access token and rotated refresh token.
/// The old refresh token is revoked immediately after use — one time only.
/// </summary>
public sealed record RefreshTokenCommand(string RefreshToken) : IRequest<AuthTokenResponse>;