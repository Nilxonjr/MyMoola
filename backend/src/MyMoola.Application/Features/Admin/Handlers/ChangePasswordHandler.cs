using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Admin.Commands;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Admin.Handlers;

public sealed class ChangePasswordHandler(
    IAdminRepository admins,
    IAuditLogService auditLog,
    ICurrentAdminService currentAdmin,
    IUnitOfWork uow,
    ILogger<ChangePasswordHandler> logger) : IRequestHandler<ChangePasswordCommand>
{
    public async Task Handle(
        ChangePasswordCommand command,
        CancellationToken ct)
    {
        // 1. Get current admin from JWT
        if (currentAdmin.AdminId is null)
            throw new UnauthorizedException();

        var adminId = currentAdmin.AdminId.Value;

        // 2. Load admin
        var admin = await admins.FindByIdAsync(adminId, ct)
            ?? throw new NotFoundException(nameof(AdminUser), adminId);

        // 3. Verify current password
        if (!BCrypt.Net.BCrypt.Verify(command.CurrentPassword, admin.PasswordHash))
            throw new InvalidCredentialsException();

        // 4. Hash new password and change
        var newHash = BCrypt.Net.BCrypt.HashPassword(command.NewPassword, workFactor: 12);

        var beforeState = $"{{\"mustChangePassword\":{admin.MustChangePassword.ToString().ToLower()}}}";

        admin.ChangePassword(newHash);

        var afterState = $"{{\"mustChangePassword\":{admin.MustChangePassword.ToString().ToLower()}}}";

        // 5. Audit
        auditLog.Log(
            actorType: "admin",
            action: "admin.password_changed",
            targetEntity: nameof(admin),
            targetId: admin.Id,
            actorId: admin.Id,
            beforeState: beforeState,
            afterState: afterState);

        await uow.SaveChangesAsync(ct);

        logger.LogInformation(
            "Admin changed password. AdminId={AdminId}",
            adminId);
    }
}