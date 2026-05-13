using System.Security.Claims;
using Microsoft.AspNetCore.Http;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;

namespace MyMoola.Infrastructure.Services;

public sealed class CurrentAdminService(
    IHttpContextAccessor httpContextAccessor) : ICurrentAdminService
{
    public Guid? AdminId
    {
        get
        {
            var claim = httpContextAccessor.HttpContext?.User
                .FindFirst(AdminClaimNames.AdminId)?.Value;
            return Guid.TryParse(claim, out var id) ? id : null;
        }
    }

    public string? Role =>
        httpContextAccessor.HttpContext?.User
            .FindFirst(AdminClaimNames.Role)?.Value;

    public bool MustChangePassword =>
        httpContextAccessor.HttpContext?.User
            .FindFirst(AdminClaimNames.MustChangePassword)?.Value == "true";
}