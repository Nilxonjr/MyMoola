using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Crypto.DTOs;

public sealed record WithdrawalFailedOutboxPayload(
    Guid TransactionId,
    Guid UserWalletId,
    decimal Amount,
    string Reason);