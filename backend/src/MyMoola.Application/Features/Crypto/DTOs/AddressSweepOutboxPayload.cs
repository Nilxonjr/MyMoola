using MyMoola.Domain.Enums;

namespace MyMoola.Application.Features.Crypto.DTOs;

public sealed record AddressSweepOutboxPayload(
    Guid TransactionId,
    Guid DepositAddressId,
    Currency Currency,
    decimal Amount,
    string TxHash);