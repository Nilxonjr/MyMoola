using MediatR;
using Microsoft.Extensions.Logging;
using MyMoola.Application.Common.Constants;
using MyMoola.Application.Common.Interfaces;
using MyMoola.Domain.Exceptions;
using MyMoola.Application.Features.Users.Queries;

namespace MyMoola.Application.Features.Users.Handlers;

public sealed class GetTransactionsHandler(
    ICurrentUserService currentUser,
    IUserRepository users,
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

        // Collect all user IDs referenced in these transactions
        // Exclude system wallet users — they are platform internals
        // and should never be shown to end users as counterparties
        var relatedUserIds = items
            .SelectMany(t => new[] { t.InitiatorUserId, t.CounterpartyUserId })
            .Where(id => id.HasValue)
            .Select(id => id!.Value)
            .Where(id => id != SystemWallets.OperationalBufferUserId
                      && id != SystemWallets.PlatformFeeUserId)
            .Distinct()
            .ToList();

        // Single batch query — not one query per transaction
        var relatedUsers = await users.GetByIdsAsync(relatedUserIds, ct);
        var phoneByUserId = relatedUsers.ToDictionary(u => u.Id, u => u.PhoneNumberValue);

        var dtos = items.Select(t => new TransactionDto(
            Id: t.Id,
            ReferenceCode: t.ReferenceCode,
            Type: t.Type.ToString(),
            Status: t.Status.ToString(),
            InitiatorUserId: t.InitiatorUserId,
            CounterpartyUserId: t.CounterpartyUserId,
            InteractedPhone: ResolveInteractedPhone(userId, t.InitiatorUserId, t.CounterpartyUserId, phoneByUserId),
            Currency: t.Currency.ToString(),
            Amount: t.Amount,
            FeeAmount: t.FeeAmount,
            KesAmount: t.KesAmount,
            ExchangeRateSnapshot: t.ExchangeRateSnapshot,
            MarketRateSnapshot: t.MarketRateSnapshot,
            OnChainTxHash: t.OnChainTxHash,
            OnChainConfirmations: t.OnChainConfirmations,
            MpesaReference: t.MpesaReference,
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

    /// <summary>
    /// Resolves the phone number of the other party in the transaction.
    /// If the current user is the initiator → return counterparty phone.
    /// If the current user is the counterparty → return initiator phone.
    /// Returns null for system transactions or if the user is not found.
    /// </summary>
    private static string? ResolveInteractedPhone(
        Guid currentUserId,
        Guid? initiatorUserId,
        Guid? counterpartyUserId,
        IReadOnlyDictionary<Guid, string> phoneByUserId)
    {
        var interactedUserId = initiatorUserId == currentUserId
            ? counterpartyUserId
            : initiatorUserId;

        if (!interactedUserId.HasValue)
            return null;

        return phoneByUserId.TryGetValue(interactedUserId.Value, out var phone)
            ? phone
            : null;
    }
}