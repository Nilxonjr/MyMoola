using MediatR;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using MyMoola.Application.Features.Crypto.Commands;
using System.Text.Json;

namespace MyMoola.API.Controllers
{
    [ApiController]
    [Route("api/webhooks")]
    public sealed class WebhooksController(ISender sender) : ControllerBase
    {
        [HttpPost("alchemy")]
        [ProducesResponseType(StatusCodes.Status200OK)]
        [ProducesResponseType(StatusCodes.Status403Forbidden)]
        public async Task<IActionResult> Alchemy(
            [FromBody] JsonElement payload,
            CancellationToken ct)
        {
            await sender.Send(new ProcessDepositWebhookCommand(payload.GetRawText()), ct);
            return Ok();
        }
    }
}
