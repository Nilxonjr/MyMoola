using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Admin.Commands;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Admin.Handlers;

public sealed class UpdateAdminStatusHandler(
    IAdminRepository admins,
    ICurrentAdminService currentAdmin,
    IUnitOfWork uow,
    ILogger<UpdateAdminStatusHandler> logger) : IRequestHandler<UpdateAdminStatusCommand>
{
    public async Task Handle(
        UpdateAdminStatusCommand command,
        CancellationToken ct)
    {
        // 1. Get acting SuperAdmin
        if (currentAdmin.AdminId is null)
            throw new UnauthorizedException();

        var actorId = currentAdmin.AdminId.Value;

        // 2. Cannot deactivate yourself
        if (command.AdminId == actorId)
            throw new InvalidOperationException("You cannot change your own status.");

        // 3. Load target admin
        var admin = await admins.FindByIdAsync(command.AdminId, ct)
            ?? throw new NotFoundException(nameof(AdminUser), command.AdminId);

        var beforeState = $"{{\"isActive\":{admin.IsActive.ToString().ToLower()}}}";

        // 4. Apply status change
        if (command.Activate)
            admin.Activate();
        else
            admin.Deactivate();

        var afterState = $"{{\"isActive\":{admin.IsActive.ToString().ToLower()}}}";

        // 5. Audit
        //auditLog.Log(
        //    actorType: "admin",
        //    action: command.Activate ? "admin.activated" : "admin.deactivated",
        //    targetEntity: nameof(AdminUser),
        //    targetId: admin.Id,
        //    actorId: actorId,
        //    beforeState: beforeState,
        //    afterState: afterState);

        await uow.SaveChangesAsync(ct);

        logger.LogInformation(
            "Admin status updated. AdminId={AdminId} IsActive={IsActive} UpdatedBy={ActorId}",
            admin.Id, admin.IsActive, actorId);
    }
}