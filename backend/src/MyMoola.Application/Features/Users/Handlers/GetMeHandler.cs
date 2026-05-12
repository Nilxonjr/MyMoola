using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;
using MyMoola.Application.Features.Users.Queries;

namespace MyMoola.Application.Features.Users.Handlers;

public sealed class GetMeHandler(
    ICurrentUserService currentUser,
    IUserRepository users,
    ILogger<GetMeHandler> logger)
    : IRequestHandler<GetMeQuery, GetMeResponse>
{
    public async Task<GetMeResponse> Handle(GetMeQuery request, CancellationToken ct)
    {
        if (currentUser.UserId is null)
            throw new UnauthorizedException();

        var userId = currentUser.UserId.Value;

        var user = await users.FindByIdAsync(userId, ct)
            ?? throw new NotFoundException(nameof(User), userId);

        logger.LogInformation("Profile fetched. UserId={UserId}", userId);

        return new GetMeResponse(
            Id: user.Id,
            FullName: user.FullName,
            Phone: user.PhoneNumberValue,
            Email: user.Email,
            IsEmailVerified: user.EmailVerifiedAt.HasValue,
            AccountStatus: user.AccountStatus.ToString(),
            KycStatus: user.KycStatus.ToString(),
            CreatedAt: user.CreatedAt);
    }
}