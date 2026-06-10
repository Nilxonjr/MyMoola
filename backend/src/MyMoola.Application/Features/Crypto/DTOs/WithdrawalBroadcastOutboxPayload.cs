using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Crypto.DTOs;

public sealed record WithdrawalBroadcastOutboxPayload(
    Guid TransactionId,
    Guid UserId,
    Guid UserWalletId,
    Guid HotWalletId,
    Guid RevenueWalletId,
    Currency Currency,
    decimal Amount,
    decimal FeeAmount,
    decimal NetAmount,
    string ToAddress);