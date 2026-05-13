using MediatR;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using MyMoola.Application.Features.Admin.Commands;

namespace MyMoola.API.Controllers;

[ApiController]
[Route("api/admin/auth")]
public sealed class AdminAuthController(ISender sender) : ControllerBase
{
    [HttpPost("login")]
    [ProducesResponseType(typeof(LoginAdminResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<IActionResult> Login(
        [FromBody] LoginAdminCommand command,
        CancellationToken ct)
    {
        var response = await sender.Send(command, ct);
        return Ok(response);
    }

    [HttpPost("change-password")]
    [Authorize(AuthenticationSchemes = "AdminBearer")]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<IActionResult> ChangePassword(
        [FromBody] ChangePasswordCommand command,
        CancellationToken ct)
    {
        await sender.Send(command, ct);
        return Ok(new { message = "Password changed successfully." });
    }
}