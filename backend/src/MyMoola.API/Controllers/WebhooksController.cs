using MediatR;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using MyMoola.Application.Features.Crypto.Commands;
using System.Text.Json;

namespace MyMoola.API.Controllers
{
    [ApiController]
    [Route("api/webhooks")]
    public sealed class WebhooksController(
    ISender sender,
    ILogger<WebhooksController> logger) : ControllerBase
    {
        [HttpPost("alchemy")]
        [ProducesResponseType(StatusCodes.Status200OK)]
        public async Task<IActionResult> Alchemy(
            [FromBody] JsonElement payload,
            CancellationToken ct)
        {
            try
            {
                await sender.Send(new ProcessDepositWebhookCommand(payload.GetRawText()), ct);
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Alchemy webhook processing failed.");
            }

            return Ok();
        }
    }
}
