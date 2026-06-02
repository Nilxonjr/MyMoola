// MyMoola.API/Controllers/MpesaCallbackController.cs
using MediatR;
using Microsoft.AspNetCore.Mvc;
using MyMoola.Application.Features.Transactions.Commands;
using MyMoola.Application.Features.Transactions.DTOs;

namespace MyMoola.API.Controllers;

[ApiController]
[Route("api/mpesa/callbacks")]
public sealed class MpesaCallbackController(
    ISender sender,
    ILogger<MpesaCallbackController> logger) : ControllerBase
{
    [HttpPost("stk")]
    public async Task<IActionResult> StkCallback(
        [FromBody] StkCallbackPayload callback,
        CancellationToken ct)
    {
        try
        {
            await sender.Send(new ProcessStkCallbackCommand(callback), ct);
        }
        catch (Exception ex)
        {
            // Always return 200 to Safaricom — non-200 triggers external retry
            // which risks processing an already-completed callback.
            // Outbox handles internal retry independently.
            logger.LogError(ex,
                "STK callback handler failed. CheckoutRequestId={Id}",
                callback.Body.StkCallback.CheckoutRequestID);
        }

        return Ok();
    }

    [HttpPost("b2c")]
    public async Task<IActionResult> B2CCallback(
        [FromBody] B2CCallbackPayload callback,
        CancellationToken ct)
    {
        try
        {
            await sender.Send(new ProcessB2CCallbackCommand(callback), ct);
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "B2C callback handler failed.");
        }

        return Ok();
    }

    [HttpPost("b2c-timeout")]
    public async Task<IActionResult> B2CTimeout(
        [FromBody] B2CCallbackPayload callback,
        CancellationToken ct)
    {
        try
        {
            await sender.Send(new ProcessB2CTimeoutCommand(callback), ct);
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "B2C timeout handler failed.");
        }

        return Ok();
    }

    [HttpPost("b2b")]
    public async Task<IActionResult> B2BCallback(
    [FromBody] B2BCallbackPayload callback,
    CancellationToken ct)
    {
        try
        {
            await sender.Send(new ProcessB2BCallbackCommand(callback), ct);
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "B2B callback handler failed.");
        }
        return Ok();
    }

    [HttpPost("b2b-timeout")]
    public async Task<IActionResult> B2BTimeout(
        [FromBody] B2BCallbackPayload callback,
        CancellationToken ct)
    {
        try
        {
            await sender.Send(new ProcessB2BTimeoutCommand(callback), ct);
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "B2B timeout handler failed.");
        }
        return Ok();
    }
}