using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Admin.Commands;
using MyMoola.Domain.Entities;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Admin.Handlers;

public sealed class CreateAdminHandler(
    IAdminRepository admins,
    IEmailService email,
    ICurrentAdminService currentAdmin,
    IUnitOfWork uow,
    ILogger<CreateAdminHandler> logger) : IRequestHandler<CreateAdminCommand, CreateAdminResponse>
{
    public async Task<CreateAdminResponse> Handle(
        CreateAdminCommand command,
        CancellationToken ct)
    {
        // 1. Get acting SuperAdmin from JWT
        if (currentAdmin.AdminId is null)
            throw new UnauthorizedException();

        var actorId = currentAdmin.AdminId.Value;

        // 2. Check email not already taken
        if (await admins.ExistsByEmailAsync(command.Email, ct))
            throw new ConflictException(nameof(AdminUser));

        // 3. Generate temporary password
        var tempPassword = GenerateTemporaryPassword();
        var passwordHash = BCrypt.Net.BCrypt.HashPassword(tempPassword, workFactor: 12);

        // 4. Create admin
        var admin = AdminUser.Create(
            name: command.Name,
            email: command.Email,
            passwordHash: passwordHash,
            role: command.Role,
            createdBy: actorId);

        await admins.AddAsync(admin, ct);

        // 5. Audit
        //auditLog.Log(
        //    actorType: "admin",
        //    action: "admin.created",
        //    targetEntity: nameof(AdminUser),
        //    targetId: admin.Id,
        //    actorId: actorId,
        //    afterState: $"{{\"name\":\"{admin.Name}\",\"email\":\"{admin.Email}\",\"role\":\"{admin.Role}\"}}");

        await uow.SaveChangesAsync(ct);

        // 6. Send temporary password via email
        await email.SendAsync(
            to: admin.Email,
            subject: "Welcome to MyMoola Admin",
            body: $"""
                Hi {admin.Name},

                Your admin account has been created.

                Email: {admin.Email}
                Temporary Password: {tempPassword}
                Role: {admin.Role}

                Please log in and change your password immediately.

                This is an automated message. Do not reply.
                """,
            ct);

        logger.LogInformation(
            "Admin created. AdminId={AdminId} Role={Role} CreatedBy={CreatedBy}",
            admin.Id, admin.Role, actorId);

        return new CreateAdminResponse(
            AdminId: admin.Id,
            Name: admin.Name,
            Email: admin.Email,
            Role: admin.Role.ToString());
    }

    private static string GenerateTemporaryPassword()
    {
        const string chars =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";

        return new string(
            Enumerable.Range(0, 12)
                .Select(_ => chars[Random.Shared.Next(chars.Length)])
                .ToArray());
    }
}