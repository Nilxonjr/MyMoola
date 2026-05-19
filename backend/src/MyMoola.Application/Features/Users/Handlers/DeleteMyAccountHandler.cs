using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Users.Commands;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Users.Handlers;

public sealed class DeleteMyAccountHandler(
    ICurrentUserService currentUser,
    IUserRepository users,
    IWalletRepository wallets,
    IRefreshTokenRepository refreshTokens,
    IUnitOfWork uow,
    ILogger<DeleteMyAccountHandler> logger)
    : IRequestHandler<DeleteMyAccountCommand>
{
    public async Task Handle(DeleteMyAccountCommand request, CancellationToken ct)
    {
        if (currentUser.UserId is null)
            throw new UnauthorizedException();

        var userId = currentUser.UserId.Value;

        var user = await users.FindByIdAsync(userId, ct)
            ?? throw new NotFoundException(nameof(User), userId);

        // Ensure no wallet has a balance before deleting
        var userWallets = await wallets.GetByUserIdAsync(userId, ct);
        if (userWallets.Any(w => w.TotalBalance > 0))
            throw new InvalidOperationException(
                "Cannot delete account with remaining balance. Please withdraw all funds first.");

        // Revoke all active refresh tokens
        await refreshTokens.RevokeAllForUserAsync(userId, ct);

        // Soft delete — mark as deleted rather than removing the row
        // Preserves transaction history and audit trail
        user.MarkDeleted();


        //auditLog.Log(
        //    actorType: "user",
        //    action: "account.deleted",
        //    targetEntity: nameof(User),
        //    targetId: userId,
        //    actorId: userId);


        await uow.SaveChangesAsync(ct);

        logger.LogInformation("Account deleted. UserId={UserId}", userId);
    }
}