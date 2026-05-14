using MediatR;
using Microsoft.AspNetCore.Authorization;
using Microsoft.EntityFrameworkCore;
using Microsoft.AspNetCore.Mvc;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Application.Features.Users.Queries;
using MyMoola.Domain.Enums;
using MyMoola.Infrastructure.Persistence;

namespace MyMoola.API.Controllers;

[ApiController]
[Route("api/users")]
[Authorize]
public sealed class UsersController(
    ISender sender,
    AppDbContext db,
    ICurrentUserService currentUser) : ControllerBase
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
    public async Task<IActionResult> DeleteMyAccount(CancellationToken ct)
    {
        var userId = currentUser.UserId;
        if (userId is null)
            return Unauthorized();

        var existingUser = await db.Users.FirstOrDefaultAsync(u => u.Id == userId.Value, ct);
        if (existingUser is null)
            return NotFound();

        var walletIds = await db.Wallets
            .Where(w => w.UserId == userId.Value)
            .Select(w => w.Id)
            .ToListAsync(ct);

        var userTransactionIds = await db.Transactions
            .Where(t => t.InitiatorUserId == userId.Value || t.CounterpartyUserId == userId.Value)
            .Select(t => t.Id)
            .ToListAsync(ct);

        if (walletIds.Count > 0)
        {
            var ledgerEntries = db.LedgerEntries.Where(l => walletIds.Contains(l.WalletId));
            db.LedgerEntries.RemoveRange(ledgerEntries);
        }

        if (userTransactionIds.Count > 0)
        {
            var mpesaTransactions = db.MpesaTransactions.Where(m => userTransactionIds.Contains(m.TransactionId));
            db.MpesaTransactions.RemoveRange(mpesaTransactions);

            var transactions = db.Transactions.Where(t => userTransactionIds.Contains(t.Id));
            db.Transactions.RemoveRange(transactions);
        }

        var depositAddresses = db.DepositAddresses.Where(d => d.UserId == userId.Value);
        db.DepositAddresses.RemoveRange(depositAddresses);

        var wallets = db.Wallets.Where(w => w.UserId == userId.Value);
        db.Wallets.RemoveRange(wallets);

        var refreshTokens = db.RefreshTokens.Where(r => r.UserId == userId.Value);
        db.RefreshTokens.RemoveRange(refreshTokens);

        db.Users.Remove(existingUser);

        await db.SaveChangesAsync(ct);
        return NoContent();
    }
}
