using MediatR;
using Microsoft.AspNetCore.Authorization;
using Microsoft.EntityFrameworkCore;
using Microsoft.AspNetCore.Mvc;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Users.Queries;
using MyMoola.Domain.Enums;
using MyMoola.Infrastructure.Persistence;
using MyMoola.Application.Features.Users.Commands;

namespace MyMoola.API.Controllers;

[ApiController]
[Route("api/users")]
[Authorize]
public sealed class UsersController(
    ISender sender) : ControllerBase
{
    [HttpGet("me")]
    [Authorize]
    [ProducesResponseType(typeof(GetMeResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetMe(CancellationToken ct)
    {
        var response = await sender.Send(new GetMeQuery(), ct);
        return Ok(response);
    }

    [HttpGet("me/balance")]
    [Authorize]
    [ProducesResponseType(typeof(GetBalanceResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<IActionResult> GetBalance(
        [FromQuery] DisplayCurrency currency = DisplayCurrency.KES,
        CancellationToken ct = default)
    {
        var response = await sender.Send(new GetBalanceQuery(currency), ct);
        return Ok(response);
    }

    [HttpGet("me/transactions")]
    [Authorize]
    [ProducesResponseType(typeof(GetTransactionsResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<IActionResult> GetTransactions(
        [FromQuery] int page = 1,
        [FromQuery] int pageSize = 20,
        CancellationToken ct = default)
    {
        var response = await sender.Send(new GetTransactionsQuery(page, pageSize), ct);
        return Ok(response);
    }

    [HttpGet("lookup")]
    [ProducesResponseType(typeof(LookupUserByPhoneResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<IActionResult> LookupByPhone(
    [FromQuery] string phone,
    CancellationToken ct)
    {
        var response = await sender.Send(new LookupUserByPhoneQuery(phone), ct);
        return Ok(response);
    }

    [HttpDelete("me")]
    [Authorize]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status422UnprocessableEntity)]
    public async Task<IActionResult> DeleteMyAccount(CancellationToken ct)
    {
        await sender.Send(new DeleteMyAccountCommand(), ct);
        return NoContent();
    }
}
