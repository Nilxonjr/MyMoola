using System.Security.Claims;
using Microsoft.AspNetCore.Http;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Common.Constants;

namespace MyMoola.Infrastructure.Services;

public sealed class CurrentUserService(IHttpContextAccessor httpContextAccessor) : ICurrentUserService
{
    public Guid? UserId
    {
        get
        {
            var claim = httpContextAccessor.HttpContext?.User
                .FindFirst(ClaimNames.UserId)?.Value;

            return Guid.TryParse(claim, out var id) ? id : null;
        }
    }
}