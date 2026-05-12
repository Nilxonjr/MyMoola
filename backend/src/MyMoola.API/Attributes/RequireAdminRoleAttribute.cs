using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Filters;
using MyMoola.Application.Common.Constants;
using MyMoola.Domain.Enums;

namespace MyMoola.API.Attributes;

[AttributeUsage(AttributeTargets.Method | AttributeTargets.Class)]
public sealed class RequireAdminRoleAttribute(params AdminRole[] roles)
    : Attribute, IAuthorizationFilter
{
    public void OnAuthorization(AuthorizationFilterContext context)
    {
        var user = context.HttpContext.User;

        // 1. Must be authenticated with AdminBearer scheme
        if (!user.Identity?.IsAuthenticated ?? true)
        {
            context.Result = new UnauthorizedObjectResult(new
            {
                title = "Unauthorized",
                status = 401,
                detail = "Authentication is required."
            });
            return;
        }

        // 2. Check MustChangePassword — block all endpoints except change-password
        var mcp = user.FindFirst(AdminClaimNames.MustChangePassword)?.Value;
        if (mcp == "true")
        {
            var path = context.HttpContext.Request.Path.Value ?? string.Empty;
            if (!path.EndsWith("change-password", StringComparison.OrdinalIgnoreCase))
            {
                context.Result = new ObjectResult(new
                {
                    title = "Forbidden",
                    status = 403,
                    detail = "You must change your password before accessing this resource."
                })
                { StatusCode = StatusCodes.Status403Forbidden };
                return;
            }
        }

        // 3. Check role claim matches required roles
        var roleClaim = user.FindFirst(AdminClaimNames.Role)?.Value;

        if (string.IsNullOrEmpty(roleClaim))
        {
            context.Result = new ObjectResult(new
            {
                title = "Forbidden",
                status = 403,
                detail = "You do not have permission to access this resource."
            })
            { StatusCode = StatusCodes.Status403Forbidden };
            return;
        }

        if (!Enum.TryParse<AdminRole>(roleClaim, out var adminRole) ||
            !roles.Contains(adminRole))
        {
            context.Result = new ObjectResult(new
            {
                title = "Forbidden",
                status = 403,
                detail = "You do not have permission to access this resource."
            })
            { StatusCode = StatusCodes.Status403Forbidden };
            return;
        }
    }
}