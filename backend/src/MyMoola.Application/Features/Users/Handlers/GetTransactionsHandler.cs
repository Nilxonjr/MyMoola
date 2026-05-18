using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Exceptions;
using MyMoola.Application.Features.Users.Queries;

namespace MyMoola.Application.Features.Users.Handlers;

public sealed class GetTransactionsHandler(
    ICurrentUserService currentUser,
    ITransactionRepository transactions,
    ILogger<GetTransactionsHandler> logger)
    : IRequestHandler<GetTransactionsQuery, GetTransactionsResponse>
{
    private const int MaxPageSize = 50;

    public async Task<GetTransactionsResponse> Handle(
        GetTransactionsQuery request, CancellationToken ct)
    {
        if (currentUser.UserId is null)
            throw new UnauthorizedException();

        var userId = currentUser.UserId.Value;
        var page = Math.Max(1, request.Page);
        var pageSize = Math.Clamp(request.PageSize, 1, MaxPageSize);

        var (items, totalCount) = await transactions
            .GetPagedByUserIdAsync(userId, page, pageSize, ct);

        var dtos = items.Select(t => new TransactionDto(
            Id: t.Id,
            ReferenceCode: t.ReferenceCode,
            Type: t.Type.ToString(),
            Status: t.Status.ToString(),
            InitiatorUserId: t.InitiatorUserId,
            CounterpartyUserId: t.CounterpartyUserId,
            Currency: t.Currency.ToString(),
            Amount: t.Amount,
            FeeAmount: t.FeeAmount,
            KesAmount: t.KesAmount,
            ExchangeRateSnapshot: t.ExchangeRateSnapshot,
            OnChainTxHash: t.OnChainTxHash,
            CreatedAt: t.CreatedAt,
            CompletedAt: t.CompletedAt))
            .ToList();

        var totalPages = (int)Math.Ceiling(totalCount / (double)pageSize);

        logger.LogInformation(
            "Transactions fetched. UserId={UserId} Page={Page} PageSize={PageSize} Total={Total}",
            userId, page, pageSize, totalCount);

        return new GetTransactionsResponse(
            Items: dtos,
            Page: page,
            PageSize: pageSize,
            TotalCount: totalCount,
            TotalPages: totalPages);
    }
}
