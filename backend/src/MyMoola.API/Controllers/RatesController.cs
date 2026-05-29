using MediatR;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using MyMoola.Application.Features.Rates.Queries;

namespace MyMoola.API.Controllers;

[ApiController]
[Route("api/rates")]
[Authorize]
public sealed class RatesController(ISender sender) : ControllerBase
{
    [HttpGet("history")]
    [ProducesResponseType(typeof(GetRateHistoryResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status422UnprocessableEntity)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<IActionResult> GetHistory(
        [FromQuery] string currencies = "BTC,ETH,USDC",
        [FromQuery] string range = "7d",
        [FromQuery] string interval = "day",
        CancellationToken ct = default)
    {
        var response = await sender.Send(
            new GetRateHistoryQuery(currencies, range, interval),
            ct);
        return Ok(response);
    }
}