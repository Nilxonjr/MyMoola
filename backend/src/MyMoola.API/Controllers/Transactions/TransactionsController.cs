using MediatR;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using MyMoola.API.Attributes;
using MyMoola.Application.Features.Transactions.Commands;
using System.Reflection;

namespace MyMoola.API.Controllers.Transactions;

[ApiController]
[Route("api/transactions")]
public sealed class TransactionsController(ISender sender) : ControllerBase
{

    [Authorize]
    [HttpPost("send")]
    [Idempotency]
    //[EnableRateLimiting("transactions")]
    [ProducesResponseType(typeof(SendCryptoResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    [ProducesResponseType(StatusCodes.Status429TooManyRequests)]
    public async Task<IActionResult> Send(
        [FromBody] SendCryptoCommand command,
        CancellationToken ct)
    {
        var response = await sender.Send(command, ct);
        return Ok(response);
    }
}