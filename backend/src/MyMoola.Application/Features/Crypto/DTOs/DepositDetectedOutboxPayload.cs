using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Crypto.DTOs;

public sealed record DepositDetectedOutboxPayload(
    Guid TransactionId,
    Guid UserId,
    Guid DepositAddressId,
    Currency Currency,
    decimal Amount,
    string TxHash);