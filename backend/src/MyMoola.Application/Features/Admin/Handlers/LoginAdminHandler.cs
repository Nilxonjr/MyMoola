using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Admin.Commands;
using MyMoola.Domain.Exceptions;

namespace MyMoola.Application.Features.Admin.Handlers;

public sealed class LoginAdminHandler(
    IAdminRepository admins,
    IAdminTokenService tokenService,
    IUnitOfWork uow,
    ILogger<LoginAdminHandler> logger) : IRequestHandler<LoginAdminCommand, LoginAdminResponse>
{
    public async Task<LoginAdminResponse> Handle(
        LoginAdminCommand command,
        CancellationToken ct)
    {
        // 1. Load admin by email
        var admin = await admins.FindByEmailAsync(command.Email, ct)
            ?? throw new InvalidCredentialsException();

        // 2. Check account is active
        if (!admin.IsActive)
            throw new InvalidCredentialsException();

        // 3. Verify password
        if (!BCrypt.Net.BCrypt.Verify(command.Password, admin.PasswordHash))
            throw new InvalidCredentialsException();

        // 4. Record login timestamp
        admin.RecordLogin();

        // moved logging to interceptor
        // 5. Audit login
        //auditLog.Log(
        //    actorType: "admin",
        //    action: "admin.login",
        //    targetEntity: nameof(admin),
        //    targetId: admin.Id,
        //    actorId: admin.Id);

        await uow.SaveChangesAsync(ct);

        // 6. Generate token
        var token = tokenService.GenerateToken(admin);

        logger.LogInformation(
            "Admin logged in. AdminId={AdminId} Role={Role}",
            admin.Id, admin.Role);

        return new LoginAdminResponse(
            AccessToken: token,
            Role: admin.Role.ToString(),
            MustChangePassword: admin.MustChangePassword);
    }
}