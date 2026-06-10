// MyMoola.API/Controllers/Transactions/TransactionsController.cs
using MediatR;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using MyMoola.Application.Features.Transactions.Commands;
using MyMoola.API.Filters;
using MyMoola.API.Attributes;
using MyMoola.Domain.Enums;
using MyMoola.Application.Features.Crypto.Commands;
using MyMoola.Application.Features.Crypto.Queries;

namespace MyMoola.API.Controllers.Transactions;

[ApiController]
[Route("api/transactions")]
[Authorize]
public sealed class TransactionsController(ISender sender) : ControllerBase
{
    [HttpPost("send")]
    [Idempotency]
    [EnableRateLimiting("transactions")]
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

    [HttpGet("quote/{currency}")]
    [ProducesResponseType(typeof(GetQuoteResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<IActionResult> GetQuote(
        [FromRoute] Currency currency,
        CancellationToken ct)
    {
        var response = await sender.Send(new GetQuoteCommand(currency), ct);
        return Ok(response);
    }

    [HttpPost("buy")]
    [Idempotency]
    [EnableRateLimiting("transactions")]
    [ProducesResponseType(typeof(BuyResponse), StatusCodes.Status202Accepted)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    [ProducesResponseType(StatusCodes.Status429TooManyRequests)]
    public async Task<IActionResult> Buy(
        [FromBody] BuyCommand command,
        CancellationToken ct)
    {
        var response = await sender.Send(command, ct);
        return Accepted(response);
    }

    [HttpPost("sell")]
    [Idempotency]
    [EnableRateLimiting("transactions")]
    [ProducesResponseType(typeof(SellResponse), StatusCodes.Status202Accepted)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    [ProducesResponseType(StatusCodes.Status422UnprocessableEntity)]
    [ProducesResponseType(StatusCodes.Status429TooManyRequests)]
    public async Task<IActionResult> Sell(
    [FromBody] SellCommand command,
    CancellationToken ct)
    {
        var response = await sender.Send(command, ct);
        return Accepted(response);
    }

    [HttpPost("pay-merchant")]
    [Authorize]
    [Idempotency]
    [EnableRateLimiting("transactions")]
    [ProducesResponseType(typeof(PayMerchantResponse), StatusCodes.Status202Accepted)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    [ProducesResponseType(StatusCodes.Status422UnprocessableEntity)]
    [ProducesResponseType(StatusCodes.Status429TooManyRequests)]
    public async Task<IActionResult> PayMerchant(
    [FromBody] PayMerchantCommand command,
    CancellationToken ct)
    {
        var response = await sender.Send(command, ct);
        return Accepted(response);
    }

    [HttpGet("withdrawal-quote")]
    [Authorize]
    [ProducesResponseType(typeof(GetWithdrawalQuoteResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<IActionResult> GetWithdrawalQuote(
    [FromQuery] Currency currency,
    CancellationToken ct)
    {
        var response = await sender.Send(new GetWithdrawalQuoteQuery(currency), ct);
        return Ok(response);
    }

    [HttpPost("withdraw")]
    [Authorize]
    [ProducesResponseType(typeof(WithdrawResponse), StatusCodes.Status202Accepted)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    public async Task<IActionResult> Withdraw(
        [FromBody] WithdrawCommand command,
        CancellationToken ct)
    {
        var response = await sender.Send(command, ct);
        return Accepted(response);
    }
}