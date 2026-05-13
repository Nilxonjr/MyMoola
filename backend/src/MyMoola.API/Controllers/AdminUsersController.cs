using MediatR;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using MyMoola.API.Attributes;
using MyMoola.Application.Features.Admin.Commands;
using MyMoola.Application.Features.Admin.Queries;
using MyMoola.Domain.Enums;

namespace MyMoola.API.Controllers;

[ApiController]
[Route("api/admin/admins")]
[Authorize(AuthenticationSchemes = "AdminBearer")]
public sealed class AdminUsersController(ISender sender) : ControllerBase
{
    [HttpPost]
    [RequireAdminRole(AdminRole.SuperAdmin)]
    [ProducesResponseType(typeof(CreateAdminResponse), StatusCodes.Status201Created)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status403Forbidden)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    public async Task<IActionResult> CreateAdmin(
        [FromBody] CreateAdminCommand command,
        CancellationToken ct)
    {
        var response = await sender.Send(command, ct);
        return CreatedAtAction(nameof(GetAdmin), new { id = response.AdminId }, response);
    }

    [HttpGet]
    [RequireAdminRole(AdminRole.SuperAdmin)]
    [ProducesResponseType(typeof(ListAdminsResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status403Forbidden)]
    public async Task<IActionResult> ListAdmins(CancellationToken ct)
    {
        var response = await sender.Send(new ListAdminsQuery(), ct);
        return Ok(response);
    }

    [HttpGet("{id:guid}")]
    [RequireAdminRole(AdminRole.SuperAdmin)]
    [ProducesResponseType(typeof(AdminDto), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status403Forbidden)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetAdmin(Guid id, CancellationToken ct)
    {
        var response = await sender.Send(new GetAdminQuery(id), ct);
        return Ok(response);
    }

    [HttpPatch("{id:guid}/status")]
    [RequireAdminRole(AdminRole.SuperAdmin)]
    [ProducesResponseType(StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status403Forbidden)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<IActionResult> UpdateStatus(
        Guid id,
        [FromBody] UpdateAdminStatusCommand command,
        CancellationToken ct)
    {
        await sender.Send(command with { AdminId = id }, ct);
        return Ok(new { message = "Admin status updated successfully." });
    }
}